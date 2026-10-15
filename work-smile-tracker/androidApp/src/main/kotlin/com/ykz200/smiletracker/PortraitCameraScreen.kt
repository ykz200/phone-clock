package com.ykz200.smiletracker

import android.util.Log
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ykz200.smiletracker.domain.FaceAnalysis
import com.ykz200.smiletracker.ui.EmotionProbabilities
import com.ykz200.smiletracker.ui.estimatePosture
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

// ════════════════════════════════════════════════════════════════════════
//  PortraitCameraScreen v8.0 — Astral Glow · 星光璀璨 · 沉浸式HUD
// ════════════════════════════════════════════════════════════════════════
//
//  v8.0 Upgrade "Astral Glow" 星光璀璨:
//   - 星辉色温系统: 更细腻的晨/午/晚/夜 四时段色温过渡
//   - 柔和星尘流光: 降低流光亮度, 减少 OLED 刺眼感
//   - 智能伙伴指示器: 微笑一致性追踪 + 情绪同步提示
//   - 呼吸灯优化: 呼吸节奏更慢更深, 助放松
//   - 情绪色环升级: 渐变过渡更平滑, 颜色更舒适
//   - 玻璃质感提升: 更柔和的毛玻璃质感
// ════════════════════════════════════════════════════════════════════════

// ── v8.0 Astral Glow Color Palette — 星光色温、超低刺激 ──
private val ColorBackgroundDeep    = Color(0xFF030308)  // v8.0 更温暖的黑
private val ColorGlassPanel        = Color(0xCC0C0C16).copy(alpha = 0.80f)  // v8.0 更通透
private val ColorGlassBorderSubtle = Color.White.copy(alpha = 0.07f)  // v8.0 更微妙
private val ColorLiveRed           = Color(0xFFFF6080)  // v8.0 更柔和的红
private val ColorPositiveGreen     = Color(0xFF90F0C8)  // v8.0 极光绿
private val ColorAuroraGlow        = Color(0xFF90F0C8)  // v8.0 星光绿
private val ColorWarningYellow     = Color(0xFFFFDAAA)
private val ColorLiquidHighlight   = Color.White.copy(alpha = 0.04f)  // v8.0 更低亮度
private val ColorLiquidRefraction  = Color.White.copy(alpha = 0.010f)  // v8.0 更微妙
// v8.0 晨/午/晚/夜 星辉色温
private val MorningMist   = Color(0xFFD0E4F0)   // v8.0 晨雾蓝 — 更柔软
private val NoonGold      = Color(0xFFFFE0A0)   // v8.0 正午金 — 更温暖
private val EveningRose   = Color(0xFFFFC0A8)   // v8.0 暮光玫瑰 — 更温柔
private val NightIndigo   = Color(0xFFB8C0E8)   // v8.0 夜空靛 — 更沉静
private val MistGray      = Color(0xFF1A1A28)   // v8.0 深空灰
// v8.0 Astral 星尘色彩
private val AuroraGreen   = Color(0xFF90F0C8)   // 星光绿
private val SunsetOrange  = Color(0xFFFCC090)   // 夕阳橙
private val NebulaPink    = Color(0xFFFFB0C8)   // 星云粉
private val CosmicViolet  = Color(0xFFC8B0F0)   // 宇宙紫

// ── v8.0 Spring Configuration — 更慢、更舒适 ──
private val SpringSnappy = spring<Float>(stiffness = 80f)  // v8.0 更柔和
private val SpringGentle = spring<Float>(stiffness = Spring.StiffnessLow)
private val SpringBreath = spring<Float>(stiffness = Spring.StiffnessVeryLow)  // v8.0 不变
private val SpringSoft = spring<Float>(dampingRatio = 0.78f, stiffness = 115f)  // v8.0 更柔和

// ── Color Constants for Emotions ──
private val ColorSmile    = Color(0xFF4CAF50)
private val ColorAngry    = Color(0xFFE91E63)
private val ColorNeutral  = Color(0xFF5C6BC0)
private val ColorThinking = Color(0xFF7E57C2)
private val ColorSurprise = Color(0xFFFFC107)
private val ColorFear     = Color(0xFFFF5722)
private val ColorDisgust  = Color(0xFF795548)

/**
 * v4.0: 摄像头检测主界面 — 全屏相机 + 全息 HUD 叠加
 *
 * 布局 (全屏 Box):
 *   底层: 摄像头 AndroidView (PreviewView) 全宽显示
 *   中层: 全息 HUD 面板 (半透明毛玻璃)
 *   顶层: 控件按钮 + 提示
 *
 * 双重功能:
 *   1. 左半区域叠加 — 情绪色环 + 姿态指示 + LIVE
 *   2. 右半区域叠加 — 积极度/能量/情绪分类 (v3 风格数据面板)
 *   3. 底部 — 微笑波形图 + 用眼提醒
 *
 * 极简模式: 双击预览区切换 HUD 显示/隐藏
 */
@Composable
fun PortraitCameraScreen(
    onNavigateToDashboard: () -> Unit = {},
    enableAnalysis: Boolean = true
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // ─── Core State ───
    var latestFace by remember { mutableStateOf<FaceAnalysis?>(null) }
    var emotionProbs by remember { mutableStateOf(EmotionProbabilities()) }
    var isLearning by remember { mutableStateOf(true) }
    var learningProgress by remember { mutableFloatStateOf(0f) }
    var baselineSampleCount by remember { mutableIntStateOf(0) }
    var dominantEmotion by remember { mutableStateOf(Pair("中性", 0f)) }
    var cameraReady by remember { mutableStateOf(false) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var analyzer by remember { mutableStateOf<AndroidCameraAnalyzer?>(null) }

    // ─── v5.1: 自动追脸变焦状态 ──—
    /** 电影感 800ms easeInOut 变焦动画值 */
    val faceZoom = remember { Animatable(1f) }
    /** 脸在画面中的归一化位置 (用于平移) */
    var faceCenterX by remember { mutableFloatStateOf(0.5f) }
    var faceCenterY by remember { mutableFloatStateOf(0.5f) }
    /** 视野中目标脸宽比 */
    var faceWidthRatio by remember { mutableFloatStateOf(1f) }
    /** 人脸丢失计时器 */
    var noFaceFrames by remember { mutableIntStateOf(0) }

    // ─── v5.2: 电池监控 — 低电量暂停 / 充满恢复 ───
    var batteryLevel by remember { mutableIntStateOf(100) }
    var isCharging by remember { mutableStateOf(false) }
    var isBatteryPaused by remember { mutableStateOf(false) }
    /** 触发重新绑定摄像头 (恢复时) */
    var bindTrigger by remember { mutableIntStateOf(0) }

    // ─── v4.0 New State ───
    var showMinimalMode by remember { mutableStateOf(false) }
    var eyeStrainDetected by remember { mutableStateOf(false) }
    var continuousUseTime by remember { mutableIntStateOf(0) }
    val smileHistory = remember { mutableStateListOf<Float>() }
    var ambientLightLevel by remember { mutableFloatStateOf(0.5f) }

    // v4.0 Smart Hint system — auto-cycling tips
    var smartHintText by remember { mutableStateOf<String?>(null) }

    // v4.0 Breathing dot animation for learning
    var learningPulseTrigger by remember { mutableIntStateOf(0) }

    // ─── Step 1: Initialize MediaPipe ───
    LaunchedEffect(enableAnalysis) {
        if (!enableAnalysis) return@LaunchedEffect
        try {
            val a = AndroidCameraAnalyzer(context)
            analyzer = a

            launch {
                a.diagnosticState.collect { diag ->
                    baselineSampleCount = diag.faceDetected
                }
            }

            launch {
                a.latestFlow.collect { face ->
                    latestFace = face

                    if (baselineSampleCount < 600) {
                        learningPulseTrigger++
                    } else if (isLearning) {
                        isLearning = false
                    }

                    learningProgress = (baselineSampleCount / 600f).coerceIn(0f, 1f)

                    val smileVal = face.smileProbability.coerceIn(0f, 1f)
                    smileHistory.add(smileVal)
                    if (smileHistory.size > 60) smileHistory.removeAt(0)

                    // Eye strain detection
                    val eyeClosed = face.leftEyeOpenProbability in 0f..0.3f ||
                            face.rightEyeOpenProbability in 0f..0.3f
                    if (eyeClosed && baselineSampleCount > 100) {
                        eyeStrainDetected = true
                    }

                    emotionProbs = EmotionProbabilities(
                        smile = face.smileProbability.coerceIn(0f, 1f),
                        angry = 0f,
                        neutral = if (face.smileProbability in 0.1f..0.4f) 0.6f else 0.1f,
                        thinking = if (face.headPitch < -5f) 0.3f else 0f,
                        surprise = if (face.smileProbability < 0.05f && face.headPitch > 5f) 0.4f else 0f
                    )
                    dominantEmotion = emotionProbs.dominantEmotion()
                }
            }
        } catch (e: Exception) {
            Log.e("PortraitScreenV4", "Analyzer init failed", e)
        }
    }

    // ─── Step 2: Bind PreviewView to lifecycle (仅工作时间 + 电量正常时) ───
    LaunchedEffect(previewView, analyzer, bindTrigger) {
        val pv = previewView
        val a = analyzer
        if (pv == null || a == null) return@LaunchedEffect
        // 电量低暂停期间不绑定
        if (isBatteryPaused) return@LaunchedEffect
        // 仅工作时间绑定
        if (!a.isWorkingHours()) return@LaunchedEffect
        try {
            a.bindToLifecycle(lifecycleOwner, pv)
            a.rememberBinding(lifecycleOwner, pv)
            a.startBatteryMonitor()
            cameraReady = true
        } catch (e: Exception) {
            Log.e("PortraitScreenV4", "bind failed", e)
        }
    }

    // ─── v5.2: 收集电量 + 工作时间信息 ───
    LaunchedEffect(analyzer) {
        val a = analyzer ?: return@LaunchedEffect
        launch { a.batteryLevelFlow.collect { batteryLevel = it } }
        launch { a.isChargingFlow.collect { isCharging = it } }
    }

    // ─── v5.2: 工作时间窗口控制 (8:00-18:00) ───
    LaunchedEffect(analyzer) {
        val a = analyzer ?: return@LaunchedEffect
        a.workingHoursFlow.collect { isWorking ->
            if (isWorking && !isBatteryPaused) {
                // 进入工作时间 → 触发绑定
                bindTrigger++
            } else if (!isWorking) {
                // 离开工作时间 → 解绑省电
                a.pauseForLowBattery()
                cameraReady = false
            }
        }
    }

    // ─── v5.2: 低电量暂停, 充满恢复 ───
    LaunchedEffect(analyzer, batteryLevel, isCharging) {
        val a = analyzer ?: return@LaunchedEffect
        if (!isCharging && batteryLevel <= 10) {
            // 未充电 + 电量 ≤ 10% → 暂停摄像头
            if (!isBatteryPaused) {
                isBatteryPaused = true
                a.pauseForLowBattery()
                cameraReady = false
            }
        } else if (isCharging && batteryLevel >= 100 && isBatteryPaused) {
            // 正在充电 + 电量 ≥ 100% → 恢复摄像头 (如果在工作时间内)
            if (isBatteryPaused && a.isWorkingHours()) {
                isBatteryPaused = false
                a.resumeCameraIfPossible()
                bindTrigger++
            }
        }
    }

    // ─── v5.1: 追脸变焦 — 只在用户注视 camera时平滑放大 ───
    LaunchedEffect(analyzer) {
        val a = analyzer ?: return@LaunchedEffect
        a.faceTrackFlow.collect { track ->
            if (track == null) {
                noFaceFrames++
                if (noFaceFrames > 15) {
                    faceCenterX = 0.5f
                    faceCenterY = 0.5f
                    faceWidthRatio = 1f
                    launch { faceZoom.animateTo(1f, tween(800, easing = EaseInOutCubic)) }
                }
            } else {
                noFaceFrames = 0
                faceCenterX = track.centerXf
                faceCenterY = track.centerYf
                faceWidthRatio = track.widthf
                if (track.isLookingAtCamera) {
                    // 注视时才平滑放大 (脸占画面 ~55%)
                    val targetZoom = (0.55f / track.widthf).coerceIn(1f, 3.5f)
                    launch { faceZoom.animateTo(targetZoom, tween(800, easing = EaseInOutCubic)) }
                } else {
                    // 没注视 (转头/侧脸) → 平滑回退全景
                    launch { faceZoom.animateTo(1f, tween(800, easing = EaseInOutCubic)) }
                }
            }
        }
    }

    // ─── v5.1: 追脸变焦 — 通过 CameraX CameraControl.setZoomRatio 实现
    LaunchedEffect(analyzer) {
        val a = analyzer ?: return@LaunchedEffect
        var cameraControl: CameraControl? = null
        var cameraInfo: CameraInfo? = null
        // 等待 cameraInfo 可用 (最多等 5 秒)
        for (i in 1..50) {
            cameraControl = a.getCameraControl()
            cameraInfo = a.getCameraInfo()
            if (cameraControl != null && cameraInfo != null) break
            delay(100)
        }
        val ctrl = cameraControl ?: return@LaunchedEffect
        val info = cameraInfo ?: return@LaunchedEffect
        // 读取摄像头支持的变焦范围
        val maxZoom = info.zoomState.value?.maxZoomRatio ?: 5f
        val minZoom = info.zoomState.value?.minZoomRatio ?: 1f
        val safeZoom = if (maxZoom > 1.2f) maxZoom * 0.85f else 1f
        while (true) {
            val zoom = faceZoom.value
            if (zoom in 1.05f..safeZoom) {
                runCatching { ctrl.setZoomRatio(zoom.coerceIn(minZoom, safeZoom)) }
            } else {
                runCatching { ctrl.setZoomRatio(minZoom) }
            }
            delay(100)
        }
    }

    // ─── v4.0: Eye strain timer (20 min reminder) ───
    LaunchedEffect(cameraReady) {
        if (cameraReady) {
            while (true) {
                delay(1000)
                continuousUseTime++
                if (continuousUseTime > 0 && continuousUseTime % 1200 == 0) {
                    eyeStrainDetected = true
                    delay(8000)
                    eyeStrainDetected = false
                }
            }
        }
    }

    // ─── v4.0: Smart Hint auto-cycle ───
    val hintMessages = remember {
        listOf(
            "保持微笑哦!",
            "注意坐姿端正",
            "记得眨眼放松",
            "关注面部表情变化",
            "让心情保持愉悦"
        )
    }
    LaunchedEffect(cameraReady, isLearning) {
        if (cameraReady && !isLearning) {
            while (true) {
                delay(12000)
                val randomHint = hintMessages.random()
                smartHintText = randomHint
                delay(3500)
                smartHintText = null
            }
        }
    }

    // ─── Cleanup ───
    DisposableEffect(Unit) {
        onDispose { analyzer?.release() }
    }

    // ═══════════════════════ FULL SCREEN LAYOUT ═══════════════════════
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ──── LAYER 0: Camera Preview (full screen) ────
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            // Camera preview area (9:16 top portion)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            ) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp)),
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            previewView = this
                        }
                    }
                )
            }
        }

        // ──── LAYER 1: Double-tap to toggle HUD ────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { showMinimalMode = !showMinimalMode }
                    )
                }
        )

        // ──── LAYER 2: Holographic HUD (animated visibility) ────
        val hudAlpha by animateFloatAsState(
            targetValue = if (showMinimalMode) 0f else 1f,
            animationSpec = spring(stiffness = 120f),
            label = "hudAlpha"
        )

        if (hudAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = hudAlpha }
            ) {
                // ─── Left-side HUD overlay: Emotion Ring + LIVE + Posture ───
                HolographicHUDPanel(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .aspectRatio(9f / 16f)
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp),
                    dominantEmotionName = dominantEmotion.first,
                    dominantEmotionProb = dominantEmotion.second,
                    isLearning = isLearning,
                    headPitch = latestFace?.headPitch ?: 0f,
                    headYaw = latestFace?.headYaw ?: 0f,
                    ambientLight = ambientLightLevel,
                    showMinimalMode = showMinimalMode
                )

                // ─── LIVE indicator — Top Right of preview ───
                EnhancedLiveIndicatorV4(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp, top = 14.dp),
                    isVisible = !showMinimalMode,
                    isActive = cameraReady
                )

                // ─── Ambient Light Indicator — Left side vertical bar ───
                AmbientLightIndicatorV4(
                    lightLevel = ambientLightLevel,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 4.dp)
                )

                // ─── Waveform Bar — Bottom of preview area ───
                if (!isLearning && smileHistory.size >= 3) {
                    WaveformBarV4(
                        data = smileHistory.toList(),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(0.88f)
                            .padding(bottom = 10.dp)
                    )
                }

                // ─── Posture Guide — Top left corner ───
                PostureGuideV4(
                    pitch = latestFace?.headPitch ?: 0f,
                    yaw = latestFace?.headYaw ?: 0f,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 16.dp, top = 14.dp)
                )

                // ─── Breathing Dot — Center (during learning) ───
                if (isLearning) {
                    BreathingDotV4(
                        progress = learningProgress,
                        pulseTrigger = learningPulseTrigger,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // ─── Learning Baseline Overlay ───
                if (isLearning) {
                    LearningBaselineOverlayV4(
                        progress = learningProgress,
                        count = baselineSampleCount,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(0.88f)
                            .padding(bottom = 60.dp)
                    )
                }

                // ─── Diagnostic Overlay ───
                if (baselineSampleCount == 0 && !cameraReady) {
                    DiagnosticOverlayV4(
                        analyzer = analyzer,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 8.dp, bottom = 8.dp)
                    )
                }

                // ─── Emotion Label (below face area) ───
                AnimatedVisibility(
                    visible = !isLearning && dominantEmotion.second > 0.3f,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = 90.dp),
                    enter = scaleIn(animationSpec = spring(stiffness = 120f)) + fadeIn(animationSpec = spring(stiffness = 120f)),
                    exit = scaleOut(animationSpec = spring(stiffness = 120f)) + fadeOut(animationSpec = spring(stiffness = 120f))
                ) {
                    DominantEmotionLabelV4(
                        name = dominantEmotion.first,
                        probability = dominantEmotion.second
                    )
                }
            }
        }

        // ──── LAYER 3: Right-side Data Panel (above HUD) ────
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.weight(1.5f))
            // Right 40% data panel
            AnimatedVisibility(
                visible = !showMinimalMode && !isLearning,
                modifier = Modifier.weight(1f),
                enter = slideInHorizontally(animationSpec = spring(stiffness = 120f), initialOffsetX = { it / 2 }) + fadeIn(animationSpec = SpringSnappy),
                exit = slideOutHorizontally(animationSpec = spring(stiffness = 120f), targetOffsetX = { it / 3 }) + fadeOut(animationSpec = SpringSnappy)
            ) {
                DataPanelV4(
                    emotionProbs = emotionProbs,
                    positivityIndex = emotionProbs.positivityIndex(),
                    onDashboardClick = onNavigateToDashboard
                )
            }
        }

        // ──── LAYER 4: Smart Hint Bubble (bottom-right) ────
        AnimatedVisibility(
            visible = smartHintText != null,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 80.dp),
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)),
            exit = slideOutVertically(
                targetOffsetY = { it / 2 },
                animationSpec = tween(500)
            ) + fadeOut(animationSpec = tween(500))
        ) {
            SmartHintBubbleV4(
                message = smartHintText ?: "",
                emotionName = dominantEmotion.first
            )
        }

        // ──── LAYER 5: Eye Strain Reminder (center modal) ────
        AnimatedVisibility(
            visible = eyeStrainDetected,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(animationSpec = tween(600)) + scaleIn(
                animationSpec = spring(stiffness = 120f),
                initialScale = 0.9f
            ),
            exit = fadeOut(animationSpec = tween(800)) + scaleOut(
                animationSpec = spring(stiffness = 120f),
                targetScale = 0.95f
            )
        ) {
            EyeRelaxReminderV4(onDismiss = { eyeStrainDetected = false })
        }

        // ──── LAYER 6: Minimal mode hint text ────
        AnimatedVisibility(
            visible = showMinimalMode,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(400)),
            exit = fadeOut(tween(400))
        ) {
            Text(
                text = "双击屏幕显示 HUD",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.4f),
                textAlign = TextAlign.Center
            )
        }

        // ──── LAYER 7: Emotion Particle Field (background decoration) ────
        if (!isLearning && dominantEmotion.second > 0.2f) {
            EmotionParticleField(
                emotionColor = emotionColorFromName(dominantEmotion.first),
                intensity = dominantEmotion.second,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
//  SUB-COMPONENTS v4.0
// ════════════════════════════════════════════════════════════════════════

// ─── 1. Holographic HUD Panel v5.0 ───
@Composable
private fun HolographicHUDPanel(
    modifier: Modifier = Modifier,
    dominantEmotionName: String,
    dominantEmotionProb: Float,
    isLearning: Boolean,
    headPitch: Float,
    headYaw: Float,
    ambientLight: Float,
    showMinimalMode: Boolean
) {
    // v6.0: 流光动画 — liquidFlowV6
    val liquidFlow = rememberInfiniteTransition(label = "liquidFlowV6")
        .animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(7000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ), label = "hudLiquidFlow"
        )

    Box(modifier = modifier) {
        // Background frosted glass panel with v5.0 Liquid refraction
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x330C0C14).copy(alpha = 0.55f),
                            Color.Transparent,
                            Color(0x330C0C14).copy(alpha = 0.45f)
                        )
                    )
                )
                .drawBehind {
                    // v6.0: 极光折射光效 with AuroraGreen
                    val flowX = size.width * liquidFlow.value
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                ColorLiquidRefraction,
                                ColorAuroraGlow.copy(alpha = 0.4f),
                                ColorLiquidRefraction,
                                ColorAuroraGlow.copy(alpha = 0.15f),
                                Color.Transparent
                            ),
                            start = Offset(flowX - size.width * 0.3f, 0f),
                            end = Offset(flowX + size.width * 0.15f, size.height)
                        )
                    )
                    // v6.0: secondary aurora refraction line
                    val flowY2 = size.height * ((liquidFlow.value + 0.5f) % 1f)
                    drawLine(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                ColorAuroraGlow.copy(alpha = 0.03f),
                                Color.Transparent
                            )
                        ),
                        start = Offset(flowX - size.width * 0.2f, flowY2),
                        end = Offset(flowX + size.width * 0.4f, flowY2),
                        strokeWidth = 1.dp.toPx()
                    )
                }
        )

        // Top glass strip — LIVE zone
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth(0.45f)
                .padding(start = 40.dp, top = 40.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            ColorGlassPanel.copy(alpha = 0.06f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Bottom glass strip — waveform zone
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.88f)
                .padding(start = 6.dp, bottom = 40.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            ColorGlassPanel.copy(alpha = 0.09f), // v5.0 略增透明度
                            Color.Transparent
                        )
                    )
                )
        )

        // ─── Center: Emotion Color Ring ───
        EmotionColorRingV4(
            emotionName = dominantEmotionName,
            emotionProb = dominantEmotionProb,
            isActive = !isLearning && dominantEmotionProb > 0.2f,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .aspectRatio(1f)
                .align(Alignment.Center)
        )

        // ─── Ambient Light right edge ───
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp)
                .width(3.dp)
                .fillMaxHeight(0.4f)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFFFFD68A).copy(alpha = ambientLight),
                            Color.Transparent
                        )
                    )
                )
        )

        // ─── Border glow effect v5.0 — 更精细的液态边框 ───
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .padding(4.dp)
        ) {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.045f),
                cornerRadius = CornerRadius(8.dp.toPx()),
                style = Stroke(width = 1f)
            )
            // v5.0: 额外的折射光线条
            val refractionY = size.height * liquidFlow.value
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        ColorLiquidRefraction,
                        Color.Transparent
                    )
                ),
                start = Offset(size.width * 0.1f, refractionY),
                end = Offset(size.width * 0.9f, refractionY),
                strokeWidth = 0.5.dp.toPx()
            )
        }
    }
}

// ─── 2. Emotion Color Ring v4.0 ───
@Composable
private fun EmotionColorRingV4(
    emotionName: String,
    emotionProb: Float,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val ringColor = emotionColorFromName(emotionName)
    val breathe by rememberInfiniteTransition(label = "ringBreatheV6").animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breatheV6"
    )

    val rotation by rememberInfiniteTransition(label = "ringRotateV6").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationV6"
    )

    val rotationReverse by rememberInfiniteTransition(label = "ringRotateRevV6").animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationRevV6"
    )

    val animatedAlpha by animateFloatAsState(
        targetValue = if (isActive) (0.5f + emotionProb * 0.5f) else 0f,
        animationSpec = spring(stiffness = 120f),
        label = "ringAlphaV4"
    )

    Canvas(modifier = modifier) {
        val cx = size.width / 2
        val cy = size.height / 2
        val baseRadius = size.minDimension * 0.28f * breathe

        if (animatedAlpha > 0.02f) {
            // Outer glow layer 2 — wide soft glow
            drawCircle(
                color = ringColor.copy(alpha = animatedAlpha * 0.08f),
                radius = baseRadius * 1.6f,
                center = Offset(cx, cy)
            )
            // Outer glow layer 1
            drawCircle(
                color = ringColor.copy(alpha = animatedAlpha * 0.15f),
                radius = baseRadius * 1.3f,
                center = Offset(cx, cy)
            )

            // Outer ring — rotating gradient arc
            val outerRadius = baseRadius * 1.15f
            val sweepArc = 100f + emotionProb * 80f

            rotate(rotation, Offset(cx, cy)) {
                drawArc(
                    color = ringColor.copy(alpha = animatedAlpha * 0.7f),
                    startAngle = 0f,
                    sweepAngle = sweepArc,
                    useCenter = false,
                    topLeft = Offset(cx - outerRadius, cy - outerRadius),
                    size = Size(outerRadius * 2, outerRadius * 2),
                    style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                )
                drawArc(
                    color = ringColor.copy(alpha = animatedAlpha * 0.4f),
                    startAngle = 180f,
                    sweepAngle = sweepArc * 0.8f,
                    useCenter = false,
                    topLeft = Offset(cx - outerRadius, cy - outerRadius),
                    size = Size(outerRadius * 2, outerRadius * 2),
                    style = Stroke(width = 2.5f, cap = StrokeCap.Round)
                )
            }

            // Inner ring — counter-rotating shimmer particles
            val innerRadius = baseRadius * 0.75f
            rotate(-rotationReverse, Offset(cx, cy)) {
                val dotCount = 8
                for (i in 0 until dotCount) {
                    val angle = Math.toRadians((i * 360.0 / dotCount))
                    val px = cx + innerRadius * kotlin.math.cos(angle).toFloat()
                    val py = cy + innerRadius * kotlin.math.sin(angle).toFloat()
                    val flicker = (kotlin.math.sin(angle * 3 + rotation * 0.02f).toFloat() + 1f) / 2f
                    drawCircle(
                        color = ringColor.copy(alpha = animatedAlpha * (0.3f + flicker * 0.4f)),
                        radius = 2.5f + flicker * 1.5f,
                        center = Offset(px, py)
                    )
                }
            }

            // Center breathing dot
            val dotRadius = baseRadius * 0.12f * (1f + emotionProb * 0.5f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ringColor.copy(alpha = animatedAlpha * 0.8f),
                        ringColor.copy(alpha = 0f)
                    ),
                    center = Offset(cx, cy),
                    radius = dotRadius * 2
                ),
                radius = dotRadius,
                center = Offset(cx, cy)
            )
        }
    }
}

// ─── 3. Posture Guide v4.0 ───
@Composable
private fun PostureGuideV4(
    pitch: Float,
    yaw: Float,
    modifier: Modifier = Modifier
) {
    val statusColor = when {
        kotlin.math.abs(pitch) < 8f && kotlin.math.abs(yaw) < 10f -> ColorPositiveGreen
        kotlin.math.abs(pitch) < 15f && kotlin.math.abs(yaw) < 20f -> ColorWarningYellow
        else -> ColorLiveRed
    }

    val statusText = estimatePosture(pitch, yaw, 0f)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Mini indicator
        Canvas(modifier = Modifier.size(18.dp)) {
            // Outer ring
            drawCircle(
                color = statusColor.copy(alpha = 0.5f),
                radius = size.minDimension / 2,
                style = Stroke(width = 1.5f)
            )
            // Inner dot — offset based on head position
            val dotOffset = Offset(
                (yaw / 30f * size.width * 0.2f).coerceIn(-size.width * 0.15f, size.width * 0.15f),
                (-pitch / 20f * size.height * 0.2f).coerceIn(-size.height * 0.15f, size.height * 0.15f)
            )
            drawCircle(
                color = statusColor,
                radius = size.minDimension * 0.15f,
                center = Offset(size.width / 2, size.height / 2) + dotOffset
            )
        }

        // Status text
        Text(
            text = statusText,
            fontSize = 9.sp,
            color = statusColor,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

// ─── 3b. Mood Temperature Indicator v6.0 (arc gauge for smile probability) ───
@Composable
private fun MoodTemperatureIndicatorV6(
    smileProbability: Float,
    modifier: Modifier = Modifier
) {
    // Color lerp from blue (cold/low smile) to yellow (warm/high smile)
    val tempColor = lerp(
        Color(0xFF42A5F5),  // cool blue
        Color(0xFFFFD54F),  // warm yellow
        smileProbability.coerceIn(0f, 1f)
    )

    val animatedProb by animateFloatAsState(
        targetValue = smileProbability.coerceIn(0f, 1f),
        animationSpec = SpringBreath,
        label = "moodTempV6"
    )

    Canvas(
        modifier = modifier.size(40.dp)
    ) {
        val strokeW = 3.dp.toPx()
        val arcSize = Size(size.width - strokeW, size.height - strokeW)
        val topLeft = Offset(strokeW / 2f, strokeW / 2f)

        // Background arc track (270 degree sweep, starting from bottom-left)
        drawArc(
            color = Color.White.copy(alpha = 0.08f),
            startAngle = 135f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )

        // Filled arc representing smile probability with lerp color
        drawArc(
            brush = Brush.sweepGradient(
                0f to Color(0xFF42A5F5),
                0.5f to Color(0xFF66BB6A),
                1f to Color(0xFFFFD54F),
                center = Offset(size.width / 2, size.height / 2)
            ),
            startAngle = 135f,
            sweepAngle = 270f * animatedProb,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )

        // Tiny indicator dot at the end of the arc
        val endAngle = Math.toRadians((135f + 270f * animatedProb).toDouble())
        val radius = size.width / 2 - strokeW / 2
        val dotCx = size.width / 2 + radius * kotlin.math.cos(endAngle).toFloat()
        val dotCy = size.height / 2 + radius * kotlin.math.sin(endAngle).toFloat()
        drawCircle(
            color = tempColor,
            radius = strokeW * 0.8f,
            center = Offset(dotCx, dotCy)
        )

        // Inner glow dot
        drawCircle(
            color = tempColor.copy(alpha = 0.4f),
            radius = strokeW * 1.4f,
            center = Offset(dotCx, dotCy)
        )
    }
}

// ─── 4. Smart Hint Bubble v4.0 ───
@Composable
private fun SmartHintBubbleV4(
    message: String,
    emotionName: String
) {
    val hintColor = emotionColorFromName(emotionName).copy(alpha = 0.8f)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ColorGlassPanel,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .drawBehind {
                    drawRoundRect(
                        color = hintColor.copy(alpha = 0.2f),
                        cornerRadius = CornerRadius(16.dp.toPx()),
                        style = Stroke(width = 1f)
                    )
                }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Icon dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(hintColor, CircleShape)
            )
            Text(
                text = message,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.9f),
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

// ─── 5. Ambient Light Indicator v4.0 ───
@Composable
private fun AmbientLightIndicatorV4(
    lightLevel: Float,
    modifier: Modifier = Modifier
) {
    val isLowLight = lightLevel < 0.3f
    val indicatorColor = if (isLowLight) Color(0xFF5C6BC0) else Color(0xFFFFC107)

    Row(
        modifier = modifier.height(100.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Vertical light bar
        Canvas(modifier = Modifier.width(4.dp).fillMaxHeight()) {
            val barHeight = size.height * lightLevel
            // Background track
            drawRoundRect(
                color = Color.White.copy(alpha = 0.05f),
                size = Size(size.width, size.height),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
            // Fill level (from bottom)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        indicatorColor.copy(alpha = 0.1f),
                        indicatorColor.copy(alpha = 0.7f)
                    )
                ),
                topLeft = Offset(0f, size.height - barHeight),
                size = Size(size.width, barHeight),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
        }

        // Sun / Moon icon
        Canvas(modifier = Modifier.size(10.dp)) {
            val color = indicatorColor
            val cx = size.width / 2
            val cy = size.height / 2
            if (!isLowLight) {
                // Sun — circle with rays
                drawCircle(color = color, radius = size.minDimension * 0.3f)
                // Rays
                for (i in 0 until 6) {
                    val angle = Math.toRadians((i * 60).toDouble())
                    val r1 = size.minDimension * 0.35f
                    val r2 = size.minDimension * 0.45f
                    drawLine(
                        color = color,
                        start = Offset(cx + r1 * kotlin.math.cos(angle).toFloat(), cy + r1 * kotlin.math.sin(angle).toFloat()),
                        end = Offset(cx + r2 * kotlin.math.cos(angle).toFloat(), cy + r2 * kotlin.math.sin(angle).toFloat()),
                        strokeWidth = 1f,
                        cap = StrokeCap.Round
                    )
                }
            } else {
                // Moon — crescent shape
                drawCircle(color = color, radius = size.minDimension * 0.35f)
                drawCircle(
                    color = ColorBackgroundDeep,
                    radius = size.minDimension * 0.28f,
                    center = Offset(size.width * 0.35f, size.height * 0.5f)
                )
            }
        }
    }
}

// ─── 6. Enhanced LIVE Indicator v5.0 — Liquid Glass Glow ───
@Composable
private fun EnhancedLiveIndicatorV4(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    isActive: Boolean
) {
    val pulse by rememberInfiniteTransition(label = "livePulseV6").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseV6"
    )

    val waveAlpha by rememberInfiniteTransition(label = "wavePulseV6").animateFloat(
        initialValue = 0.8f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = EaseOutCubic),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveV6"
    )

    val glowPulse by rememberInfiniteTransition(label = "glowV6").animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ), label = "glowV6"
    )

    val dotColor = if (isActive) ColorLiveRed else Color(0xFFFF8A65)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(modifier = Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                drawCircle(color = dotColor.copy(alpha = glowPulse * 0.2f), radius = size.minDimension / 2)
                drawCircle(color = dotColor.copy(alpha = waveAlpha * 0.3f), radius = size.minDimension / 2, style = Stroke(width = 1f))
            }
            Box(modifier = Modifier.size(8.dp).background(
                brush = Brush.radialGradient(listOf(dotColor.copy(alpha = pulse), dotColor.copy(alpha = pulse * 0.5f))),
                CircleShape
            ))
        }
        Text(text = "LIVE", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = dotColor.copy(alpha = pulse), letterSpacing = 2.sp)
    }
}

// ─── 7. Waveform Bar v4.0 (5-segment histogram) ───
@Composable
private fun WaveformBarV4(
    data: List<Float>,
    modifier: Modifier = Modifier
) {
    val waveColor = Color(0xFF8FD6A0)

    Surface(
        modifier = modifier.shadow(4.dp, RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp),
        color = ColorGlassPanel
    ) {
        Row(
            modifier = Modifier
                .drawBehind {
                    drawRoundRect(
                        color = waveColor.copy(alpha = 0.1f),
                        cornerRadius = CornerRadius(10.dp.toPx()),
                        style = Stroke(width = 1f)
                    )
                }
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .height(32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Label
            Text(
                text = "微笑",
                fontSize = 9.sp,
                color = Color(0xFF888888),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(24.dp)
            )

            // 6-segment histogram (v6.0)
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val segments = 6
                val segmentData = if (data.size >= segments) {
                    val step = data.size / segments
                    (0 until segments).map { i ->
                        val start = i * step
                        val end = minOf((i + 1) * step, data.size)
                        data.subList(start, end).average().toFloat()
                    }
                } else { List(segments) { 0f } }

                segmentData.forEachIndexed { index, value ->
                    val animatedHeight by animateFloatAsState(
                        targetValue = value.coerceIn(0.05f, 1f),
                        animationSpec = spring(stiffness = 120f),
                        label = "barV6_$index"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(animatedHeight)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        waveColor.copy(alpha = 0.9f),
                                        waveColor.copy(alpha = 0.4f)
                                    )
                                ),
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                }
            }

            // Current value
            Text(
                text = "${(data.lastOrNull() ?: 0f) * 100}%",
                fontSize = 10.sp,
                color = waveColor,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                modifier = Modifier.width(26.dp)
            )
        }
    }
}

// ─── 8. Eye Relax Reminder v4.0 ───
@Composable
private fun EyeRelaxReminderV4(onDismiss: () -> Unit) {
    var countdown by remember { mutableIntStateOf(20) }

    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
        delay(1000)
        onDismiss()
    }

    val isUrgent = countdown <= 10
    val ringColor = if (isUrgent) ColorLiveRed else Color(0xFF4FC3F7)

    // Urgent pulse animation (last 10 seconds)
    val urgentPulse by rememberInfiniteTransition(label = "urgentPulse").animateFloat(
        initialValue = 1f,
        targetValue = if (isUrgent) 0.6f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "urgentPulse"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xE60D0D0D),
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier
                .drawBehind {
                    drawRoundRect(
                        color = ringColor.copy(alpha = 0.2f),
                        cornerRadius = CornerRadius(20.dp.toPx()),
                        style = Stroke(width = 1.5f)
                    )
                }
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Eyes icon (Canvas draw)
            Canvas(modifier = Modifier.size(if (isUrgent) (40 * urgentPulse).dp else 36.dp)) {
                val color = ringColor
                val cx = size.width / 2
                val cy = size.height / 2
                val w = size.width * 0.35f
                val h = size.height * 0.2f
                drawOval(
                    color = color,
                    topLeft = Offset(cx - w * 1.2f, cy - h / 2),
                    size = Size(w, h),
                    style = Stroke(width = 2f)
                )
                drawOval(
                    color = color,
                    topLeft = Offset(cx + w * 0.2f, cy - h / 2),
                    size = Size(w, h),
                    style = Stroke(width = 2f)
                )
            }

            Text(
                text = "20-20-20 护眼法则",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = ringColor
            )
            Text(
                text = "请看向20英尺外持续20秒",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )

            // Countdown ring with last-10-sec red pulse
            val progress = (20 - countdown) / 20f
            Box(
                modifier = Modifier.size(if (isUrgent) (48 * urgentPulse).dp else 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeW = 3f
                    val radius = (size.minDimension - strokeW) / 2
                    drawCircle(
                        color = Color(0xFF1E1E1E),
                        radius = radius,
                        style = Stroke(width = strokeW)
                    )
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = progress * 360f,
                        useCenter = false,
                        topLeft = Offset((size.width - radius * 2) / 2, (size.height - radius * 2) / 2),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                }
                Text(
                    text = "${ countdown }",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = ringColor
                )
            }

            // Dismiss button
            Text(
                text = "知道了",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1A1A1A))
                    .clickable { onDismiss() }
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            )
        }
    }
}

// ─── 9. Breathing Dot v4.0 (learning baseline animation) ───
@Composable
private fun BreathingDotV4(
    progress: Float,
    pulseTrigger: Int,
    modifier: Modifier = Modifier
) {
    val breathe by rememberInfiniteTransition(label = "dotBreathe").animateFloat(
        initialValue = 0.9f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotBreathe"
    )

    val ringPulse by rememberInfiniteTransition(label = "dotRing").animateFloat(
        initialValue = 0.5f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseOutCubic),
            repeatMode = RepeatMode.Restart
        ),
        label = "dotRing"
    )

    val ringAlpha by rememberInfiniteTransition(label = "dotAlpha").animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseOutCubic),
            repeatMode = RepeatMode.Restart
        ),
        label = "dotAlpha"
    )

    Box(
        modifier = modifier.size(60.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer ring pulse
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            drawCircle(
                color = Color(0xFF4FC3F7).copy(alpha = ringAlpha * 0.3f),
                radius = size.minDimension / 2 * ringPulse,
                center = center,
                style = Stroke(width = 1.5f)
            )
            // Inner breathing dot
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AuroraGreen.copy(alpha = 0.8f),
                        Color(0xFF4FC3F7).copy(alpha = 0.3f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.minDimension * 0.4f * breathe
                ),
                radius = size.minDimension * 0.2f * breathe,
                center = center
            )
        }
    }
}

// ─── 10. Emotion Particle Field ───
@Composable
private fun EmotionParticleField(
    emotionColor: Color,
    intensity: Float,
    modifier: Modifier = Modifier
) {
    val particleCount = 16
    val animPhase = rememberInfiniteTransition(label = "particlePhase")
        .animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(24000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "particlePhase"
        )

    Canvas(modifier = modifier) {
        for (i in 0 until particleCount) {
            val seed = (i * 137.5f) % 360f
            val px = size.width * (0.1f + 0.8f * ((seed + animPhase.value * 200f) % 100f) / 100f)
            val py = size.height * (animPhase.value * 0.3f + (seed % 50f) / 100f) % (size.height * 0.5f)
            val radius = 1.5f + 2f * ((seed % 30f) / 30f)
            val alpha = (0.15f + 0.25f * intensity) * (0.5f + 0.5f * kotlin.math.sin(animPhase.value * 6.28f + seed).toFloat())

            drawCircle(
                color = emotionColor.copy(alpha = alpha),
                radius = radius,
                center = Offset(px, py)
            )
        }
    }
}

// ─── Data Panel v4.0 (right side) ───
@Composable
private fun DataPanelV4(
    emotionProbs: EmotionProbabilities,
    positivityIndex: Int,
    onDashboardClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .background(Color(0xFF0A0A0A))
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top: Dashboard button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            MiniCircleButtonV4(
                label = "D",
                backgroundColor = Color(0xCC1A1A1A),
                onClick = onDashboardClick
            )
        }

        // Positivity Index
        PositivityIndexCompactV4(index = positivityIndex)

        // Energy level row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                EmotionMiniStatV4("能量", emotionProbs.energyLevel(), Color(0xFFFFC107))
                EmotionMiniStatV4("效价", ((emotionProbs.valence + 1f) * 50).toInt(), Color(0xFF4CAF50))
        }

        // Emotion Bars
        EmotionBarsVerticalV4(
            emotions = emotionProbs.sorted().take(5)
        )
    }
}

// ─── Learning Baseline Overlay v4.0 ───
@Composable
private fun LearningBaselineOverlayV4(
    progress: Float,
    count: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = ColorGlassPanel
    ) {
        Column(
            modifier = Modifier
                .drawBehind {
                    drawRoundRect(
                        color = Color(0xFF4FC3F7).copy(alpha = 0.2f),
                        cornerRadius = CornerRadius(14.dp.toPx()),
                        style = Stroke(width = 1f)
                    )
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "学习中...",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = "请面对摄像头保持自然",
                fontSize = 10.sp,
                color = Color(0xFFAAAAAA)
            )
            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF1E1E1E))
            ) {
                val animatedProgress by animateFloatAsState(
                    targetValue = progress,
                    animationSpec = spring(stiffness = 120f),
                    label = "learnProgressV4"
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF4FC3F7), Color(0xFF00E5FF))
                            )
                        )
                )
            }
            Text(
                text = "$count / 600",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00E5FF)
            )
        }
    }
}

// ─── Diagnostic Overlay v4.0 ───
@Composable
private fun DiagnosticOverlayV4(
    analyzer: AndroidCameraAnalyzer?,
    modifier: Modifier = Modifier
) {
    if (analyzer == null) {
        Surface(modifier = modifier, shape = RoundedCornerShape(8.dp), color = Color(0xCC1A1A1A)) {
            Text(
                text = "等待初始化...",
                fontSize = 11.sp,
                color = Color(0xFFFF8A65),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        return
    }
    val diag by analyzer.diagnosticState.collectAsState()
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = Color(0xDD0E0E0E)
    ) {
        Column(
            modifier = Modifier
                .drawBehind {
                    drawRoundRect(
                        color = Color(0xFFFFC107).copy(alpha = 0.15f),
                        cornerRadius = CornerRadius(10.dp.toPx()),
                        style = Stroke(width = 1f)
                    )
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(text = "[诊断]", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFC107))
            Text(
                text = if (diag.detectorCreated) "[OK] 检测器已创建" else "[--] 等待...",
                fontSize = 10.sp,
                color = if (diag.detectorCreated) ColorPositiveGreen else Color(0xFFFF8A65)
            )
            Text(
                text = if (diag.cameraBound) "[OK] 摄像头已绑定" else "[--] 未绑定",
                fontSize = 10.sp,
                color = if (diag.cameraBound) ColorPositiveGreen else Color(0xFFFF8A65)
            )
            Text(
                text = "帧:${diag.frameReceived} 脸:${diag.faceDetected}",
                fontSize = 10.sp,
                color = Color(0xFFBBBBBB)
            )
            if (diag.lastError != null) {
                Text(text = "WARN: ${diag.lastError}", fontSize = 9.sp, color = ColorLiveRed)
            }
        }
    }
}

// ─── Dominant Emotion Label v4.0 ───
@Composable
private fun DominantEmotionLabelV4(name: String, probability: Float) {
    val emotionColor = emotionColorFromName(name)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xCC1A1A1A)
    ) {
        Row(
            modifier = Modifier
                .drawBehind {
                    drawRoundRect(
                        color = emotionColor.copy(alpha = 0.25f),
                        cornerRadius = CornerRadius(18.dp.toPx()),
                        style = Stroke(width = 1.5f)
                    )
                }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = emotionColor)
            Text(
                text = "${(probability * 100).toInt()}%",
                fontSize = 13.sp,
                color = emotionColor.copy(alpha = 0.7f),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ─── Positivity Index Compact v4.0 ───
@Composable
private fun PositivityIndexCompactV4(index: Int) {
    val indexColor = when {
        index >= 70 -> Color(0xFF4CAF50)
        index >= 40 -> Color(0xFFFFC107)
        else -> Color(0xFF5C6BC0)
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xCC141414)
    ) {
        Row(
            modifier = Modifier
                .drawBehind {
                    drawRoundRect(
                        color = indexColor.copy(alpha = 0.15f),
                        cornerRadius = CornerRadius(14.dp.toPx()),
                        style = Stroke(width = 1f)
                    )
                }
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = "积极指数", fontSize = 12.sp, color = Color(0xFF888888))
                Text(text = "$index", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = indexColor)
            }
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                val animatedIndex by animateFloatAsState(
                    targetValue = index / 100f,
                    animationSpec = spring(stiffness = 120f),
                    label = "positivityV4"
                )
                CircularProgressIndicator(
                    progress = { animatedIndex },
                    modifier = Modifier.size(48.dp),
                    color = indexColor,
                    trackColor = Color(0xFF1E1E1E),
                    strokeWidth = 4.dp
                )
                Text(text = "$index", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

// ─── Emotion Mini Stat v4.0 ───
@Composable
private fun EmotionMiniStatV4(label: String, value: Int, color: Color) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xCC141414),
        modifier = Modifier.width(60.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = "$value", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
            Text(text = label, fontSize = 9.sp, color = Color(0xFF888888))
        }
    }
}

// ─── Emotion Bars Vertical v4.0 ───
@Composable
private fun EmotionBarsVerticalV4(
    emotions: List<Triple<String, Float, Long>>
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xCC141414)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            emotions.forEach { (name, prob, colorLong) ->
                val color = Color(colorLong.toInt())
                val isTop = name == emotions.first().first
                EmotionBarRow(name, prob, color, isTop)
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
//  HELPER FUNCTIONS — 已拆分为 PortraitCameraHelpers.kt
// ════════════════════════════════════════════════════════════════════════

// ─── v6.0 emotionColorV6: aurora-tinted color mapping ───
/**
 * v7.0 Zen绽放: Maps emotion names to softer aurora-tinted colors.
 * Each emotion gets a distinct but softer palette hue for gentle visual variety.
 */
internal fun emotionColorV7(emotionName: String): Color = when (emotionName) {
    "微笑"   -> AuroraGreen        // Happy / positive — soft green
    "生气"   -> NebulaPink         // Angry / intense — softer pink
    "中性"   -> CosmicViolet       // Neutral / calm — dreamy violet
    "思考"   -> CosmicViolet       // Thinking — deep violet
    "惊讶"   -> SunsetOrange       // Surprise — gentler orange
    "恐惧"   -> CosmicViolet       // Fear — muted violet
    "厌恶"   -> SunsetOrange       // Disgust — softer orange
    else     -> AuroraGreen        // Default — aurora green
}

/** v7.0 兼容别名 */
internal fun emotionColorV6(emotionName: String): Color = emotionColorV7(emotionName)

// ─── v7.0 色温感知 — 根据时间段返回对应色温色彩 ───

/**
 * v7.0: 昼夜色温 — 根据当前时间返回对应的HUD色温色调
 * 早晨(6-10)冷蓝 → 正午(11-14)暖金 → 午后(15-17)中性 → 傍晚(18-21)橙粉 → 夜间(22-5)靛蓝
 */
internal fun getCircadianColor(hour: Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)): Color {
    return when {
        hour in 6..10 -> MorningMist.copy(alpha = 0.5f)
        hour in 11..14 -> NoonGold.copy(alpha = 0.4f)
        hour in 15..17 -> MistGray.copy(alpha = 0.3f)
        hour in 18..21 -> EveningRose.copy(alpha = 0.45f)
        else -> NightIndigo.copy(alpha = 0.5f)
    }
}

/**
 * v7.0: 色温描述文案 — 返回当前时段的描述
 */
internal fun getCircadianLabel(hour: Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)): String {
    return when {
        hour in 6..10 -> "晨间清爽"
        hour in 11..14 -> "正午温暖"
        hour in 15..17 -> "午后柔和"
        hour in 18..21 -> "暮色温柔"
        else -> "夜幕宁静"
    }
}
