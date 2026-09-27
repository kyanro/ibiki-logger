package com.kyanro.ibiki_logger.ui

import com.kyanro.ibiki_logger.data.ClipRecord
import com.kyanro.ibiki_logger.data.SessionRecord
import org.junit.Assert.*
import org.junit.Test

class TimelineDataTest {
    private fun clip(start: Long, peaks: List<Float>) = ClipRecord("$start.m4a", start, peaks.size * 100L, .9f, 100, peaks, emptyList())
    private fun session(duration: Long, clips: List<ClipRecord>) =
        SessionRecord("test", 0, duration, device = "test", androidVersion = "test", startBattery = 100, clips = clips)

    @Test fun heightsReflectRecordedVolumeAndKeepGapsOnTheOriginalTimeAxis() {
        val quiet = clip(100, listOf(.001f, .001f))
        val loud = clip(700, listOf(.1f, .1f))
        val bars = timelineEnvelope(session(1000, listOf(quiet, loud)), 10)
        assertTrue(bars[1] > 0)
        assertTrue(bars[7] > bars[1])
        assertEquals(-1f, bars[0], 0f)
        assertEquals(-1f, bars[5], 0f)
        assertEquals(-1f, bars[9], 0f)
        // A clip retains the same scale even when a louder clip is added to the night.
        assertEquals(timelineEnvelope(session(1000, listOf(quiet)), 10)[1], bars[1], 0f)
    }

    @Test fun narrowEventsRemainVisibleAndAggregationKeepsTheLoudestPeak() {
        val bars = timelineEnvelope(session(28_800_000, listOf(clip(100, listOf(.001f, .5f, .001f)))), 100)
        assertTrue(bars[0] > .9f)
        assertEquals(1, bars.count { it >= 0 })
        val expanded = timelineEnvelope(session(100, listOf(clip(0, listOf(.1f)))), 20)
        assertTrue(expanded.all { it > .5f })
    }

    @Test fun tapsInsideClipsAndInGapsSelectTheNearestSavedInterval() {
        val clips = listOf(clip(1000, List(10) { .1f }), clip(5000, List(10) { .1f }))
        assertEquals(0, nearestClipIndex(clips, 0))
        assertEquals(0, nearestClipIndex(clips, 1999))
        assertEquals(0, nearestClipIndex(clips, 3000))
        assertEquals(1, nearestClipIndex(clips, 4500))
        assertEquals(1, nearestClipIndex(clips, 5500))
        assertEquals(1, nearestClipIndex(clips, 20_000))
        assertNull(nearestClipIndex(emptyList(), 1000))
    }

    @Test fun emptyRecordAndMissingOrInvalidWaveformDoNotBreakTheOverview() {
        assertTrue(timelineEnvelope(session(0, emptyList()), 20).all { it < 0 })
        val rmsOnly = clip(0, listOf(.1f)).copy(peaks = emptyList(), rmsDb = listOf(-20.0))
        assertTrue(timelineEnvelope(session(100, listOf(rmsOnly)), 10).all { it > .5f })
        assertEquals(0f, waveformHeight(Float.NaN), 0f)
        assertEquals(0f, waveformHeight(0f), 0f)
        assertEquals(1f, waveformHeight(1f), 0f)
    }
}
