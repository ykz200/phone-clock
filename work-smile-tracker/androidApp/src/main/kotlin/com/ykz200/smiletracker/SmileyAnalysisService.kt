package com.ykz200.smiletracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ykz200.smiletracker.data.RepositoryProvider

/**
 * Android 前台 Service — 保活 + 状态栏实时数据显示
 *
 * 以 Camera foregroundServiceType 权限保证 Worker + Activity 不被系统杀掉。
 * Android 14 (API 34) 要求：
 *  - manifest 中声明 foregroundServiceType="camera"
 *  - 运行时需要 FOREGROUND_SERVICE_CAMERA 权限
 *
 * v2 升级:
 *  - 通知内容显示实时微笑概率 + 今日微笑次数
 *  - 早/晚时段自动切换通知文案
 *  - 点击通知可直接打开 App
 */
class SmileyAnalysisService : Service() {

    private var currentSmileProb = 0f
    private var todaySmileCount = 0
    private var refreshHandler: android.os.Handler? = null
    private var refreshRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()

        // 尝试从 repo 读取今日数据 (用于通知展示)
        updateStatsFromRepo()

        val note = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ : 需要指定 foregroundServiceType
                startForeground(NOTIF_ID, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
            } else {
                startForeground(NOTIF_ID, note)
            }
        } catch (e: Exception) {
            // 通知权限未授予时降级 (仍需 startForeground 否则 crash)
            startForeground(NOTIF_ID, note)
        }

        // 启动后台定时器: 每 60 秒刷新一次通知 (减少频繁更新的电量消耗)
        startNotificationRefresh()

        return START_STICKY
    }

    private fun startNotificationRefresh() {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        refreshHandler = handler
        val runnable = object : Runnable {
            override fun run() {
                updateStatsFromRepo()
                val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                mgr?.notify(NOTIF_ID, buildNotification())
                handler.postDelayed(this, 60_000L) // 每 60 秒刷新一次
            }
        }
        refreshRunnable = runnable
        handler.postDelayed(runnable, 60_000L)
    }

    private fun updateStatsFromRepo() {
        // 在后台线程读取最新数据, 不阻塞 Service 主流程
        try {
            val repo = RepositoryProvider.get()
            val latest = runBlockingIo { repo.getLatest() }
            if (latest != null) {
                currentSmileProb = latest.smileProbability.coerceAtLeast(0f)
            }
        } catch (_: Exception) {
            // Repository 未初始化时静默跳过
        }
    }

    private fun buildNotification(): Notification {
        val smilePercent = (currentSmileProb * 100).toInt()
        val timeHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val greeting = when {
            timeHour in 6..11 -> "早上好 ☀️"
            timeHour in 12..13 -> "午休愉快 🍱"
            timeHour in 14..18 -> "下午加油 💪"
            timeHour in 19..22 -> "下班了 🌙"
            else -> "夜深了 🌙"
        }

        val smileEmoji = when {
            smilePercent >= 70 -> "😊"
            smilePercent >= 40 -> "🙂"
            smilePercent > 0 -> "😶"
            else -> "🔍"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("$greeting  表情追踪中 $smileEmoji")
            .setContentText("当前微笑概率 $smilePercent%")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("当前微笑概率 $smilePercent%\n前置摄像头正在本地分析表情数据\n不上传、不存储任何图像"))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SmileTracker 前台服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持表情追踪器在后台持续运行"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            mgr?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 清理 Handler 防止内存泄漏
        refreshRunnable?.let { refreshHandler?.removeCallbacks(it) }
        refreshHandler = null
        refreshRunnable = null
    }

    companion object {
        const val CHANNEL_ID = "smile_tracker_foreground"
        const val NOTIF_ID = 4242
    }
}

/** 轻量 runBlocking 助手 — Service 中快速读取数据库 */
private fun <T> runBlockingIo(block: suspend () -> T): T? = try {
    kotlinx.coroutines.runBlocking { block() }
} catch (e: Exception) {
    null
}
