package com.kyanro.ibiki_logger

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyanro.ibiki_logger.data.ClipRecord
import com.kyanro.ibiki_logger.data.SessionRecord
import com.kyanro.ibiki_logger.data.SessionStore
import com.kyanro.ibiki_logger.theme.IbikiLoggerTheme
import com.kyanro.ibiki_logger.ui.LoggerApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.exp
import kotlin.math.log10

/** Documentation capture only: this source set is excluded from the normal app and test APKs. */
@RunWith(AndroidJUnit4::class)
class ReadmeScreenshots {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test fun capture() {
        val context = ui.activity
        check(context.packageName == "com.kyanro.ibiki_logger.screenshots")
        val store = SessionStore.get(context)
        check(store.list().isEmpty()) { "Use a fresh screenshots-only installation." }
        val demo = demoSession()
        try {
            store.save(demo)
            // Keep typography independent of the owner's font-size preference, without changing it.
            ui.runOnUiThread {
                context.setContent {
                    val density = LocalDensity.current.density
                    CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1f)) {
                        IbikiLoggerTheme {
                            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { LoggerApp() }
                        }
                    }
                }
            }
            ui.waitUntil(10_000) { ui.onAllNodesWithText("1件").fetchSemanticsNodes().isNotEmpty() }
            capture("home_screen", "home.png")

            ui.onNodeWithTag("home_screen").performScrollToNode(hasTestTag("session_${demo.id}"))
            ui.onNodeWithTag("session_${demo.id}").performClick()
            ui.waitUntil(10_000) { ui.onAllNodesWithTag("detail_screen").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithTag("detail_clips").performScrollToNode(hasTestTag("clip_0"))
            ui.onNodeWithTag("detail_clips").performScrollToIndex(0)
            capture("detail_screen", "timeline.png")

            ui.onNodeWithTag("detail_timeline").performTouchInput { click(Offset(width * .54f, height / 2f)) }
            capture("detail_screen", "clips.png")
        } finally {
            store.delete(demo.id)
        }
    }

    private fun capture(tag: String, name: String) {
        ui.waitForIdle()
        val image = ui.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        val directory = checkNotNull(ui.activity.getExternalFilesDir("readme-screenshots"))
        File(directory, name).outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    private fun demoSession(): SessionRecord {
        val startedAt = LocalDateTime.of(2026, 1, 1, 22, 30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val groups = listOf(35, 100, 180, 255, 330, 420)
        val amplitudes = listOf(.008, .025, .10, .035, .15, .055)
        val clips = List(72) { index ->
            val group = index / 12
            val seconds = 8 + index % 5
            val peaks = List(seconds * 10) { sample ->
                val phase = (sample % 28 - 12) / 4.5
                val envelope = exp(-phase * phase / 2)
                (.0003 + amplitudes[group] * (.65 + index % 4 * .11) * envelope).toFloat()
            }
            ClipRecord(
                file = "demo_${index + 1}.m4a", startMs = groups[group] * 60_000L + (index % 12) * 40_000L,
                durationMs = seconds * 1000L, maxSnoreScore = .56f + index % 8 * .05f,
                hitWindowMs = 2000L + index % 4 * 500L, peaks = peaks,
                rmsDb = peaks.map { 20 * log10((it * .45).coerceAtLeast(.000001)) }
            )
        }
        return SessionRecord(
            id = "readme_demo", startedAt = startedAt, durationMs = 8 * 60 * 60 * 1000L,
            status = "completed", device = "Documentation demo", androidVersion = "demo",
            startBattery = 100, endBattery = 100, clips = clips
        )
    }
}
