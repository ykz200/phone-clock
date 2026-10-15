#!/usr/bin/env python3
"""
阿里云 OCR 统一识别 — 排查专用版
==================================
把 OSS 上所有图片批量 OCR，识别结果入库 SQLite（支持全文检索），
方便按关键词、路径、时间排查图片内容。

一、安装依赖：
    pip install alibabacloud-ocr-api20210707 alibabacloud-tea-openapi oss2

二、配置 CONFIG（AK 也可走环境变量）：
    $env:ALIBABA_CLOUD_ACCESS_KEY_ID     = "…"
    $env:ALIBABA_CLOUD_ACCESS_KEY_SECRET = "…"

三、运行：
    python ocr_investigation.py                  # 跑 OCR 入库
    python ocr_investigation.py --search "嫌疑人关键词"  # 只查库，不打 OCR
    python ocr_investigation.py --search "keyword" --limit 20

四、数据库：
    默认保存在 ./ocr_investigation.db
    表 records 字段：
        id          INTEGER PK
        source_key  TEXT    -- OSS key 或本地文件名
        source_type TEXT    -- local|oss
        text_content TEXT   -- OCR 识别的完整文字
        file_size   INTEGER -- 图片字节数
        file_ext    TEXT
        confidence  REAL    -- OCR 平均置信度（如有）
        ocr_at      TEXT    -- ISO8601 时间
        ocr_cost_ms INTEGER -- 本次调用耗时毫秒
        
    FTS5 虚拟表 records_fts 支持全文检索。

五、进度：
    进程内按 source_key 去重；按 Ctrl-C 中断后下次追问自动跳过。
"""

import os
import sys
import json
import time
import signal
import sqlite3
import argparse
from pathlib import Path
from datetime import datetime, timezone
from typing import Optional, Iterable, Tuple

# ═══════════════════════════════════════════════
#  配置
# ═══════════════════════════════════════════════
CONFIG = {
    # ── 输入模式：local | oss_bucket | oss_list ──
    "input_mode":         "local",

    # ── AccessKey（优先读环境变量）──
    "access_key_id":      os.environ.get("ALIBABA_CLOUD_ACCESS_KEY_ID",    "LTAI5tXXXXXXXXXXXXXXXX"),
    "access_key_secret":  os.environ.get("ALIBABA_CLOUD_ACCESS_KEY_SECRET", "YOUR_SECRET_HERE"),
    "endpoint":           "ocr.cn-shanghai.aliyuncs.com",

    # ── 本地模式 ──
    "image_dir":          r".\ocr_images",

    # ── OSS List 模式 ──
    "oss_url_file":       r".\oss_urls.txt",

    # ── OSS Bucket 模式 ──
    "oss_endpoint":       "https://oss-cn-shanghai.aliyuncs.com",
    "oss_bucket":         "your-bucket-name",
    "oss_prefix":         "",
    "oss_filter_exts":    {".jpg",".jpeg",".png",".bmp",".webp",".tif",".tiff",".gif",".pdf"},

    # ── 限流 / 重试 ──
    "qps":                9,
    "max_retries":        3,
    "retry_backoff":      2,

    # ── 数据库 ──
    "db_path":            r".\ocr_investigation.db",
}

SUPPORTED_EXTS = CONFIG["oss_filter_exts"]

# ═══════════════════════════════════════════════
#  退出信号处理
# ═══════════════════════════════════════════════
_shutdown = False
def _on_sigint(signum, frame):
    global _shutdown
    print("\n[⚠]  中断信号收到，安全停止 …")
    _shutdown = True
signal.signal(signal.SIGINT, _on_sigint)


# ═══════════════════════════════════════════════
#  SQLite 初始化 & 读写
# ═══════════════════════════════════════════════
def init_db(db_path: str) -> sqlite3.Connection:
    conn = sqlite3.connect(db_path)
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA synchronous=NORMAL")
    conn.execute("""
        CREATE TABLE IF NOT EXISTS records (
            id            INTEGER PRIMARY KEY AUTOINCREMENT,
            source_key    TEXT UNIQUE NOT NULL,
            source_type   TEXT NOT NULL,
            text_content  TEXT NOT NULL DEFAULT '',
            file_size     INTEGER,
            file_ext      TEXT,
            confidence    REAL,
            ocr_at        TEXT NOT NULL,
            ocr_cost_ms   INTEGER
        )
    """)
    # 全文检索
    conn.execute("""
        CREATE VIRTUAL TABLE IF NOT EXISTS records_fts USING fts5(
            source_key,
            text_content,
            content='records',
            content_rowid='id',
            tokenize='unicode61'
        )
    """)
    # 触发器：保持 FTS 同步
    for trigger in ("ai", "ad", "au"):
        pass  # 显式写插入删除逻辑代替触发器，更可控
    conn.commit()
    return conn


def db_insert_record(conn: sqlite3.Connection, *,
                     source_key: str, source_type: str,
                     text_content: str,
                     file_size: Optional[int] = None,
                     file_ext: str = "",
                     confidence: Optional[float] = None,
                     cost_ms: int = 0):
    cur = conn.execute(
        """INSERT INTO records
           (source_key, source_type, text_content, file_size, file_ext, confidence, ocr_at, ocr_cost_ms)
           VALUES (?,?,?,?,?,?,?,?)
           ON CONFLICT(source_key) DO UPDATE SET
             text_content=excluded.text_content,
             confidence=excluded.confidence,
             ocr_at=excluded.ocr_at,
             ocr_cost_ms=excluded.ocr_cost_ms""",
        (source_key, source_type, text_content, file_size, file_ext,
         confidence, datetime.now(timezone.utc).isoformat(timespec="seconds"), cost_ms)
    )
    rowid = cur.lastrowid
    # 同步 FTS
    conn.execute("DELETE FROM records_fts WHERE rowid=?", (rowid,))
    conn.execute(
        "INSERT INTO records_fts(rowid, source_key, text_content) VALUES (?,?,?)",
        (rowid, source_key, text_content)
    )
    conn.commit()
    return rowid


def db_has_key(conn: sqlite3.Connection, source_key: str) -> bool:
    cur = conn.execute("SELECT 1 FROM records WHERE source_key=? LIMIT 1", (source_key,))
    return cur.fetchone() is not None


def db_get_done_set(conn: sqlite3.Connection) -> set:
    cur = conn.execute("SELECT source_key FROM records")
    return {row[0] for row in cur.fetchall()}


def db_search(conn: sqlite3.Connection, query: str, limit: int = 50) -> list:
    # FTS5 查询，支持 AND/OR/NOT，前缀匹配等
    sql = """
        SELECT r.source_key, r.source_type, r.text_content, r.confidence, r.ocr_at,
               snippet(records_fts, 2, '<mark>', '</mark>', '…', 32) AS snip
        FROM records_fts f
        JOIN records r ON r.id = f.rowid
        WHERE records_fts MATCH ?
        ORDER BY rank
        LIMIT ?
    """
    cur = conn.execute(sql, (query, limit))
    return cur.fetchall()


def db_stats(conn: sqlite3.Connection) -> dict:
    cur = conn.execute("SELECT COUNT(*), SUM(file_size), AVG(confidence) FROM records")
    total, bytes_sum, avg_conf = cur.fetchone()
    cur = conn.execute("SELECT COUNT(*) FROM records WHERE text_content=''")
    empty = cur.fetchone()[0]
    return {
        "total": total or 0,
        "has_text": (total or 0) - (empty or 0),
        "empty": empty or 0,
        "total_mb": round((bytes_sum or 0) / 1024 / 1024, 1),
        "avg_confidence": round(avg_conf, 3) if avg_conf else None,
    }


def db_export_missing(conn: sqlite3.Connection, output_path: str):
    """把 text_content 为空的记录导出对应的 source_key 清单"""
    cur = conn.execute("SELECT source_key FROM records WHERE text_content=''")
    with open(output_path, "w", encoding="utf-8") as f:
        for (k,) in cur.fetchall():
            f.write(k + "\n")
    print(f"[→]  空结果清单已导出到 {output_path}")


# ═══════════════════════════════════════════════
#  输入源枚举
# ═══════════════════════════════════════════════
def scan_local_images(root: str) -> list:
    p = Path(root)
    if not p.exists():
        return []
    out = [f for f in p.rglob("*") if f.is_file() and f.suffix.lower() in SUPPORTED_EXTS]
    out.sort()
    return out


def scan_oss_url_file(file_path: str) -> list:
    p = Path(file_path)
    if not p.exists():
        return []
    out = []
    for line in p.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#"):
            out.append(line)
    return out


def iter_oss_objects(endpoint: str, bucket: str, prefix: str,
                    ak: str, sk: str,
                    filter_exts: set) -> Iterable[Tuple[str, bytes]]:
    import oss2
    auth = oss2.Auth(ak, sk)
    b = oss2.Bucket(auth, endpoint, bucket)
    marker = ""
    while True:
        res = b.list_objects(prefix=prefix, marker=marker, max_keys=1000)
        for obj in res.object_list:
            key = obj.key
            if filter_exts and Path(key).suffix.lower() not in filter_exts:
                continue
            if key.endswith("/") and obj.size == 0:
                continue
            try:
                data = b.get_object(key).read()
                if data:
                    yield key, data
            except Exception as e:
                print(f"\n  [✗]  OSS 读 {key} 失败: {e}")
        if not res.is_truncated:
            break
        marker = res.next_marker


def fetch_url_bytes(url: str, timeout: int = 30) -> bytes:
    import urllib.request
    req = urllib.request.Request(url, headers={"User-Agent": "ocr-investigation/1.0"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read()


# ═══════════════════════════════════════════════
#  OCR 部分
# ═══════════════════════════════════════════════
def get_ocr_client(cfg: dict):
    from alibabacloud_ocr20210707.client import Client
    from alibabacloud_tea_openapi import models as open_api_models
    return Client(open_api_models.Config(
        access_key_id=cfg["access_key_id"],
        access_key_secret=cfg["access_key_secret"],
        endpoint=cfg["endpoint"],
    ))


def call_ocr_with_retry(client, build_req, cfg: dict):
    from alibabacloud_tea_openapi import models as open_api_models
    from Tea.exceptions import TeaException

    runtime = open_api_models.RuntimeOptions(
        read_timeout=30000, connect_timeout=10000, max_attempts=0)
    last_err = None
    for attempt in range(cfg["max_retries"]):
        try:
            t0 = time.time()
            resp = client.recognize_all_text_advance_with_options(build_req(), runtime)
            cost_ms = int((time.time() - t0) * 1000)
            return resp.to_map(), cost_ms
        except TeaException as e:
            last_err = e
            code = getattr(e, "code", "") or ""
            msg = getattr(e, "message", "") or ""
            if code in ("Throttling", "Throttling.User", "ServiceUnavailable") or "throttl" in msg.lower():
                wait = cfg["retry_backoff"] * (attempt + 1)
                print(f"\n    [⏳]  {code} → {wait}s 后重试")
                time.sleep(wait)
            else:
                raise
        except Exception as e:
            last_err = e
            time.sleep(cfg["retry_backoff"] * (attempt + 1))
    raise RuntimeError(f"重试 {cfg['max_retries']} 次失败: {last_err}")


def extract_text(resp_map: dict) -> tuple:
    """返回 (full_text, avg_confidence)。"""
    texts = []
    confidences = []

    def walk(obj, depth=0):
        if depth > 12:
            return
        if isinstance(obj, dict):
            for k in ("content", "text", "word", "prism_wnum", "line", "col", "row"):
                if k in obj and isinstance(obj[k], str):
                    texts.append(obj[k])
            if "confidence" in obj:
                try:
                    confidences.append(float(obj["confidence"]))
                except Exception:
                    pass
            if "prob" in obj and isinstance(obj["prob"], (int, float)):
                confidences.append(float(obj["prob"]))
            for v in obj.values():
                walk(v, depth + 1)
        elif isinstance(obj, list):
            for item in obj:
                walk(item, depth + 1)
        elif isinstance(obj, str):
            texts.append(obj)

    walk(resp_map)
    full = "\n".join(t for t in texts if t.strip())
    avg_c = round(sum(confidences) / len(confidences), 4) if confidences else None
    return full, avg_c


def make_throttle(qps: int):
    calls = []
    def _t():
        nonlocal calls
        while True:
            now = time.time()
            calls = [c for c in calls if now - c < 1.0]
            if len(calls) < qps:
                calls.append(now)
                return
            time.sleep(max(calls[0] + 1.0 - now, 0.001))
    return _t


# ═══════════════════════════════════════════════
#  RUN 模式：抓图 + OCR → 入库
# ═══════════════════════════════════════════════
def run_ocr(cfg: dict):
    from alibabacloud_ocr20210707 import models as ocr_models

    if "YOUR" in cfg["access_key_id"] or "YOUR" in cfg["access_key_secret"]:
        print("[✗]  请先配置 AccessKey")
        sys.exit(1)

    conn = init_db(cfg["db_path"])
    done_set = db_get_done_set(conn)
    print(f"[i]  库中已有 {len(done_set):,} 条，本次跳过它们")

    mode = cfg["input_mode"]

    # ── items 生成器：产出 (source_key, bytes, file_size, file_ext) ──
    def gen_items():
        if mode == "local":
            for p in scan_local_images(cfg["image_dir"]):
                yield str(p), p.read_bytes(), p.stat().st_size, p.suffix.lower()
        elif mode == "oss_list":
            for url in scan_oss_url_file(cfg["oss_url_file"]):
                try:
                    data = fetch_url_bytes(url)
                    key = url.split("?")[0].split("/")[-1] or url
                    yield f"oss://{key}", data, len(data), Path(key).suffix.lower()
                except Exception as e:
                    print(f"\n  [✗]  URL 下载失败 {url}: {e}")
        elif mode == "oss_bucket":
            if "your-bucket-name" in cfg["oss_bucket"]:
                print("[✗]  请配置 oss_endpoint / oss_bucket")
                sys.exit(1)
            for key, data in iter_oss_objects(
                cfg["oss_endpoint"], cfg["oss_bucket"], cfg["oss_prefix"],
                cfg["access_key_id"], cfg["access_key_secret"],
                cfg["oss_filter_exts"],
            ):
                yield key, data, len(data), Path(key).suffix.lower()

    client = get_ocr_client(cfg)
    throttle = make_throttle(cfg["qps"])

    total_ok = 0
    total_fail = 0
    total_skip = 0
    start = time.time()
    last_commit = time.time()

    for src_key, data, size, ext in gen_items():
        if _shutdown:
            break
        if src_key in done_set:
            total_skip += 1
            continue

        throttle()

        def build_req(d=data, e=ext):
            return ocr_models.RecognizeAllTextAdvanceRequest(body=d)

        try:
            resp_map, cost_ms = call_ocr_with_retry(client, build_req, cfg)
            text, conf = extract_text(resp_map)
            total_ok += 1
        except Exception as e:
            total_fail += 1
            print(f"\n  [✗]  {src_key}: {e}")
            text, conf = "", None
            cost_ms = 0

        db_insert_record(
            conn,
            source_key=src_key,
            source_type="oss" if src_key.startswith("oss://") else mode,
            text_content=text,
            file_size=size,
            file_ext=ext,
            confidence=conf,
            cost_ms=cost_ms,
        )

        # 进度
        elapsed = time.time() - start
        qps_now = (total_ok + total_fail) / elapsed if elapsed > 0 else 0
        done = total_ok + total_fail
        name = src_key if len(src_key) <= 50 else "…" + src_key[-49:]
        sys.stdout.write(
            f"\r  ✓{total_ok} ✗{total_fail} ⊙跳过{total_skip} | "
            f"{qps_now:4.1f} qps | {name:<50s}"
        )
        sys.stdout.flush()

        # 定期 checkpoint
        if time.time() - last_commit > 30:
            conn.execute("PRAGMA optimize")
            last_commit = time.time()

    # 汇总
    stats = db_stats(conn)
    conn.close()
    elapsed = time.time() - start
    print()
    print("═" * 60)
    print(f"  本次新增   ✓ {total_ok:,} / ✗ {total_fail:,} / ⊙跳过 {total_skip:,}")
    print(f"  用时       {elapsed:,.1f}s ({elapsed/60:.1f} min)")
    print(f"  数据库     {cfg['db_path']}")
    print(f"  库内总计   {stats['total']:,} 条 ({stats['has_text']:,} 条有文字)")
    print(f"  总大小     {stats['total_mb']} MB, 平均置信度 {stats['avg_confidence']}")
    print("═" * 60)


# ═══════════════════════════════════════════════
#  SEARCH 模式：纯查库
# ═══════════════════════════════════════════════
def run_search(cfg: dict, query: str, limit: int = 50, show_full: bool = False):
    db_path = cfg["db_path"]
    if not Path(db_path).exists():
        print(f"[✗]  数据库 {db_path} 不存在，先跑 --run 攒数据")
        sys.exit(1)

    conn = init_db(db_path)
    stats = db_stats(conn)

    if not query:
        print(f"[i]  库状态：{stats['total']:,} 条，{stats['has_text']:,} 条有文字，共 {stats['total_mb']} MB")
        sys.exit(0)

    print(f"[i]  搜索: {query!r}  (limit={limit})")
    rows = db_search(conn, query, limit)
    if not rows:
        print("  (无结果)")
        return

    print(f"[✓]  命中 {len(rows)} 条：")
    print("-" * 80)
    for source_key, src_type, full_text, conf, ott, snip in rows:
        print(f"  key   : {source_key}")
        print(f"  类型  : {src_type}  |  置信度: {conf}  |  时间: {ott}")
        if show_full:
            preview = full_text[:500] + ("…" if len(full_text) > 500 else "")
            print(f"  全文  : {preview}")
        else:
            print(f"  摘要  : {snip}")
        print("-" * 80)
    conn.close()


# ═══════════════════════════════════════════════
#  CLI
# ═══════════════════════════════════════════════
def main():
    ap = argparse.ArgumentParser(
        description="阿里云 OCR 排查工具：批量 OCR OSS 图片 + 全文检索")
    ap.add_argument("--run", action="store_true",
                    help="跑 OCR 并建/更新库")
    ap.add_argument("--search", type=str, default=None,
                    help="全文搜索关键词（FTS5 语法，如 '张三 AND 李四'）")
    ap.add_argument("--limit", type=int, default=50,
                    help="搜索结果数量上限")
    ap.add_argument("--full", action="store_true",
                    help="搜索结果展示全文")
    ap.add_argument("--stats", action="store_true",
                    help="显示数据库统计")
    ap.add_argument("--export-empty", type=str, default=None,
                    help="导出 OCR 结果为空的 source_key 清单到文件")
    args = ap.parse_args()

    cfg = CONFIG

    # 默认没参数 → run
    if not any([args.run, args.search, args.stats, args.export_empty]):
        args.run = True

    if args.run:
        run_ocr(cfg)
        return

    if args.search is not None or args.stats:
        if args.export_empty:
            conn = init_db(cfg["db_path"])
            db_export_missing(conn, args.export_empty)
            conn.close()
        run_search(cfg, args.search or "", args.limit, args.full)
        return


if __name__ == "__main__":
    main()
