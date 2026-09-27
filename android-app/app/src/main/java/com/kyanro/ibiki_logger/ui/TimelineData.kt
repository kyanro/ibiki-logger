package com.kyanro.ibiki_logger.ui

import com.kyanro.ibiki_logger.data.ClipRecord
import com.kyanro.ibiki_logger.data.SessionRecord
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.pow

internal const val CLIPS_PER_PAGE = 5

// The same fixed -70..0 dBFS scale is used for the overview and individual clips.
internal fun waveformHeight(peak: Float): Float =
    if (!peak.isFinite()) 0f else ((20f * log10(peak.coerceAtLeast(.000001f)) + 70f) / 70f).coerceIn(0f, 1f)

/** Empty time buckets stay negative; quiet saved audio can still be shown at the baseline. */
internal fun timelineEnvelope(session: SessionRecord, columns: Int): FloatArray {
    require(columns > 0)
    val result = FloatArray(columns) { -1f }
    val duration = session.durationMs.coerceAtLeast(1)
    fun add(startMs: Long, endMs: Long, peak: Float) {
        val start = startMs.coerceIn(0, duration)
        val end = endMs.coerceIn(0, duration)
        if (end <= start) return
        val from = (start.toDouble() / duration * columns).toInt().coerceIn(result.indices)
        val to = (ceil(end.toDouble() / duration * columns).toInt() - 1).coerceIn(result.indices)
        val height = waveformHeight(peak)
        for (i in from..to) result[i] = maxOf(result[i], height)
    }
    session.clips.forEach { clip ->
        val end = clip.startMs + clip.durationMs
        when {
            clip.peaks.isNotEmpty() -> clip.peaks.forEachIndexed { index, peak ->
                val start = clip.startMs + index * 100L
                add(start, minOf(start + 100, end), peak)
            }
            clip.rmsDb.isNotEmpty() -> clip.rmsDb.forEachIndexed { index, db ->
                val start = clip.startMs + index * 100L
                add(start, minOf(start + 100, end), 10.0.pow(db / 20).toFloat())
            }
            else -> add(clip.startMs, end, 0f)
        }
    }
    return result
}

/** Taps in gaps select the nearest saved interval, including taps before/after all clips. */
internal fun nearestClipIndex(clips: List<ClipRecord>, offsetMs: Long): Int? =
    clips.indices.minByOrNull { index ->
        val clip = clips[index]
        when {
            offsetMs < clip.startMs -> clip.startMs - offsetMs
            offsetMs > clip.startMs + clip.durationMs -> offsetMs - clip.startMs - clip.durationMs
            else -> 0L
        }
    }
