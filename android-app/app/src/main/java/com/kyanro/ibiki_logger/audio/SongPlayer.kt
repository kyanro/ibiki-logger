package com.kyanro.ibiki_logger.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import com.kyanro.ibiki_logger.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Optional foreground-only music. Recording and leaving the screen always stop it. */
class SongPlayer(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener({ change ->
            if (change < 0) stop()
        }, Handler(Looper.getMainLooper()))
        .build()
    private var media: MediaPlayer? = null
    private var hasFocus = false
    private val mutablePlaying = MutableStateFlow(false)
    val playing = mutablePlaying.asStateFlow()

    fun play(): Boolean {
        stop()
        val recording = RecordingService.state.value
        if (recording.running || recording.stopping) return false
        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
        hasFocus = true
        return try {
            // Create the decoder only when the user asks to listen.
            val player = MediaPlayer.create(appContext, R.raw.nyaa_nyaa_nyaa, attributes, AudioManager.AUDIO_SESSION_ID_GENERATE)
                ?: run { stop(); return false }
            media = player
            player.isLooping = true
            player.setVolume(0.25f, 0.25f)
            player.setOnErrorListener { _, _, _ -> stop(); true }
            player.start()
            mutablePlaying.value = true
            true
        } catch (_: Exception) {
            stop()
            false
        }
    }

    fun stop() {
        media?.release()
        media = null
        mutablePlaying.value = false
        if (hasFocus) {
            hasFocus = false
            audioManager.abandonAudioFocusRequest(focusRequest)
        }
    }
}
