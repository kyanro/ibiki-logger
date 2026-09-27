package com.kyanro.ibiki_logger.audio

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.StatFs
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kyanro.ibiki_logger.MainActivity
import com.kyanro.ibiki_logger.R
import com.kyanro.ibiki_logger.data.ClipRecord
import com.kyanro.ibiki_logger.data.GapRecord
import com.kyanro.ibiki_logger.data.SessionRecord
import com.kyanro.ibiki_logger.data.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

data class RecordingState(
    val running: Boolean = false, val stopping: Boolean = false, val sessionId: String? = null,
    val durationMs: Long = 0, val levelDb: Double = -120.0, val snoreScore: Float = 0f,
    val silenced: Boolean = false, val error: String? = null
)

class RecordingService : Service() {
    private val stopRequested = AtomicBoolean(false)
    @Volatile private var recorder: AudioRecord? = null
    private var worker: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { requestStop(); return START_NOT_STICKY }
        if (intent?.action != ACTION_START || worker?.isAlive == true) return START_NOT_STICKY
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            state.value = RecordingState(error = "マイクの使用を許可してから開始してください")
            stopSelf(); return START_NOT_STICKY
        }
        val sensitivity = runCatching { Sensitivity.valueOf(intent.getStringExtra("sensitivity") ?: "NORMAL") }.getOrDefault(Sensitivity.NORMAL)
        val soundOnly = intent.getBooleanExtra("soundOnly", false)
        try {
            showForegroundNotification()
            wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ibiki:recording").apply {
                acquire(TimeUnit.HOURS.toMillis(12) + 60_000)
            }
            stopRequested.set(false)
            state.value = RecordingState(running = true)
            worker = Thread({ record(sensitivity, soundOnly) }, "IbikiCapture").apply { start() }
        } catch (error: Exception) {
            state.value = RecordingState(error = "録音を開始できませんでした: ${error.message}")
            releaseWakeLock(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun showForegroundNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "夜間の録音", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, RecordingService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_moon).setContentTitle("いびきログ・録音中")
            .setContentText("画面を消しても記録を続けます").setContentIntent(open)
            .setOngoing(true).setOnlyAlertOnce(true).setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "録音を停止", stop).build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) startForeground(10, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(10, notification)
    }

    private fun battery() = getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    private fun record(sensitivity: Sensitivity, soundOnly: Boolean) {
        val store = SessionStore.get(this)
        val id = "night_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
        val segmenter = ClipSegmenter()
        val writerFailure = AtomicReference<Exception?>(null)
        val writers = ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, ArrayBlockingQueue(4))
        var classifier: SnoreClassifier? = null
        var failure: Exception? = null
        var sampleOffset = 0L
        var gapStart: Long? = null
        var savedSession = false
        val silenced = AtomicBoolean(false)
        val callback = object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
                silenced.set(recorder?.activeRecordingConfiguration?.isClientSilenced == true)
            }
        }
        fun enqueue(clip: RawClip?) {
            if (clip == null) return
            writers.execute {
                try {
                    val name = "clip_${clip.startMs.toString().padStart(9, '0')}.m4a"
                    AacClipWriter.write(clip, File(store.directory(id), name))
                    val metadata = ClipRecord(name, clip.startMs, clip.durationMs, clip.maxSnoreScore, clip.hitFrames * 100L,
                        clip.frames.map { it.peak }, clip.frames.map { it.rmsDb })
                    store.update(id) { it.copy(durationMs = maxOf(it.durationMs, clip.startMs + clip.durationMs), clips = (it.clips + metadata).sortedBy { c -> c.startMs }) }
                } catch (error: Exception) { writerFailure.compareAndSet(null, error); stopRequested.set(true) }
            }
        }
        try {
            check(StatFs(filesDir.absolutePath).availableBytes > 100L * 1024 * 1024) { "空き容量を100 MB以上確保してください" }
            if (!soundOnly) classifier = SnoreClassifier(this)
            val minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(minimum > 0) { "この端末のマイク設定に対応していません" }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                throw SecurityException("マイクの許可が取り消されました")
            }
            val audio = AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(AudioFormat.Builder().setSampleRate(SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(maxOf(minimum * 4, SAMPLE_RATE * 2 * 4)).build()
            recorder = audio
            check(audio.state == AudioRecord.STATE_INITIALIZED) { "マイクを初期化できませんでした" }
            audio.registerAudioRecordingCallback(mainExecutor, callback)
            store.save(SessionRecord(id, System.currentTimeMillis(), sensitivity = sensitivity.name, soundOnly = soundOnly,
                device = "${Build.MANUFACTURER} ${Build.MODEL}", androidVersion = Build.VERSION.RELEASE, startBattery = battery()))
            savedSession = true
            state.value = state.value.copy(sessionId = id)
            audio.startRecording()
            check(audio.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "マイクを開始できませんでした" }
            var frameCount = 0L
            var score = 0f
            var recentLoud = false
            while (!stopRequested.get() && sampleOffset < SAMPLE_RATE * 60L * 60L * 12L) {
                writerFailure.get()?.let { throw it }
                val pcm = ShortArray(FRAME_SAMPLES)
                var filled = 0
                while (filled < pcm.size && !stopRequested.get()) {
                    val read = audio.read(pcm, filled, pcm.size - filled, AudioRecord.READ_BLOCKING)
                    check(read > 0) { "マイクの読み取りが中断されました ($read)" }
                    filled += read
                }
                if (filled != pcm.size) break
                val frame = AudioFrame.from(sampleOffset, pcm)
                val offsetMs = sampleOffset * 1000 / SAMPLE_RATE
                sampleOffset += pcm.size
                frameCount++
                val muted = silenced.get() || audio.activeRecordingConfiguration?.isClientSilenced == true || getSystemService(AudioManager::class.java).isMicrophoneMute
                if (muted) {
                    if (gapStart == null) { gapStart = offsetMs; enqueue(segmenter.flush()); classifier?.clear(); score = 0f }
                } else {
                    gapStart?.let { start -> store.update(id) { it.copy(gaps = it.gaps + GapRecord(start, offsetMs, "microphone_silenced")) }; gapStart = null }
                    classifier?.append(pcm)
                    recentLoud = recentLoud || frame.rmsDb >= sensitivity.minimumDb
                    if (frameCount % 5L == 0L) {
                        score = if (!soundOnly && recentLoud) classifier!!.score() else 0f
                        recentLoud = false
                    }
                    val hit = if (soundOnly) frame.rmsDb >= sensitivity.minimumDb else score >= sensitivity.score
                    enqueue(segmenter.accept(frame, hit, score))
                }
                if (frameCount % 5L == 0L) state.value = state.value.copy(durationMs = sampleOffset * 1000 / SAMPLE_RATE, levelDb = frame.rmsDb, snoreScore = score, silenced = muted)
                if (frameCount % 300L == 0L) {
                    val duration = sampleOffset * 1000 / SAMPLE_RATE
                    store.update(id) { it.copy(durationMs = duration) }
                    check(StatFs(filesDir.absolutePath).availableBytes > 50L * 1024 * 1024) { "空き容量が少なくなったため停止しました" }
                }
            }
        } catch (error: Exception) { if (!stopRequested.get() || writerFailure.get() != null) failure = error }
        finally {
            state.value = state.value.copy(stopping = true)
            recorder?.let { audio -> runCatching { audio.unregisterAudioRecordingCallback(callback) }; runCatching { audio.stop() }; audio.release() }
            recorder = null
            runCatching { enqueue(segmenter.flush()) }.onFailure { if (failure == null) failure = Exception(it) }
            writers.shutdown()
            if (!writers.awaitTermination(120, TimeUnit.SECONDS)) { writers.shutdownNow(); failure = IllegalStateException("音声の保存が完了しませんでした") }
            failure = failure ?: writerFailure.get()
            classifier?.close()
            if (savedSession) {
                val duration = sampleOffset * 1000 / SAMPLE_RATE
                runCatching {
                    store.update(id) { session -> session.copy(durationMs = duration,
                        status = if (failure == null) "completed" else "error", message = failure?.message.orEmpty(), endBattery = battery(),
                        gaps = session.gaps + listOfNotNull(gapStart?.let { GapRecord(it, duration, "microphone_silenced") })) }
                }.onFailure { if (failure == null) failure = Exception(it) }
            }
            releaseWakeLock()
            state.value = RecordingState(sessionId = if (savedSession) id else null, error = failure?.let { "録音を停止しました: ${it.message}" })
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun requestStop() {
        if (state.value.running) state.value = state.value.copy(stopping = true)
        stopRequested.set(true)
        runCatching { recorder?.stop() }
        if (worker == null) stopSelf()
    }

    private fun releaseWakeLock() { wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null }
    override fun onDestroy() { requestStop(); super.onDestroy() }

    companion object {
        private const val CHANNEL = "ibiki_recording"
        private const val ACTION_START = "com.kyanro.ibiki_logger.START"
        private const val ACTION_STOP = "com.kyanro.ibiki_logger.STOP"
        val state = MutableStateFlow(RecordingState())
        fun start(context: Context, sensitivity: Sensitivity, soundOnly: Boolean) {
            ContextCompat.startForegroundService(context, Intent(context, RecordingService::class.java).setAction(ACTION_START)
                .putExtra("sensitivity", sensitivity.name).putExtra("soundOnly", soundOnly))
        }
        fun stop(context: Context) { context.startService(Intent(context, RecordingService::class.java).setAction(ACTION_STOP)) }
    }
}
