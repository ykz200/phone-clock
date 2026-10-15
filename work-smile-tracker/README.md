# 上班表情追踪器 · WorkSmileTracker

![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-blueviolet)
![Compose Multiplatform](https://img.shields.io/badge/Compose-1.7.3-brightgreen)
![Android](https://img.shields.io/badge/Android-26%2B-3DDC84)
![iOS](https://img.shields.io/badge/iOS-16.0+-000000)
![MediaPipe](https://img.shields.io/badge/MediaPipe-tasks--vision-orange)
![License](https://img.shields.io/badge/License-MIT-green)

**打工人桌面实时表情追踪 · OLED 友好 · 双端共享一套代码 · 玻璃拟态 UI**

用手机前置摄像头以每分钟一次的采样频率，记录上班时的表情状态（微笑概率、眼部睁开程度、头部姿态），生成每日/每周统计报表。

> **项目状态**: v9.0 "Astral Radiance" 星光璀璨。全链路 CameraX + MediaPipe 已打通，18色舒适情感色板，星轨粒子背景，情绪天气系统，6种心情弧线，自我对话系统，里程碑庆祝，呼吸引导。

---

## 设计理念

### OLED 友好
纯黑背景 + 柔和亮色文字 + AOD（息屏显示）风格，减少像素发光面积、防烧屏、省电。

### 玻璃拟态
顶部微光 + 半透明边框 + 层级感卡片，每个图标模块都以玻璃质感呈现。

### 智能洞察 v2.0
基于统计数据自动生成个性化建议 + 压力指数计算 + 情绪波动率推断。

### 烧屏保护
全屏像素缓慢漂移 (0-2px，60秒周期) + AOD 风格低亮度设计。

### Material 3 Expressive
圆角卡片 + 渐变装饰条 + 呼吸灯动画 + spring 物理动画系统。

---

## 为什么是 KMP + Compose Multiplatform

打工人时间紧，需要一个"学一次代码两端共用"的方案。Kotlin Multiplatform + Compose Multiplatform 可以做到：

- **Business 逻辑** (表情分析、数据库、定时器) 一套代码，iOS / Android 双端共享，包名统一 `com.ykz200.smiletracker.domain`
- **UI** (Dashboard 仪表盘、折线图、实时状态) 一套 `@Composable` 函数，iOS / Android 双端共享
- **SQLite 数据库** SQLDelight 自动生成双端驱动，schema 一个文件定义
- **后台任务** 各端用各平台 SDK (WorkManager / BGTaskScheduler) 实现

---

## 项目结构

```
work-smile-tracker/
├─ gradle/
│  └─ libs.versions.toml                            # Gradle Version Catalog (统一版本)
├─ shared/                                          # KMP 共享模块 (业务 + UI + 数据)
│  ├─ build.gradle.kts
│  └─ src/
│     ├─ commonMain/                                # 🟢 双端共享层
│     │  ├─ kotlin/com/ykz200/smiletracker/
│     │  │  ├─ domain/Domain.kt                    #   数据类 + 接口: FaceAnalysis, MinuteStat, WeeklySummary
│     │  │  ├─ data/SmileRepository.kt             #   共享数据库操作 + RepositoryProvider + 数据清理
│     │  │  └─ ui/Dashboard.kt                     #   Compose 共享 Dashboard UI (玻璃拟态 + 圆形仪表盘)
│     │  └─ sqldelight/...db/SmileDatabase.sq      #   SQLDelight Schema (双端共享 + 索引优化)
│     ├─ androidMain/                               # 🟠 Android 特定层
│     └─ iosMain/                                  # 🔵 iOS 特定层 (skeleton)
├─ androidApp/                                     # Android 应用入口
│  ├─ build.gradle.kts
│  └─ src/main/
│     ├─ kotlin/com/ykz200/smiletracker/
│     │  ├─ MainActivity.kt                       # 主页 + 玻璃拟态权限引导 + WorkManager 调度
│     │  ├─ CameraAnalyzer.kt                     # CameraX + MediaPipe 端到端 + Worker
│     │  └─ SmileyAnalysisService.kt              # 前台 Service 保活 + 实时通知 (Android 14 兼容)
│     ├─ AndroidManifest.xml
│     └─ res/values/strings.xml
├─ iosApp/                                        # iOS XCode 容器 (skeleton)
├─ build.gradle.kts
├─ gradle.properties
├─ settings.gradle.kts
└─ README.md
```

---

## 数据流总览

```
                前置摄像头 (CameraX / AVFoundation)
                           │ (逐帧, 30fps → 每 3 帧推理 1 次)
                  MediaPipe Face Detection
                           │ (smileProb, eyeProb, headEulerAngle)
                     shared: domain.FaceAnalysis
                           │
                 ┌─────────┴──────────┐
                 │                    │
          shared: UI Layer      shared: data.Repository
      (Dashboard 实时更新)    (SQLite INSERT, 每 60s 批量聚合)
                 │                    │
                 └─────────┬──────────┘
                           │ (SQLDelight Flow)
                  Dashboard (折线图 + 数字卡片 + 圆形仪表盘 + 智能洞察)
                           │
                  WorkManager (后台心跳 + 数据清理)
```

---

## 业务模型

### `FaceAnalysis` (每分钟采样一次，原始数据)

| 字段 | 类型 | 说明 |
|------|------|------|
| `tsMillis` | Long | 毫秒时间戳 |
| `smileProbability` | Float | 微笑概率 `[0..1]`，`-1` = 未检出人脸 |
| `leftEyeOpenProbability` | Float | 左眼睁开概率 |
| `rightEyeOpenProbability` | Float | 右眼睁开概率 |
| `headPitch / headYaw / headRoll` | Float | 头部三轴角度 (基于 bbox 位移估算) |

### `MinuteStat` (Dashboard 显示用，每分钟聚合)

| 字段 | 说明 |
|------|------|
| `minuteKey` | `"2026-10-07T15:30:00"` 格式 |
| `sampleCount` | 该分钟内实际推理次数 |
| `avgSmile` | 平均微笑概率 |
| `maxSmile` | 峰值微笑概率 |
| `smileCount` | 微笑次数 (smileProb ≥ 0.7) |
| `smilePercent` | 微笑时长占比 `%` |
| `avgEyeLeft / avgEyeRight` | 眼部综合疲劳度 |

### `WeeklySummary` (周视图数据)

| 字段 | 说明 |
|------|------|
| `weekStart / weekEnd` | 周起止日期 |
| `totalSmileCount` | 周总微笑次数 |
| `avgDailySmile` | 日均微笑次数 |
| `bestDay` | 最佳日 |
| `trend` | 周环比趋势 |

---

## UI v2.0 升级亮点

### 视觉系统
- **玻璃拟态卡片**: 1px 半透明白色边框 + 顶部 5% 高光 + 深色半透明底色
- **圆形微笑仪表盘**: 90dp 圆形 arc 渐变 + 终端白点 + spring 动画
- **呼吸指示灯 v2**: 径向渐变 + 平滑 alpha 呼吸
- **多阶段入场动画**: spring 物理 + 逐项延迟

### 数据可视化
- **增强折线图**: 三次贝赛尔平滑曲线 + 渐进动画绘制 + 峰值双层光晕
- **情绪热力图**: 每日色块 (粉→黄渐变表示心情)
- **周度柱状图**: 渐变柱 + X 轴标签

### 智能洞察 v2.0
- **压力指数**: 微笑占比 (60%) + 眼疲劳 (40%) 加权 → "低/中/较高/高" 四级
- **情绪波动率**: 微笑标准差 → 自动检测情绪波动
- **多洞察展示**: 主洞察 + 副洞察 (最多 3 条)

### 烧屏保护
- **像素漂移**: 全屏 0-2px 缓慢线性漂移 (60 秒周期)
- **纯黑背景**: `Color(0xFF000000)` 仅约 10% 像素实际发光
- **组件**: compose可见性动画包装器确保懒加载淡入

---

## 数据库 v2.0

### 新增 SQL 索引
```sql
CREATE INDEX idx_smile_record_ts ON smile_record(ts_millis);         -- 时间查询
CREATE INDEX idx_smile_record_prob ON smile_record(smile_probability)  -- 高微笑筛选
    WHERE smile_probability >= 0.7;
```

### 新增查询
- `analyzeWeekly` — 周度聚合
- `deleteOlderThan` — 数据清理

### 数据保留
默认 90 天自动清理 (可配置)，由 WorkManager 每小时触发。

---

## 快速启动

### 环境要求

- **JDK 17+**
- **Android Studio Ladybug+** (2024.2+)
- **XCode 16+** (编译 iOS 时需要)
- **NDK** (KMP iOS 构建)
- **Git**

### Option 1: Android Studio 打开

> File → Open → 选择 `work-smile-tracker/` 文件夹

Gradle 自动下载依赖。**第一版可能耗时 20 分钟+**（首次下载 Compose Multiplatform、MediaPipe AAR、SQLDelight）。

### Option 2: 命令行

```bash
cd work-smile-tracker
./gradlew :androidApp:assembleDebug    # 构建 debug APK

# 安装到 Pixel 3a (已开 USB 调试后)
./gradlew :androidApp:installDebug
```

### 安装后验证

打开 App → 授权摄像头权限 → 进入 Dashboard：

- 显示实时微笑概率 (每 2 秒更新) + 圆形仪表盘
- 四大统计卡显示今日数据 (玻璃拟态)
- 中部渐变折线图展示全天微笑趋势
- 智能压力指数 + 多维度洞察
- 可切换至周视图 (柱状图 + 情绪热力图)
- 每分钟详情列表可滚动

---

## Android CameraX + MediaPipe 集成要点

### Step 1: 下载 tflite 模型

从 [Google Face Detection Models](https://ai.google.dev/edge/mediapipe/solutions/vision) 下载：

- `face_detection_short_range.tflite` 放在 `androidApp/src/main/assets/`

### Step 2: build.gradle 依赖已就绪

```kotlin
// shared/build.gradle.kts
implementation(libs.mediapipe.tasks.vision)   // MediaPipe Tasks 0.10.x
implementation(libs.camerax.core)              // CameraX 1.4.x
implementation(libs.camerax.camera2)
implementation(libs.camerax.lifecycle)
implementation(libs.camerax.view)
implementation(libs.workmanager.runtime)       // WorkManager 2.10
```

### Step 3: CameraAnalyzer 性能参数

```kotlin
// 帧跳越: 每 3 帧推理一次 (30fps → ~10fps 推理)
private val FRAME_SKIP = 3

// 媒体管道: OUTPUT_IMAGE_FORMAT_RGBA_8888
// 头部姿态角: 基于 bounding box 中心位移 + 宽高变化的估算值
// 数据写入: 异步 fire-and-forget (不阻塞推理流水线)
```

### Step 4: AndroidManifest 权限

已配置 CAMERA / FOREGROUND_SERVICE / FOREGROUND_SERVICE_CAMERA / WAKE_LOCK / RECEIVE_BOOT_COMPLETED / REQUEST_IGNORE_BATTERY_OPTIMIZATIONS

---

## iOS 端集成 - 待做

iOS 端共享逻辑可以复用，Camera + MediaPipe 需要在 XCode 工程侧额外注入：

- CocoaPods: `pod 'MediaPipeTasksVision', '~> 0.10.0'`
- BGTaskScheduler 注册 background mode
- Info.plist: `NSCameraUsageDescription` = "上班时记录表情统计数据"

---

## Version History

### v8.0 "Astral Glow" 星光璀璨 — 2026-10-15
- **色板全面升级**: Astral Glow 星辉色系 — 更柔软温暖的晨/午/晚/夜四时段色温
- **4 张全新智能卡片**:
  - 📈 DailyMoodArcCardV8 — Canvas 绘制的日内情绪弧线
  - 💡 PersonalizedTipsCardV8 — 基于天气×作息的个性化建议
  - 🪞 VoiceOfSelfCardV8 — 内心独窗（反思/鼓励/每日肯定）
  - 🎉 MilestoneCelebrateCardV8 — 里程碑庆祝卡片
- **视觉舒适度优化**: 降低所有色彩饱和度、增大圆角和间距、放慢动画节奏
- **HUD 升级**: PortraitCameraScreen 色温配色升级，呼吸弹簧更柔和
- **编译修复**: 移除 Android-only API 依赖，修复跨平台兼容性

### v7.0 "Zen绽放" — 2026-10-14
- **情绪天气系统**: EmotionWeather 8种天气状态
- **智能日程感知**: 根据时间段自适应色温
- **情绪周期分析**: EmotionCycleCardV7 周/月/季节分析
- **色温感知极光**: AuroraBackgroundV7 5层景深

### v6.0 "Aurora Glow" — 2026-10-13
- Liquid Glass 3.0 液态玻璃视觉系统
- 极光粒子背景效果
- 心情预测功能

## Roadmap

- [x] Gradle 工程 + Version Catalog
- [x] CameraX + MediaPipe 端到端打通
- [x] 权限引导界面 + 前台 Service 保活（Android 14 兼容）
- [x] OLED 友好纯黑主题 UI
- [x] 智能洞察功能
- [x] 渐变折线图 + 动画系统
- [x] Code cleanup: 修复所有占位符/伪代码/TODO
- [x] 玻璃拟态 UI + 圆形仪表盘 + 周视图 + 烧屏保护
- [x] 智能洞察 v2.0 (压力指数 + 情绪波动率)
- [x] 帧跳越优化 + Worker 数据清理
- [x] SQL 索引 + 数据保留策略
- [x] v8.0 Astral Glow 星光璀璨 — 舒适视觉与智能卡片
- [ ] 工作日 (Mon-Fri) 09:00-18:00 自动启停
- [ ] 每日/每周报告 (PDF 邮件发送)
- [ ] iOS 端 AVFoundation + MediaPipe 接入
- [ ] 图表交互：峰值点击查看分钟级详情
- [ ] WiFi 同步 (手机 → 桌面端看板)

---

## License

© 2026 ykz200. Released under the **MIT License**.
