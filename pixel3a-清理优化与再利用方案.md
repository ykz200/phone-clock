# Pixel 3a 深度清理优化与再利用方案

> **设备**：Google Pixel 3a | **系统**：Android 12 (官方最终版) | **状态**：未 Root、bootloader 锁  
> **原则**：不能变砖、不能升级系统、全程通过 ADB（免 Root）完成

---

## 现状评估

| 问题 | 原因 | 解决思路 |
|------|------|----------|
| 卡顿 | App 过多、后台常驻、存储满 | 禁用/卸载用户应用 + 关闭动画 + 限制后台进程 |
| 存储快满 | Android 12 后 OTA 残留、应用缓存 | 清理 cache + 移除不用的预装应用（ADB disable） |
| 电池老化 | Pixel 3a 发布于 2019，电池衰减 | 关闭 5G 类选项、降低刷新率压力、开启电池保护 |
| 与电脑连接 | 无 | ADB 无线 + scrcpy 局域网投屏操控 |
| 当显示器用 | 无 | 仪表监控场景常驻 + 偶尔当 PC 第二屏 |

---

## 第一阶段：连接准备（电脑端一次性配置）

### 1. 安装 ADB 工具

Pixel 3a 需要 **Google USB Driver** + **ADB 工具链**。最简单的方式是安装 **SDK Platform Tools**：

1. 访问 https://developer.android.com/tools/releases/platform-tools
2. 下载 Windows 版 `platform-tools-windows-*.zip`
3. 解压到 `C:\platform-tools`（固定路径，方便后续命令）
4. 将其加入系统环境变量：

```powershell
# PowerShell 管理员
[System.Environment]::SetEnvironmentVariable("Path", $env:Path + ";C:\platform-tools", [System.EnvironmentVariableTarget::User)
```

5. 新开 PowerShell 验证：

```powershell
adb version
# 应输出 Android Debug Bridge version x.x.x
```

### 2. 安装 scrcpy（电脑端投屏操控）

scrcpy 是开源免安装方案，通过 ADB 反向端口 + 本地解码，延迟极低。

1. 访问 https://github.com/Genymobile/scrcpy/releases
2. 下载最新版 `scrcpy-win64-*.zip`
3. 解压到 `C:\scrcpy`
4. 可选加入环境变量，或直接用完整路径执行：

```powershell
& "C:\scrcpy\scrcpy.exe"
```

### 3. 手机端开启开发者选项与 USB 调试

1. 手机设置 → 关于手机 → 连续点 **版本号** 7 次，开启开发者模式
2. 设置 → 系统 → 开发者选项 → 开启 **USB 调试**
3. 首次连接时手机上会弹 "允许 USB 调试" → 勾选 **始终允许**

### 4. 第一次 USB 连接验证

```powershell
adb devices
# 输出应包含 xxxxxxxx device（不是 unauthorized 或 offline）
```

如果显示 **unauthorized**，重新插拔、手机上重新允许；  
如果显示 **offline**，先 `adb kill-server` 再 `adb start-server` 重试。

### 5. 配置无线 ADB（后续可免 USB）

USB 连接正常后，开一个端口：

```powershell
adb tcpip 5555
adb connect 192.168.x.x:5555   # 替换为手机局域网 IP
```

以后只要手机和电脑在同一局域网，直接 `adb connect IP:5555` 即可，无需 USB。

> **建议**：在路由器后台给手机分配固定 DHCP 租约（绑定 MAC 地址），这样手机 IP 不会变。

---

## 第二阶段：深度清理优化（ADB 免 Root）

以下所有命令都可以安全执行，不会变砖，不会影响 Android 12 系统完整性。

### 1. 先看现状

```powershell
# 列出所有已安装包名（按行显示，方便查看）
adb shell pm list packages -e

# 列出被禁用的包
adb shell pm list packages -d

# 查看存储使用情况
adb shell df /data

# 查看电池健康（如果支持）
adb shell dumpsys battery
```

### 2. 卸载/禁用用户应用（无需 Root）

以下命令可以**卸载当前用户的某个应用**，不会影响系统分区，恢复出厂设置后会自动回来。

```powershell
adb shell pm uninstall -k --user 0 com.example.app
```

**常见可卸载的用户应用表格**（Pixel 3a 预装的 Google 套件，按需选择）：

| 应用 | 包名 | 是否建议卸载 |
|------|------|--------------|
| YouTube | com.google.android.youtube | 可选 |
| YouTube Music | com.google.android.apps.youtube.music | 可选 |
| Google Play Movies | com.google.android.videos | 建议卸 |
| Google Photos | com.google.android.apps.photos | 若不用云端备份可卸 |
| Google Drive | com.google.android.apps.docs | 若不用可卸 |
| Google Docs/Sheets/Slides | 对应包名 | 若不用可卸 |
| Google Maps | com.google.android.apps.maps | 看情况 |
| Gmail | com.google.android.gm | 若不用可卸 |
| Google Duo | com.google.android.talk | 建议卸 |
| Google Assistant | com.google.android.apps.googleassistant | 不用可卸 |
| Google News | com.google.android.apps.magazines | 建议卸 |
| Google Podcasts | com.google.android.apps.podcasts | 建议卸 |
| Pixel Wallpapers | com.google.android.apps.wallpaper | 可选 |

**示例**（卸载 Google 新闻）：
```powershell
adb shell pm uninstall -k --user 0 com.google.android.apps.magazines
```

> **注意**：`-k` 参数保留数据和 cache，避免突然失败；如果包名输错了直接报错，不会误删其他应用，放心。

### 3. 由你决定留什么应用

根据你的用途（显示器/时钟/仪表），**建议保留**：

- 系统框架（动不了）
- 设置 / 文件管理器
- Chrome 或某个全屏浏览器（用于展示网页仪表盘）
- 梯子应用（必需）
- 关机时钟 / 翻页时钟类应用（你自己选）

### 4. 清理应用缓存（不丢数据）

```powershell
# 清除所有应用缓存（不影响登录状态）
adb shell pm clear --cache

# 或清除单个应用的缓存
adb shell pm clear com.example.app
```

### 5. 关闭动画（立刻变流畅感）

```powershell
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
```

这三个设置对流畅度提升非常明显，相当于关闭了所有过渡动画。

### 6. 限制后台进程

开发者选项 → **后台进程限制** → 设为 **不得超过 2 个进程**，或通过命令：

```powershell
adb shell settings put global app_standby_enabled 1
adb shell settings put global forced_app_standby_enabled 1
```

### 7. 禁用系统预装但用不到的服务（可选，高级）

以下包是系统级的（但用户可以 disable），disable 后不会运行：

```powershell
# Chrome（用其他浏览器替代时）
adb shell pm disable-user --user 0 com.android.chrome

# Google 搜索小部件（如果不用）
adb shell pm disable-user --user 0 com.google.android.googlequicksearchbox

# 打印服务（不用打印）
adb shell pm disable-user --user 0 com.android.bips

# Carrier Services（电信运营商服务，国内用不到）
adb shell pm disable-user --user 0 com.google.android.ims
```

> **安全原则**：禁用前先 `adb shell pm dump 包名 | head -20` 读一下应用作用，不熟悉的不要动；系统关键服务（telephony、systemui、settings）一律不碰。

---

## 第三阶段：显示器用途软件方案

你说了两种场景：**仪表监控常驻**（场景 1）和 **偶尔当 PC 第二屏**（场景 2）。

### 方案 A：仪表/监控常驻（推荐）

通过一个全屏浏览器加载自托管网页仪表盘，手机只做"屏幕"。

**推荐应用**：

| 应用 | 用途 | 来源 |
|------|------|------|
| **Kiosk Browser Lockdown** | 全屏浏览器，藏状态栏、Home劫持 | Play Store |
| **Fully Kiosk Browser** | 同上，支持运动检测亮屏、远程管理 | Play Store |
| **Screen Alive** | 保持屏幕常亮 + 防息屏 | Play Store |
| **Deki Monitor** | 手机硬件信息仪表盘（CPU/内存/电池/网络）| Play Store |
| **Fliqlo** | 翻页时钟，极简漂亮 | Play Store |
| **Chronus** | 信息性时钟（天气+日历+通知） | Play Store |

**推荐组合**：
1. **Fliqlo** 做日常时钟 + 临时抬眼看时间
2. **Deki Monitor** 常驻显示手机自身状态（或显示 PC 推过来的仪表数据）
3. **Fully Kiosk Browser** 全屏加载一个自制的"系统状态页面"（比如显示你服务器、NAS、股票等）

**自制仪表盘思路**（你作为 Electron 开发最熟）：
- 某台服务器上跑一个 Web Dashboard
- 手机 Fully Kiosk 全屏打开这个页面
- Fully Kiosk 支持白名单、自动恢复、远程唤醒

### 方案 B：把手机当 PC 第二屏

Lag 和 Root 无关，但有线方案最稳。

| 方案 | 延迟 | 费用 | 是否需 Root | 备注 |
|------|------|------|-------------|------|
| **Spacedesk**（USB / WiFi）| 低 | 免费 | ❌ | Windows 主机装服务端，手机装客户端，推荐 |
| **Duet Display** | 低 | 付费 | ❌ | 稳定但收费 |
| **TwomonUSB** | 极低（USB） | 付费 | ❌ | 有线最稳 |
| **scrcpy 反向（已装）** | 中 | 免费 | ❌ | 反向控制 PC，不是当屏幕 |
| **Webtop /桌面模式** | N/A | 内置 | ❌ | Pixel 3a 不支持桌面模式 |

**推荐**：**Spacedesk**（免费 + USB + WiFi 都行）。

部署步骤：
1. PC 端装 Spacedesk Windows 服务端：https://spacedesk.net/
2. 手机端装 **spacedesk** 客户端（Google Play）
3. 有 USB 时更稳定（打开 USB 网络共享或直接用 adb reverse 转发端口）
4. 手机就变成一台 1080x2220 的竖屏副屏

> **实际体验**：竖屏当副屏，适合显示聊天窗口、股票行情、音乐播放控制、监控画面，不太适合主工作区扩展。

### 方案 C：无线信息显示牌（两套都用）

最实用的最终形态：

1. **电源**：一直插电（Pixel 3a 电池老化，长期插电 + 限充 80% 更长寿）
2. **显示管理**：Fully Kiosk 设为"常亮模式"，超时自动锁屏关闭
3. **内容**：两种模式
   - **时钟模式**：锁屏时 Fliqlo 翻页时钟，唤醒进 Deki Monitor
   - **仪表模式**：Fully Kiosk 全屏打开 PC 推送的监控页

---

## 第四阶段：电源与长期健康

Pixel 3a 电池老化处理：

- **开启电池保护**（设置 → 电池 → 自适应充电 / Pixel 叫"优化电池"）
- **限制充电到 80%**：Pixel 没有原生的充电上限，可装 **Battery Charge Limit**（免 Root，用 ADB 一次性授权）：
  ```powershell
  adb shell pm grant com.slash.batterychargelimit android.permission.WRITE_SECURE_SETTINGS
  ```
  设置 80% 后不再充满，减少发热膨胀
- **替代方案**：直接用智能插座（比如小米插座）设定时，充到 2 小时就断电

> **长期插电 + 限充 80%** = 手机放桌上当显示器用一年不鼓包的基础保障。

---

## 第五阶段：日常维护脚本

生成以下两个脚本放在电脑上，以后一键检查手机健康。

### `check-battery.ps1`（电池健康检查）

```powershell
Write-Host "=== Pixel 3a Battery Status ===" -ForegroundColor Cyan
$lines = adb shell dumpsys battery
$lines | ForEach-Object {
    if ($_ -match '\s*(\w+):\s*(.+)') {
        Write-Host "$($Matches[1]): $($Matches[2])"
    }
}
# 提取关键行
$capLine = $lines | Select-String "level:"
$healthLine = $lines | Select-String "health:"
$tempLine = $lines | Select-String "temperature:"
Write-Host "`n📊 Summary:" -ForegroundColor Green
Write-Host "  $capLine"
Write-Host "  $healthLine"
Write-Host "  $tempLine $([math]::Round(([int](($tempLine -split ':')[1].Trim()) / 10)), 1) °C"
```

### `launch-mirror.ps1`（一键投屏）

```powershell
$phoneIP = Read-Host "Enter Pixel 3a IP (or press Enter for USB)"
if ($phoneIP) {
    adb connect "$phoneIP:5555"
}
Start-Process "C:\scrcpy\scrcpy.exe" -ArgumentList "--max-size 1080 --window-title='Pixel3a-Display' --show-touches"
```

---

## 风险清单与承诺

| 操作 | 有风险？ | 原因 |
|------|----------|------|
| adb 卸载用户 App | ❌ 无 | 仅删除当前用户数据 |
| adb 禁用系统 App | ❌ 无 | 可逆，出厂恢复即可 |
| 卸载 carrier services | ⚠️ 极低 | 国内无运营商依赖 |
| 关闭动画 | ❌ 无 | 软件设置 |
| 限制后台 | ❌ 无 | 开发者选项本机设置 |
| 刷 Magisk/解锁 bootloader | ⛔ 禁止 | 会触发 data wipe、可能变砖风险 |
| 系统升级 | ⛔ 禁止 | 违反要求 |

---

## 后续调整建议

1. **先跑一遍本项目的**第二阶段清理，看是否已经流畅，再决定要不要动其他
2. **ADB 无线连上**后，日常完全不用 USB，手机放在桌上当显示器
3. **显示器场景**强烈推荐 Fully Kiosk + 自制 Web Dashboard，完全 Free
4. **第二屏**用 Spacedesk,竖屏足够
5. **电池**如果是长期插电，务必装 Battery Charge Limit 限 80%
6. 有问题回头翻本文档，每步命令直接 copy 回车即可

---

## 执行记录（2026-10-07）

| 步骤 | 状态 | 备注 |
|------|------|------|
| 电脑上装 ADB + scrcpy | ✅ | winget 安装 |
| 连接手机 USB 调试 | ✅ | device id 949AY0L9BH |
| 关闭三种动画缩放 | ✅ | 0/0/0 |
| 卸载 Google Videos | ✅ | com.google.android.videos |
| 卸载 Google Photos | ✅ | com.google.android.apps.photos |
| 卸载 Google Docs/Drive | ✅ | com.google.android.apps.docs |
| 卸载 Google Maps | ✅ | com.google.android.apps.maps |
| 卸载 Google Restore | ✅ | com.google.android.apps.restore |
| 禁用打印服务后台 | ✅ | com.android.bips |
| 禁用打印推荐服务 | ✅ | com.google.android.printservice.recommendation |
| 禁用小区广播接收器 | ✅ | com.google.android.cellbroadcastreceiver |
| 启用应用待机（后台限制）| ✅ | adb 命令 |
| 开启无线 ADB 端口 | ✅ | 192.168.1.110:5555 |
| 手机重启 | ✅ | 用户手动完成 |

**一键脚本文档位置**（放在 `scripts/` 目录）：
- `scripts/battery-check.ps1` — 电池健康与存储检查
- `scripts/launch-mirror.ps1` — 一键启动 scrcpy 投屏

---

*文档版本*：v1.1（含执行记录） | *设备指纹*：Pixel 3a (Android 12, US) | *适用读者*：电脑前的你
