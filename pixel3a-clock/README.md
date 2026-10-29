# 活 · Clock

> Pixel 3a 桌面摆件时钟 · Zen Garden · 禅意花园

基于 Pixel 3a 设备二次利用的桌面摆件时钟 PWA 应用。单文件交付，无框架无依赖。

## v13.0 "Zen Garden" 新特性

- **Liquid Glass 9.0**：禅意流体玻璃 — 新增矿物脉络层(`.glass-vein`)模拟细微晶体反光、水波纹扩散层(`.glass-ripple`)悬停触发涟漪、backdrop-filter blur 增至 52px、虹彩旋转周期增至 160s、`--ease-zen` 专属缓动曲线
- **Sand Ripple 沙纹光场**：全新 Canvas 背景层 `sandRippleCanvas` — 模拟枯山水沙纹同心圆波动，极低频率 (~50ms/帧)，养眼养心
- **Flow Light 流动光河**：全新 Canvas 背景层 `flowLightCanvas` — 缓慢流动的光带粒子，沿固定方向漂移，如同砂纹中流动的光线
- **Desk Scene 3.0**：桌面预览禅意化 — 更深邃的 background (#020208)、更柔和的环境光 (26s 周期)、更细腻的机身玻璃质感 (oklch 1% 黑)，hover 阴影更收敛
- **OLED Care 3.0**：三重李萨如 + 呼吸调制 — X/Y 轴各自三条正弦/余弦叠加，振幅按 ±12% 呼吸起伏，过渡时间增至 52s，更不可察觉
- **季节色系 3.0**：饱和度调整范围收窄约20%（春夏+0.05~+0.1，秋+0.15，冬-0.25），更不抢注意力；季节色温同时影响沙纹与流动光的透明度
- **文字减负**：主字重从 350→340，副字重从 450→440，全界面更轻盈舒适
- **深度软化**：所有玻璃卡片/背景不透明度普降约 15-20%，减少视觉锐度

## v12.0 "Aurora Weave" 特性（继承）

- **Aurora Weave 极光编织**：多层极光背景系统（nebula + aurora + neuralAurora 三层 Canvas 叠加）
- **Corona Flare 日冕辉光**：动态日冕脉动 + 太阳风粒子效果
- **Horizon Light 地平线光带**：昼夜相位控制的地平线光带，连续变化位置/宽度/亮度
- **桌面预览 v12**：环境光动画、色温渐变、设备呼吸光泽

## v11.x 历史特性（继承）

- **Liquid Glass 7.0/8.0**：流体玻璃拟态系统（棱镜散射 + 内部辉光 + 边缘色散）
- **Bio-Rhythmic 生物节律**：22:00-06:00 夜间所有动画自动放慢至 55% 速度
- **Weather Mood Canvas**：根据天气数据动态叠加情绪色温
- **桌面小水母「泡」**：随天气与时段换心情的水母宠物
- **四模式切换**：数字 / 指针 / 文字 / Flip 翻页
- **Circadian 节律背景**：24 小时色温插值系统
- **智能感知**：环境光传感器、电池状态、自动省电模式

## 消息投递

```
?msg=内容&title=标题        # URL 投递
BroadcastChannel('pixel3a-clock')
localStorage.clockV7Inbox
pushMessage(title, body, opts)
```

## 资讯订阅

设置 → 资讯订阅 可增删 RSS / Atom 源、改刷新时间、填代理前缀。

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
| `{solar_term}` | 临近的二十四节气 |
| `#quote` | 随机语录 |
| `#affirmation` | 随机自我肯定 |

## 技术栈

- 纯 HTML + CSS + JavaScript 单文件
- Service Worker 离线缓存
- PWA 可安装
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
- OLED 纯黑背景 (#000) + 像素漂移保护（三重李萨如 ±1.9px，周期 ~52s）
- SDM670 SoC 友好的自适应帧率（省电模式 12fps / AOD 5fps）
- 环境光自适应亮度调节（42%-106%，2.0 平滑过渡）
- 电池 <15% 自动省电模式
- 生物节律：夜间动画降速 55%，字重降至 340

## v13 深度检测项目

- [ ] CSS 大括号平衡
- [ ] JS 语法检查 (node --check)
- [ ] DOM ID 引用一致性
- [ ] 标签闭合
- [ ] 新元素 (sandRippleCanvas, flowLightCanvas, glass-vein, glass-ripple) 引用完整
- [ ] 季节色温对沙纹/流动光影响
- [ ] OLED Care 3.0 呼吸调制效果
