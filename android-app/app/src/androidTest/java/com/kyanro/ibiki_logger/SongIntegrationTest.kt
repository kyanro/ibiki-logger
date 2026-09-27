package com.kyanro.ibiki_logger

import android.Manifest
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.kyanro.ibiki_logger.audio.RecordingService
import com.kyanro.ibiki_logger.data.SessionStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SongIntegrationTest {
    @get:Rule(order = 0) val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS
    )
    @get:Rule(order = 1) val ui = createAndroidComposeRule<MainActivity>()

    @Test fun bundledSongAndLicenseDocumentsAreReadable() {
        val context = ui.activity
        val extractor = MediaExtractor()
        try {
            context.resources.openRawResourceFd(R.raw.nyaa_nyaa_nyaa).use { descriptor ->
                extractor.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
            }
            assertEquals(1, extractor.trackCount)
            val format = extractor.getTrackFormat(0)
            assertEquals("audio/mpeg", format.getString(MediaFormat.KEY_MIME))
            assertTrue(format.getLong(MediaFormat.KEY_DURATION) > 20_000_000)
        } finally { extractor.release() }
        fun asset(name: String) = context.assets.open("licenses/$name").bufferedReader().use { it.readText() }
        assertTrue(asset("LICENSE").contains("Version 2.0, January 2004"))
        assertTrue(asset("ASSET_LICENSES.md").contains("https://otogishift.com/songs/nyaa-nyaa-nyaa/"))
        assertTrue(asset("THIRD_PARTY_NOTICES.md").contains("YAMNet"))
    }

    @Test fun recordingStopsMusicAndDoesNotRestartIt() {
        assertFalse("Run this test only when no recording is active", RecordingService.state.value.running)
        val store = SessionStore.get(ui.activity)
        var sessionId: String? = null
        var startedRecording = false
        try {
            scrollTo("BGMを再生").performClick()
            ui.onNodeWithText("BGMを停止").assertIsEnabled()
            scrollTo("録音を開始").performClick()
            startedRecording = true
            ui.waitUntil(10_000) { RecordingService.state.value.sessionId != null || RecordingService.state.value.error != null }
            assertNull(RecordingService.state.value.error)
            sessionId = RecordingService.state.value.sessionId
            assertNotNull(sessionId)
            scrollTo("BGMを再生").assertIsNotEnabled()
            scrollTo("録音を停止").performClick()
            ui.waitUntil(10_000) { !RecordingService.state.value.running }
            assertNull(RecordingService.state.value.error)
            scrollTo("BGMを再生").assertIsEnabled()
            assertEquals("completed", store.read(sessionId!!)!!.status)
        } finally {
            if (startedRecording) {
                RecordingService.stop(ui.activity)
                ui.waitUntil(10_000) { !RecordingService.state.value.running }
                sessionId?.let { store.delete(it) }
            }
        }
    }

    @Test fun leavingAppStopsMusicAndReturningDoesNotAutoplay() {
        scrollTo("BGMを再生").performClick()
        ui.onNodeWithText("BGMを停止").assertIsEnabled()
        ui.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        ui.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        scrollTo("BGMを再生").assertIsEnabled()
    }

    private fun scrollTo(text: String): SemanticsNodeInteraction {
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
        return ui.onNodeWithText(text)
    }
}
