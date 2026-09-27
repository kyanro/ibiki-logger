package com.kyanro.ibiki_logger.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.SystemClock
import java.io.File
import java.nio.ByteOrder

object AacClipWriter {
    fun write(clip: RawClip, destination: File) {
        val pending = File(destination.parentFile, destination.name + ".part")
        val pcm = ShortArray(clip.frames.sumOf { it.pcm.size })
        var position = 0
        clip.frames.forEach { it.pcm.copyInto(pcm, position); position += it.pcm.size }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        var muxer: MediaMuxer? = null
        var started = false
        var successful = false
        try {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 32_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 4096)
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            muxer = MediaMuxer(pending.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var track = -1
            var sent = 0
            var inputEnded = false
            var outputEnded = false
            val info = MediaCodec.BufferInfo()
            val deadline = SystemClock.elapsedRealtime() + 30_000
            while (!outputEnded) {
                check(SystemClock.elapsedRealtime() < deadline) { "音声の保存がタイムアウトしました" }
                if (!inputEnded) {
                    val index = codec.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val buffer = codec.getInputBuffer(index)!!.apply { clear(); order(ByteOrder.LITTLE_ENDIAN) }
                        val count = minOf(buffer.capacity() / 2, pcm.size - sent)
                        if (count > 0) {
                            buffer.asShortBuffer().put(pcm, sent, count)
                            codec.queueInputBuffer(index, 0, count * 2, sent * 1_000_000L / SAMPLE_RATE, 0)
                            sent += count
                        } else {
                            codec.queueInputBuffer(index, 0, 0, sent * 1_000_000L / SAMPLE_RATE, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        }
                    }
                }
                val index = codec.dequeueOutputBuffer(info, 10_000)
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = muxer.addTrack(codec.outputFormat); muxer.start(); started = true
                } else if (index >= 0) {
                    val buffer = codec.getOutputBuffer(index)!!
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                        check(started)
                        buffer.position(info.offset); buffer.limit(info.offset + info.size)
                        muxer.writeSampleData(track, buffer, info)
                    }
                    outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(index, false)
                }
            }
            muxer.stop(); started = false
            successful = true
        } finally {
            runCatching { codec.stop() }; codec.release()
            if (started) runCatching { muxer?.stop() }
            muxer?.release()
            if (!successful) pending.delete()
        }
        check(pending.renameTo(destination)) { "音声ファイルを確定できませんでした" }
    }
}
