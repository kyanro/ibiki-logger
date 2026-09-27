package dev.ibiki.logger

import android.Manifest
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import dev.ibiki.logger.audio.*
import dev.ibiki.logger.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.math.PI
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class AudioIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    @get:Rule val permissions: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)

    @Test fun bundledClassifierRunsAndRejectsDigitalSilence() {
        SnoreClassifier(context).use { classifier ->
            repeat(10) { classifier.append(ShortArray(FRAME_SAMPLES)) }
            val score = classifier.score()
            assertTrue(score.isFinite())
            assertTrue("Silent snore score: $score", score < Sensitivity.HIGH.score)
        }
    }

    @Test fun encodedClipIsPlayableAndExportPreservesTimestampsAndWaveform() {
        val store = SessionStore.get(context)
        val id = "test_codec_${System.currentTimeMillis()}"
        val frames = (0 until 15).map { index -> AudioFrame.from((index + 100) * FRAME_SAMPLES.toLong(), ShortArray(FRAME_SAMPLES) { n ->
            (sin(2 * PI * 220 * (index * FRAME_SAMPLES + n) / SAMPLE_RATE) * 8000).toInt().toShort()
        }) }
        val raw = RawClip(frames, .8f, 10)
        val file = File(store.directory(id), "test.m4a")
        try {
            AacClipWriter.write(raw, file)
            assertTrue(file.length() > 100)
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(file.absolutePath)
                assertEquals(1, extractor.trackCount)
                val format = extractor.getTrackFormat(0)
                assertEquals("audio/mp4a-latm", format.getString(MediaFormat.KEY_MIME))
                assertEquals(SAMPLE_RATE, format.getInteger(MediaFormat.KEY_SAMPLE_RATE))
                assertEquals(1, format.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
                assertTrue(format.getLong(MediaFormat.KEY_DURATION) >= 1_400_000)
                extractor.selectTrack(0)
                var previous = -1L
                var count = 0
                while (extractor.sampleTime >= 0) { assertTrue(extractor.sampleTime > previous); previous = extractor.sampleTime; count++; extractor.advance() }
                assertTrue(count > 10)
            } finally { extractor.release() }
            val clip = ClipRecord(file.name, raw.startMs, raw.durationMs, .8f, 1000, frames.map { it.peak }, frames.map { it.rmsDb })
            store.save(SessionRecord(id, 1_000_000, 15000, "completed", device = "test", androidVersion = "test", startBattery = 90, clips = listOf(clip)))
            val output = ByteArrayOutputStream()
            store.export(id, output)
            val entries = mutableMapOf<String, ByteArray>()
            ZipInputStream(output.toByteArray().inputStream()).use { zip ->
                while (true) { val entry = zip.nextEntry ?: break; entries[entry.name] = zip.readBytes() }
            }
            assertEquals(setOf("session.json", "clips.csv", "waveform.csv", "README.txt", "audio/test.m4a"), entries.keys)
            assertTrue(entries["clips.csv"]!!.toString(Charsets.UTF_8).contains("test.m4a,10000,"))
            assertEquals(16, entries["waveform.csv"]!!.toString(Charsets.UTF_8).trim().lines().size)
            assertArrayEquals(file.readBytes(), entries["audio/test.m4a"])
        } finally {
            if (store.read(id) != null) store.delete(id) else file.delete()
        }
    }

    @Test fun foregroundRecordingContinuesWithScreenOffAndStopsCleanly() {
        val store = SessionStore.get(context)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        var id: String? = null
        fun shell(command: String) { automation.executeShellCommand(command).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() } }
        try {
            assertFalse("A recording is already active", RecordingService.state.value.running)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { RecordingService.start(it, Sensitivity.NORMAL, false) }
                waitUntil { (RecordingService.state.value.running && RecordingService.state.value.sessionId != null) || RecordingService.state.value.error != null }
                assertNull(RecordingService.state.value.error)
                id = RecordingService.state.value.sessionId
                assertNotNull(id)
                shell("input keyevent 223")
                SystemClock.sleep(6500)
                assertTrue(RecordingService.state.value.running)
                assertTrue(RecordingService.state.value.durationMs >= 5000)
                shell("input keyevent 224")
                RecordingService.stop(context)
                waitUntil { !RecordingService.state.value.running }
                assertNull(RecordingService.state.value.error)
                assertEquals("completed", store.read(id!!)!!.status)
            }
        } finally {
            shell("input keyevent 224")
            RecordingService.stop(context)
            waitUntil { !RecordingService.state.value.running }
            id?.let { if (store.read(it)?.status != "recording") store.delete(it) }
        }
    }

    private fun waitUntil(predicate: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + 30_000
        while (!predicate() && SystemClock.elapsedRealtime() < until) SystemClock.sleep(100)
        assertTrue("Timed out waiting for recording state", predicate())
    }
}
