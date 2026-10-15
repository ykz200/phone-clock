# 工具链配置指南

## 1. Android Studio 安装

最新版本：2026.2.1.8 (Rabbit) - 已装到 D:\Android\AndroidStudio
安装器由 winget 静默安装，带完整 SDK + JDK 17

### 首次打开 (D:\Android\AndroidStudio\bin\studio64.exe)
Wizard:
- Import 不选
- Custom 安装
- SDK 默认 C:\Users\kzyuan\AppData\Local\Android\Sdk（后续可改 D:\Android\Sdk）
- 点 Next → Next → Accept → Finish

## 2. JDK
Android Studio 自带 JDK 17 (jbr-17)，无需额外装。

外部 JDK 装 D 盘:
`
winget install EclipseAdoptiumTemurin.17.JDK --location D:\Java\jdk-17
`

## 3. 项目环境变量

work-smile-tracker/local.properties（如不存在手动创建）:
`
sdk.dir=C\:\\Users\\kzyuan\\AppData\\Local\\Android\\Sdk
org.gradle.jvmargs=-Xmx4G -XX:MaxMetaspaceSize=2G -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
org.gradle.parallel=true
org.gradle.caching=true
`

## 4. 镜像（KMP + Compose Multiplatform 必做）

打开 Android Studio 后:
- shared/build.gradle.kts 的 pluginManagement + settings.gradle.kts 都加上阿里云镜像
- Maven Central + Google Maven 走国内镜像快 5-10 倍

## 5. 手动上传 MediaPipe AAR 备选

如果 Gradle 下载 com.google.mediapipe:tasks-vision 失败:
- 从 https://github.com/google/mediapipe/releases 下载 .aar
- 放 androidApp/libs
- build.gradle.kts api(files("libs/tasks-vision=aar"))

## 6. ADB / scrcpy 调用

- ADB: C:\Users\kzyuan\AppData\Local\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe
- scrcpy: C:\Users\kzyuan\AppData\Local\Microsoft\WinGet\Packages\Genymobile.scrcpy_Microsoft.Winget.Source_8wekyb3d8bbwe\scrcpy-win64-v4.1\scrcpy.exe

## 7. 首次 build 常见报错处理

  Default interface methods API 24 -> minSdk 改 26
  Cache doesn't exist for CompositionLocal -> Invalidate Caches
  MediaPipe load native fail -> assets 目录必须在 src/main/assets/

## 8. 首次 build 耗时

KMP + Compose Multiplatform 首次 build 下载 ~200-400MB 依赖，必须走国内镜像。
如无镜像，Gradle build 常死锁。

## 9. 运行 task list

- 等 Gradle Sync 完 (10-30 分钟)
- SDK Manager 勾选 Android SDK Platform-Tools (>=35)
- Pixel 3a USB 连接 -> adb devices
- run dropdown 选 androidApp -> Run
- 手机弹摄像头授权 -> Allow
- 进入极简黑底 UI
