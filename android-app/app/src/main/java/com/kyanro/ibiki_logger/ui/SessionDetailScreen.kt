package com.kyanro.ibiki_logger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyanro.ibiki_logger.audio.Sensitivity
import com.kyanro.ibiki_logger.data.ClipRecord
import com.kyanro.ibiki_logger.data.SessionRecord
import java.time.Instant

// Summary, export action, and page controls precede the clip cards.
private const val FIRST_CLIP_ITEM = 3

@Composable internal fun SessionDetailScreen(
    session: SessionRecord,
    player: ClipPlayer,
    playbackEnabled: Boolean,
    exporting: Boolean,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onPlay: (ClipRecord) -> Unit
) {
    var requestedPage by rememberSaveable(session.id) { mutableIntStateOf(0) }
    var focusedIndex by rememberSaveable(session.id) { mutableStateOf<Int?>(null) }
    var pendingItem by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    val pageCount = ((session.clips.size + CLIPS_PER_PAGE - 1) / CLIPS_PER_PAGE).coerceAtLeast(1)
    val page = requestedPage.coerceIn(0, pageCount - 1)
    val first = page * CLIPS_PER_PAGE
    val pageClips = session.clips.subList(first, minOf(first + CLIPS_PER_PAGE, session.clips.size))
    fun goToClip(index: Int) {
        if (player.fileName != session.clips[index].file) player.stop()
        requestedPage = index / CLIPS_PER_PAGE
        focusedIndex = index
        pendingItem = FIRST_CLIP_ITEM + index % CLIPS_PER_PAGE
    }
    fun changePage(next: Int) {
        player.stop()
        requestedPage = next.coerceIn(0, pageCount - 1)
        focusedIndex = null
        pendingItem = FIRST_CLIP_ITEM - 1
    }
    // Wait for the new page's items to exist before resolving the target position.
    LaunchedEffect(page, pendingItem) {
        pendingItem?.let { listState.scrollToItem(it); pendingItem = null }
    }
    Column(modifier) {
        Surface(shadowElevation = 3.dp, color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onBack) { Text("‹  記録一覧") }
                    Text(dateFormat.format(Instant.ofEpochMilli(session.startedAt)), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                InteractiveTimeline(session, focusedIndex, ::goToClip)
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(timeFormat.format(Instant.ofEpochMilli(session.startedAt)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(timeFormat.format(Instant.ofEpochMilli(session.startedAt + session.durationMs)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    if (session.clips.isEmpty()) "保存した区間はありません" else "高いほど大きな音 · タップで近くの区間へ",
                    modifier = Modifier.padding(top = 6.dp, bottom = 12.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().testTag("detail_clips"), state = listState,
            contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item(key = "summary") {
                NightCard {
                    Text(if (session.soundOnly) "音量で検出した記録" else "いびき候補の記録", fontWeight = FontWeight.Medium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Metric("記録時間", durationLabel(session.durationMs))
                        Metric("保存区間", "${session.clips.size}")
                        Metric("候補の目安", if (session.candidateMs in 1..999) "1秒未満" else durationLabel(session.candidateMs))
                    }
                    Text("波形は全区間で共通の音量尺度です。保存音声には前後の余白を含みます。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    if (session.message.isNotEmpty()) Text(session.message, color = MaterialTheme.colorScheme.error)
                    if (session.gaps.isNotEmpty()) Text("マイクの中断 ${session.gaps.size}件（赤色）。", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    Text("${Sensitivity.valueOf(session.sensitivity).title}の感度 · 保存音声 ${durationLabel(session.savedMs)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item(key = "export") {
                Button(modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = session.status != "recording" && !exporting, onClick = onExport) {
                    Text(if (exporting) "書き出し中…" else "音声＋解析データを書き出す")
                }
                Text("M4A・JSON・CSV を1つのZIPにまとめます。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
            item(key = "pages") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("保存した区間", style = MaterialTheme.typography.titleMedium)
                    if (session.clips.isNotEmpty()) ClipPages(page, session.clips.size, "top", ::changePage)
                }
            }
            if (session.clips.isEmpty()) item(key = "empty") {
                Text("保存した音声はありません。\n検出の感度やマイクの位置を変えて試せます。", color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 24.sp)
            }
            items(pageClips.size, key = { pageClips[it].file }) { position ->
                val index = first + position
                val clip = pageClips[position]
                ClipCard(session, clip, index, focusedIndex == index, player, playbackEnabled) { onPlay(clip) }
            }
            if (pageCount > 1) item(key = "bottom_pages") { ClipPages(page, session.clips.size, "bottom", ::changePage) }
            item(key = "delete") {
                TextButton(enabled = session.status != "recording" && !exporting, onClick = onDelete) { Text("この記録を削除", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable private fun ClipPages(page: Int, total: Int, tagSuffix: String, onPage: (Int) -> Unit) {
    val first = page * CLIPS_PER_PAGE + 1
    val last = minOf(first + CLIPS_PER_PAGE - 1, total)
    Row(Modifier.fillMaxWidth().testTag("page_controls_$tagSuffix"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(enabled = page > 0, modifier = Modifier.testTag("page_previous_$tagSuffix"), onClick = { onPage(page - 1) }) { Text("‹ 前へ") }
        Text("$first–$last / ${total}区間", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(enabled = last < total, modifier = Modifier.testTag("page_next_$tagSuffix"), onClick = { onPage(page + 1) }) { Text("次へ ›") }
    }
}

@Composable private fun InteractiveTimeline(session: SessionRecord, focusedIndex: Int?, onClip: (Int) -> Unit) {
    val mint = MaterialTheme.colorScheme.primary
    val selectedColor = MaterialTheme.colorScheme.secondary
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = .25f)
    val error = MaterialTheme.colorScheme.error
    val barWidth = with(LocalDensity.current) { 2.dp.toPx() }
    var columns by remember { mutableIntStateOf(1) }
    val envelope = remember(session.clips, session.durationMs, columns) { timelineEnvelope(session, columns) }
    val latestOnClip by rememberUpdatedState(onClip)
    val duration = session.durationMs.coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(64.dp).testTag("detail_timeline")
        .onSizeChanged { columns = (it.width / barWidth).toInt().coerceAtLeast(1) }
        .pointerInput(session.clips, duration) {
            detectTapGestures { point ->
                val offset = (point.x / size.width.coerceAtLeast(1) * duration).toLong().coerceIn(0, duration)
                nearestClipIndex(session.clips, offset)?.let(latestOnClip)
            }
        }
        .semantics {
            contentDescription = "夜全体の音量。${session.clips.size}区間。タップした時刻に近い区間へ移動" +
                (focusedIndex?.let { "。選択中の区間 ${it + 1}" } ?: "")
            customActions = buildList {
                if (session.clips.isNotEmpty()) {
                    add(CustomAccessibilityAction("前の区間へ") { latestOnClip(((focusedIndex ?: 1) - 1).coerceAtLeast(0)); true })
                    add(CustomAccessibilityAction("次の区間へ") { latestOnClip(((focusedIndex ?: -1) + 1).coerceAtMost(session.clips.lastIndex)); true })
                }
            }
        }) {
        repeat(7) { val x = size.width * it / 6; drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1f) }
        drawLine(grid, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1f)
        val focusedClip = focusedIndex?.let { session.clips.getOrNull(it) }
        focusedClip?.let { clip ->
            drawRect(selectedColor.copy(alpha = .16f), Offset(size.width * clip.startMs / duration, 0f),
                Size(maxOf(barWidth * 3, size.width * clip.durationMs / duration), size.height))
        }
        envelope.forEachIndexed { index, height ->
            if (height >= 0) {
                val amplitude = maxOf(1f, height * size.height * .48f)
                val x = (index + .5f) * size.width / columns
                drawLine(mint, Offset(x, size.height / 2 - amplitude), Offset(x, size.height / 2 + amplitude), maxOf(1f, size.width / columns * .8f))
            }
        }
        session.gaps.forEach { gap ->
            drawRect(error.copy(alpha = .6f), Offset(size.width * gap.startMs / duration, 0f),
                Size(maxOf(2f, size.width * (gap.endMs - gap.startMs) / duration), size.height))
        }
        focusedClip?.let { clip ->
            val x = size.width * (clip.startMs + clip.durationMs / 2) / duration
            drawLine(selectedColor, Offset(x, 0f), Offset(x, size.height), 2f)
        }
    }
}
