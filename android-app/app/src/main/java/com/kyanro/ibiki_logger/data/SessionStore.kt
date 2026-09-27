package com.kyanro.ibiki_logger.data

import android.content.Context
import android.util.AtomicFile
import com.kyanro.ibiki_logger.audio.SAMPLE_RATE
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SessionStore private constructor(context: Context) {
    private val root = File(context.filesDir, "sessions").apply { mkdirs() }
    val revision = MutableStateFlow(0)
    fun directory(id: String): File {
        require(id.matches(Regex("[a-zA-Z0-9_-]+")))
        return File(root, id).apply { mkdirs() }
    }

    @Synchronized fun list(): List<SessionRecord> = root.listFiles().orEmpty()
        .filter { it.isDirectory }.mapNotNull { read(it.name, false) }.sortedByDescending { it.startedAt }

    @Synchronized fun read(id: String, includeWaveforms: Boolean = true): SessionRecord? = runCatching {
        decode(JSONObject(AtomicFile(File(directory(id), "session.json")).readFully().toString(Charsets.UTF_8)), includeWaveforms)
    }.getOrNull()

    @Synchronized fun save(record: SessionRecord) {
        // Waveforms are immutable per clip. Do not rewrite hours of waveform data at every checkpoint.
        record.clips.filter { it.peaks.isNotEmpty() }.forEach { clip ->
            val file = File(directory(record.id), clip.file + ".waveform.json")
            if (!file.exists()) {
                writeJson(file, JSONObject().put("peaks", JSONArray(clip.peaks)).put("rms_dbfs", JSONArray(clip.rmsDb)))
            }
        }
        writeJson(File(directory(record.id), "session.json"), encode(record, false))
        revision.value++
    }

    private fun writeJson(file: File, json: JSONObject) {
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try { stream.write(json.toString(2).toByteArray()); atomic.finishWrite(stream) }
        catch (error: Exception) { atomic.failWrite(stream); throw error }
    }

    @Synchronized fun update(id: String, transform: (SessionRecord) -> SessionRecord) {
        val record = read(id, false) ?: error("録音の記録を読み込めませんでした")
        save(transform(record))
    }

    @Synchronized fun recoverInterrupted(isRecording: () -> Boolean = { false }) {
        list().filter { it.status == "recording" }.forEach {
            if (!isRecording()) save(it.copy(status = "interrupted", message = "録音が中断されました。表示時刻は最後に保存できた時点です。"))
        }
    }

    @Synchronized fun delete(id: String) {
        val record = read(id) ?: return
        require(record.status != "recording")
        directory(id).deleteRecursively()
        revision.value++
    }

    fun export(id: String, output: OutputStream) {
        val record = read(id) ?: error("記録が見つかりません")
        require(record.status != "recording") { "録音を停止してから書き出してください" }
        ZipOutputStream(output.buffered()).use { zip ->
            fun entry(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            }
            entry("session.json", encode(record).toString(2).toByteArray())
            val csv = buildString {
                appendLine("file,start_offset_ms,start_utc,duration_ms,candidate_window_ms,max_snore_score")
                record.clips.forEach { appendLine("${it.file},${it.startMs},${Instant.ofEpochMilli(record.startedAt + it.startMs)},${it.durationMs},${it.hitWindowMs},${it.maxSnoreScore}") }
            }
            entry("clips.csv", csv.toByteArray())
            val waveform = buildString {
                appendLine("file,offset_in_clip_ms,session_offset_ms,peak_linear,rms_dbfs")
                record.clips.forEach { clip -> clip.peaks.forEachIndexed { index, peak ->
                    appendLine("${clip.file},${index * 100},${clip.startMs + index * 100},$peak,${clip.rmsDb[index]}")
                } }
            }
            entry("waveform.csv", waveform.toByteArray())
            entry("README.txt", """
                Ibiki Logger export v1
                Audio: AAC-LC, M4A, mono, 16000 Hz, target 32000 bit/s.
                All offsets refer to the original recording session, not concatenated clips.
                Clips include context before/after detections (up to about 2 seconds).
                candidate_window_ms counts positive 100 ms decision frames, NOT diagnosed snoring duration.
                max_snore_score is a YAMNet model score, NOT a calibrated probability.
                In sound_only mode the score is zero and detections indicate sound only.
                RMS is dBFS relative to digital full scale, NOT calibrated sound pressure in dB SPL.
                Waveform values were measured before lossy audio compression.
                Missing clips do not prove silence. See gaps, status and message in session.json.
                The classifier cannot identify which person made a sound.
                No network upload is performed by this app.
            """.trimIndent().toByteArray())
            record.clips.forEach { clip ->
                val file = File(directory(id), clip.file)
                check(file.isFile) { "音声ファイルが見つかりません: ${clip.file}" }
                zip.putNextEntry(ZipEntry("audio/${clip.file}"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    private fun encode(r: SessionRecord, includeWaveforms: Boolean = true) = JSONObject().apply {
        put("schema_version", 1); put("id", r.id); put("started_at_epoch_ms", r.startedAt)
        put("started_at_utc", Instant.ofEpochMilli(r.startedAt).toString()); put("duration_ms", r.durationMs)
        put("status", r.status); put("message", r.message); put("sensitivity", r.sensitivity)
        put("sound_only", r.soundOnly); put("device", r.device); put("android_version", r.androidVersion)
        put("start_battery_percent", r.startBattery); put("end_battery_percent", r.endBattery)
        put("sample_rate", SAMPLE_RATE); put("channels", 1); put("bitrate", 32000)
        put("audio_source", "VOICE_RECOGNITION"); put("detector", "yamnet-tflite-1-gate-v1")
        put("waveform_step_ms", 100)
        put("clips", JSONArray().apply { r.clips.forEach { c -> put(JSONObject().apply {
            put("file", c.file); put("start_ms", c.startMs); put("duration_ms", c.durationMs)
            put("max_snore_score", c.maxSnoreScore); put("candidate_window_ms", c.hitWindowMs)
            if (includeWaveforms) { put("peaks", JSONArray(c.peaks)); put("rms_dbfs", JSONArray(c.rmsDb)) }
            else put("waveform_file", c.file + ".waveform.json")
        }) } })
        put("gaps", JSONArray().apply { r.gaps.forEach { g -> put(JSONObject().apply {
            put("start_ms", g.startMs); put("end_ms", g.endMs); put("reason", g.reason)
        }) } })
    }

    private fun decode(j: JSONObject, includeWaveforms: Boolean): SessionRecord {
        val clips = j.getJSONArray("clips")
        val gaps = j.getJSONArray("gaps")
        return SessionRecord(j.getString("id"), j.getLong("started_at_epoch_ms"), j.getLong("duration_ms"),
            j.getString("status"), j.optString("message"), j.getString("sensitivity"), j.getBoolean("sound_only"),
            j.getString("device"), j.getString("android_version"), j.getInt("start_battery_percent"), j.getInt("end_battery_percent"),
            (0 until clips.length()).map { i -> clips.getJSONObject(i).let { c ->
                val wave = if (c.has("peaks")) c else if (!includeWaveforms) JSONObject() else
                    JSONObject(AtomicFile(File(directory(j.getString("id")), c.getString("waveform_file"))).readFully().toString(Charsets.UTF_8))
                val peaks = wave.optJSONArray("peaks") ?: JSONArray(); val rms = wave.optJSONArray("rms_dbfs") ?: JSONArray()
                ClipRecord(c.getString("file"), c.getLong("start_ms"), c.getLong("duration_ms"), c.getDouble("max_snore_score").toFloat(),
                    c.getLong("candidate_window_ms"), (0 until peaks.length()).map { peaks.getDouble(it).toFloat() }, (0 until rms.length()).map { rms.getDouble(it) })
            } }, (0 until gaps.length()).map { i -> gaps.getJSONObject(i).let { GapRecord(it.getLong("start_ms"), it.getLong("end_ms"), it.getString("reason")) } })
    }

    companion object {
        @Volatile private var instance: SessionStore? = null
        fun get(context: Context): SessionStore = instance ?: synchronized(this) {
            instance ?: SessionStore(context.applicationContext).also { instance = it }
        }
    }
}
