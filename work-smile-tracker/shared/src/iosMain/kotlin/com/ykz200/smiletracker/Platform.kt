package com.ykz200.smiletracker

/**
 * iOS 端 Platform 特定代码占位
 * - Camera 使用 AVFoundation (Swift 侧手写 Swift-KMP bridge 或使用 SKIE 导出 Swift API)
 * - MediaPipe 视觉任务通过 CocoaPods: `pod 'MediaPipeTasksVision'` 直接集成
 * - 双端共享的 Repository 通过 Kotlin/Native 直接调用 SQLite (native-driver)
 *
 * 鉴于此仓库只推动 skeleton, iOS 端保持占位。实际接入有两种路线:
 *   1. 在 XCode 工程中单独 pod MediaPipeTasksVision, 通过 Swift 编写实现后, 暴露给 KMP 端使用 `actual` 注入
 *   2. 使用 SKIE 自动把 Kotlin Flow 导出为 Swift ObservableObject
 */
class IosCameraAnalyzer {
    fun start() { TODO("iOS camera 实现") }
    fun stop()  { TODO("iOS camera 实现") }
}

/** BGTaskScheduler 等价物 (iOS 后台刷新) 占位 */
object IosScheduler {
    fun scheduleDailySmileCapture() {
        // TODO: 使用 BGTaskScheduler (iOS 13+) 注册每分钟采样任务
        // 实际实现需要 XCode 工程侧配置 App 的 background modes = "processing"
    }
}
