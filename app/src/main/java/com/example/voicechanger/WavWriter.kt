package com.example.voicechanger

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 把 16-bit 单声道 PCM 写成 WAV 文件。 */
class WavWriter(file: File, private val sampleRate: Int) {
    private val raf = RandomAccessFile(file, "rw")
    private var dataBytes = 0

    init {
        raf.setLength(0)
        raf.write(ByteArray(44))
    }

    fun write(data: ShortArray, count: Int) {
        val bb = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) bb.putShort(data[i])
        raf.write(bb.array())
        dataBytes += count * 2
    }

    fun close() {
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray())
        h.putInt(36 + dataBytes)
        h.put("WAVE".toByteArray())
        h.put("fmt ".toByteArray())
        h.putInt(16)
        h.putShort(1)                 // PCM
        h.putShort(1)                 // 单声道
        h.putInt(sampleRate)
        h.putInt(sampleRate * 2)      // 字节率
        h.putShort(2)                 // 块对齐
        h.putShort(16)                // 位深
        h.put("data".toByteArray())
        h.putInt(dataBytes)
        raf.seek(0)
        raf.write(h.array())
        raf.close()
    }
}
