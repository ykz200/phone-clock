package com.ykz200.smiletracker

/** 跨平台 Platform 预期声明 */
expect class PlatformHelper() {
    fun currentTimeMillis(): Long
}

/** 获取平台名称 */
expect fun createPlatform(): String
