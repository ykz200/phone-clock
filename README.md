# 活 · Clock

> Pixel 3a 桌面摆件时钟 · Signal · 风讯

基于 Pixel 3a 设备二次利用的桌面摆件时钟 PWA 应用。单文件交付，无框架无依赖。

## v8.1 "Signal · 风讯" 新特性

- **资讯订阅**：每天定时抓取 RSS / Atom，去重后落到消息中心，默认两条「青岛机场·航线」「青岛·城市要闻」
- **代理链**：浏览器跨域限制下按 直连 → allorigins → corsproxy.io → codetabs 依次尝试，成功的通道记住并优先使用，也可自填代理前缀
- **单次 12 秒超时**：某个源或代理不可达时不会把摆件卡住，四条通道全挂只留一条 warn 提醒，不重复堆叠
- **点击原文**：资讯条目带链接时可直接点开（仅放行 http/https，`javascript:` 之类被拒）
- **设置面板横屏两列**：资讯组横向铺开，滚动高度 1112 → 1037
- **修复**：Service Worker 对跨域数据请求（天气 / 空气 / 资讯）改为网络优先，此前缓存优先会让摆件一直显示首次抓到的旧读数

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
- **资讯订阅**：每日定时抓取 RSS / Atom，代理链兜住跨域，去重后入消息箱
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

## 资讯订阅

设置 → 资讯订阅 里可增删订阅源（任意 RSS / Atom 地址）、改每日刷新时间、填代理前缀。

- 抓取时机：每天到点一次；开机时若已错过当日刷新点，8 秒后补抓一次
- 每次每源最多入箱 3 条，只收 3 天内的条目，资讯在消息箱留存 48 小时
- 去重键是条目链接，`clockV7NewsSeen` 最多记 300 条，抓失败的源不会污染去重表
- 失败后每 30 分钟重试一次（不越过下一次定时点）；公共代理节点稳定性有限，实测同一份订阅可能这次成功下次失败
- 默认源走 Google News RSS 检索，浏览器直连会被 CORS 挡下，实际由代理链兜住；
  若设备所在网络访问不到，可换成自建 RSSHub 或其他带 CORS 的源

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

## 深度检测（v8.1）

- ✅ 所有 DOM ID 引用一致性验证通过（113 个定义 / 94 个 `$()` 引用 / 0 重复 / 0 缺失）
- ✅ JS 语法 node --check OK（index.html 与 sw.js）
- ✅ CSS 大括号平衡验证通过（270/270）
- ✅ Button 标签闭合验证通过（26/26）
- ✅ RSS 与 Atom 双格式解析实测：带日期条目入箱、20 天前的旧条目被过滤、无日期条目保留、非 XML 的坏源不产条目
- ✅ 去重实测：第二次刷新不重复入箱；每源 3 条上限按刷新次数逐批放行
- ✅ `javascript:` 链接条目入箱但不可点（`data-url` 为空）
- ✅ 四条通道全失败时：状态显示「失败 · HH:MM」、去重表保持为空、只留 1 条 warn 提醒（再点刷新不叠加）
- ✅ 横屏 807×393 设置面板无横向溢出，滚动到底后保存按钮可达
- ✅ 横屏 807×393 主视图无元素越界/塌陷；竖屏 393×807 `docScrollW=393`
- ✅ URL / BroadcastChannel / storage / pushMessage 四通道投递实测通过
- ✅ XSS 载荷 `<img onerror>` 注入消息后 DOM 内 0 个额外元素
- ✅ Store Key 保持 clockV7State（老设备升级后设置不丢）

