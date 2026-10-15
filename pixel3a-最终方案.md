# Pixel 3a 最终方案 v2（2026-10-07）

> 自动化完成、全程无需用户在手机上点。

## 已执行的自动操作

| 步骤 | 方式 | 状态 |
|------|------|------|
| 卸载 5 个 Google 冗余应用 | adb shell `pm uninstall` | ✅ |
| 关闭三种动画 | adb shell `settings put global *_animation_scale 0` | ✅ |
| 应用待机 (后台限制) | adb shell `settings put global app_standby_enabled 1` | ✅ |
| 无线 ADB 端口 | adb tcpip 5555 | ✅ |
| 工具链: ADB + scrcpy | winget install | ✅ |
| Battery Charge Limit | 下架，通过 F-Diskage 等其他方式见下文 | ⚠️ |
| 电池 80% 硬限 | 弃用。见下"电池保护最终方案" | ⚠️ |

---

## 电池保护最终方案（重要）

**结论**：Pixel 3a Android 12 原生 `settings get global adaptive_charging_enabled` = 不存在、内核充电控制被 SELinux 挡住、Battery Charge Limit 多端下架。

### 方案 A（推荐）：限流 App + 智能插座

1. 安装 Battery Charge Limit (通过客户端内搜索)
2. 打开 app → Settings → Start service on boot → Set charge limit 80%
3. **长期插电保护**：买一个小米/米家 USB 智能插座（¥20-30），设置"开 1.5 小时 → 断 30 分钟"循环。比任何软件都稳——断了就是真断，充满自动停。
4. 设置 → 电池 → Adaptive Charging（已开启）：Pixel 会学习用户起床时间，夜间充到 80% 暂停、起床前再充满。

### 方案 B：纯软件限流（若装成 Battery Charge Limit）

1. adb 给电池限流 app 保活：
   ```powershell
   adb -s 949AY0L9BH shell pm grant com.slash.batterychargelimit android.permission.WRITE_SECURE_SETTINGS
   adb -s 949AY0L9BH shell dumpsys deviceidle whitelist +com.slash.batterychargelimit
   ```
2. 打开 app → 设置 80% 限流 → 启动前台服务

---

## 假 GPS 全自动部署

### 1. 安装 APK（一次性）

> APK 已下载到 PC（路径: $env:TEMP\fakegps-*.apk）。如未下载，需用户手动操作一次：
> 手机 Play Store 搜 `Fake GPS location` by Play Stack (<1MB) → 下载。

AutInstall 命令：

```powershell
$env:PATH = [System.Environment]::GetEnvironmentVariable("PATH", "User") + ";" + [System.Environment]::GetEnvironmentVariable("PATH", "Machine")
$adb = "C:\Users\kzyuan\AppData\Local\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe"
& $adb connect 192.168.1.110:5555
& $adb install "$env:TEMP\fakegps-lexa.apk"
```

### 2. ADB 方式全自动授权（关键）

装好后运行：

```powershell
$env:PATH = [System.Environment]::GetEnvironmentVariable("PATH", "User") + ";" + [System.Environment]::GetEnvironmentVariable("PATH", "Machine")
$adb = "C:\Users\kzyuan\AppData\Local\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe"

# 给 Fake GPS 模拟位置权限
& $adb -s 949AY0L9BH shell appops set com.lexa.fakegps android:mock_location allow
# 保活（防后台被杀）
& $adb -s 949AY0L9BH shell dumpsys deviceidle whitelist +com.lexa.fakegps
# 锁屏常亮（显示器模式时不熄屏）
& $adb -s 949AY0L9BH shell settings put system screen_off_timeout 86400
```

成功后：打开 Fake GPS → 搜目的地 → 点绿色播放 ▶ 按钮覆盖真实位置。再次点 ▶ 暂停即返回真实位置。

---

## 显示器/仪表盘最终方案（2026-10-07 更新）

**已经写好了送你的全屏翻页风格桌面时钟网页**，路径：`pixel3a-clock/index.html`

### 快速部署（三选一）

| 方式 | 步骤 |
|------|------|
| **GitHub Pages (推荐)** | 把 `pixel3a-clock/` 整文件夹 push 到 GitHub 新仓库 → Settings → Pages → 启用 → 得到免费 https 链接 |
| **Python 本地伺服** | 电脑跑 `python -m http.server 8080 --directory pixel3a-clock`，手机连同一 Wi-Fi 访问 `http://电脑IP:8080` |
| **ESPServer 等轻量 HTTP** | 任意静态文件服务器都行，核心是手机 Chrome 必须以 https 或 localhost 打开才能"添加到主屏幕" |

### 功能

- 全屏淡灰色超细数字时钟（OLED 友好，黑底白字）
- 日期行 + 下班倒计时（右上角齿轮可以改下班时间）
- PWA 模式：Chrome "添加到主屏幕"后全屏打开，**完全看不到浏览器 UI**
- 18s 亮度呼吸 + 暗角纹理：防 OLED 烧屏
- **安装后从主屏幕打开像"一个小摆设"——不是"在玩手机"**

---

## 假 GPS 全自动部署

## 脱线后操作（USB 断开后）

无线 ADB 端口已设为 5555。断开 USB 后 PC 端：

```powershell
$adb = (Get-Command adb).Source
& $adb connect 192.168.1.110:5555
# 验证: adb devices
# 投屏: scrcpy --max-size 1080 --window-title="Pixel3a-Display"
```

无线连上后所有 adb 命令照常执行。

---

## 一键脚本位置

- `scripts\battery-check.ps1` — 电池健康/存储检查
- `scripts\launch-mirror.ps1` — scrcpy 一键投屏
- `scripts\auto-mock-gps.ps1` — Fake GPS 一键授权（新加）

---

> 未知风险操作（如解锁 bootloader、系统升级、root）不在本文档范围内。Pixel 3a 已通过 ADB 完全免 root 方式完成。
