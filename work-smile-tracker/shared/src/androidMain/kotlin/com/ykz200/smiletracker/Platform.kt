package com.ykz200.smiletracker

/** Android 端 Platform 特定代码占位 - CameraX/MediaPipe 模块内实现 (android source set) */
actual class PlatformHelper {
    actual fun currentTimeMillis(): Long = System.currentTimeMillis()
}

// Android 端注入给 shared 模块的实际驱动工厂
// 实际工程中由 app Module 的 Application.onCreate 调用后, shared 模块通过 RepositoryProvider 拿到
actual fun createPlatform(): String = "android"
