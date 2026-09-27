package dev.ibiki.logger.data

import dev.ibiki.logger.audio.Sensitivity

data class ClipRecord(
    val file: String,
    val startMs: Long,
    val durationMs: Long,
    val maxSnoreScore: Float,
    val hitWindowMs: Long,
    val peaks: List<Float>,
    val rmsDb: List<Double>
)

data class GapRecord(val startMs: Long, val endMs: Long, val reason: String)

data class SessionRecord(
    val id: String,
    val startedAt: Long,
    val durationMs: Long = 0,
    val status: String = "recording",
    val message: String = "",
    val sensitivity: String = Sensitivity.NORMAL.name,
    val soundOnly: Boolean = false,
    val device: String,
    val androidVersion: String,
    val startBattery: Int,
    val endBattery: Int = -1,
    val clips: List<ClipRecord> = emptyList(),
    val gaps: List<GapRecord> = emptyList()
) {
    val savedMs: Long get() = clips.sumOf { it.durationMs }
    val candidateMs: Long get() = clips.sumOf { it.hitWindowMs }
}
