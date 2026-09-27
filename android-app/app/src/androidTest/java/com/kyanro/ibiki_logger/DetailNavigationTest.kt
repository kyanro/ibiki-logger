package com.kyanro.ibiki_logger

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyanro.ibiki_logger.audio.RecordingService
import com.kyanro.ibiki_logger.data.ClipRecord
import com.kyanro.ibiki_logger.data.SessionRecord
import com.kyanro.ibiki_logger.data.SessionStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DetailNavigationTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test fun timelineStaysVisibleAndJumpsAcrossFiveClipPages() = withRecord(12) {
        val timeline = ui.onNodeWithTag("detail_timeline")
        val originalTop = timeline.fetchSemanticsNode().boundsInRoot.top
        timeline.performTouchInput { click(Offset(width * .55f, height / 2f)) }
        ui.onNodeWithTag("clip_6").assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "選択中"))
        assertEquals(originalTop, timeline.fetchSemanticsNode().boundsInRoot.top, .5f)
        ui.onNodeWithTag("clip_0").assertDoesNotExist()
        // Empty time after the final clip still selects the final, partial page.
        timeline.performTouchInput { click(Offset(width - 1f, height / 2f)) }
        ui.onNodeWithTag("clip_11").assertIsDisplayed()
        ui.onNodeWithTag("detail_clips").performScrollToNode(hasTestTag("page_next_top"))
        ui.onNodeWithTag("page_next_top").assertIsNotEnabled()
        ui.onNodeWithTag("page_controls_top").assertIsDisplayed().assert(hasAnyDescendant(hasText("11–12 / 12区間")))
        ui.onNodeWithTag("page_previous_top").performClick()
        ui.onNodeWithTag("page_controls_top").assertIsDisplayed().assert(hasAnyDescendant(hasText("6–10 / 12区間")))
        timeline.assert(hasContentDescription("選択中の区間 6", substring = true))
        ui.onNodeWithTag("clip_5").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "選択中"))
        ui.onNodeWithTag("page_next_top").performClick()
        timeline.assert(hasContentDescription("選択中の区間 11", substring = true))
        ui.onNodeWithTag("clip_10").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "選択中"))
        ui.onNodeWithTag("page_previous_top").performClick()
        // Returning to the same target remains usable after manually scrolling away.
        timeline.performTouchInput { click(Offset(width * .55f, height / 2f)) }
        ui.onNodeWithTag("detail_clips").performScrollToNode(hasTestTag("page_next_bottom"))
        timeline.assertIsDisplayed().performTouchInput { click(Offset(width * .55f, height / 2f)) }
        ui.onNodeWithTag("clip_6").assertIsDisplayed()
        assertEquals(originalTop, timeline.fetchSemanticsNode().boundsInRoot.top, .5f)
        timeline.performTouchInput { click(Offset(0f, height / 2f)) }
        ui.onNodeWithTag("clip_0").assertIsDisplayed()
    }

    @Test fun emptyRecordingCanBeOpenedAndTimelineTapsAreHarmless() = withRecord(0) {
        ui.onNodeWithTag("detail_timeline").performTouchInput { click(center) }
        ui.onNodeWithText("保存した区間はありません").assertIsDisplayed()
        ui.onNodeWithTag("detail_clips").performScrollToNode(hasText("保存した音声はありません。\n検出の感度やマイクの位置を変えて試せます。"))
        ui.onNodeWithTag("detail_timeline").assertIsDisplayed()
        ui.onNodeWithTag("page_next_top").assertDoesNotExist()
    }

    private fun withRecord(count: Int, test: () -> Unit) {
        assertFalse("Do not interrupt an active recording", RecordingService.state.value.running)
        val store = SessionStore.get(ui.activity)
        val originalCount = store.list().size
        val id = "test_detail_${System.nanoTime()}"
        val clips = List(count) { index ->
            ClipRecord("clip_$index.m4a", 5000L + index * 10_000, 3000, .8f, 1000,
                List(30) { .002f * (index + 1) }, List(30) { -40.0 + index })
        }
        try {
            store.save(SessionRecord(id, System.currentTimeMillis(), 120_000, "completed",
                device = "test", androidVersion = "test", startBattery = 100, clips = clips))
            ui.waitUntil(10_000) { ui.onAllNodesWithText("${originalCount + 1}件").fetchSemanticsNodes().isNotEmpty() }
            ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("session_$id"))
            ui.onNodeWithTag("session_$id").performClick()
            ui.waitUntil(10_000) { ui.onAllNodesWithTag("detail_timeline").fetchSemanticsNodes().isNotEmpty() }
            test()
        } finally {
            store.delete(id)
        }
    }
}
