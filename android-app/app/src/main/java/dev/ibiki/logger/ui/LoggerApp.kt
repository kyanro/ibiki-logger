package dev.ibiki.logger.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ibiki.logger.audio.RecordingService
import dev.ibiki.logger.audio.Sensitivity
import dev.ibiki.logger.data.ClipRecord
import dev.ibiki.logger.data.SessionRecord
import dev.ibiki.logger.data.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.sqrt

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
private val dateFormat = DateTimeFormatter.ofPattern("M月d日  HH:mm").withZone(ZoneId.systemDefault())
fun durationLabel(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

@Composable fun LoggerApp() {
    val context = LocalContext.current
    val store = remember { SessionStore.get(context) }
    val revision by store.revision.collectAsStateWithLifecycle()
    val live by RecordingService.state.collectAsStateWithLifecycle()
    val sessions by produceState<List<SessionRecord>>(emptyList(), revision) { value = withContext(Dispatchers.IO) { store.list() } }
    val prefs = remember { context.getSharedPreferences("settings", 0) }
    var sensitivity by remember { mutableStateOf(runCatching { Sensitivity.valueOf(prefs.getString("sensitivity", "NORMAL")!!) }.getOrDefault(Sensitivity.NORMAL)) }
    var soundOnly by remember { mutableStateOf(prefs.getBoolean("soundOnly", false)) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var exportId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val player = remember { ClipPlayer() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player.stop() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); player.stop() }
    }
    BackHandler(selectedId != null) { player.stop(); selectedId = null }
    LaunchedEffect(live.error) { live.error?.let { snack.showSnackbar(it) } }
    fun startRecording() {
        player.stop()
        runCatching { RecordingService.start(context, sensitivity, soundOnly) }
            .onFailure { scope.launch { snack.showSnackbar("録音を開始できませんでした: ${it.message}") } }
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            permissionDenied = false; startRecording()
        } else permissionDenied = true
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val id = exportId
        if (uri != null && id != null) {
            exporting = true
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching {
                    val output = context.contentResolver.openOutputStream(uri) ?: error("保存先を開けませんでした")
                    output.use { store.export(id, it) }
                } }
                exporting = false
                snack.showSnackbar(if (result.isSuccess) "音声と解析データを保存しました" else "書き出しに失敗しました: ${result.exceptionOrNull()?.message}")
            }
        }
    }
    val selected = sessions.firstOrNull { it.id == selectedId }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, snackbarHost = { SnackbarHost(snack) }) { insets ->
        LazyColumn(Modifier.fillMaxSize().padding(insets), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    if (selected != null) TextButton(onClick = { player.stop(); selectedId = null }) { Text("‹  記録一覧") }
                    else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) { Text("☾", fontSize = 26.sp, color = MaterialTheme.colorScheme.primary) }
                        Text("いびきログ", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                    Text("端末内に保存", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            if (selected == null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("おやすみの間を、\n音で振り返る。", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, lineHeight = 40.sp)
                        Text("いびきの候補だけを残す、小さな録音帳。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    NightCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("今夜の記録", fontWeight = FontWeight.Medium)
                            Text(if (live.stopping) "保存中" else if (live.running) "● 記録中" else "待機中", color = MaterialTheme.colorScheme.primary)
                        }
                        Text(durationLabel(live.durationMs), fontSize = 52.sp, fontWeight = FontWeight.Light, letterSpacing = 2.sp)
                        if (live.running) {
                            LinearProgressIndicator(progress = { ((live.levelDb + 70) / 70).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), trackColor = MaterialTheme.colorScheme.surfaceVariant)
                            Text(if (live.silenced) "マイクが使用できません。中断を記録しています。" else "画面を消して、そのままおやすみください。", color = if (live.silenced) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        } else Text("充電しながら、枕元に置いて使えます。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Button(modifier = Modifier.fillMaxWidth().height(56.dp), enabled = !live.stopping, onClick = {
                            if (live.running) RecordingService.stop(context)
                            else {
                                val needed = buildList {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.RECORD_AUDIO)
                                    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                if (needed.isEmpty()) startRecording() else permissions.launch(needed.toTypedArray())
                            }
                        }) { Text(if (live.stopping) "録音を保存しています…" else if (live.running) "録音を停止" else "録音を開始", fontSize = 16.sp) }
                    }
                }
                if (permissionDenied) item {
                    Text("録音にはマイクの許可が必要です。", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("アプリの設定を開く") }
                }
                item {
                    NightCard {
                        Text("検出の感度", fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Sensitivity.entries.forEach { option -> FilterChip(selected = sensitivity == option, enabled = !live.running, onClick = {
                                sensitivity = option; prefs.edit().putString("sensitivity", option.name).apply()
                            }, label = { Text(option.title) }) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) { Text("音だけで検出", fontSize = 14.sp); Text("拾いにくいときの調整用", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Switch(checked = soundOnly, enabled = !live.running, onCheckedChange = { soundOnly = it; prefs.edit().putBoolean("soundOnly", it).apply() }, modifier = Modifier.semantics { contentDescription = "音だけで検出" })
                        }
                        Text(if (soundOnly) "会話や寝返りなど、音量が基準を超えた音を保存します。" else "端末内でいびきらしい音を判定します。取りこぼしや、ほかの音が混ざる場合があります。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 20.sp)
                    }
                }
                item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("これまでの記録", style = MaterialTheme.typography.titleMedium); Text("${sessions.size}件", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                if (sessions.isEmpty()) item { Text("記録はまだありません。\n最初の一晩から、少しずつ。", color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 26.sp, modifier = Modifier.padding(vertical = 20.dp)) }
                items(sessions, key = { it.id }) { session ->
                    NightCard(Modifier.clickable { player.stop(); selectedId = session.id }) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(dateFormat.format(Instant.ofEpochMilli(session.startedAt)), fontWeight = FontWeight.SemiBold)
                            Text("›", color = MaterialTheme.colorScheme.primary, fontSize = 24.sp)
                        }
                        Text("${durationLabel(session.durationMs)} の記録  ·  ${session.clips.size} 区間", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SessionTimeline(session)
                        if (session.status != "completed") Text(statusLabel(session.status), color = if (session.status == "recording") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
                item { Text("音の大きさは端末・置き場所で変わります。\n同じ位置で記録すると、日ごとの比較がしやすくなります。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 20.sp) }
            } else {
                item {
                    Text(dateFormat.format(Instant.ofEpochMilli(selected.startedAt)), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (selected.soundOnly) "音量で検出した記録" else "いびき候補の記録", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    NightCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Metric("記録時間", durationLabel(selected.durationMs)); Metric("保存区間", "${selected.clips.size}"); Metric("候補の目安", durationLabel(selected.candidateMs))
                        }
                        SessionTimeline(selected, 90)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(timeFormat.format(Instant.ofEpochMilli(selected.startedAt)), fontSize = 12.sp)
                            Text(timeFormat.format(Instant.ofEpochMilli(selected.startedAt + selected.durationMs)), fontSize = 12.sp)
                        }
                        Text("下の区間から音を確認できます。保存音声には前後の余白を含みます。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        if (selected.message.isNotEmpty()) Text(selected.message, color = MaterialTheme.colorScheme.error)
                        if (selected.gaps.isNotEmpty()) Text("マイクの中断 ${selected.gaps.size}件（赤色）。", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        Text("${Sensitivity.valueOf(selected.sensitivity).title}の感度 · 保存音声 ${durationLabel(selected.savedMs)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    Button(modifier = Modifier.fillMaxWidth().height(52.dp), enabled = selected.status != "recording" && !exporting, onClick = { exportId = selected.id; exportLauncher.launch("ibiki_${selected.startedAt}.zip") }) { Text(if (exporting) "書き出し中…" else "音声＋解析データを書き出す") }
                    Text("M4A・JSON・CSV を1つのZIPにまとめます。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
                item { Text("保存した区間", style = MaterialTheme.typography.titleMedium) }
                if (selected.clips.isEmpty()) item { Text("保存した音声はありません。\n検出の感度やマイクの位置を変えて試せます。", color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 24.sp) }
                items(selected.clips, key = { it.file }) { clip ->
                    ClipCard(selected, clip, player, !live.running) {
                        runCatching { player.play(File(store.directory(selected.id), clip.file)) }.onFailure { scope.launch { snack.showSnackbar("再生できませんでした: ${it.message}") } }
                    }
                }
                item { TextButton(enabled = selected.status != "recording" && !exporting, onClick = { deleteId = selected.id }) { Text("この記録を削除", color = MaterialTheme.colorScheme.error) } }
            }
        }
    }
    if (deleteId != null) AlertDialog(onDismissRequest = { deleteId = null }, title = { Text("この記録を削除しますか？") }, text = { Text("端末内の音声と解析データを削除します。書き出したZIPは残ります。") },
        confirmButton = { TextButton(onClick = {
            val id = deleteId!!; player.stop(); deleteId = null
            scope.launch { runCatching { withContext(Dispatchers.IO) { store.delete(id) } }.onSuccess { selectedId = null }.onFailure { snack.showSnackbar("削除できませんでした") } }
        }) { Text("削除") } }, dismissButton = { TextButton(onClick = { deleteId = null }) { Text("キャンセル") } })
}

@Composable private fun NightCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}
@Composable private fun Metric(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, fontSize = 22.sp, fontWeight = FontWeight.Medium) }
}
@Composable private fun SessionTimeline(session: SessionRecord, height: Int = 36) {
    val mint = MaterialTheme.colorScheme.primary; val grid = MaterialTheme.colorScheme.outline.copy(alpha = .3f); val error = MaterialTheme.colorScheme.error
    Canvas(Modifier.fillMaxWidth().height(height.dp).semantics { contentDescription = "${session.clips.size}区間の時間分布" }) {
        val duration = session.durationMs.coerceAtLeast(1)
        repeat(7) { val x = size.width * it / 6; drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1f) }
        session.clips.forEach { clip -> drawRect(mint.copy(alpha = .8f), Offset(size.width * clip.startMs / duration, size.height * .18f), Size(maxOf(3f, size.width * clip.durationMs / duration), size.height * .64f)) }
        session.gaps.forEach { gap -> drawRect(error.copy(alpha = .6f), Offset(size.width * gap.startMs / duration, 0f), Size(maxOf(2f, size.width * (gap.endMs - gap.startMs) / duration), size.height)) }
    }
}
@Composable private fun ClipCard(session: SessionRecord, clip: ClipRecord, player: ClipPlayer, enabled: Boolean, onPlay: () -> Unit) {
    val playing = player.fileName == clip.file
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) { while (playing) { progress = player.progress; delay(100) }; progress = 0f }
    NightCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column { Text(timeFormat.format(Instant.ofEpochMilli(session.startedAt + clip.startMs)), fontWeight = FontWeight.SemiBold); Text(durationLabel(clip.durationMs), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            FilledTonalButton(enabled = enabled, onClick = { if (playing) player.stop() else onPlay() }) { Text(if (playing) "停止" else "再生") }
        }
        Waveform(clip.peaks, if (playing) progress else null)
        if (!session.soundOnly) Text("いびき候補 · 判定スコア ${"%.2f".format(clip.maxSnoreScore)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun Waveform(peaks: List<Float>, progress: Float?) {
    val color = MaterialTheme.colorScheme.secondary; val cursorColor = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(60.dp).semantics { contentDescription = "録音した区間の音量波形" }) {
        val columns = minOf(peaks.size, (size.width / 4f).toInt()).coerceAtLeast(1)
        repeat(columns) { i ->
            val from = i * peaks.size / columns; val to = ((i + 1) * peaks.size / columns).coerceAtLeast(from + 1).coerceAtMost(peaks.size)
            val peak = (from until to).maxOfOrNull { peaks[it] } ?: 0f
            val amplitude = maxOf(1.5f, sqrt(peak) * size.height / 2); val x = (i + .5f) * size.width / columns
            drawLine(color, Offset(x, size.height / 2 - amplitude), Offset(x, size.height / 2 + amplitude), 2.5f, StrokeCap.Round)
        }
        progress?.let { drawLine(cursorColor, Offset(it * size.width, 0f), Offset(it * size.width, size.height), 2f) }
    }
}
private fun statusLabel(status: String) = when (status) { "recording" -> "記録中"; "interrupted" -> "途中で中断"; "error" -> "エラーで停止"; else -> "記録完了" }
private class ClipPlayer {
    var fileName by mutableStateOf<String?>(null)
        private set
    private var media: MediaPlayer? = null
    val progress: Float get() = runCatching { media?.let { it.currentPosition.toFloat() / it.duration.coerceAtLeast(1) } ?: 0f }.getOrDefault(0f)
    fun play(file: File) {
        stop(); val player = MediaPlayer(); media = player
        try {
            player.setDataSource(file.absolutePath); player.setOnCompletionListener { stop() }; player.setOnErrorListener { _, _, _ -> stop(); true }
            player.prepare(); fileName = file.name; player.start()
        } catch (error: Exception) { stop(); throw error }
    }
    fun stop() { media?.release(); media = null; fileName = null }
}
