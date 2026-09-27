package dev.ibiki.logger.audio

import org.junit.Assert.*
import org.junit.Test

class DetectionTest {
    private fun frame(index: Int, amplitude: Short = 0) = AudioFrame.from(index * FRAME_SAMPLES.toLong(), ShortArray(FRAME_SAMPLES) { amplitude })

    @Test fun eightHoursOfSilenceSavesNothing() {
        val detector = ClipSegmenter()
        repeat(8 * 60 * 60 * 10) { assertNull(detector.accept(frame(it), false)) }
        assertNull(detector.flush())
    }

    @Test fun preservesOriginalTimeAndBothMargins() {
        val detector = ClipSegmenter()
        repeat(100) { assertNull(detector.accept(frame(it), false)) }
        repeat(10) { assertNull(detector.accept(frame(100 + it), true, .8f)) }
        repeat(19) { assertNull(detector.accept(frame(110 + it), false)) }
        val clip = detector.accept(frame(129), false)!!
        assertEquals(8100L, clip.startMs)
        assertEquals(4900L, clip.durationMs)
        assertEquals(10, clip.hitFrames)
        assertEquals(.8f, clip.maxSnoreScore)
        assertNull(detector.flush())
    }

    @Test fun ongoingSoundIsSplitWithoutDroppingOrDuplicatingSamples() {
        val detector = ClipSegmenter(maxFrames = 30)
        val clips = mutableListOf<RawClip>()
        repeat(85) { detector.accept(frame(it), true)?.let(clips::add) }
        detector.flush()?.let(clips::add)
        assertEquals(listOf(30, 30, 25), clips.map { it.frames.size })
        assertEquals((0 until 85).map { it * FRAME_SAMPLES.toLong() }, clips.flatMap { it.frames }.map { it.sampleOffset })
    }

    @Test fun flushingOnInterruptionDoesNotBridgeAcrossMissingAudio() {
        val detector = ClipSegmenter()
        repeat(10) { detector.accept(frame(it), true) }
        assertEquals(1000L, detector.flush()!!.durationMs)
        repeat(10) { detector.accept(frame(100 + it), false) }
        detector.accept(frame(110), true)
        assertEquals(10000L, detector.flush()!!.startMs)
    }

    @Test fun digitalLevelsAreFiniteAndHandleNegativeFullScale() {
        assertEquals(-120.0, frame(0).rmsDb, .0001)
        assertEquals(1f, frame(0, Short.MIN_VALUE).peak)
        assertEquals(0.0, frame(0, Short.MIN_VALUE).rmsDb, .0001)
        assertEquals(-6.0206, frame(0, 16384).rmsDb, .001)
    }
}
