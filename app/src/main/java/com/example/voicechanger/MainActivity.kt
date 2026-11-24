package com.example.voicechanger

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.Button
import android.widget.GridLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : AppCompatActivity() {

    private val engine = VoiceEngine()
    private val presets = VoicePreset.ALL
    private val chips = mutableListOf<Button>()
    private var selected = 2          // 默认：甜妹
    private var extraSemitones = 0f
    private var recording = false
    private var lastWav: File? = null
    private var player: MediaPlayer? = null

    private lateinit var tvPitch: TextView
    private lateinit var tvStatus: TextView
    private lateinit var levelBar: ProgressBar
    private lateinit var btnPower: Button
    private lateinit var btnRecord: Button
    private lateinit var btnPlay: Button
    private lateinit var btnShare: Button

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (ok) startEngine() else toast("需要麦克风权限才能变声")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        volumeControlStream = AudioManager.STREAM_MUSIC

        tvPitch = findViewById(R.id.tvPitch)
        tvStatus = findViewById(R.id.tvStatus)
        levelBar = findViewById(R.id.levelBar)
        btnPower = findViewById(R.id.btnPower)
        btnRecord = findViewById(R.id.btnRecord)
        btnPlay = findViewById(R.id.btnPlay)
        btnShare = findViewById(R.id.btnShare)

        buildPresetGrid()
        applyPreset()

        findViewById<SeekBar>(R.id.seekPitch).setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    extraSemitones = (p - 12) / 2f        // -6 ~ +6 半音，步进 0.5
                    applyPreset()
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })

        findViewById<SwitchCompat>(R.id.switchMonitor).setOnCheckedChangeListener { _, on ->
            engine.monitor = on
        }

        engine.onLevel = { peak -> levelBar.post { levelBar.progress = (peak * 100).toInt() } }
        engine.onError = { msg ->
            runOnUiThread { toast(msg); refreshUi() }
        }

        btnPower.setOnClickListener {
            if (engine.isRunning) stopEngine() else requestAndStart()
        }
        btnRecord.setOnClickListener { toggleRecord() }
        btnPlay.setOnClickListener { playLast() }
        btnShare.setOnClickListener { shareLast() }

        refreshUi()
    }

    private fun buildPresetGrid() {
        val grid = findViewById<GridLayout>(R.id.presetGrid)
        presets.forEachIndexed { i, p ->
            val b = Button(this).apply {
                text = "${p.emoji}\n${p.name}"
                isAllCaps = false
                textSize = 15f
                setOnClickListener {
                    selected = i
                    applyPreset()
                }
            }
            val lp = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            grid.addView(b, lp)
            chips.add(b)
        }
    }

    private fun applyPreset() {
        engine.effect.apply(presets[selected], extraSemitones)
        chips.forEachIndexed { i, b ->
            val sel = i == selected
            b.background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(if (sel) 0xFFFF6FA8.toInt() else 0xFFEFEFF4.toInt())
            }
            b.setTextColor(if (sel) 0xFFFFFFFF.toInt() else 0xFF333333.toInt())
        }
        val sign = if (extraSemitones > 0) "+" else ""
        tvPitch.text = "当前：${presets[selected].name}　微调 $sign$extraSemitones 半音"
    }

    private fun requestAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) startEngine() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startEngine() {
        engine.start()
        refreshUi()
    }

    private fun stopEngine() {
        if (recording) toggleRecord()
        engine.stop()
        levelBar.progress = 0
        refreshUi()
    }

    private fun toggleRecord() {
        if (!engine.isRunning) return
        if (!recording) {
            val dir = File(cacheDir, "recordings").apply { mkdirs() }
            val f = File(dir, "voice_${System.currentTimeMillis()}.wav")
            engine.startRecording(f)
            lastWav = f
            recording = true
        } else {
            engine.stopRecording()
            recording = false
        }
        refreshUi()
    }

    private fun playLast() {
        val f = lastWav ?: return
        player?.release()
        player = MediaPlayer().apply {
            setDataSource(f.absolutePath)
            prepare()
            start()
        }
    }

    private fun shareLast() {
        val f = lastWav ?: return
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "分享变声录音"))
    }

    private fun refreshUi() {
        val on = engine.isRunning
        btnPower.text = if (on) "■ 停止变声" else "▶ 开始变声"
        btnRecord.isEnabled = on
        btnRecord.text = if (recording) "■ 停止录制" else "● 录制"
        val has = lastWav != null && !recording
        btnPlay.isEnabled = has
        btnShare.isEnabled = has
        tvStatus.text = when {
            recording -> "录制中…"
            on -> "变声中，对着麦克风说话吧"
            else -> "未开始"
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy()
        engine.stop()
        player?.release()
    }
}
