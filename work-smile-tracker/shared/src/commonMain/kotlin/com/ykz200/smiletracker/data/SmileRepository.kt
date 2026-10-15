package com.ykz200.smiletracker.data

import com.ykz200.smiletracker.db.SmileDatabase
import com.ykz200.smiletracker.domain.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * SQLDelight 生成的 DAO + coroutines adapter
 */
class SmileRepositoryImpl(
    private val database: SmileDatabase,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : SmileRepository {

    private val queries = database.smileDatabaseQueries

    override suspend fun insert(face: FaceAnalysis) {
        database.smileDatabaseQueries.insertRecord(
            ts_millis = face.tsMillis,
            smile_probability = face.smileProbability.toDouble(),
            left_eye_open_probability = face.leftEyeOpenProbability.toDouble(),
            right_eye_open_probability = face.rightEyeOpenProbability.toDouble(),
            head_pitch = face.headPitch.toDouble(),
            head_yaw = face.headYaw.toDouble(),
            head_roll = face.headRoll.toDouble(),
            raw_data = null
        )
    }

    /** 同步写入 (WorkManager 后台任务中用) */
    fun insertSync(face: FaceAnalysis) {
        try {
            database.smileDatabaseQueries.insertRecord(
                ts_millis = face.tsMillis,
                smile_probability = face.smileProbability.toDouble(),
                left_eye_open_probability = face.leftEyeOpenProbability.toDouble(),
                right_eye_open_probability = face.rightEyeOpenProbability.toDouble(),
                head_pitch = face.headPitch.toDouble(),
                head_yaw = face.headYaw.toDouble(),
                head_roll = face.headRoll.toDouble(),
                raw_data = null
            )
        } catch (e: Exception) {
            println("[SmileRepository] insertSync failed: ${e.message}")
        }
    }

    override suspend fun queryMinuteStats(startMillis: Long, endMillis: Long): List<MinuteStat> {
        return try {
            queries.analyzeSmile(startMillis, endMillis)
                .executeAsList()
                .map { row ->
                    MinuteStat(
                        minuteKey = row.minute_key,
                        sampleCount = row.sample_count.toInt(),
                        avgSmile = (row.avg_smile ?: -1.0).toFloat(),
                        maxSmile = (row.max_smile ?: -1.0).toFloat(),
                        smileCount = (row.smile_count ?: 0).toInt(),
                        smilePercent = (row.smile_percent ?: 0.0).toFloat(),
                        avgEyeLeft = (row.avg_eye_left ?: -1.0).toFloat(),
                        avgEyeRight = (row.avg_eye_right ?: -1.0).toFloat()
                    )
                }
        } catch (e: Exception) {
            println("[SmileRepository] queryMinuteStats failed: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getLatest(): FaceAnalysis? {
        return try {
            val row = queries.selectLatestSample().executeAsOneOrNull() ?: return null
            FaceAnalysis(
                tsMillis = row.ts_millis,
                smileProbability = row.smile_probability.toFloat(),
                leftEyeOpenProbability = row.left_eye_open_probability.toFloat(),
                rightEyeOpenProbability = row.right_eye_open_probability.toFloat(),
                headPitch = row.head_pitch.toFloat(),
                headYaw = row.head_yaw.toFloat(),
                headRoll = row.head_roll.toFloat()
            )
        } catch (e: Exception) {
            println("[SmileRepository] getLatest failed: ${e.message}")
            null
        }
    }

    /**
     * 清理过期数据 — 默认保留 90 天
     */
    override fun cleanupOldData(retentionDays: Int) {
        try {
            val cutoff = Clock.System.now().toEpochMilliseconds() - (retentionDays * 24L * 3600L * 1000L)
            database.smileDatabaseQueries.deleteOlderThan(cutoff)
        } catch (e: Exception) {
            println("[SmileRepository] cleanup failed: ${e.message}")
        }
    }

    /** 获取指定日期的采样总数 (DailySummary 用) */
    suspend fun getDailyStats(dateStartMillis: Long, dateEndMillis: Long): DailySummary? {
        return try {
            val stats = queryMinuteStats(dateStartMillis, dateEndMillis)
            if (stats.isEmpty()) return null
            val totalSamples = stats.sumOf { it.sampleCount.toLong() }.toInt()
            val smileCount = stats.sumOf { it.smileCount.toLong() }.toInt()
            val avgProb = stats.map { it.avgSmile.coerceAtLeast(0f) }.average().toFloat()
            val peakProb = stats.map { it.maxSmile.coerceAtLeast(0f) }.max()
            val peakStat = stats.maxByOrNull { it.maxSmile }
            val avgEye = stats.map {
                (it.avgEyeLeft.coerceAtLeast(0f) + it.avgEyeRight.coerceAtLeast(0f)) / 2f
            }.average().toFloat()
            val eyeStrainIdx = ((1.0 - avgEye.toDouble()) * 100.0).toFloat().coerceIn(0f, 100f)
            val stressLvl = when {
                eyeStrainIdx > 70f -> "高"
                eyeStrainIdx > 40f -> "中"
                else -> "低"
            }
            DailySummary(
                date = peakStat?.minuteKey?.take(10) ?: "",
                totalSamples = totalSamples,
                smileCount = smileCount,
                avgSmileProbability = avgProb,
                peakSmileProbability = peakProb,
                peakTime = peakStat?.minuteKey?.takeLast(5) ?: "--:--",
                eyeStrainIndex = eyeStrainIdx,
                stressLevel = stressLvl
            )
        } catch (e: Exception) {
            null
        }
    }
}

/** 建库 factory — 各端通过 RepositoryProvider 注入 */
object RepositoryProvider {
    @Volatile private var instance: SmileRepository? = null

    fun get(): SmileRepository =
        instance ?: throw IllegalStateException("需要先调用 RepositoryProvider.init(database)")

    fun init(database: SmileDatabase) {
        if (instance == null) {
            synchronized(this) {
                if (instance == null) {
                    instance = SmileRepositoryImpl(database)
                }
            }
        }
    }
}
