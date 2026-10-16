# 活 · Clock

> Pixel 3a 桌面摆件时钟 · Horizon · 横岭

基于 Pixel 3a 设备二次利用的桌面摆件时钟 PWA 应用。单文件交付，无框架无依赖。

## v8 "Horizon · 横岭" 新特性

- **横屏摆件布局**：时钟居左（≈62%）+ 天气/消息栏居右，适配 Pixel 3a 横屏 807×393 CSS px
- **一套 DOM 两种排版**：`.col` 在竖屏用 `display:contents` 拍平进纵向流，横屏恢复为真实弹性列
- **内置消息中心**：无后端的本地消息聚合，支持 4 条投递通道 + 12 小时留存 + 分级配色
- **天气主卡增强**：温度 / 天气描述 / 最高最低温 + 体感 · 湿度 · 风 三枚 chip
- **朝向控制**：设置面板「自动 / 竖屏 / 横屏摆件」三段开关，横屏模式可请求屏幕方向锁定
- **桌面横屏预览**：≥900px 时设备框随摆件模式切换为 807:393 横置机身，打孔移到左侧
- **状态栏横跨顶部**：横屏下状态栏绝对定位到整屏顶端，右栏自动让出高度避免遮挡
- **修复**：下班倒计时里程碑重复触发（每秒重复播报 → 阈值只触发一次）、专注模式遮罩失效（class 挂错节点）

## 特性总览

- **竖屏 / 横屏双排版**：同一套 DOM，朝向可自动判断或在设置中手动锁定
- **四模式切换**：数字 / 指针 / 文字 / Flip 翻页，支持滑动、滚轮、方向键、数字键切换
- **Circadian 节律背景**：24 小时色温插值系统，OKLCH 感知均匀色彩空间
- **星云雾多层背景**：Canvas 实时渲染（星云 + 氛围粒子 + 晨雾 + 整点脉动）
- **Lumina Glass v7**：backdrop-filter + 多层叠加 + 微光边框 + 噪点纹理
- **智能感知**：环境光传感器、电池状态、自动省电模式、时段分类
- **禅模式**：长按时钟进入极简状态，全 UI 淡出，30 分钟自动恢复
- **桌面预览**：≥900px 自动进入 Pixel 3a 设备框架模拟（支持横竖屏两种机身）
- **消息中心**：系统提醒 / 空气预警 / 下班里程碑 / 外部投递，分级显示、可清空、离线留存
- **天气 / 空气 / 日出日落 / 月相 / 节气**：Open-Meteo API，自动定位 + 缓存
- **专注统计**：双击启动专注模式 + 7 天热力图 + 连续天数
- **纸条轮播**：支持变量 + #quote / #affirmation 标签

## 消息投递通道

消息中心不需要后端，四条本地通道全部离线可用：

```
?msg=内容&title=标题        # URL 投递（读取后自动清理地址栏）
BroadcastChannel('pixel3a-clock')   # 同源其他标签页 / 控制台
localStorage.clockV7Inbox   # storage 事件跨页投递
pushMessage(title, body, opts)      # 页面控制台 / Service Worker postMessage
```

`pushMessage(title, body, opts)` 的 `opts` 支持
`{ level: 'info' | 'warn' | 'alert', source: 'system' | 'notify' | 'local', toast: false, notify: true }`；
也可整块传对象 `pushMessage({ title, body, level })`。
外部文本一律经 `esc()` 转义后写入，URL 与 storage 投递无法注入 HTML。
消息上限 40 条、留存 12 小时、面板最多渲染 14 条。

系统通知走 `ServiceWorkerRegistration.showNotification()`（Android Chrome 无 `new Notification()` 构造函数），
点击通知由 `sw.js` 的 `notificationclick` 唤回已有页面。

## 纸条变量

| 变量 | 含义 |
|------|------|
| `{worked}` | 已工作时间（从 8:30 起） |
| `{countdown}` | 距下班倒计时 |
| `{weather}` | 天气图标 |
| `{temp}` | 温度 |
| `{toFriday}` | 距周五天数 |
| `{sunrise}` | 日出时间 |
| `{sunset}` | 日落时间 |
| `{solar_term}` | 临近的二十四节气（3 天内） |
| `#quote` | 随机语录 |
| `#affirmation` | 随机自我肯定 |

## 技术栈

- 纯 HTML + CSS + JavaScript 单文件
- Service Worker 离线缓存
- PWA 可安装
- 无构建工具 / 无 npm 依赖
- OKLCH 广色域感知色彩空间

## 文件结构

```
├── index.html          # 主文件（含所有 HTML/CSS/JS）
├── sw.js               # Service Worker
├── manifest.webmanifest
├── icon.svg
└── README.md
```

## Pixel 3a 适配

- 打孔屏安全区（竖屏 34px 顶部 padding；横屏摆件归零并改为左右 20px 侧安全区）
- OLED 纯黑背景 (#000) + 像素漂移保护
- SDM670 SoC 友好的自适应帧率（省电模式 12fps / AOD 5fps）
- 环境光自适应亮度调节（48%-108%）
- 电池 <15% 自动省电模式
- 横屏摆件下状态栏横跨整屏顶部、字号收拢至 0.62rem，避免与天气卡争抢 393px 可用高度
- AOD 模式同步压暗天气卡与消息面板（opacity 0.04）

## 深度检测（v8）

- ✅ 所有 DOM ID 引用一致性验证通过（104 个定义 / 85 个 `$()` 引用 / 0 重复 / 0 缺失）
- ✅ JS 语法 node --check OK
- ✅ CSS 大括号平衡验证通过（252/252）
- ✅ Button 标签闭合验证通过（22/22）
- ✅ 横屏 807×393 渲染实测：无元素越界、无塌陷，状态栏与天气卡碰撞已消除
- ✅ 竖屏强制短视口（807×393）时时钟保持 128px 最小高度、信息区自行滚动
- ✅ URL / BroadcastChannel / storage / pushMessage 四通道投递实测通过
- ✅ XSS 载荷 `<img onerror>` 注入消息后 DOM 内 0 个额外元素
- ✅ 真实 Open-Meteo 拉取 + AQI 预警自动入箱
- ✅ Store Key 保持 clockV7State（老设备升级后设置不丢）

