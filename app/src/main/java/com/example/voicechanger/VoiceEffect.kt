package com.example.voicechanger

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.tanh

/** 单声道逐样本处理：变调 → 亮度/厚度 EQ → 气声 → 软限幅。 */
class VoiceEffect(private val sampleRate: Int) {
    private val shifter = PitchShifter(sampleRate)

    @Volatile private var brightness = 0f
    @Volatile private var warmth = 0f
    @Volatile private var breath = 0f

    private val aHi = coef(2500f)
    private val aLo = coef(350f)
    private var lpHi = 0f
    private var lpLo = 0f
    private var env = 0f
    private var prevNoise = 0f
    private var seed = 22222

    private fun coef(fc: Float): Float =
        1f - exp(-2f * PI.toFloat() * fc / sampleRate)

    fun apply(preset: VoicePreset, extraSemitones: Float) {
        shifter.ratio = 2f.pow((preset.semitones + extraSemitones) / 12f)
        brightness = preset.brightness
        warmth = preset.warmth
        breath = preset.breath
    }

    private fun noise(): Float {
        seed = seed xor (seed shl 13)
        seed = seed xor (seed ushr 17)
        seed = seed xor (seed shl 5)
        return seed / 2147483648f
    }

    fun process(x: Float): Float {
        var y = shifter.process(x)

        // 高频增强：让声音更亮更甜
        lpHi += aHi * (y - lpHi)
        y += brightness * (y - lpHi)

        // 低中频增强：让声音更厚、更成熟
        lpLo += aLo * (y - lpLo)
        y += warmth * lpLo

        // 气声：跟随音量的高通噪声
        env += 0.01f * (abs(y) - env)
        val n = noise()
        val hp = n - prevNoise
        prevNoise = n
        y += breath * env * hp

        // 软限幅，防止爆音
        return tanh(y)
    }
}
