package com.example.voicechanger

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor

/**
 * 实时变调器：双抽头延迟线 + 交叉淡化（低延迟，适合语音）。
 * ratio > 1 升调（变细、变女声），ratio < 1 降调。
 * 升调时共振峰也会一起上移，正好让男声听起来更像女声。
 */
class PitchShifter(sampleRate: Int, windowMs: Float = 40f) {
    private val size = sampleRate * windowMs / 1000f
    private val mask: Int
    private val buf: FloatArray
    private var w = 0
    private var phase = 0f

    @Volatile
    var ratio = 1f

    init {
        var l = 1
        while (l < size.toInt() + 4) l = l shl 1
        mask = l - 1
        buf = FloatArray(l)
    }

    private fun read(delay: Float): Float {
        val pos = w - delay
        val i0 = floor(pos).toInt()
        val frac = pos - i0
        val a = buf[i0 and mask]
        val b = buf[(i0 + 1) and mask]
        return a + (b - a) * frac
    }

    fun process(x: Float): Float {
        buf[w] = x
        val r = ratio
        var out = x
        if (abs(r - 1f) > 0.001f) {
            phase += (1f - r) / size
            if (phase >= 1f) phase -= 1f else if (phase < 0f) phase += 1f
            var p2 = phase + 0.5f
            if (p2 >= 1f) p2 -= 1f
            val g1 = 0.5f - 0.5f * cos(2f * PI.toFloat() * phase)
            val g2 = 1f - g1
            out = read(phase * size) * g1 + read(p2 * size) * g2
        }
        w = (w + 1) and mask
        return out
    }
}
