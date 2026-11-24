package com.example.voicechanger

/**
 * 音效预设。
 * semitones：升降调（半音），+12 = 高八度
 * brightness：高频亮度（甜、脆、气泡感）
 * warmth：低中频厚度（成熟、温暖）
 * breath：气声（说话时带一点气息）
 */
data class VoicePreset(
    val name: String,
    val emoji: String,
    val semitones: Float,
    val brightness: Float,
    val warmth: Float,
    val breath: Float,
) {
    companion object {
        val ALL = listOf(
            VoicePreset("原声", "🎤", 0f, 0f, 0f, 0f),
            VoicePreset("男变女", "🧑‍🎤", 5.5f, 0.30f, 0.10f, 0.05f),
            VoicePreset("甜妹", "🍬", 7.0f, 0.60f, 0.00f, 0.08f),
            VoicePreset("熟女", "💋", 3.5f, 0.15f, 0.35f, 0.12f),
            VoicePreset("御姐", "👠", 4.5f, 0.25f, 0.20f, 0.06f),
            VoicePreset("萝莉", "🎀", 9.0f, 0.80f, 0.00f, 0.04f),
            VoicePreset("大叔", "🧔", -5.0f, 0.00f, 0.50f, 0.00f),
        )
    }
}
