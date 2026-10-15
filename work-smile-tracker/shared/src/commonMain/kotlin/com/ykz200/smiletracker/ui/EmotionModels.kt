package com.ykz200.smiletracker.ui

import kotlin.math.abs
import kotlin.math.ln

/**
 * 情绪概率 — 对应输出的 Emotion 模型结果
 * 每种情绪一个 Float [0..1]
 */
data class EmotionProbabilities(
    val smile: Float = 0f,     // 微笑
    val angry: Float = 0f,     // 生气
    val neutral: Float = 0f,   // 冷漠/中性
    val thinking: Float = 0f,  // 思考
    val surprise: Float = 0f,  // 惊讶
    val fear: Float = 0f,      // 恐惧
    val disgust: Float = 0f    // 厌恶
) {
    /** 返回当前最高情绪的标签+概率 */
    fun dominantEmotion(): Pair<String, Float> {
        val emotions = listOf(
            "微笑" to smile,
            "生气" to angry,
            "中性" to neutral,
            "思考" to thinking,
            "惊讶" to surprise,
            "恐惧" to fear,
            "厌恶" to disgust
        )
        return emotions.maxByOrNull { it.second } ?: ("中性" to 0f)
    }

    /** 积极指数: 正面情绪 - 负面情绪 (0..100) */
    fun positivityIndex(): Int {
        val positive = smile
        val negative = angry + fear + disgust
        return ((positive - negative).coerceIn(-1f, 1f) * 50 + 50).toInt().coerceIn(0, 100)
    }

    /** 情绪能量等级 (0=极度低落, 100=极度兴奋) */
    fun energyLevel(): Int {
        val activation = surprise + angry + smile * 0.5f
        val deactivation = neutral + fear * 0.3f
        return ((activation - deactivation * 0.3f).coerceIn(0f, 1f) * 100).toInt()
    }

    /** 情绪效价 (-1负面 ~ +1正面) */
    val valence: Float
        get() = (smile * 0.8f + surprise * 0.3f - angry * 0.7f - fear * 0.5f - disgust * 0.6f)
            .coerceIn(-1f, 1f)

    /** 所有情绪排序 (由高到低)，附带颜色 */
    fun sorted(): List<Triple<String, Float, Long>> {
        return listOf(
            Triple("微笑", smile, 0xFF4CAF50L),
            Triple("生气", angry, 0xFFE91E63L),
            Triple("中性", neutral, 0xFF5C6BCEL),
            Triple("思考", thinking, 0xFF7E57C2L),
            Triple("惊讶", surprise, 0xFFFFC107L),
            Triple("恐惧", fear, 0xFFFF5722L),
            Triple("厌恶", disgust, 0xFF795548L)
        ).sortedByDescending { it.second }
    }
}

/**
 * 应用主状态 — 分为 学习中 / 实时检测 两种模式
 */
enum class AppState {
    LEARNING_BASELINE,    // 正在学习基线 (前 60 秒)
    LIVE_DETECTING       // 实时检测中
}

/** 简单的情绪上升趋势 (本次 vs 上次) */
data class EmotionTrend(
    val rising: Boolean,
    val delta: Float
)

// ═══════════════════════════════════════════════════════════════
// v5.0 全新模型 — 连续追踪 + AI 建议 + 智能节律 + 社交分享
// ═══════════════════════════════════════════════════════════════

/**
 * 心情日记条目 — 手动记录的瞬时心情
 */
data class MoodEntry(
    val timestamp: Long,
    val emoji: String,          // 心情 emoji
    val label: String,          // 标签: "开心" / "平静" / "焦虑" / "疲惫"
    val intensity: Int,         // 强度 1-5
    val note: String? = null,   // 可选备注
    val trigger: String? = null // 触发因素: "工作" / "休息" / "运动" / "社交"
)

/**
 * 情绪快照 — 某个时刻的完整情绪状态 (用于趋势分析)
 * v6.0: 新增各情绪概率、积极指数、情绪标签字段
 */
data class EmotionSnapshot(
    val timestamp: Long,
    val smileProbability: Float,
    val dominantEmotion: String,
    val dominantProb: Float,
    val eyeStrainLevel: Float,  // 眼部疲劳等级 [0..1]
    val headPosture: String,    // 头部姿态: "正视" / "低头" / "偏头"
    val valence: Float = 0f,    // 效价
    val energyLevel: Int = 50,  // 能量等级
    // v6.0 新增: 各情绪概率 (用于情感共鸣评分与极光色计算)
    val angryProbability: Float = 0f,
    val neutralProbability: Float = 0f,
    val thinkingProbability: Float = 0f,
    val surpriseProbability: Float = 0f,
    val fearProbability: Float = 0f,
    val disgustProbability: Float = 0f,
    val positivityIndex: Int = 50,        // 积极指数 0-100
    val emotionTag: String = "neutral"    // 情绪标签: "positive" / "neutral" / "negative"
)

/**
 * v5.0 工作节奏分析结果 — 智能增强版 + 周期性洞察
 */
data class WorkRhythm(
    val peakHour: Int?,             // 最开心的小时 (0-23)
    val troughHour: Int?,           // 最低谷的小时
    val avgMorningSmile: Float,     // 上午平均微笑
    val avgAfternoonSmile: Float,   // 下午平均微笑
    val avgEveningSmile: Float,     // 晚间平均微笑
    val recommendation: String,     // 自动生成的建议
    val focusScore: Int = 50,       // 专注度评分
    val burnoutRisk: String = "低",  // 倦怠风险
    val weekOverWeekChange: Float = 0f, // 周同比变化
    val bestConsecutiveHours: Int = 0,  // v5.0 最长连续积极时段(小时)
    val flowStateFrequency: Float = 0f  // v5.0 心流状态频率
)

/**
 * v5.0 AI 智能建议 — 增加可执行度评分和分类
 */
data class EmotionInsight(
    val id: String,
    val category: InsightCategory,
    val title: String,
    val description: String,
    val action: String?,            // 可执行建议
    val priority: Int,              // 1-5 (5最高)
    val timestamp: Long,
    val actionableScore: Int = 0     // v5.0 可执行度 0-100
)

enum class InsightCategory {
    WELLNESS,       // 健康提醒
    PRODUCTIVITY,   // 效率建议
    SOCIAL,         // 社交提示
    REST,           // 休息提醒
    EXERCISE,       // 运动建议
    MINDFULNESS     // v5.0 正念提醒
}

/**
 * v5.0 心情连续追踪 — 时间序列上的心情变化
 */
data class MoodTimeline(
    val entries: List<MoodEntry>,
    val trendDirection: TrendDirection,
    val volatility: Float,          // 波动性 [0..1]
    val dominantMood: String,       // 主导心情
    val avgIntensity: Float,        // 平均强度
    val bestStreak: Int = 0,        // v5.0 最长连续积极天数
    val weeklyPattern: String = ""  // v5.0 周模式总结
)

enum class TrendDirection {
    IMPROVING,      // 改善中
    STABLE,         // 稳定
    DECLINING,      // 下降中
    FLUCTUATING     // 波动
}

/**
 * v5.0 用眼健康报告 — 增加长期趋势和预测
 */
data class EyeHealthReport(
    val totalScreenMinutes: Int,
    val avgBlinkRate: Float,           // 每分钟眨眼次数
    val strainScore: Int,              // 0-100
    val twentyTwentyTwentyCompliance: Float, // 20-20-20法则遵守率
    val recommendation: String,
    val strainTrend: String = "稳定",   // "改善" / "稳定" / "恶化"
    val dryEyeRisk: String = "低",      // v5.0 干眼风险
    val nextBreakDue: String = ""       // v5.0 下次休息时间建议
)

/**
 * v5.0 环境光检测结果
 */
data class AmbientLightInfo(
    val luxLevel: Float,           // 光照强度 (lux)
    val quality: LightQuality,     // 光线质量
    val screenBrightnessSuggestion: Int, // 建议屏幕亮度 0-100
    val eyeStrainRisk: String,     // "低" / "中" / "高"
    val circadianPhase: String = "" // v5.0 昼夜节律时段
)

enum class LightQuality {
    EXCELLENT,  // >500 lux
    GOOD,       // 300-500
    DIM,        // 100-300
    DARK,       // <100
    HARSH       // 过强/直射
}

/**
 * v5.0 每日摘要 — 用于分享和回顾
 */
data class DailySummary(
    val date: String,
    val totalSmiles: Int,
    val peakEmotion: String,
    val stressAverage: String,
    val highlight: String,        // 今日亮点
    val shareText: String,        // 分享文案
    val rankPercentile: Int = 50  // 超过同周期用户的百分比
)

/**
 * v5.0 长期趋势分析
 */
data class LongTermTrend(
    val periodDays: Int,
    val smileSlope: Float,          // 微笑趋势斜率
    val consistencyScore: Int,      // 一致性评分 0-100
    val topTrigger: String,         // 最常见的积极触发器
    val growthAreas: List<String>,  // 建议改进的领域
    val milestonesAchieved: List<String> // 达成的里程碑
)

/**
 * 辅助函数: 根据头部角度估算姿态
 */
fun estimatePosture(pitch: Float, yaw: Float, roll: Float): String {
    return when {
        pitch < -10 -> "低头专注"
        pitch > 8 -> "仰望思考"
        yaw > 15 -> "右侧注视"
        yaw < -15 -> "左侧注视"
        kotlin.math.abs(roll) > 10 -> "侧头思考"
        else -> "正视前方"
    }
}

/**
 * v5.0: 增强版智能建议生成器 — 多维度个性化
 */
fun generateSmartRecommendation(rhythm: WorkRhythm): String {
    val parts = mutableListOf<String>()
    
    rhythm.peakHour?.let { hour ->
        val hourLabel = when {
            hour < 12 -> "\u4e0a\u5348${hour}\u70b9"
            hour < 18 -> "\u4e0b\u5348${hour - 12}\u70b9"
            else -> "\u665a\u4e0a${hour - 12}\u70b9"
        }
        parts.add("\u4f60\u5728${hourLabel}\u5fc3\u60c5\u6700\u597d")
    }
    
    if (rhythm.avgMorningSmile > rhythm.avgAfternoonSmile) {
        parts.add("早上的你更爱笑, 重要会议尽量排上午")
    }
    
    if (rhythm.focusScore > 70) {
        parts.add("专注度优秀!")
    }

    // v5.0 新增：心流状态建议
    if (rhythm.flowStateFrequency > 0.6f) {
        parts.add("今天频繁进入心流状态，继续保持当前工作节奏")
    }

    // v5.0 新增：倦怠预警
    if (rhythm.burnoutRisk == "高") {
        parts.add("⚠️ 近期工作压力偏高，建议安排一次深度放松")
    }
    
    return parts.joinToString("; ").ifEmpty { "继续保持观察, 积累更多数据中" }
}

/**
 * v5.0: 心情追踪辅助 — 从连续快照中分析波动性
 */
fun analyzeVolatility(snapshots: List<EmotionSnapshot>): Float {
    if (snapshots.size < 2) return 0f
    val probabilities = snapshots.map { it.smileProbability }
    val avg = probabilities.average().toFloat()
    val variance = probabilities.map { (it - avg) * (it - avg) }.average().toFloat()
    return kotlin.math.sqrt(variance).coerceIn(0f, 1f)
}

/**
 * v5.0: 趋势方向判断
 */
fun determineTrend(snapshots: List<EmotionSnapshot>): TrendDirection {
    if (snapshots.size < 3) return TrendDirection.STABLE
    val recent = snapshots.takeLast(3).map { it.smileProbability }.average()
    val earlier = snapshots.dropLast(3).takeLast(3).map { it.smileProbability }.average()
    val diff = recent - earlier
    val volatility = analyzeVolatility(snapshots)
    
    return when {
        volatility > 0.3f -> TrendDirection.FLUCTUATING
        diff > 0.1f -> TrendDirection.IMPROVING
        diff < -0.1f -> TrendDirection.DECLINING
        else -> TrendDirection.STABLE
    }
}

/**
 * v5.0: 生成每日分享文案
 */
fun generateShareText(smileCount: Int, peakEmotion: String, streakDays: Int): String {
    return when {
        smileCount > 80 -> "今日微笑次数破百！保持好心情 😊 #SmileTracker 连续记录${streakDays}天"
        smileCount > 40 -> "今天笑得不少！主打一个开心 😄 已坚持记录${streakDays}天"
        smileCount > 15 -> "平平淡淡也是真～记录情绪第${streakDays}天 ✨"
        else -> "心情需要调节的一天，明天会更好的 🌙 #自我关怀"
    }
}

/**
 * v5.0: 计算一致性评分 (数据越规律分越高)
 */
fun calculateConsistencyScore(snapshots: List<EmotionSnapshot>): Int {
    if (snapshots.size < 5) return 50
    val intervals = snapshots.zipWithNext { a, b -> kotlin.math.abs(b.timestamp - a.timestamp) }
    val avgInterval = intervals.average()
    val deviation = intervals.map { kotlin.math.abs(it - avgInterval) }.average()
    val consistency = (1.0 - (deviation / (avgInterval + 1.0)).coerceIn(0.0, 1.0)) * 100
    return consistency.toInt().coerceIn(0, 100)
}

/**
 * v5.0: 检测心流状态 (连续长时间保持积极且稳定的情绪)
 */
fun detectFlowState(snapshots: List<EmotionSnapshot>, windowMinutes: Int = 30): Boolean {
    if (snapshots.size < 10) return false
    val recent = snapshots.takeLast(windowMinutes)
    if (recent.size < 5) return false
    val avgSmile = recent.map { it.smileProbability }.average()
    val volatility = analyzeVolatility(recent)
    return avgSmile > 0.5 && volatility < 0.15
}

/**
 * v5.0: 生成长期趋势里程碑
 */
fun checkMilestones(totalDays: Int, totalSmiles: Int, currentStreak: Int): List<String> {
    val milestones = mutableListOf<String>()
    if (totalDays >= 7) milestones.add("坚持一周! 🎉")
    if (totalDays >= 30) milestones.add("月度达人! 坚持30天 🏆")
    if (totalDays >= 100) milestones.add("百日里程碑! 超强毅力 💯")
    if (totalSmiles >= 100) milestones.add("累计微笑100次 😊")
    if (totalSmiles >= 1000) milestones.add("千笑达人! 累计微笑1000次 🌟")
    if (currentStreak >= 7) milestones.add("连续7天记录! 🔥")
    if (currentStreak >= 30) milestones.add("连续30天! 习惯养成 💪")
    return milestones
}

// ═══════════════════════════════════════════════════════════════
// v6.0 Aurora Glow — 新增智能模型
// ═══════════════════════════════════════════════════════════════

/**
 * v6.0: 心情预测 — 基于历史趋势预测未来情绪走向
 */
data class MoodForecast(
    val predictedTrend: String = "稳定",
    val predictedMood: String = "中性",
    val confidence: Float = 0.5f,
    val suggestion: String = "保持当前节奏",
    val forecastColor: Int = 0xFF9FA8DA.toInt()
)

/**
 * v6.0: 情感共鸣评分 — 评估用户情绪的多样性和平衡性 (0-100)
 * 高分 = 情绪丰富且平衡; 低分 = 情绪单调或极端
 */
fun calculateEmotionalResonance(snapshots: List<EmotionSnapshot>): Int {
    if (snapshots.size < 10) return 50
    val emotions = listOf("微笑", "生气", "中性", "思考", "惊讶", "恐惧", "厌恶")
    val weights = emotions.map { label ->
        snapshots.map { snap ->
            when (label) {
                "微笑" -> snap.smileProbability
                "生气" -> snap.angryProbability
                "中性" -> snap.neutralProbability
                "思考" -> snap.thinkingProbability
                "惊讶" -> snap.surpriseProbability
                "恐惧" -> snap.fearProbability
                else -> snap.disgustProbability
            }
        }.average()
    }
    // Shannon entropy-based diversity score
    val sum = weights.sum().coerceAtLeast(0.001)
    val normalized = weights.map { it / sum }
    val entropy = -normalized.filter { it > 0 }.sumOf { p -> p * ln(p) }
    val maxEntropy = ln(weights.size.toDouble())
    val diversityScore = (entropy / maxEntropy * 100).toInt().coerceIn(0, 100)

    // Check extremeness (one emotion too dominant)
    val maxWeight = weights.maxOrNull() ?: 0.0
    val extremenessPenalty = if (maxWeight > 0.7) 20 else 0

    return (diversityScore - extremenessPenalty).coerceIn(0, 100)
}

/**
 * v6.0: 极光情绪色 — 根据情绪混合实时计算色彩
 * 权重混合所有情绪对应的情感色
 */
fun auroraMoodColor(
    smile: Float, angry: Float, neutral: Float,
    thinking: Float, surprise: Float, fear: Float, disgust: Float
): Int {
    val colors = listOf(
        0xFFFFD68A.toInt() to smile,
        0xFFFF6B6B.toInt() to angry,
        0xFF9FA8DA.toInt() to neutral,
        0xFFB388FF.toInt() to thinking,
        0xFFFFE082.toInt() to surprise,
        0xFFFFAB40.toInt() to fear,
        0xFFA1887F.toInt() to disgust
    )
    var r = 0.0; var g = 0.0; var b = 0.0; var totalW = 0.0
    colors.forEach { (col, w) ->
        val rr = ((col shr 16) and 0xFF).toDouble()
        val gg = ((col shr 8) and 0xFF).toDouble()
        val bb = (col and 0xFF).toDouble()
        r += rr * w; g += gg * w; b += bb * w; totalW += w
    }
    if (totalW < 0.001) return 0xFF9FA8DA.toInt()
    val ri = (r / totalW).toInt().coerceIn(0, 255)
    val gi = (g / totalW).toInt().coerceIn(0, 255)
    val bi = (b / totalW).toInt().coerceIn(0, 255)
    return (0xFF000000.toInt() or (ri shl 16) or (gi shl 8) or bi)
}

/**
 * v6.0: 心情预测引擎 — 简单线性趋势外推
 */
fun generateMoodForecast(snapshots: List<EmotionSnapshot>): MoodForecast {
    if (snapshots.size < 10) return MoodForecast(confidence = 0.2f)
    val recent = snapshots.takeLast(20)
    val avgSmile = recent.map { it.smileProbability }.average()
    val avgPositive = recent.map { it.positivityIndex }.average()
    val moodSlope = recent.takeLast(10).map { it.smileProbability }.average() -
                     recent.take(10).map { it.smileProbability }.average()

    val trend = when {
        moodSlope > 0.15 -> "上升趋势 ✨"
        moodSlope < -0.15 -> "下降趋势 🌙"
        else -> "稳定平衡 ⚖️"
    }
    val mood = when {
        avgSmile > 0.7 -> "愉悦"
        avgSmile > 0.4 -> "平静"
        avgPositive > 60 -> "积极"
        avgPositive < 40 -> "低落"
        else -> "中性"
    }
    val suggestion = when {
        moodSlope > 0.15 -> "情绪在上升! 保持当前活动"
        moodSlope < -0.15 -> "情绪有所低落，建议起身活动"
        avgSmile < 0.3 -> "今天微笑较少，试试听听音乐"
        else -> "情绪稳定，继续保持"
    }
    val color = when {
        moodSlope > 0.15 -> 0xFF69F0AE.toInt()  // AuroraGreen
        moodSlope < -0.15 -> 0xFF9FA8DA.toInt()  // Indigo
        else -> 0xFFFFD68A.toInt()  // WarmGold
    }
    return MoodForecast(
        predictedTrend = trend,
        predictedMood = mood,
        confidence = (0.4 + kotlin.math.abs(moodSlope) * 2).toFloat().coerceIn(0.2f, 0.9f),
        suggestion = suggestion,
        forecastColor = color
    )
}

// ═══════════════════════════════════════════════════════════════
// v7.0 "Zen绽放" — 情绪天气 · 智能日程 · 情绪周期 · 体验增强
// ═══════════════════════════════════════════════════════════════

/**
 * v7.0: 情绪天气模型 — 用天气术语诗意化描述整体情绪状态
 * 结合微笑占比、积极性、波动性、能量等级 → 拟天气表达
 */
data class EmotionWeather(
    val condition: WeatherCondition,
    val temperature: Int,       // "情绪温度" 0-100
    val humidity: Int,          // "情绪湿度" (压力累积度)
    val windSpeed: Int,         // "情绪风速" (变化速率)
    val forecast: String,       // 天气式预测文案
    val icon: String,           // 天气图标 emoji
    val accentColor: Long       // 对应的点缀色
)

enum class WeatherCondition {
    SUNNY,          // 阳光明媚 — 高微笑高积极
    PARTLY_CLOUDY,  // 多云 — 中等微笑有波动
    OVERCAST,       // 阴天 — 低微笑偏中性
    LIGHT_RAIN,     // 小雨 — 负面情绪上升
    THUNDERSTORM,   // 雷雨 — 情绪剧烈波动
    STARRY_NIGHT,   // 星夜 — 平静安宁
    DAWN_GLOW,      // 晨曦 — 低开高走
    GOLDEN_HOUR     // 黄金时刻 — 巅峰状态
}

/**
 * v7.0: 生成当前情绪天气
 */
fun generateEmotionWeather(snapshots: List<EmotionSnapshot>): EmotionWeather {
    if (snapshots.size < 5) {
        return EmotionWeather(
            condition = WeatherCondition.PARTLY_CLOUDY,
            temperature = 50, humidity = 40, windSpeed = 10,
            forecast = "数据积累中，稍后将为你生成情绪天气 🌤",
            icon = "🌤", accentColor = 0xFF9FA8DA
        )
    }
    val recent = snapshots.takeLast(30)
    val avgSmile = recent.map { it.smileProbability }.average().toFloat()
    val avgPositive = recent.map { it.positivityIndex }.average()
    val volatility = analyzeVolatility(recent)
    val avgEnergy = recent.map { it.energyLevel }.average()
    val avgValence = recent.map { it.valence }.average().toFloat()
    val trendSlope = if (recent.size >= 6) {
        recent.takeLast(6).map { it.smileProbability }.average() -
            recent.take(6).map { it.smileProbability }.average()
    } else 0.0

    val temp = (avgSmile * 60 + avgPositive * 0.4).toInt().coerceIn(0, 100)
    val humid = ((1.0 - avgSmile) * 50 + volatility * 50).toInt().coerceIn(0, 100)
    val wind = (volatility * 80 + kotlin.math.abs(trendSlope) * 20).toInt().coerceIn(0, 100)

    return when {
        avgSmile > 0.7 && avgPositive > 65 && volatility < 0.2 ->
            EmotionWeather(WeatherCondition.SUNNY, temp, humid, wind,
                "今天阳光明媚，笑容灿烂！适合社交和重要决策 ☀️",
                "☀️", 0xFFFFD68A)
        avgSmile > 0.6 && avgEnergy > 60 ->
            EmotionWeather(WeatherCondition.GOLDEN_HOUR, temp, humid, wind,
                "处于黄金时刻，能量充沛！抓住这个状态做最重要的事 ✨",
                "✨", 0xFFFFAB40)
        trendSlope > 0.1 && volatility < 0.25 ->
            EmotionWeather(WeatherCondition.DAWN_GLOW, temp, humid, wind,
                "情绪如晨曦初升，越来越好！保持当前节奏 🌅",
                "🌅", 0xFFFF80AB)
        volatility < 0.15 && avgValence > 0f ->
            EmotionWeather(WeatherCondition.STARRY_NIGHT, temp, humid, wind,
                "内心如星夜般安宁平静，享受这份宁静 🌌",
                "🌌", 0xFF9FA8DA)
        avgSmile > 0.4 && avgSmile < 0.7 && volatility > 0.25 ->
            EmotionWeather(WeatherCondition.PARTLY_CLOUDY, temp, humid, wind,
                "情绪有晴有云，属正常波动。注意适时自我调节 ⛅",
                "⛅", 0xFF8AC4F7)
        avgSmile in 0.2f..0.4f && volatility < 0.2 ->
            EmotionWeather(WeatherCondition.OVERCAST, temp, humid, wind,
                "情绪有些沉闷，像是阴天。试着出门走走或听首喜欢的歌 ☁️",
                "☁️", 0xFF78909C)
        avgPositive < 35 || avgValence < -0.3 ->
            EmotionWeather(WeatherCondition.LIGHT_RAIN, temp, humid, wind,
                "情绪有小雨，给自己一些温柔。深呼吸，一切会好起来的 🌧",
                "🌧", 0xFF7986CB)
        volatility > 0.4 || (trendSlope < -0.15 && avgValence < -0.1) ->
            EmotionWeather(WeatherCondition.THUNDERSTORM, temp, humid, wind,
                "情绪波动较大，请允许自己暂停。喝杯温水，休息一下 ⛈",
                "⛈", 0xFFB388FF)
        else ->
            EmotionWeather(WeatherCondition.PARTLY_CLOUDY, temp, humid, wind,
                "情绪如多云天气，有起有伏是生活的常态 🌤",
                "🌤", 0xFF8AC4F7)
    }
}

/**
 * v7.0: 智能日程建议 — 基于当前情绪状态给出时段性建议
 */
data class SmartScheduleAdvice(
    val currentPhase: String,     // "晨间启动" / "午后低谷" / "傍晚恢复" / "夜间安宁"
    val bestTaskType: String,     // 推荐做的事项类型
    val breakReminder: String,    // 休息提示
    val socialSuggestion: String, // 社交建议
    val icon: String
)

fun generateSmartScheduleAdvice(
    snapshots: List<EmotionSnapshot>,
    currentHour: Int = 12
): SmartScheduleAdvice {
    val weather = generateEmotionWeather(snapshots)
    return when {
        currentHour in 6..9 -> SmartScheduleAdvice(
            "晨间启动",
            if (weather.temperature > 50) "处理重要决策和规划" else "轻松热身任务，逐渐进入状态",
            "早餐后15分钟再开始工作",
            if (weather.temperature > 50) "适合晨会和团队协作" else "先独处充电，晚些再社交",
            "🌅"
        )
        currentHour in 10..12 -> SmartScheduleAdvice(
            "上午黄金期",
            if (weather.temperature > 60) "攻坚克难，做最有挑战的事" else "整理和复盘，稳步推进",
            "每25分钟休息5分钟（番茄钟）",
            "主动沟通推动事项进展",
            "⚡"
        )
        currentHour in 13..15 -> SmartScheduleAdvice(
            "午后低谷期",
            "机械性任务、邮件处理、文件整理",
            "午休10-20分钟，或起身散步5分钟",
            "避免重要谈判，适合信息同步",
            "☕"
        )
        currentHour in 16..18 -> SmartScheduleAdvice(
            "傍晚恢复期",
            if (weather.windSpeed < 30) "处理创意性工作" else "执行已确定的计划",
            "活动肩颈，远眺窗外20秒",
            "总结今日进展，规划明天重点",
            "🌆"
        )
        else -> SmartScheduleAdvice(
            "夜间安宁期",
            "阅读、学习、轻度思考",
            "准备放松，减少蓝光刺激",
            "和家人朋友轻松聊天，享受当下",
            "🌙"
        )
    }
}

/**
 * v7.0: 情绪周期分析 — 识别情绪循环模式
 */
data class EmotionCycle(
    val cycleLengthHours: Int,     // 周期长度（小时）
    val currentPosition: String,   // 当前位置: "上升段"/"峰值"/"下降段"/"谷值"
    val nextPeakTime: String,      // 下次高峰时间
    val rhythmQuality: String,     // "规律"/"较规律"/"不规律"
    val insight: String
)

fun analyzeEmotionCycle(snapshots: List<EmotionSnapshot>): EmotionCycle {
    if (snapshots.size < 20) return EmotionCycle(
        0, "采集中", "数据不足", "待评估",
        "持续记录，积累足够数据后你的情绪节律会被揭示"
    )
    val smileValues = snapshots.map { it.smileProbability }
    val avg = smileValues.average().toFloat()

    // Simple peak/trough detection
    var peaks = 0
    var troughs = 0
    for (i in 1 until smileValues.size - 1) {
        if (smileValues[i] > smileValues[i - 1] && smileValues[i] > smileValues[i + 1] && smileValues[i] > avg) peaks++
        if (smileValues[i] < smileValues[i - 1] && smileValues[i] < smileValues[i + 1] && smileValues[i] < avg) troughs++
    }

    val lastVal = smileValues.last()
    val prevVal = smileValues.dropLast(1).lastOrNull() ?: lastVal
    val direction = lastVal - prevVal

    val position = when {
        lastVal > avg * 1.1f && direction > 0 -> "峰值附近 😊"
        lastVal > avg * 1.1f && direction <= 0 -> "从峰值回落"
        lastVal < avg * 0.9f && direction < 0 -> "谷值附近 😔"
        lastVal < avg * 0.9f && direction >= 0 -> "从谷值回升 📈"
        direction > 0.02 -> "上升中 ↗"
        direction < -0.02 -> "下降中 ↘"
        else -> "平稳区间 ➡"
    }

    val regularity = when {
        peaks + troughs >= 3 -> "规律"
        peaks + troughs >= 1 -> "较规律"
        else -> "不规律"
    }

    val insight = when {
        lastVal > avg * 1.2f -> "你正在情绪高峰期，适合做创造性和有挑战的事!"
        lastVal < avg * 0.8f -> "情绪低谷时不做重大决定，照顾好自己才是首位"
        regularity == "不规律" -> "今天的情绪波动较大，尝试保持规律作息来稳定节奏"
        direction > 0.05 -> "情绪正在上扬! 乘势推进手头的任务吧"
        else -> "保持当前节奏，你的情绪状态很稳定"
    }

    return EmotionCycle(
        cycleLengthHours = if (peaks + troughs > 0) (snapshots.size / (peaks + troughs).coerceAtLeast(1)) * 60 else 0,
        currentPosition = position,
        nextPeakTime = if (direction < 0) "约30-60分钟后" else "高峰期正在持续",
        rhythmQuality = regularity,
        insight = insight
    )
}

// ═══════════════════════════════════════════════════════════════
// v8.0 "Astral Glow" 星光璀璨 — 深度智能体验升级
// ═══════════════════════════════════════════════════════════════

/**
 * v8.0: 每日心情弧线 — 将一天的心情变化概括为故事线
 */
data class DailyMoodArc(
    val arcType: ArcType,
    val title: String,
    val description: String,
    val startTime: String,
    val endTime: String,
    val highlight: String,
    val arcColor: Long,
    val icon: String
)

enum class ArcType {
    STEADY_RISE,    // 稳步上升
    MORNING_PEAK,   // 早峰型
    AFTERNOON_DROP, // 午后低谷型
    EVENING_REBOUND,// 傍晚反弹型
    ROLLER_COASTER, // 过山车型
    FLAT_CALM       // 平静型
}

/**
 * v8.0: 生成每日心情弧线
 */
fun generateDailyMoodArc(snapshots: List<EmotionSnapshot>): DailyMoodArc {
    if (snapshots.size < 5) {
        return DailyMoodArc(
            ArcType.FLAT_CALM, "数据积累中", "让摄像头运行一段时间，你的心情弧线将逐渐清晰可见",
            "--:--", "--:--", "",
            0xFF9FA8DA, "🌙"
        )
    }
    val morning = snapshots.filter {
        val h = ((it.timestamp / 3600000) % 24).toInt()
        h in 6..11
    }
    val afternoon = snapshots.filter {
        val h = ((it.timestamp / 3600000) % 24).toInt()
        h in 12..17
    }
    val evening = snapshots.filter {
        val h = ((it.timestamp / 3600000) % 24).toInt()
        h >= 18
    }

    val avgMorning = morning.map { it.smileProbability }.average().let { if (it.isNaN()) 0.5 else it }.toFloat()
    val avgAfternoon = afternoon.map { it.smileProbability }.average().let { if (it.isNaN()) 0.4 else it }.toFloat()
    val avgEvening = evening.map { it.smileProbability }.average().let { if (it.isNaN()) 0.45 else it }.toFloat()

    val arcType = when {
        avgMorning < avgAfternoon && avgAfternoon < avgEvening -> ArcType.STEADY_RISE
        avgMorning > avgAfternoon * 1.3 && avgMorning > avgEvening * 1.1 -> ArcType.MORNING_PEAK
        avgAfternoon < avgMorning * 0.7 && avgAfternoon < avgEvening * 0.8 -> ArcType.AFTERNOON_DROP
        avgEvening > avgAfternoon * 1.3 -> ArcType.EVENING_REBOUND
        snapshots.map { it.smileProbability }.let { kotlin.math.sqrt(it.map { v -> (v - it.average()) * (v - it.average()) }.average()) } > 0.25 -> ArcType.ROLLER_COASTER
        else -> ArcType.FLAT_CALM
    }

    return when (arcType) {
        ArcType.STEADY_RISE -> DailyMoodArc(arcType, "稳步上升型 📈",
            "你的心情像清晨的阳光，一天比一天更温暖。这种渐进式提升说明你正在进入良好状态。",
            "09:00", "18:00", "傍晚达到状态巅峰", 0xFF69F0AE, "🌅")
        ArcType.MORNING_PEAK -> DailyMoodArc(arcType, "早鸟高峰型 🐦",
            "早晨的你充满能量！这段时间适合处理最重要的任务，把困难安排在上午攻克。",
            "09:00", "12:00", "上午是最强时间段", 0xFFFFD68A, "☀️")
        ArcType.AFTERNOON_DROP -> DailyMoodArc(arcType, "午后小憩型 ☕",
            "午后低谷是正常的生理节律。安排10分钟散步或一杯咖啡，能显著提升下午后半段的状态。",
            "13:00", "15:00", "低谷后傍晚回升", 0xFF8AC4F7, "☕")
        ArcType.EVENING_REBOUND -> DailyMoodArc(arcType, "傍晚反弹型 🌟",
            "下午有所回落但傍晚大幅回升，你是夜晚型能量人。适合晚间从事创意类工作。",
            "18:00", "21:00", "傍晚能量回归", 0xFFB388FF, "✨")
        ArcType.ROLLER_COASTER -> DailyMoodArc(arcType, "过山车型 🎢",
            "今天情绪波动较大，可能受到了外部事件的影响。建议晚间花5分钟记录今天发生的事。",
            "09:00", "18:00", "起伏中寻找规律", 0xFFFFAB40, "🎢")
        ArcType.FLAT_CALM -> DailyMoodArc(arcType, "宁静水平型 🌊",
            "今天是平静稳定的一天，情绪如平湖般安宁。这种状态适合深度思考和创意工作。",
            "09:00", "18:00", "持久平稳", 0xFF7AE8E8, "🌊")
    }
}

/**
 * v8.0: 个人化智能建议 — 基于多维数据的个性化文字
 */
data class PersonalizedTip(
    val tipType: TipType,
    val title: String,
    val content: String,
    val action: String,
    val icon: String,
    val accentName: String
)

enum class TipType {
    SELF_CARE,      // 自我关怀
    PRODUCTIVITY,   // 生产力
    SOCIAL,         // 社交
    REST,           // 休息
    MINDFULNESS     // 正念
}

/**
 * v8.0: 生成个性化建议
 */
fun generatePersonalizedTips(
    snapshots: List<EmotionSnapshot>,
    weather: EmotionWeather,
    cycle: EmotionCycle
): List<PersonalizedTip> {
    val tips = mutableListOf<PersonalizedTip>()

    // 基于情绪天气的建议
    when (weather.condition) {
        WeatherCondition.THUNDERSTORM -> tips.add(PersonalizedTip(
            TipType.SELF_CARE, "让心灵避雨 🌧",
            "检测到情绪波动较大。不要评判自己的感受，允许自己暂时不那么坚强。",
            "深呼吸3次，告诉自己「此刻的感受是正常的」", "🌧", "雨紫"
        ))
        WeatherCondition.LIGHT_RAIN -> tips.add(PersonalizedTip(
            TipType.MINDFULNESS, "雨后总会天晴 🌦",
            "情绪有小雨，这说明你在认真感受生活。给自己一些温柔的关照。",
            "泡一杯热饮，听一首喜欢的歌", "🌦", "雾蓝"
        ))
        WeatherCondition.OVERCAST -> tips.add(PersonalizedTip(
            TipType.PRODUCTIVITY, "阴天也有价值 ☁️",
            "低沉时适合做不需要太多创造力的机械性任务。接纳当前状态，量力而行。",
            "整理文件、回复邮件、做简单规划", "☁️", "云灰"
        ))
        WeatherCondition.STARRY_NIGHT -> tips.add(PersonalizedTip(
            TipType.MINDFULNESS, "享受内心的宁静 🌌",
            "你正处于平静祥和的时期。这是深度思考和记忆整理的好时机。",
            "写下3件今天感恩的小事", "🌌", "星紫"
        ))
        WeatherCondition.GOLDEN_HOUR -> tips.add(PersonalizedTip(
            TipType.PRODUCTIVITY, "抓住你的黄金时刻 ✨",
            "状态巅峰期！能量充沛、心情愉悦，是做最重要的事的最佳时机。",
            "现在就开始那个你一直拖延的重要任务", "✨", "金橙"
        ))
        else -> {}
    }

    // 基于情绪周期的建议
    if (cycle.currentPosition.contains("谷值")) {
        tips.add(PersonalizedTip(
            TipType.REST, "低谷不等于失败 💚",
            "你正处于周期的低谷段，这是身体在提醒你需要休息。",
            "起身活动5分钟，做一次颈肩拉伸", "💚", "草绿"
        ))
    }

    // 通用正念建议
    tips.add(PersonalizedTip(
        TipType.MINDFULNESS, "此刻的你，刚刚好 💫",
        "无论今天过得如何，你愿意观察自己，这本身就是一种勇气和温柔。",
        "关注下一次呼吸，感受空气进入身体", "💫", "柔光"
    ))

    return tips.take(3)
}

/**
 * v8.0: 自我对话 — 帮助建立积极的自我认知
 */
data class VoiceOfSelf(
    val reflection: String,
    val encouragement: String,
    val dailyAffirmation: String,
    val icon: String
)

/**
 * v8.0: 生成今日自我对话
 */
fun generateVoiceOfSelf(snapshots: List<EmotionSnapshot>): VoiceOfSelf {
    if (snapshots.size < 10) return VoiceOfSelf(
        "开始记录的第一天，就是改变的开始 ✨",
        "每一次对自我的关注，都是送给自己的礼物。",
        "我值得拥有平静与快乐 🌸", "🌱"
    )
    val avgSmile = snapshots.map { it.smileProbability }.average().toFloat()
    val positiveRatio = snapshots.count { it.smileProbability > 0.5f } / snapshots.size.toFloat()

    return when {
        positiveRatio > 0.6f -> VoiceOfSelf(
            "今天你笑了${(positiveRatio * 100).toInt()}% 的时间。你比自己以为的更容易感到快乐 😊",
            "这份积极可以感染身边保持微笑的人。分享你的快乐吧！",
            "我正变得越来越从容 🌸", "🌷"
        )
        positiveRatio > 0.3f -> VoiceOfSelf(
            "今天有${(positiveRatio * 100).toInt()}% 的时间你带着微笑。不容易，但你做到了 💪",
            "平淡的一天也值得庆祝。稳定本身就是一种了不起的能量。",
            "我接纳此刻的自己，一切都在变好 🌿", "🍀"
        )
        else -> VoiceOfSelf(
            "今天对生活的要求有些苛刻。微笑不是义务，但记得给自已一点温柔 🌙",
            "低落的日子不会永远持续。你要做的只是等待，对自己耐心一点。",
            "我允许自己有情绪低谷，这并不意味着失败 🕯️", "🕯"
        )
    }
}

/**
 * v8.0: 里程碑模型 — 庆祝小胜利
 */
data class MilestoneCard(
    val title: String,
    val description: String,
    val icon: String,
    val isNew: Boolean,
    val accentHex: Long
)

/**
 * v8.0: 生成里程碑卡片
 */
fun generateMilestone(totalDays: Int, totalSmiles: Int, currentStreak: Int): MilestoneCard? {
    return when {
        totalDays == 1 -> MilestoneCard(
            "初见 · 记录第一天 ✨",
            "千里之行始于足下。从今天起，SmileTracker 将陪伴你记录每一个微笑。",
            "✨", true, 0xFFFFD68A
        )
        totalDays == 7 -> MilestoneCard(
            "一周 · 初识自我 💫",
            "连续记录一周！你已经比90%的人更了解自己的情绪模式了。",
            "💫", true, 0xFF8AC4F7
        )
        totalDays == 30 -> MilestoneCard(
            "一月 · 情绪掌舵者 🌙",
            "坚持30天！你的情绪地图已经初具规模，继续前行吧。",
            "🌙", true, 0xFFB388FF
        )
        currentStreak == 7 -> MilestoneCard(
            "连击 · 七日不休 🔥",
            "连续7天记录！习惯正在养成，你比想象中更坚持。",
            "🔥", true, 0xFFFFAB40
        )
        totalSmiles >= 100 -> MilestoneCard(
            "百日微笑 · 值得庆祝 🎉",
            "累计微笑达到100次！每一次上扬的嘴角都值得被记住。",
            "🎉", true, 0xFF69F0AE
        )
        totalSmiles >= 1000 -> MilestoneCard(
            "千笑收藏 · 情绪艺术家 🎨",
            "超过1000条微笑记录！你的情绪历程本身就是一件艺术品。",
            "🎨", true, 0xFF7AE8E8
        )
        else -> null
    }
}
