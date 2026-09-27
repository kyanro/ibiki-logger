package com.kyanro.ibiki_logger.audio

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SnoreClassifier(context: Context) : AutoCloseable {
    private val interpreter: Interpreter
    private val window = ShortArray(15_600)
    private var cursor = 0
    private var filled = 0
    private val input = ByteBuffer.allocateDirect(window.size * 4).order(ByteOrder.nativeOrder())
    private val output = ByteBuffer.allocateDirect(521 * 4).order(ByteOrder.nativeOrder())

    init {
        val bytes = context.assets.open("yamnet.tflite").use { it.readBytes() }
        val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).apply { rewind() }
        interpreter = Interpreter(model, Interpreter.Options().setNumThreads(1))
        check(interpreter.getInputTensor(0).numElements() == window.size)
        check(interpreter.getOutputTensor(0).numElements() == 521)
    }

    fun append(pcm: ShortArray) {
        pcm.forEach { window[cursor] = it; cursor = (cursor + 1) % window.size }
        filled = minOf(window.size, filled + pcm.size)
    }

    fun clear() { window.fill(0); cursor = 0; filled = 0 }

    fun score(): Float {
        if (filled < window.size) return 0f
        input.rewind()
        repeat(window.size) { input.putFloat(window[(cursor + it) % window.size] / 32768f) }
        input.rewind(); output.rewind()
        interpreter.run(input, output)
        // Class 38 is Snoring in the published 521-class AudioSet label map.
        return output.getFloat(38 * 4).coerceIn(0f, 1f)
    }

    override fun close() = interpreter.close()
}
