# 活 · Clock

> Pixel 3a 桌面摆件时钟 · Aura · 气场

基于 Pixel 3a 设备二次利用的桌面摆件时钟 PWA 应用。单文件交付，无框架无依赖。

## v9 "Aura · 气场" 新特性

- **Aura Glass v9**：全新彩虹边缘光系统（`.glass-iris` conic-gradient），玻璃卡片边缘随色温变化产生柔和的棱镜散射效果
- **Bio-Rhythmic 生物节律**：22:00-06:00 夜间所有动画自动放慢至 55% 速度，字重降至 180（更轻盈），全程无刺眼瞬变
- **Weather Mood Canvas**：背景第六层氛围系统（`bg-mood`），根据天气数据动态叠加情绪色温 — 晴暖金、雨冷蓝、雪亮白、雾灰蒙、雷暴偏紫
- **Weather Particles**：雨雪天气时渲染对应的柔和粒子效果（细雨、飘雪、迷雾），不影响主体内容
- **桌面预览 v9**：更真实的 Pixel 3a 机身体感 — 多方向环境光、桌面反射、hover 交互景深变化、设备的呼吸光泽
- **OLED Protection v9**：漂移幅度进一步缩小（±1.5/±1.2px），周期 50s，漂移路径经过平滑曲线过渡
- **信息密度渐进**：自动光感暗化更低（42%），夜间文字呼吸更柔和
- **视觉动效统一**：所有 `.glass-iris` 卡片共享缓慢旋转的棱镜散色动画，营造"气场"感

## v8.x 历史特性（继承）

- **资讯订阅**：每天定时抓取 RSS / Atom，去重后落到消息中心
- **代理链**：直连 → allorigins → corsproxy.io → codetabs，记忆成功通道
- **横屏摆件布局**：807×393 CSS px，时钟居左 + 消息/天气栏居右
- **一套 DOM 两套排版**：`.col` 在竖屏 `display:contents` 拍平，横屏恢复为真实弹性列
- **四模式切换**：数字 / 指针 / 文字 / Flip 翻页，滑动/滚轮/方向键/数字键切换
- **Circadian 节律背景**：24 小时色温插值系统，OKLCH 感知均匀色彩空间
- **星云雾多层背景**：Canvas 实时渲染 + 晨雾 + 整点脉动
- **智能感知**：环境光传感器、电池状态、自动省电模式

## 消息投递

```
?msg=内容&title=标题        # URL 投递
BroadcastChannel('pixel3a-clock')
localStorage.clockV7Inbox
pushMessage(title, body, opts)
```

## 资讯订阅

设置 → 资讯订阅 可增删 RSS / Atom 源、改刷新时间、填代理前缀。每天到点抓取一次；开机补抓延迟 8 秒。

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
- OLED 纯黑背景 (#000) + 像素漂移保护（±1.5/±1.2px，周期 50s）
- SDM670 SoC 友好的自适应帧率（省电模式 12fps / AOD 5fps）
- 环境光自适应亮度调节（42%-106%）
- 电池 <15% 自动省电模式
- 生物节律：夜间动画降速 55%，字重降至 180

## 深度检测（v9）

- ✅ CSS 大括号平衡验证通过（821/821）
- ✅ JS 语法 node --check OK（index.html 与 sw.js）
- ✅ 所有 DOM ID 引用一致性验证通过（114 个定义 / 0 重复 / 0 缺失）
- ✅ 所有 dom. 引用均在 dom 对象中定义（86/96 全部连线）
- ✅ Button 标签闭合验证通过（26/26）
- ✅ DIV/SPAN/P 标签开闭平衡验证通过
