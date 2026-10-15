package com.ykz200.smiletracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.work.*
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.ykz200.smiletracker.data.RepositoryProvider
import com.ykz200.smiletracker.db.SmileDatabase
import com.ykz200.smiletracker.ui.DashboardApp
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

// ═══════════════════════════════════════════════════════════════
// v8.0 色彩系统 — Astral Glow 星光璀璨 · 超低刺激 · 5层景深
// ═══════════════════════════════════════════════════════════════

private val DeepSpaceBg     = Color(0xFF030308)   // v8.0 更温暖的黑
private val WarmGold        = Color(0xFFFFE0A0)   // v8.0 更柔和暖金
private val LavenderPurple  = Color(0xFFD8C0F0)   // v8.0 更梦幻薰衣草
private val AuroraGlow      = Color(0xFF90F0C8)   // v8.0 星光绿
private val GlassCard       = Color(0xFF0A0A14).copy(alpha = 0.86f)  // v8.0 更通透
private val GlassBorder     = Color(0xFFFFE0A0).copy(alpha = 0.16f)  // v8.0 更微妙边框
private val TextPrimary     = Color(0xFFF8F8FF)   // v8.0 更温暖的白
private val TextSecondary   = Color(0xFFB8B8D0)   // v8.0 次要文字
private val TextMuted       = Color(0xFF5A5A78)   // v8.0
private val AccentPink      = Color(0xFFFFB0C8)   // v8.0 更轻柔粉
private val AccentCyan      = Color(0xFF90E8E8)   // v8.0
private val LiquidHighlight = Color.White.copy(alpha = 0.045f)  // v8.0 更低亮度
// v8.0 新增 — 星辉色温系统
private val MorningMist    = Color(0xFFD0E4F0)   // v8.0 晨雾蓝 — 更柔软
private val EveningRose    = Color(0xFFFFC0A8)   // v8.0 暮光玫瑰 — 更温柔
private val NightIndigo    = Color(0xFFB8C0E8)   // v8.0 夜空靛 — 更沉静

// ═══════════════════════════════════════════════════════════════
// v8.0 应用状态机
// ═══════════════════════════════════════════════════════════════

/**
 * v8.0 应用全局 UI 状态
 * 使用 sealed class 管理屏幕切换，取代原来的 Boolean 分屏逻辑
 */
sealed class AppUiState {
    /** 启动闪屏 — Canvas 绘制定制 Logo + 流光效果 */
    object Splash : AppUiState()

    /** 引导流程 — step: 0=欢迎, 1=隐私, 2=开始 */
    data class Onboarding(val step: Int) : AppUiState()

    /** 权限请求中 */
    object RequestingPermission : AppUiState()

    /** 主功能运行中 */
    object Running : AppUiState()

    /** 错误状态 */
    data class Error(val message: String) : AppUiState()
}

// ═══════════════════════════════════════════════════════════════
// MainActivity v8.0
// ═══════════════════════════════════════════════════════════════

/**
 * Android 端 MainActivity — v8.0
 *
 * 核心升级:
 *  - SplashScreen v8.0: Canvas动态绘制微笑弧线Logo，流光笔触效果
 *  - OnboardingFlow v8.0: 3步引导流程 (欢迎→隐私→开始)
 *  - 状态管理: sealed class AppUiState
 *  - 权限请求: 步骤化引导，拒绝后说明卡片，永久拒绝引导系统设置
 *  - 微交互: spring按压效果，slideInVertically入场，AnimatedContent切换
 *
 * 职责:
 *  - 运行时权限请求（摄像头、通知）
 *  - 初始化 RepositoryProvider
 *  - 调度 WorkManager 后台任务
 *  - 拉起前台 Service 保活
 */
class MainActivity : ComponentActivity() {

    private var cameraPermissionGranted by mutableStateOf(false)
    private var notificationPermissionGranted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化 AppContextHolder
        AppContextHolder.init(this)

        // 检查权限状态
        cameraPermissionGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            notificationPermissionGranted = true
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DeepSpaceBg
                ) {
                    AppContent()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 从系统设置返回后重新检查权限
        if (!cameraPermissionGranted) {
            cameraPermissionGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// v4.0 — AppContent 主入口 (状态机驱动)
// ═══════════════════════════════════════════════════════════════

@Composable
private fun AppContent() {
    val ctx = LocalContext.current
    // 全局状态
    var cameraGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(
        ctx, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED) }
    var notifGranted by remember { mutableStateOf(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    ) }

    var uiState: AppUiState by remember { mutableStateOf(AppUiState.Splash) }

    // 权限请求 Launcher
    val context = LocalContext.current

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraGranted = granted
        if (granted) {
            // 摄像头权限已授予，检查通知权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifGranted = false
            }
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifGranted = granted
    }

    // 主渲染 — 状态机驱动
    AnimatedContent(
        targetState = uiState,
        transitionSpec = {
            (fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 6 }) togetherWith
                    (fadeOut(tween(300)) + slideOutVertically(tween(300)) { -it / 6 })
        },
        label = "appUiStateTransition"
    ) { state ->
        when (state) {
            is AppUiState.Splash -> {
                SplashScreenV4(
                    onFinished = {
                        if (cameraGranted) {
                            uiState = AppUiState.Running
                        } else {
                            uiState = AppUiState.Onboarding(0)
                        }
                    }
                )
            }

            is AppUiState.Onboarding -> {
                OnboardingFlowV4(
                    currentStep = state.step,
                    onNext = {
                        val nextStep = state.step + 1
                        uiState = if (nextStep > 2) {
                            AppUiState.RequestingPermission
                        } else {
                            AppUiState.Onboarding(nextStep)
                        }
                    },
                    onSkip = {
                        uiState = AppUiState.RequestingPermission
                    }
                )
            }

            is AppUiState.RequestingPermission -> {
                PermissionRequestScreenV4(
                    onAllPermissionsGranted = {
                        cameraGranted = true
                        notifGranted = true
                        uiState = AppUiState.Running
                    },
                    onRequestCamera = {
                        cameraLauncher.launch(Manifest.permission.CAMERA)
                    },
                    onRequestNotification = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    cameraGranted = cameraGranted,
                    notificationGranted = notifGranted,
                    onCheckPermission = {
                        if (ContextCompat.checkSelfPermission(
                                context, Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            cameraGranted = true
                            uiState = AppUiState.Running
                        }
                    },
                    onOpenSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                        )
                    }
                )
            }

            is AppUiState.Running -> {
                SmileTrackerAppV4(
                    onAllPermissionsGranted = {
                        cameraGranted = true
                        notifGranted = true
                    }
                )
            }

            is AppUiState.Error -> {
                ErrorScreenV4(
                    message = state.message,
                    onRetry = { uiState = AppUiState.Splash }
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// v8.0 — 极光背景 (星辉色温 · 5层景深 · 柔和漂移)
// ═══════════════════════════════════════════════════════════════

/**
 * v8.0: 极光缓慢流动渐变背景 — Astral Glow 星光璀璨 版
 * 5层景深 + 昼夜色温感知: 根据时间段自动调整极光色调
 * 早晨偏冷蓝 → 正午偏暖金 → 傍晚偏橙粉 → 夜间偏靛蓝
 */
@Composable
private fun AuroraBackgroundV7(alpha: Float = 1f) {
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val auroraProgress by rememberInfiniteTransition(label = "auroraV7").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),  // v8.0 更慢的周期
            repeatMode = RepeatMode.Restart
        ),
        label = "auroraProgressV7"
    )

    // v8.0: 时间段色温 — 选择主色调
    val primaryGlow = when {
        hour in 6..10 -> MorningMist
        hour in 11..14 -> WarmGold
        hour in 15..17 -> AuroraGlow
        hour in 18..21 -> EveningRose
        else -> NightIndigo
    }
    val secondaryGlow = when {
        hour in 6..10 -> AuroraGlow
        hour in 11..14 -> LavenderPurple
        hour in 15..17 -> LavenderPurple
        hour in 18..21 -> WarmGold
        else -> LavenderPurple
    }
    val tertiaryGlow = when {
        hour in 6..10 -> LavenderPurple
        hour in 11..12 -> EveningRose
        hour in 15..17 -> MorningMist
        hour in 18..21 -> NightIndigo
        else -> AuroraGlow
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 基础深色背景
        drawRect(color = DeepSpaceBg)

        // v8.0 极光层 1 — 主色光带
        val offset1 = (w * 0.25f * kotlin.math.sin(auroraProgress.toDouble() * kotlin.math.PI * 2)).toFloat()
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryGlow.copy(alpha = 0.065f * alpha),
                    primaryGlow.copy(alpha = 0.02f * alpha),
                    Color.Transparent
                ),
                center = Offset(x = (w * 0.5f + offset1).toFloat(), y = h * 0.22f),
                radius = w * 0.55f
            )
        )

        // v8.0 极光层 2 — 副色光带
        val offset2 = (w * 0.2f * kotlin.math.cos(auroraProgress.toDouble() * kotlin.math.PI * 2.0 + 1.5)).toFloat()
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    secondaryGlow.copy(alpha = 0.05f * alpha),
                    Color.Transparent
                ),
                center = Offset(x = (w * 0.4f + offset2), y = h * 0.65f),
                radius = w * 0.45f
            )
        )

        // v8.0 极光层 3 — 第三色光带
        val offset3 = (w * 0.15f * kotlin.math.sin(auroraProgress.toDouble() * kotlin.math.PI * 2.0 + 3.0)).toFloat()
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    tertiaryGlow.copy(alpha = 0.045f * alpha),
                    Color.Transparent
                ),
                center = Offset(x = (w * 0.7f + offset3).toFloat(), y = h * 0.80f),
                radius = w * 0.40f
            )
        )

        // v8.0 极光层 4 — 顶部微光 (适配色温)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    primaryGlow.copy(alpha = 0.035f * alpha),
                    Color.Transparent
                ),
                startY = 0f,
                endY = h * 0.35f
            )
        )

        // v8.0 极光层 5 — 底部微光偏移带
        val offset5 = (w * 0.1f * kotlin.math.sin(auroraProgress.toDouble() * kotlin.math.PI * 2.0 + 2.5)).toFloat()
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    secondaryGlow.copy(alpha = 0.025f * alpha),
                    Color.Transparent
                ),
                center = Offset(x = (w * 0.3f + offset5).toFloat(), y = h * 0.92f),
                radius = w * 0.35f
            )
        )
    }
}

/** v8.0 AuroraBackgroundV6 兼容别名 */
@Composable private fun AuroraBackgroundV6(alpha: Float = 1f) = AuroraBackgroundV7(alpha)
/** v4.0 兼容别名 */
@Composable private fun AuroraBackgroundV4(alpha: Float = 1f) = AuroraBackgroundV7(alpha)

// ═══════════════════════════════════════════════════════════════
// v4.0 — SplashScreen: Canvas动态绘制微笑Logo + 流光效果
// ═══════════════════════════════════════════════════════════════

/**
 * 启动闪屏 v4.0 — Canvas动态绘制微笑弧线Logo
 *
 * 动画流程:
 *  1. 从底部绘制微笑弧线到完整笑脸 (约1.2s)
 *  2. 绘制笔触带流光渐变 stroke 效果
 *  3. Logo完成后呼吸缩放 (约0.8s)
 *  4. 隐私引导卡片淡入 (约0.5s)
 *  总计约 2.5s
 */
@Composable
private fun SplashScreenV4(onFinished: () -> Unit) {
    var showPrivacyCard by remember { mutableStateOf(false) }
    var startBreathing by remember { mutableStateOf(false) }

    // v6.0: 更柔和的绘制进度
    val drawProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(1600, easing = EaseOutQuart), // v6.0 更慢更柔
        label = "splashDrawProgressV6"
    )

    // v6.0: 流光效果 — 更慢的速度
    val flowProgress by rememberInfiniteTransition(label = "splashFlowV6").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "splashFlowProgressV6"
    )

    // v6.0: 呼吸缩放 — 更柔和的振幅
    val breatheScale by rememberInfiniteTransition(label = "splashBreatheV6").animateFloat(
        initialValue = 1f,
        targetValue = 1.045f, // v6.0 更小振幅
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = EaseInOutSine), // v6.0 更慢
            repeatMode = RepeatMode.Reverse
        ),
        label = "splashBreatheScaleV6"
    )

    // v6.0: 卡片淡入 — 更平滑
    val cardAlpha by animateFloatAsState(
        targetValue = if (showPrivacyCard) 1f else 0f,
        animationSpec = tween(800, easing = EaseOutQuart), // v6.0 使用更柔和的缓动
        label = "splashCardAlphaV6"
    )

    val currentScale = if (startBreathing) breatheScale else 1f

    // 控制时序: 1.4s绘制 → 0.6s呼吸 → 0.9s → 结束
    LaunchedEffect(Unit) {
        delay(1400)   // v6.0 等待绘制完成
        startBreathing = true
        delay(600)    // v6.0 短暂呼吸
        showPrivacyCard = true
        delay(900)    // v6.0 卡片淡入后结束
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpaceBg)
    ) {
        // 背景微光
        AuroraBackgroundV4(alpha = 0.5f)

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 动态绘制微笑Logo
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(currentScale),
                contentAlignment = Alignment.Center
            ) {
                SplashSmileLogoV4(
                    drawProgress = drawProgress,
                    flowProgress = flowProgress
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 品牌名称
            Text(
                text = "SmileTracker",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = WarmGold,
                textAlign = TextAlign.Center,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "记录每一份用心",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            // 隐私引导卡片 (淡入)
            if (showPrivacyCard) {
                PrivacyHintCardV4(alpha = cardAlpha)
            }
        }
    }
}

/**
 * v4.0 SplashScreen 动态绘制微笑Logo — Canvas流光效果
 * drawProgress: 0→1 控制弧线从底部绘制到完整笑脸
 * flowProgress: 0→1 控制流光渐变流动
 */
@Composable
private fun SplashSmileLogoV4(
    drawProgress: Float,
    flowProgress: Float
) {
    Canvas(modifier = Modifier.size(120.dp)) {
        val cx = size.width / 2
        val cy = size.height / 2
        val baseR = size.minDimension / 2 * 0.65f
        val strokeW = size.minDimension * 0.09f

        // 根据 drawProgress 计算当前可见的弧线角度
        // 从底部 (270°附近) 开始向上绘制到完整微笑 (200°→340°)
        val totalSweep = 140f
        val currentSweep = totalSweep * drawProgress
        val startAngle = 200f

        // 微笑主体弧线 — 带流光渐变
        val flowOffset = flowProgress * size.width
        val glowBrush = Brush.linearGradient(
            colors = listOf(
                WarmGold.copy(alpha = 0.2f),
                WarmGold,
                AuroraGlow,
                LavenderPurple,
                WarmGold.copy(alpha = 0.2f)
            ),
            start = Offset(flowOffset - size.width * 0.3f, flowOffset - size.height * 0.3f),
            end = Offset(flowOffset + size.width * 0.3f, flowOffset + size.height * 0.3f)
        )

        // 已绘制部分 — 流光
        drawArc(
            brush = glowBrush,
            startAngle = startAngle,
            sweepAngle = currentSweep,
            useCenter = false,
            topLeft = Offset(cx - baseR, cy - baseR * 0.7f),
            size = Size(baseR * 2, baseR * 2),
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )

        // 未绘制部分 — 微弱底色轨道
        if (drawProgress < 1f) {
            drawArc(
                color = WarmGold.copy(alpha = 0.08f),
                startAngle = startAngle + currentSweep,
                sweepAngle = totalSweep - currentSweep,
                useCenter = false,
                topLeft = Offset(cx - baseR, cy - baseR * 0.7f),
                size = Size(baseR * 2, baseR * 2),
                style = Stroke(width = strokeW * 0.6f, cap = StrokeCap.Round)
            )
        }

        // 眼睛和点缀 (绘制超过一半时显示)
        if (drawProgress > 0.6f) {
            val eyeAlpha = ((drawProgress - 0.6f) / 0.4f).coerceIn(0f, 1f)
            // 左眼
            drawCircle(
                color = WarmGold.copy(alpha = eyeAlpha),
                radius = strokeW * 0.35f,
                center = Offset(cx - baseR * 0.35f, cy - baseR * 0.1f)
            )
            // 右眼
            drawCircle(
                color = WarmGold.copy(alpha = eyeAlpha),
                radius = strokeW * 0.35f,
                center = Offset(cx + baseR * 0.45f, cy - baseR * 0.1f)
            )
            // 星光点
            drawCircle(
                color = AuroraGlow.copy(alpha = eyeAlpha * 0.6f),
                radius = strokeW * 0.2f,
                center = Offset(cx + baseR * 0.1f, cy - baseR * 0.55f)
            )
        }

        // 绘制完成后的中心光点
        if (drawProgress >= 1f) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(WarmGold.copy(alpha = 0.4f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = baseR * 0.3f
                ),
                radius = baseR * 0.3f,
                center = Offset(cx, cy)
            )
        }
    }
}

/**
 * 隐私引导卡片 — 简洁提示
 */
@Composable
private fun PrivacyHintCardV4(alpha: Float) {
    Box(
        modifier = Modifier
            .padding(horizontal = 40.dp)
            .alpha(alpha)
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(GlassCard)
            .border(1.dp, AuroraGlow.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "🔒 ",
                fontSize = 16.sp
            )
            Text(
                text = "所有数据仅在本地处理\n不会上传任何图像",
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// v4.0 — OnboardingFlow 引导流程
// ═══════════════════════════════════════════════════════════════

/**
 * 3步引导流程 v4.0
 *  - Step 0: 品牌欢迎 (Logo动画 + 产品介绍)
 *  - Step 1: 隐私说明 (简洁卡片 + 摄像头用途解释)
 *  - Step 2: 开始体验 (流光边框按钮 + spring按压效果)
 */
@Composable
private fun OnboardingFlowV4(
    currentStep: Int,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    valStepAnimTrigger(currentStep)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpaceBg)
    ) {
        // 背景极光
        AuroraBackgroundV4(alpha = 0.3f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 步骤内容区
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    (fadeIn(tween(400)) + slideInHorizontally(tween(400)) { it / 4 }) togetherWith
                            (fadeOut(tween(300)) + slideOutHorizontally(tween(300)) { -it / 4 })
                },
                label = "onboardingStepContent"
            ) { step ->
                when (step) {
                    0 -> OnboardingWelcomeStepV4()
                    1 -> OnboardingPrivacyStepV4()
                    2 -> OnboardingGetStartedStepV4()
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 步骤指示器
            StepIndicatorV4(currentStep = currentStep, totalSteps = 3)

            Spacer(modifier = Modifier.height(24.dp))

            // 按钮区域
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    fadeIn(tween(400)) togetherWith fadeOut(tween(200))
                },
                label = "onboardingButtonContent"
            ) { step ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (step) {
                        0 -> {
                            FlowingBorderButtonV4(
                                text = "开启微笑之旅",
                                onClick = onNext
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            SkipButtonV4(onClick = onSkip)
                        }
                        1 -> {
                            FlowingBorderButtonV4(
                                text = "我了解，继续",
                                onClick = onNext
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            SkipButtonV4(onClick = onSkip)
                        }
                        2 -> {
                            FlowingBorderButtonV4(
                                text = "开始体验",
                                onClick = onNext,
                                primary = true
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 触发步骤动画
 */
@Composable
private fun valStepAnimTrigger(currentStep: Int) {
    LaunchedEffect(currentStep) {
        delay(100)   // v6.0: 100ms 延迟确保重组完成
    }
}

/**
 * Step 0: 品牌欢迎 — Logo + 产品介绍
 */
@Composable
private fun OnboardingWelcomeStepV4() {
    // Logo呼吸动画
    val breatheScale by rememberInfiniteTransition(label = "welcomeBreathe").animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "welcomeBreatheScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
    ) {
        // Logo
        Box(
            modifier = Modifier
                .size(80.dp)
                .scale(breatheScale),
            contentAlignment = Alignment.Center
        ) {
            SplashSmileLogoV4(
                drawProgress = 1f,
                flowProgress = 0.5f
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Welcome",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = WarmGold,
            textAlign = TextAlign.Center,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "SmileTracker 让你轻松记录\n每天的微笑数据",
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "用前置摄像头分析表情变化\n见证每天的成长与进步",
            fontSize = 14.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}

/**
 * Step 1: 隐私说明 — 简洁卡片 + 摄像头用途
 */
@Composable
private fun OnboardingPrivacyStepV4() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
    ) {
        Text(
            text = "🔐 隐私优先",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "你的数据安全是我们的底线",
            fontSize = 16.sp,
            color = LavenderPurple,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 隐私要点卡片
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(GlassCard)
                .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PrivacyIconRowV4(
                icon = "📍",
                text = "所有数据只在本地处理",
                color = WarmGold
            )
            PrivacyIconRowV4(
                icon = "🚫",
                text = "不会上传或存储任何图像",
                color = LavenderPurple
            )
            PrivacyIconRowV4(
                icon = "🎛️",
                text = "你随时可以删除所有数据",
                color = AccentCyan
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "摄像头仅用于实时表情分析\n分析后立即丢弃图像帧",
            fontSize = 13.sp,
            color = TextMuted,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}

/**
 * 隐私要点行
 */
@Composable
private fun PrivacyIconRowV4(
    icon: String,
    text: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            fontSize = 20.sp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            fontSize = 15.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Step 2: 开始体验 — 流光边框按钮
 */
@Composable
private fun OnboardingGetStartedStepV4() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
    ) {
        // 闪烁星光效果
        val sparkleAlpha by rememberInfiniteTransition(label = "sparkle").animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "sparkleAlpha"
        )

        Text(
            text = "✨",
            fontSize = 40.sp,
            modifier = Modifier.graphicsLayer { alpha = sparkleAlpha }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "一切就绪",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "授权摄像头权限后\nSmileTracker 开始记录你的微笑",
            fontSize = 15.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "你的信任，我们用心守护",
            fontSize = 13.sp,
            color = WarmGold.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// v4.0 — 权限请求界面
// ═══════════════════════════════════════════════════════════════

/**
 * 权限请求界面 v4.0 — 步骤化引导 + 拒绝说明 + 系统设置入口
 */
@Composable
private fun PermissionRequestScreenV4(
    onAllPermissionsGranted: () -> Unit,
    onRequestCamera: () -> Unit,
    onRequestNotification: () -> Unit,
    cameraGranted: Boolean,
    notificationGranted: Boolean,
    onCheckPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    var showDeniedCard by remember { mutableStateOf(false) }
    var showPermanentlyDeniedCard by remember { mutableStateOf(false) }

    // 检查是否永久拒绝 (shouldShowRequestPermissionRationale 返回 false 且未授权)
    val permissionStatus = remember(cameraGranted) {
        if (cameraGranted) "granted"
        else {
            val activity = context as? ComponentActivity
            if (activity != null &&
                !activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
            ) "permanently_denied"
            else "denied"
        }
    }

    LaunchedEffect(permissionStatus) {
        when (permissionStatus) {
            "denied" -> showDeniedCard = true
            "permanently_denied" -> showPermanentlyDeniedCard = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpaceBg)
    ) {
        AuroraBackgroundV4(alpha = 0.25f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 权限图标
            Text(
                text = "📷",
                fontSize = 48.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "需要摄像头权限",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "用于实时分析表情数据\n本地处理，不保存任何图像",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            // 权限状态卡片
            if (cameraGranted) {
                // 已授权成功卡片
                SuccessCardV4(text = "摄像头权限已授予 ✓")
                Spacer(modifier = Modifier.height(16.dp))
                FlowingBorderButtonV4(
                    text = "进入 SmileTracker",
                    onClick = onAllPermissionsGranted,
                    primary = true
                )
            } else {
                // 拒绝后说明卡片
                if (showPermanentlyDeniedCard) {
                    DeniedPermanentlyCardV4(
                        onOpenSettings = onOpenSettings,
                        onCheckPermission = onCheckPermission
                    )
                } else if (showDeniedCard) {
                    DeniedExplanationCardV4(onRetry = onRequestCamera)
                    Spacer(modifier = Modifier.height(16.dp))
                    FlowingBorderButtonV4(
                        text = "重新授权",
                        onClick = onRequestCamera,
                        primary = true
                    )
                } else {
                    // 首次请求
                    FlowingBorderButtonV4(
                        text = "授权并开始",
                        onClick = onRequestCamera,
                        primary = true
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 重新检查入口
                TextButton(onClick = onCheckPermission) {
                    Text(
                        "已手动授权？点这里进入 →",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

/**
 * 成功卡片
 */
@Composable
private fun SuccessCardV4(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A3A2A).copy(alpha = 0.85f))
            .border(1.dp, Color(0xFF4ADE80).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            color = Color(0xFF4ADE80),
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 拒绝说明卡片 — 解释为什么需要权限
 */
@Composable
private fun DeniedExplanationCardV4(onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF3A1A1A).copy(alpha = 0.85f))
            .border(1.dp, AccentPink.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = "为什么需要摄像头权限？",
                fontSize = 15.sp,
                color = AccentPink,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "前置摄像头仅用于实时表情检测\n数据立即分析后丢弃原始图像\n不会保存或上传任何画面",
                fontSize = 13.sp,
                color = TextSecondary,
                lineHeight = 18.sp
            )
        }
    }
}

/**
 * 永久拒绝卡片 — 引导到系统设置
 */
@Composable
private fun DeniedPermanentlyCardV4(
    onOpenSettings: () -> Unit,
    onCheckPermission: () -> Unit
) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF3A2A1A).copy(alpha = 0.85f))
                .border(1.dp, WarmGold.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "需要手动开启权限",
                    fontSize = 15.sp,
                    color = WarmGold,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "系统设置 → 应用 → SmileTracker\n→ 权限 → 摄像头 → 允许",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        FlowingBorderButtonV4(
            text = "前往系统设置",
            onClick = onOpenSettings,
            primary = true
        )
        Spacer(modifier = Modifier.height(10.dp))
        TextButton(onClick = onCheckPermission) {
            Text(
                "已开启权限？点这里 →",
                color = TextMuted,
                fontSize = 13.sp
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// v4.0 — 错误界面
// ═══════════════════════════════════════════════════════════════

@Composable
private fun ErrorScreenV4(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpaceBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(text = "⚠️", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "出错了",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            FlowingBorderButtonV4(
                text = "重试",
                onClick = onRetry,
                primary = true
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// v4.0 — SmileTrackerApp 主功能
// ═══════════════════════════════════════════════════════════════

/**
 * 主 App 内容 v4.0 — 双屏切换 (PortraitCamera + Dashboard)
 */
@Composable
private fun SmileTrackerAppV4(onAllPermissionsGranted: () -> Unit) {
    // 初始化数据库
    LaunchedEffect(Unit) {
        initRepository()
    }

    // 调度后台任务
    LaunchedEffect(Unit) {
        scheduleBackgroundWork()
    }

    // 拉起前台服务
    LaunchedEffect(Unit) {
        startForegroundServiceSafely()
    }

    // 切换状态
    var showDashboard by remember { mutableStateOf(false) }

    AnimatedContent(
        targetState = showDashboard,
        transitionSpec = {
            fadeIn(animationSpec = tween(300)) togetherWith
                    fadeOut(animationSpec = tween(300))
        },
        label = "screenSwitchV4"
    ) { isDashboard ->
        if (isDashboard) {
            DashboardApp()
        } else {
            PortraitCameraScreen(
                onNavigateToDashboard = { showDashboard = true }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// v4.0 — 通用 UI 组件
// ═══════════════════════════════════════════════════════════════

/**
 * 流光边框按钮 v4.0 — spring按压效果 (stiffness=300, dampingRatio=0.6)
 */
@Composable
private fun FlowingBorderButtonV4(
    text: String,
    onClick: () -> Unit,
    primary: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val flowProgress by rememberInfiniteTransition(label = "flowV4").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flowProgressV4"
    )

    // v4.0: spring(stiffness=300, dampingRatio=0.6) 按压效果
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = 300f
        ),
        label = "buttonScaleV4"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .fillMaxWidth()
            .height(54.dp)
            .shadow(if (primary) 8.dp else 4.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (primary) WarmGold.copy(alpha = 0.12f)
                else Color(0xFF0A0A1A)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // 流光边框
        Canvas(modifier = Modifier.fillMaxSize().padding(1.5.dp)) {
            val flowBrush = Brush.sweepGradient(
                colorStops = arrayOf(
                    0.0f to WarmGold,
                    0.3f to LavenderPurple,
                    0.6f to AccentCyan,
                    0.9f to WarmGold,
                    1.0f to WarmGold
                ),
                center = Offset(
                    size.width / 2 + (flowProgress - 0.5f) * size.width * 0.8f,
                    size.height / 2 + (flowProgress - 0.5f) * size.height * 0.4f
                )
            )
            drawRoundRect(
                brush = flowBrush,
                topLeft = Offset.Zero,
                size = size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f, 14f),
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // 按钮文字
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (primary) WarmGold else Color.White
        )
    }
}

/**
 * 步骤指示器 v4.0 — 圆点指示当前步骤
 */
@Composable
private fun StepIndicatorV4(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until totalSteps) {
            val isActive = i == currentStep
            val width by animateDpAsState(
                targetValue = if (isActive) 28.dp else 8.dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "dotWidthV4"
            )
            val alpha by animateFloatAsState(
                targetValue = if (isActive) 1f else 0.35f,
                animationSpec = tween(300),
                label = "dotAlphaV4"
            )
            Box(
                modifier = Modifier
                    .width(width)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (isActive) WarmGold.copy(alpha = alpha)
                        else TextMuted.copy(alpha = alpha)
                    )
            )
        }
    }
}

/**
 * 跳过按钮 v4.0
 */
@Composable
private fun SkipButtonV4(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            "跳过引导",
            color = LavenderPurple.copy(alpha = 0.7f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// 初始化 & 后台任务 (保持不变)
// ═══════════════════════════════════════════════════════════════

private fun initRepository() {
    try {
        val driver: SqlDriver = AndroidSqliteDriver(
            schema = SmileDatabase.Schema,
            context = AppContextHolder.get(),
            name = "smile_tracker.db"
        )
        val database = SmileDatabase(driver)
        RepositoryProvider.init(database)
    } catch (e: Exception) {
        android.util.Log.e("MainActivity", "DB init failed: ${e.message}")
    }
}

private fun scheduleBackgroundWork() {
    val constraints = Constraints.Builder()
        .setRequiresBatteryNotLow(true)
        .build()
    val workRequest = PeriodicWorkRequestBuilder<SmileyAnalysisWorker>(15, TimeUnit.MINUTES)
        .setConstraints(constraints)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
        .addTag("smiley_analysis_worker")
        .build()
    WorkManager.getInstance(AppContextHolder.get())
        .enqueueUniquePeriodicWork("smiley_analysis", ExistingPeriodicWorkPolicy.KEEP, workRequest)
}

private fun startForegroundServiceSafely() {
    try {
        val intent = Intent(AppContextHolder.get(), SmileyAnalysisService::class.java)
        ContextCompat.startForegroundService(AppContextHolder.get(), intent)
    } catch (e: Exception) {
        android.util.Log.w("MainActivity", "Start FGS failed: ${e.message}")
    }
}

// ═══════════════════════════════════════════════════════════════
// AppContextHolder — 单例模式 (保持不变)
// ═══════════════════════════════════════════════════════════════

/**
 * Application Context 持有器 — 避免各处 this 传参
 */
object AppContextHolder {
    @Volatile
    private var context: android.content.Context? = null

    fun init(ctx: android.content.Context) {
        context = ctx.applicationContext
    }

    fun get(): android.content.Context =
        context ?: throw IllegalStateException("AppContextHolder not initialized")
}
