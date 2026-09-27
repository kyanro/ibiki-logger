package com.kyanro.ibiki_logger.audio

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

const val SAMPLE_RATE = 16_000
const val FRAME_SAMPLES = 1_600

enum class Sensitivity(val title: String, val minimumDb: Double, val score: Float) {
    LOW("控えめ", -40.0, 0.30f), NORMAL("標準", -50.0, 0.15f), HIGH("高め", -60.0, 0.07f)
}

data class AudioFrame(val sampleOffset: Long, val pcm: ShortArray, val rmsDb: Double, val peak: Float) {
    companion object {
        fun from(offset: Long, pcm: ShortArray): AudioFrame {
            var energy = 0.0
            var peak = 0
            pcm.forEach { val value = it.toInt(); energy += value.toDouble() * value; peak = maxOf(peak, abs(value)) }
            val rms = sqrt(energy / pcm.size) / 32768.0
            return AudioFrame(offset, pcm, 20.0 * log10(rms.coerceAtLeast(0.000001)), peak / 32768f)
        }
    }
}

data class RawClip(val frames: List<AudioFrame>, val maxSnoreScore: Float, val hitFrames: Int) {
    val startMs: Long get() = frames.first().sampleOffset * 1000 / SAMPLE_RATE
    val durationMs: Long get() = frames.sumOf { it.pcm.size }.toLong() * 1000 / SAMPLE_RATE
}

/** Only retains a two-second ring while idle. A hit opens a clip including that ring. */
class ClipSegmenter(private val preFrames: Int = 20, private val tailFrames: Int = 20, private val maxFrames: Int = 300) {
    private val pre = ArrayDeque<AudioFrame>()
    private var active: MutableList<AudioFrame>? = null
    private var quietFrames = 0
    private var maxScore = 0f
    private var hitFrames = 0

    fun accept(frame: AudioFrame, hit: Boolean, score: Float = 0f): RawClip? {
        if (active == null) {
            pre.addLast(frame)
            while (pre.size > preFrames) pre.removeFirst()
            if (!hit) return null
            active = pre.toMutableList()
            pre.clear()
        } else active!!.add(frame)
        if (hit) { quietFrames = 0; hitFrames++; maxScore = maxOf(maxScore, score) } else quietFrames++
        return if (quietFrames >= tailFrames || active!!.size >= maxFrames) flush() else null
    }

    fun flush(): RawClip? {
        val frames = active?.toList()
        val clip = frames?.let { RawClip(it, maxScore, hitFrames) }
        active = null
        pre.clear()
        quietFrames = 0
        maxScore = 0f
        hitFrames = 0
        return clip
    }
}
