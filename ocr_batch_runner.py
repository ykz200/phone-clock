#!/usr/bin/env python3
"""
阿里云 OCR 统一识别 — 批量消耗脚本
========================================
用途：把 26万+ 点额度在到期前跑完。
      逐张读图 → 调 OCR Unified 接口 → 保存识别结果、断点续传、限速 9 QPS。

一、安装依赖（首次运行前执行，PowerShell 管理员）：
    pip install alibabacloud-ocr-api20210707 alibabacloud-tea-openapi oss2

二、配置：修改 CONFIG 字典里的 access_key_id / access_key_secret（或改用环境变量）。
   环境变量优先：ALIBABA_CLOUD_ACCESS_KEY_ID / ALIBABA_CLOUD_ACCESS_KEY_SECRET

三、输入模式（三选一，见 CONFIG.input_mode）：

   ── 模式 "local"（默认）────────────────────────
   把图片丢进 ./ocr_images 文件夹，支持子目录。
   运行：python ocr_batch_runner.py

   ── 模式 "oss_list" ───────────────────────────
   准备一个文本文件 ./oss_urls.txt，每行一个 OSS 完整 URL：
     https://my-bucket.oss-cn-shanghai.aliyuncs.com/path/img001.jpg
     https://my-bucket.oss-cn-shanghai.aliyuncs.com/path/img002.png
   脚本逐行下载字节流后调 OCR，不落地。
   运行：python ocr_batch_runner.py

   ── 模式 "oss_bucket" ──────────────────────────
   直接扫 OSS bucket 全量对象：
     1. 在 CONFIG 里填 oss_bucket / oss_endpoint / oss_prefix
     2. 用 oss2 分页列出所有对象，流式读取送进 OCR
   运行：python ocr_batch_runner.py

四、进度恢复：
   按 Ctrl-C 中断；下次运行自动跳过已处理文件。
"""

import os
import sys
import json
import time
import base64
import signal
from pathlib import Path
from datetime import datetime
from typing import Optional, Iterable, Tuple

# ═══════════════════════════════════════════════
#  配置区（直接改或设环境变量）
# ═══════════════════════════════════════════════
CONFIG = {
    # ── 输入模式：local | oss_list | oss_bucket ──
    "input_mode":         "local",

    # ── AccessKey（也可设环境变量）──
    "access_key_id":      os.environ.get("ALIBABA_CLOUD_ACCESS_KEY_ID",    "LTAI5tXXXXXXXXXXXXXXXX"),
    "access_key_secret":  os.environ.get("ALIBABA_CLOUD_ACCESS_KEY_SECRET", "YOUR_SECRET_HERE"),
    "endpoint":           "ocr.cn-shanghai.aliyuncs.com",

    # ── 本地模式路径 ──
    "image_dir":          r".\ocr_images",
    "output_dir":         r".\ocr_results",
    "progress_file":      r".\ocr_progress.json",

    # ── OSS List 模式路径 ──
    "oss_url_file":       r".\oss_urls.txt",

    # ── OSS Bucket 模式参数 ──
    "oss_endpoint":       "https://oss-cn-shanghai.aliyuncs.com",
    "oss_bucket":         "your-bucket-name",
    "oss_prefix":         "",           # 只扫某个前缀目录，留空 = 全桶
    "oss_filter_exts":    {".jpg",".jpeg",".png",".bmp",".webp",".tif",".tiff",".gif",".pdf"},  # 只识别这些

    # ── 限流 ── OCR 统一识别 QPS 上限 10，留 1 缓冲
    "qps":                9,

    # ── 重试 ──
    "max_retries":        3,
    "retry_backoff":      2,   # 秒

    # ── 日志 ──
    "save_full_response": False,   # 是否保存完整 JSON 回包（大量跑时建议关）
}

SUPPORTED_EXTS = {".jpg", ".jpeg", ".png", ".bmp", ".webp", ".tif", ".tiff", ".gif", ".pdf"}

# ═══════════════════════════════════════════════
#  全局状态（供信号处理 & 进度保存用）
# ═══════════════════════════════════════════════
class GracefulExit(Exception): pass

_shutdown = False
def _on_sigint(signum, frame):
    global _shutdown
    print("\n[⚠]  收到中断信号，正在安全退出 …")
    _shutdown = True
signal.signal(signal.SIGINT, _on_sigint)


# ═══════════════════════════════════════════════
#  进度文件
# ═══════════════════════════════════════════════
def load_progress(path: str) -> dict:
    p = Path(path)
    if p.exists():
        try:
            with p.open(encoding="utf-8") as f:
                data = json.load(f)
            print(f"[✓]  已加载进度：已完成 {len(data.get('processed', []))} 个")
            return data
        except Exception as e:
            print(f"[!]  进度文件损坏 ({e})，重建")
    return {"processed": [], "total_calls": 0, "start_time": time.time()}


def save_progress(path: str, data: dict):
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    os.replace(tmp, path)


def ensure_dirs(*dirs):
    for d in dirs:
        Path(d).mkdir(parents=True, exist_ok=True)


# ═══════════════════════════════════════════════
#  输入源：本地目录
# ═══════════════════════════════════════════════
def scan_local_images(root: str) -> list:
    root_path = Path(root)
    if not root_path.exists():
        return []
    files = []
    for p in root_path.rglob("*"):
        if p.is_file() and p.suffix.lower() in SUPPORTED_EXTS:
            files.append(p)
    files.sort()
    return files


# ═══════════════════════════════════════════════
#  输入源：OSS URL 列表（逐行）
# ═══════════════════════════════════════════════
def scan_oss_url_file(file_path: str) -> list:
    p = Path(file_path)
    if not p.exists():
        return []
    urls = []
    for line in p.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#"):
            urls.append(line)
    return urls


def fetch_oss_url_bytes(url: str, timeout: int = 30) -> bytes:
    """HTTP GET 下载 OSS URL 的字节流（公开桶直接下，私有桶需要签名 URL）。"""
    import urllib.request
    req = urllib.request.Request(url, headers={"User-Agent": "ocr-batch/1.0"})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.read()


# ═══════════════════════════════════════════════
#  输入源：OSS Bucket 直接分页列举
# ═══════════════════════════════════════════════
def iter_oss_objects(endpoint: str, bucket: str, prefix: str = "",
                    ak: str = "", sk: str = "",
                    filter_exts: set = None) -> Iterable[Tuple[str, bytes]]:
    """用 oss2 分页列举 + 流式读取对象，产出 (key, bytes)。"""
    import oss2
    auth = oss2.Auth(ak, sk)
    bucket_obj = oss2.Bucket(auth, endpoint, bucket)

    marker = ""
    while True:
        result = bucket_obj.list_objects(prefix=prefix, marker=marker, max_keys=1000)
        for obj in result.object_list:
            key = obj.key
            # 过滤扩展名
            if filter_exts and Path(key).suffix.lower() not in filter_exts:
                continue
            # 跳过"目录"占位对象（以 / 结尾且大小为 0）
            if key.endswith("/") and obj.size == 0:
                continue
            # 流式读进内存后送入 OCR（OCR SDK 需要完整 body）
            try:
                data = bucket_obj.get_object(key).read()
                if data:
                    yield key, data
            except Exception as e:
                print(f"\n  [✗]  OSS 读 {key} 失败: {e}")

        if not result.is_truncated:
            break
        marker = result.next_marker


# ═══════════════════════════════════════════════
#  OCR 客户端 & 调用
# ═══════════════════════════════════════════════
def get_ocr_client(cfg: dict):
    from alibabacloud_ocr20210707.client import Client
    from alibabacloud_tea_openapi import models as open_api_models
    config = open_api_models.Config(
        access_key_id     = cfg["access_key_id"],
        access_key_secret = cfg["access_key_secret"],
        endpoint          = cfg["endpoint"],
    )
    return Client(config)


def call_ocr_bytes(client, image_bytes: bytes, cfg: dict):
    """把图片二进制直接送 RecognizeAllTextAdvance。"""
    from alibabacloud_ocr20210707 import models as ocr_models
    from alibabacloud_tea_openapi import models as open_api_models
    from Tea.exceptions import TeaException

    req = ocr_models.RecognizeAllTextAdvanceRequest(body=image_bytes)
    runtime = open_api_models.RuntimeOptions(
        read_timeout=30000,
        connect_timeout=10000,
        max_attempts=0,
    )
    last_err = None
    for attempt in range(cfg["max_retries"]):
        try:
            resp = client.recognize_all_text_advance_with_options(req, runtime)
            return resp.to_map()
        except TeaException as e:
            last_err = e
            code = getattr(e, "code", "") or ""
            msg  = getattr(e, "message", "") or ""
            if code in ("Throttling", "Throttling.User", "ServiceUnavailable") or "throttl" in msg.lower():
                wait = cfg["retry_backoff"] * (attempt + 1)
                print(f"\n    [⏳]  限流({code})，{wait}s 后重试 …")
                time.sleep(wait)
            else:
                print(f"\n    [✗]  API 错误(code={code}): {msg}")
                raise
        except Exception as e:
            last_err = e
            wait = cfg["retry_backoff"] * (attempt + 1)
            print(f"\n    [✗]  网络/未知错误: {e}，{wait}s 后重试 …")
            time.sleep(wait)
    raise RuntimeError(f"重试 {cfg['max_retries']} 次均失败: {last_err}")


def call_ocr_url(client, image_url: str, cfg: dict):
    """传 URL 给 OCR（OSS 公开桶 → 直接传 URL，免下载）。"""
    from alibabacloud_ocr20210707 import models as ocr_models
    from alibabacloud_tea_openapi import models as open_api_models
    from Tea.exceptions import TeaException

    req = ocr_models.RecognizeAllTextAdvanceRequest(url=image_url)
    runtime = open_api_models.RuntimeOptions(
        read_timeout=30000,
        connect_timeout=10000,
        max_attempts=0,
    )
    last_err = None
    for attempt in range(cfg["max_retries"]):
        try:
            resp = client.recognize_all_text_advance_with_options(req, runtime)
            return resp.to_map()
        except TeaException as e:
            last_err = e
            code = getattr(e, "code", "") or ""
            msg  = getattr(e, "message", "") or ""
            if code in ("Throttling", "Throttling.User", "ServiceUnavailable") or "throttl" in msg.lower():
                wait = cfg["retry_backoff"] * (attempt + 1)
                print(f"\n    [⏳]  限流({code})，{wait}s 后重试 …")
                time.sleep(wait)
            else:
                print(f"\n    [✗]  API 错误(code={code}): {msg}")
                raise
        except Exception as e:
            last_err = e
            wait = cfg["retry_backoff"] * (attempt + 1)
            print(f"\n    [✗]  网络/未知错误: {e}，{wait}s 后重试 …")
            time.sleep(wait)
    raise RuntimeError(f"重试 {cfg['max_retries']} 次均失败: {last_err}")


# ═══════════════════════════════════════════════
#  结果抽取 & 保存
# ═══════════════════════════════════════════════
def extract_text(resp_map: dict) -> str:
    text_parts = []
    def walk(obj, depth=0):
        if depth > 10:
            return
        if isinstance(obj, str):
            text_parts.append(obj)
        elif isinstance(obj, dict):
            for k in ("content", "text", "word", "prism_wnum", "line", "col", "row"):
                if k in obj and isinstance(obj[k], str):
                    text_parts.append(obj[k])
            for v in obj.values():
                walk(v, depth + 1)
        elif isinstance(obj, list):
            for item in obj:
                walk(item, depth + 1)
    walk(resp_map)
    return "\n".join(t for t in text_parts if t.strip())


def format_eta(done: int, left: int, qps: float) -> str:
    if qps <= 0 or left <= 0:
        return "—"
    secs = left / qps
    h, rem = divmod(int(secs), 3600)
    m, s = divmod(rem, 60)
    return f"{h:02d}:{m:02d}:{s:02d}"


def sanitize_filename(s: str) -> str:
    """把 OSS key 或本地路径转成安全文件名，用于保存结果。"""
    s = s.replace("\\", "__").replace("/", "__").replace(":", "_")
    if len(s) > 200:
        # 太长则用 hash 缩短
        import hashlib
        h = hashlib.md5(s.encode()).hexdigest()[:12]
        s = s[:100] + "__" + h + s[-50:]
    return s


# ═══════════════════════════════════════════════
#  限速器（滑动窗口）
# ═══════════════════════════════════════════════
def make_throttle(qps: int):
    call_times: list[float] = []
    def throttle():
        nonlocal call_times
        while True:
            now = time.time()
            call_times = [t for t in call_times if now - t < 1.0]
            if len(call_times) < qps:
                call_times.append(now)
                return
            sleep_to = call_times[0] + 1.0 - now
            if sleep_to > 0.001:
                time.sleep(sleep_to)
    return throttle


# ═══════════════════════════════════════════════
#  主流程
# ═══════════════════════════════════════════════
def main():
    cfg = CONFIG

    # 1) 校验 AccessKey
    if "YOUR" in cfg["access_key_id"] or "YOUR" in cfg["access_key_secret"]:
        print("[✗]  请先配置 AccessKey！")
        print("     方式一：改脚本里 CONFIG 字典")
        print("     方式二：设环境变量")
        print("       $env:ALIBABA_CLOUD_ACCESS_KEY_ID     = \"…\"")
        print("       $env:ALIBABA_CLOUD_ACCESS_KEY_SECRET = \"…\"")
        sys.exit(1)

    mode = cfg["input_mode"]

    # 2) 准备输入源
    if mode == "local":
        ensure_dirs(cfg["image_dir"], cfg["output_dir"])
        items = scan_local_images(cfg["image_dir"])
        if not items:
            print(f"[!]  {cfg['image_dir']} 下没找到图片。支持的格式：{SUPPORTED_EXTS}")
            sys.exit(0)
        print(f"[i]  [local] 找到 {len(items):,} 个本地文件")

    elif mode == "oss_list":
        items = scan_oss_url_file(cfg["oss_url_file"])
        if not items:
            print(f"[!]  {cfg['oss_url_file']} 为空或不存在。每行填一个 OSS URL。")
            sys.exit(0)
        print(f"[i]  [oss_list] 读取到 {len(items):,} 个 OSS URL")

    elif mode == "oss_bucket":
        ensure_dirs(cfg["output_dir"])
        if "your-bucket-name" in cfg["oss_bucket"]:
            print("[✗]  oss_bucket 模式需要配置：oss_endpoint / oss_bucket / oss_prefix")
            sys.exit(1)
        items = None  # 动态生成器
        print(f"[i]  [oss_bucket] 准备扫描 {cfg['oss_endpoint']}/{cfg['oss_bucket']}/{cfg['oss_prefix']}")
    else:
        print(f"[✗] 未知 input_mode: {mode}")
        sys.exit(1)

    # 3) 加载进度
    prog = load_progress(cfg["progress_file"])
    done_set = set(prog.get("processed", []))

    # 4) 连线 OCR
    print(f"[→]  正在连接 {cfg['endpoint']} …")
    client = get_ocr_client(cfg)

    # 5) 主循环
    throttle = make_throttle(cfg["qps"])
    ok = 0
    fail = 0
    start = time.time()

    def pbar(i, n, name):
        elapsed = time.time() - start
        qps_now = (ok + fail) / elapsed if elapsed > 0 else 0
        eta = format_eta(i, n - i, qps_now if qps_now > 0 else cfg["qps"])
        pct = (i / n) * 100 if n else 100
        sys.stdout.write(
            f"\r  [{i:>6d}/{n:<6d}] {pct:5.1f}% | "
            f"✓{ok} ✗{fail} | "
            f"{qps_now:4.1f} qps | "
            f"ETA {eta} | {name[:42]:<42s}"
        )
        sys.stdout.flush()

    if mode == "local":
        pending = [(str(p), p) for p in items if str(p) not in done_set]
        total = len(pending)
        print(f"[i]  待处理 {total:,} 个（跳过已完成 {len(items) - total:,} 个）")
        for idx, (key, file_path) in enumerate(pending, 1):
            if _shutdown:
                break
            throttle()
            try:
                data = file_path.read_bytes()
                resp = call_ocr_bytes(client, data, cfg)
                ok += 1
                text = extract_text(resp)
                out_stem = sanitize_filename(str(file_path.relative_to(cfg["image_dir"])))
                if cfg["save_full_response"]:
                    (Path(cfg["output_dir"]) / (out_stem + ".ocr.json")).write_text(
                        json.dumps(resp, ensure_ascii=False, indent=2), encoding="utf-8")
                (Path(cfg["output_dir"]) / (out_stem + ".txt")).write_text(text, encoding="utf-8")
            except Exception as e:
                fail += 1
                print(f"\n  [✗]  {file_path.name}: {e}")
            prog["processed"].append(key)
            prog["total_calls"] = ok + fail
            save_progress(cfg["progress_file"], prog)
            pbar(idx, total, file_path.name)

    elif mode == "oss_list":
        pending = [u for u in items if u not in done_set]
        total = len(pending)
        print(f"[i]  待处理 {total:,} 个 OSS URL")
        for idx, url in enumerate(pending, 1):
            if _shutdown:
                break
            throttle()
            try:
                # 先进性判断：若是公开 URL，优先走 URL 模式（省流量）
                resp = call_ocr_url(client, url, cfg)
                ok += 1
                text = extract_text(resp)
                out_stem = sanitize_filename(url)
                if cfg["save_full_response"]:
                    (Path(cfg["output_dir"]) / (out_stem + ".ocr.json")).write_text(
                        json.dumps(resp, ensure_ascii=False, indent=2), encoding="utf-8")
                (Path(cfg["output_dir"]) / (out_stem + ".txt")).write_text(text, encoding="utf-8")
            except Exception as e:
                fail += 1
                print(f"\n  [✗]  {url}: {e}")
            prog["processed"].append(url)
            prog["total_calls"] = ok + fail
            save_progress(cfg["progress_file"], prog)
            pbar(idx, total, url.split("?")[0].split("/")[-1])

    elif mode == "oss_bucket":
        # oss_bucket 模式：先把待处理列表枚举出来（含总数），再逐个跑
        all_keys = []
        for key, _data in iter_oss_objects(
            cfg["oss_endpoint"], cfg["oss_bucket"], cfg["oss_prefix"],
            ak=cfg["access_key_id"], sk=cfg["access_key_secret"],
            filter_exts=cfg.get("oss_filter_exts"),
        ):
            if key not in done_set:
                all_keys.append(key)
        total = len(all_keys)
        print(f"[i]  OSS 待处理 {total:,} 个对象")
        if total == 0:
            print("[✓]  没有新文件需要处理。")
            return

        import oss2
        _auth = oss2.Auth(cfg["access_key_id"], cfg["access_key_secret"])
        _bucket = oss2.Bucket(_auth, cfg["oss_endpoint"], cfg["oss_bucket"])

        for idx, key in enumerate(all_keys, 1):
            if _shutdown:
                break
            throttle()
            try:
                data = _bucket.get_object(key).read()
                if not data:
                    print(f"\n  [!]  {key}: 空对象，跳过")
                    continue
                resp = call_ocr_bytes(client, data, cfg)
                ok += 1
                text = extract_text(resp)
                out_stem = sanitize_filename(key)
                if cfg["save_full_response"]:
                    (Path(cfg["output_dir"]) / (out_stem + ".ocr.json")).write_text(
                        json.dumps(resp, ensure_ascii=False, indent=2), encoding="utf-8")
                (Path(cfg["output_dir"]) / (out_stem + ".txt")).write_text(text, encoding="utf-8")
            except Exception as e:
                fail += 1
                print(f"\n  [✗]  {key}: {e}")
            prog["processed"].append(key)
            prog["total_calls"] = ok + fail
            save_progress(cfg["progress_file"], prog)
            pbar(idx, total, key.split("/")[-1])

    # 6) 汇总
    print()
    print("═" * 60)
    print(f"  完成      ✓ {ok:,} / ✗ {fail:,}")
    elapsed = time.time() - start
    print(f"  用时      {elapsed:,.1f}s ({elapsed/60:.1f} min)")
    if elapsed > 0:
        print(f"  平均 QPS  {(ok+fail)/elapsed:.1f}")
    print(f"  结果目录  {cfg['output_dir']}")
    print(f"  进度文件  {cfg['progress_file']}")
    print("═" * 60)


if __name__ == "__main__":
    main()
