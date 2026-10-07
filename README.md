# 桌面时钟 · Desk Clock

为 Pixel 3a 当显示器使用而写的极简全屏网页。

## 效果

- **主数字**：超大淡灰色超细字体，柔和呼吸感冒号
- **日期行**：`2026 · 10-07 · 星期二`
- **下班倒计时**：`离下班还有 2 小时 35 分钟`（超过 8 小时自动显示"今日已收工 ✦"）
- **防 OLED 烧屏**：整体亮度 18 秒一周期缓慢呼吸；背景加暗角纹理缓解图像残留
- **PWA 安装**：添加到主屏幕后 Chrome 全屏打开（无地址栏/状态栏）
- **设置下班时间**：点击右上角隐藏的齿轮图标调出浮窗，选择后自动保存到 localStorage

## 文件

| 文件 | 用途 |
|------|------|
| `index.html` | 主页（单文件，含 HTML+CSS+JS） |
| `manifest.webmanifest` | PWA manifest |
| `sw.js` | Service Worker（离线缓存） |
| `icon.svg` | 自适应矢量图标（Chrome 桌面图标） |

## 在手机 Chrome 上使用方法

1. 把整个 `pixel3a-clock/` 文件夹托管到一个 Web 服务器（GitHub Pages / 本地 Python http.server 都行）
2. Chrome 打开 `https://your-domain/pixel3a-clock/`
3. Chrome 菜单 → "添加到主屏幕"（Add to Home Screen）
4. 从主屏幕新图标打开 → 进入全屏 PWA 模式

## 像素完美适配

- 横竖屏：CSS clamp() 根据 `vh`/`vw` 动态调整字号
- 安全区：`env(safe-area-inset-*)` 避开刘海
- 竖屏 1080x2220 下字号撑满横向，两侧留白极小

## 自定义

- 默认下班时间 `18:00`，在时钟页点击右上角齿轮修改
- 修改颜色：CSS 变量在 `body` 背景 `#080808` + 文字 `#f0f0f0`（OLED 友好纯黑底）
- 字体：`font-weight: 200`（超细），macOS / iOS 显示更佳
