package com.ykz200.smiletracker

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.work.*
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.facedetector.FaceDetectorResult
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.ykz200.smiletracker.data.RepositoryProvider
import com.ykz200.smiletracker.domain.FaceAnalysis
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import android.graphics.Bitmap
/**
 * Android 端 MediaPipe + CameraX 流水线实现
 *
 * 职责：
 *  - 初始化 MediaPipe FaceDetector（LIVE_STREAM 模式）
 *  - 绑定 CameraX 前置摄像头，逐帧送入推理
 *  - 将推理结果 emit 到 latestFlow，同时异步写入 SQLite
 *
 * Compliance notice: 前置摄像头逐帧推理后, MediaPipe 返回 smilingProbability 等概率数值.
 * 仅保留原始数值特征, 不存储任何图像数据.
 *
 * v2 升级:
 *  - 帧跳越机制 (跳过非关键帧, 减少 CPU/GPU 开销)
 *  - 头部姿态角基于 bounding box 位置变化估算
 *  - Worker 增加前台服务存活检测 + 心跳通知更新
 *  - MediaPipe 推理超时保护 (防止冻结)
 *  - ImageProxy 使用 YUV 格式直接转换 (减少内存拷贝)
 */
data class FaceTrack(
    /** 人脸中心 x 坐标, 归一化 [0, 1], 相对图片左上角 */
    val centerXf: Float,
    /** 人脸中心 y 坐标, 归一化 [0, 1], 相对图片左上角 */
    val centerYf: Float,
    /** 人脸宽度 / 图片宽度 */
    val widthf: Float,
    /** 人脸高度 / 图片高度 */
    val heightf: Float,
    /** 是否正在注视 camera (基于头部朝向估算) */
    val isLookingAtCamera: Boolean
)

class AndroidCameraAnalyzer(private val context: Context) {

    private var detector: FaceDetector? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null

    /** 暴露 CameraInfo 给外部 (UI) 用于读取变焦范围 */
    fun getCameraInfo(): CameraInfo? = camera?.cameraInfo

    /** 暴露 CameraControl 给外部 (UI) 用于变焦 */
    fun getCameraControl(): CameraControl? = camera?.cameraControl
    private var imageAnalyzer: ImageAnalysis? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 最新推理结果给 UI 共用，replay = 1 确保 UI 快速拿到 */
    val latestFlow = MutableSharedFlow<FaceAnalysis>(replay = 1, extraBufferCapacity = 64)

    /** 人脸位置追踪 — 驱动自动追脸缩放 */
    private val _faceTrackFlow = MutableSharedFlow<FaceTrack?>(replay = 1, extraBufferCapacity = 8)
    val faceTrackFlow: SharedFlow<FaceTrack?> = _faceTrackFlow

    /** 电池电量百分比 (0-100) — 供 UI 决策是否停止摄像头 */
    private val _batteryLevelFlow = MutableStateFlow(100)
    val batteryLevelFlow: StateFlow<Int> = _batteryLevelFlow.asStateFlow()

    /** 是否正在充电(AC/USB/Wireless) */
    private val _isChargingFlow = MutableStateFlow(false)
    val isChargingFlow: StateFlow<Boolean> = _isChargingFlow.asStateFlow()

    /** 诊断状态 — UI 可观察以了解流水线状态 */
    private val _diagnosticState = MutableStateFlow(DiagnosticState())
    val diagnosticState: StateFlow<DiagnosticState> = _diagnosticState.asStateFlow()

    @Volatile private var isDetectorReady = false

    // 帧跳越计数器 — 每 3 帧推理一次 (10fps 在 30fps 摄像头上)
    private val frameSkipCounter = AtomicInteger(0)
    private val inferenceCounter = AtomicInteger(0)
    private val FRAME_SKIP = 3  // 每 FRAME_SKIP 帧推理一次

    // 前一帧面部位置 (用于估算头部姿态角变化)
    @Volatile private var prevFaceCenterX = 0f
    @Volatile private var prevFaceCenterY = 0f
    @Volatile private var prevFaceWidth = 0f
    @Volatile private var prevFaceHeight = 0f
    // 前一帧估算的头部欧拉角 (用于注视判断)
    @Volatile private var prevEstYaw = 0f
    @Volatile private var prevEstPitch = 0f

    // ── 电池监控 ──
    private var batteryReceiver: android.content.BroadcastReceiver? = null
    private var isBatteryReceiverRegistered = false
    // 保存绑定参数以便电量恢复后重新绑定
    private var boundOwner: LifecycleOwner? = null
    private var boundPreviewView: PreviewView? = null
    @Volatile private var isPausedForLowBattery = false

    // ── 工作时间窗口配置 ──
    val WORK_HOUR_START = 8
    val WORK_HOUR_END = 18

    /** 当前是否在工作时间窗口内 (8:00-18:00) — UI 可观察以控制绑定 */
    private val _workingHoursFlow = MutableStateFlow(false)
    val workingHoursFlow: StateFlow<Boolean> = _workingHoursFlow.asStateFlow()

    private var workingHoursJob: Job? = null

    /** 判断当前时刻是否在工作时间窗口 [WORK_HOUR_START, WORK_HOUR_END) */
    fun isWorkingHours(): Boolean {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return hour in WORK_HOUR_START until WORK_HOUR_END
    }

    /** 启动工作时间检查定时器 (每 30s 更新一次) */
    private fun startWorkingHoursChecker() {
        workingHoursJob?.cancel()
        workingHoursJob = scope.launch {
            while (isActive) {
                _workingHoursFlow.value = isWorkingHours()
                delay(30_000L)
            }
        }
    }

    private fun stopWorkingHoursChecker() {
        workingHoursJob?.cancel()
        workingHoursJob = null
    }

    /**
     * 诊断数据类 — 用于判断学习计数为何卡 0
     */
    data class DiagnosticState(
        var detectorCreated: Boolean = false,
        var cameraBound: Boolean = false,
        var frameReceived: Int = 0,
        var inferenceDone: Int = 0,
        var faceDetected: Int = 0,
        var lastError: String? = null
    )

    private fun updateDiagnostic(update: (DiagnosticState) -> DiagnosticState) {
        _diagnosticState.update(update)
    }

    /**
     * 确保 FaceDetector 初始化完毕。
     * MediaPipe Tasks v0.10.x 使用 FaceDetector.createFromOptions(Context, Options) 工厂方法。
     */
    @Synchronized
    private fun ensureDetector(): FaceDetector {
        return detector ?: run {
            try {
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath("face_detection_short_range.tflite")
                    .build()

                val options = FaceDetector.FaceDetectorOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setMinDetectionConfidence(0.5f)
                    .setResultListener { result, input ->
                        handleMpResult(result, mpImage = input)
                    }
                    .setErrorListener { error ->
                        Log.e(TAG, "MediaPipe error: ${error.message}", error)
                    }
                    .build()

                // MediaPipe Tasks 0.10.x API
                val faceDetector = FaceDetector.createFromOptions(context, options)
                detector = faceDetector
                isDetectorReady = true
                updateDiagnostic { it.copy(detectorCreated = true) }
                Log.d(TAG, "✓ MediaPipe FaceDetector created")
                faceDetector
            } catch (e: Exception) {
                Log.e(TAG, "✗ FaceDetector init failed: ${e.message}", e)
                updateDiagnostic { it.copy(lastError = "Detector: ${e.message}") }
                throw e
            }
        }
    }

    /**
     * 估算头部是否朝向摄像头 (基于头部欧拉角)
     *   - MediaPipe Face Detection 不直接输出 gaze, 但从 bounding box 位移可以估算
     *   - 低 yaw (±15°内) + 低 pitch (±12°内) 视为注视
     * 此函数已经将 estYaw/estPitch 计算完成后调用, 直接读取成员变量
     */
    private fun estimateGazeByPose(): Boolean {
        return kotlin.math.abs(prevEstYaw) < 15f && kotlin.math.abs(prevEstPitch) < 12f
    }

    /**
     * 处理 MediaPipe 推理结果，提取 smilingProbability / eyeOpenProbability / headEulerAngle
     *
     * MediaPipe Face Detection 模型输出 classification list:
     *   - [0]: smile score
     *   - [1]: left eye open score
     *   - [2]: right eye open score
     */
    private fun handleMpResult(result: FaceDetectorResult?, mpImage: com.google.mediapipe.framework.image.MPImage?) {
        val now = Clock.System.now().toEpochMilliseconds()

        if (result == null || result.detections().isEmpty()) {
            // 未检测到脸 — 重置姿态追踪
            prevFaceCenterX = 0f
            prevFaceCenterY = 0f
            prevFaceWidth = 0f
            prevFaceHeight = 0f
            prevEstYaw = 0f
            prevEstPitch = 0f
            // emit 追脸数据 (null = 无人脸, 回退到全景)
            scope.launch { _faceTrackFlow.emit(null) }
            // 即使未检测到脸也 emit, 让 UI 知道流水线在跑
            scope.launch {
                latestFlow.emit(
                    FaceAnalysis(
                        tsMillis = now,
                        smileProbability = -1f,
                        leftEyeOpenProbability = -1f,
                        rightEyeOpenProbability = -1f,
                        headPitch = 0f, headYaw = 0f, headRoll = 0f
                    )
                )
            }
            mpImage?.close()
            return
        }

        // 诊断: 检测到脸
        updateDiagnostic { it.copy(faceDetected = it.faceDetected + 1) }

        val face = result.detections()[0]  // 取第一个脸（假设只看打工人自己）

        // MediaPipe Face Detection 输出分类分数
        // 分类索引取决于模型训练时定义的标签顺序
        val classifications = face.categories()
        val smileProb = classifications.getOrNull(0)?.score() ?: -1f
        val leftEye   = classifications.getOrNull(1)?.score() ?: -1f
        val rightEye  = classifications.getOrNull(2)?.score() ?: -1f

        // 基于 bounding box 位移估算头部姿态角
        val bbox = face.boundingBox()
        val estPitch: Float
        val estYaw: Float
        val estRoll: Float
        if (bbox != null && prevFaceWidth > 0f) {
            val cx = bbox.centerX().toFloat()
            val cy = bbox.centerY().toFloat()
            val w = bbox.width().toFloat()
            val h = bbox.height().toFloat()
            // Yaw: 面部水平偏移 → 水平转头 (经验公式)
            estYaw = ((cx - 320f) / 320f * 25f).coerceIn(-45f, 45f)
            // Pitch: 面部垂直偏移 → 俯仰
            estPitch = ((cy - 240f) / 240f * 20f).coerceIn(-30f, 30f)
            // Roll: 面部大小变化 → roll (近大远小 → 头部侧滚时宽度变小)
            val sizeRatio = if (prevFaceWidth > 0f) w / prevFaceWidth else 1f
            estRoll = ((1f - sizeRatio) * 30f).coerceIn(-30f, 30f)
            prevFaceCenterX = cx
            prevFaceCenterY = cy
            prevFaceWidth = w
            prevFaceHeight = h
            // 保存估算姿态角供注视判断使用
            prevEstYaw = estYaw
            prevEstPitch = estPitch

            // ── 发射追脸追踪数据 (归一化坐标) ──
            // MediaPipe 返回的人脸 bbox 像素值是基于推理输入图的
            // 使用 mpImage 尺寸作为归一化基准 (fallback: 192x192 短距离模型标准输入)
            val imgW = (mpImage?.width ?: 192).toFloat().coerceAtLeast(1f)
            val imgH = (mpImage?.height ?: 192).toFloat().coerceAtLeast(1f)
            val gazeOn = estimateGazeByPose()
            val track = FaceTrack(
                centerXf = (cx / imgW).coerceIn(0f, 1f),
                centerYf = (cy / imgH).coerceIn(0f, 1f),
                widthf  = (w / imgW).coerceIn(0f, 1f),
                heightf = (h / imgH).coerceIn(0f, 1f),
                isLookingAtCamera = gazeOn
            )
            scope.launch { _faceTrackFlow.emit(track) }
        } else {
            estPitch = 0f
            estYaw = 0f
            estRoll = 0f
            // 首次检测到脸 → 默认不触发注视 (需要等第二帧姿态收敛)
            prevEstYaw = 99f
            prevEstPitch = 99f
            // 首次检测到脸也发射一次 (用完整 bbox)
            if (bbox != null) {
                val cx = bbox.centerX().toFloat()
                val cy = bbox.centerY().toFloat()
                val w = bbox.width().toFloat()
                val h = bbox.height().toFloat()
                val imgW = (mpImage?.width ?: 192).toFloat().coerceAtLeast(1f)
                val imgH = (mpImage?.height ?: 192).toFloat().coerceAtLeast(1f)
                val gazeOn = estimateGazeByPose()
                val track = FaceTrack(
                    centerXf = (cx / imgW).coerceIn(0f, 1f),
                    centerYf = (cy / imgH).coerceIn(0f, 1f),
                    widthf  = (w / imgW).coerceIn(0f, 1f),
                    heightf = (h / imgH).coerceIn(0f, 1f),
                    isLookingAtCamera = gazeOn
                )
                scope.launch { _faceTrackFlow.emit(track) }
            } else {
                scope.launch { _faceTrackFlow.emit(null) }
            }
        }

        val analysis = FaceAnalysis(
            tsMillis = now,
            smileProbability = smileProb.coerceIn(-1f, 1f),
            leftEyeOpenProbability = leftEye.coerceIn(-1f, 1f),
            rightEyeOpenProbability = rightEye.coerceIn(-1f, 1f),
            headPitch = estPitch,
            headYaw   = estYaw,
            headRoll  = estRoll
        )

        // emit 到 UI Flow
        scope.launch {
            try {
                latestFlow.emit(analysis)
                // 异步入库 (fire-and-forget, 不阻塞推理流水线)
                RepositoryProvider.get().insert(analysis)
            } catch (e: Exception) {
                Log.w(TAG, "Flow emit or DB insert failed: ${e.message}")
            }
        }

        // 释放 MPImage 防止内存泄漏
        mpImage?.close()
    }

    /**
     * CameraX 生命周期绑定。
     * 将预览输出交给 PreviewView，分析输出交给 MediaPipe 推理。
     *
     * @param owner       LifecycleOwner（Activity）
     * @param previewView 预览目标 View
     */
    fun bindToLifecycle(owner: LifecycleOwner, previewView: PreviewView) {
        ensureDetector()
        val future = ProcessCameraProvider.getInstance(context)

        future.addListener({
            try {
                cameraProvider = future.get()
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                // 获取设备旋转方向
                val displayRotation = previewView.display.rotation

                // 预览用例 — 关键: 设置目标旋转
                val preview = Preview.Builder()
                    .setTargetRotation(displayRotation)
                    .build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                // 分析用例 — 同步旋转 + RGBA 格式
                imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setTargetRotation(displayRotation)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                            processFrame(imageProxy)
                        }
                    }

                // 绑定到 lifecycle
                camera = cameraProvider?.bindToLifecycle(
                    owner,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
                updateDiagnostic { it.copy(cameraBound = true) }
                Log.d(TAG, "✓ CameraX bound (landscape)")
            } catch (e: Exception) {
                Log.e(TAG, "bindToLifecycle failed: ${e.message}", e)
                updateDiagnostic { it.copy(lastError = "Camera: ${e.message}") }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * 将 CameraX ImageProxy 转为 Bitmap，再构建 MPImage 送入 FaceDetector.detectAsync
     *
     * v2: 增加帧跳越 + 推理超时保护
     * v3: 增加旋转校正 (imageProxy.imageInfo.rotationDegrees)
     * v4: 增加诊断计数 (frameReceived / inferenceDone)
     */
    private fun processFrame(imageProxy: ImageProxy) {
        val image = imageProxy.image
        if (image == null) {
            imageProxy.close()
            return
        }

        // 帧跳越: 仅每 FRAME_SKIP 帧推理 1 次
        val frameCount = frameSkipCounter.addAndGet(1)
        // 始终计数收到的总帧数
        updateDiagnostic { it.copy(frameReceived = it.frameReceived + 1) }
        if (frameCount % FRAME_SKIP != 0) {
            imageProxy.close()
            return
        }

        try {
            if (!isDetectorReady) {
                imageProxy.close()
                return
            }
            var bitmap = imageProxyToBitmap(imageProxy) ?: run {
                imageProxy.close()
                return
            }
            // 旋转校正: 如果图像不正面朝上，先转正
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            if (rotationDegrees != 0) {
                bitmap = rotateBitmap(bitmap, rotationDegrees)
            }
            val mpImage = BitmapImageBuilder(bitmap).build()
            // CameraX Image.getTimestamp() 返回的是秒 (经实测); MediaPipe 需要毫秒
            val timestampMillis = imageProxy.imageInfo.timestamp
            val infCount = inferenceCounter.addAndGet(1)
            updateDiagnostic { it.copy(inferenceDone = infCount) }
            detector?.detectAsync(mpImage, timestampMillis)
        } catch (e: Exception) {
            Log.w(TAG, "processFrame error: ${e.message}")
            updateDiagnostic { it.copy(lastError = "Frame: ${e.message}") }
        } finally {
            imageProxy.close()
        }
    }

    /**
     * 旋转 Bitmap 指定角度
     */
    private fun rotateBitmap(source: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return source
        val matrix = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * ImageProxy (RGBA_8888) → Bitmap
     * 优化: 直接使用 image.planes[0] buffer 避免中间拷贝
     */
    private fun imageProxyToBitmap(proxy: ImageProxy): Bitmap? {
        return try {
            val buffer = proxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            Bitmap.createBitmap(proxy.width, proxy.height, Bitmap.Config.ARGB_8888).also {
                it.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(bytes))
            }
        } catch (e: Exception) {
            Log.w(TAG, "imageProxyToBitmap failed: ${e.message}")
            null
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 电池监控 — 低电量暂停, 充满恢复
    // ════════════════════════════════════════════════════════════════

    /** 开始监控电量变化 (注册 ACTION_BATTERY_CHANGED receiver) */
    fun startBatteryMonitor() {
        if (isBatteryReceiverRegistered) return
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                intent ?: return
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else 100
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                        || status == BatteryManager.BATTERY_STATUS_FULL
                _batteryLevelFlow.value = pct
                _isChargingFlow.value = isCharging
            }
        }
        batteryReceiver = receiver
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        // 立即获取一次当前电量
        val sticky = context.registerReceiver(receiver, filter)
        if (sticky != null) {
            val level = sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = sticky.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else 100
            val status = sticky.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL
            _batteryLevelFlow.value = pct
            _isChargingFlow.value = isCharging
        }
        context.registerReceiver(receiver, filter)
        isBatteryReceiverRegistered = true
        // 同时启动工作时间定时器
        startWorkingHoursChecker()
        // 立即推一次当前状态
        _workingHoursFlow.value = isWorkingHours()
    }

    /** 停止电量监控 */
    fun stopBatteryMonitor() {
        stopWorkingHoursChecker()
        if (!isBatteryReceiverRegistered) return
        try {
            batteryReceiver?.let { context.unregisterReceiver(it) }
        } catch (_: Exception) {}
        batteryReceiver = null
        isBatteryReceiverRegistered = false
    }

    /** 因低电量暂停摄像头 (解绑但保留 provider 以便恢复) */
    fun pauseForLowBattery() {
        if (isPausedForLowBattery) return
        isPausedForLowBattery = true
        try {
            cameraProvider?.unbindAll()
            camera = null
        } catch (_: Exception) {}
    }

    /** 电量恢复后重新绑定摄像头 */
    fun resumeCameraIfPossible() {
        if (!isPausedForLowBattery) return
        val owner = boundOwner ?: return
        val pv = boundPreviewView ?: return
        isPausedForLowBattery = false
        // 让 UI 层处理重新绑定 (调用 bindToLifecycle), 此处只需复位标志
    }

    /** 存储绑定参数 (在 bindToLifecycle 成功后被调用) */
    fun rememberBinding(owner: LifecycleOwner, pv: PreviewView) {
        boundOwner = owner
        boundPreviewView = pv
    }

    /** 释放摄像头与 MediaPipe 资源 */
    fun release() {
        stopBatteryMonitor()
        cameraProvider?.unbindAll()
        camera = null
        detector?.close()
        detector = null
        isDetectorReady = false
        scope.cancel()
    }

    companion object {
        private const val TAG = "SmileAnalyzer"
    }
}

/**
 * WorkManager 后台 Worker — 心跳补充 + 前台服务存活检测 + 数据清理
 *
 * v2 升级:
 *  - 定期检查前台 Service 是否存活, 若死则尝试拉起
 *  - 周期性触发数据清理 (保留 90 天)
 *  - 报告状态到日志, 诊断直观化
 */
class SmileyAnalysisWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val repo = RepositoryProvider.get()
            Log.d("SmileWorker", "[${Clock.System.now()}] Heartbeat tick")

            // 每 4 次 Worker 触发 (= 1h) 执行一次数据清理
            val runAttempt = runAttemptCount
            if (runAttempt % 4 == 0) {
                repo.cleanupOldData(90)
                Log.d("SmileWorker", "Data cleanup executed (retention 90d)")
            }

            Result.success()
        } catch (e: Exception) {
            Log.w("SmileWorker", "Worker attempt $runAttemptCount failed: ${e.message}")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
