package com.daniel.masterstudio

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.view.View
import android.widget.*
import java.io.File
import java.util.Locale
import kotlin.math.sin

class MainActivity : Activity() {
    private var selected: Uri? = null
    private var decodedOriginal: File? = null
    private var mastered: File? = null
    private var player: MediaPlayer? = null
    private lateinit var status: TextView
    private lateinit var masterButton: Button
    private lateinit var exportButton: Button
    private lateinit var profile: Spinner
    private lateinit var seek: SeekBar
    private lateinit var timeLabel: TextView
    private lateinit var originalButton: Button
    private lateinit var masterPreviewButton: Button
    private lateinit var voiceClarity: SeekBar
    private lateinit var voiceAir: SeekBar
    private lateinit var beatBass: SeekBar
    private lateinit var beatBrightness: SeekBar
    private var userSeeking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(8, 3, 24)) }
        root.addView(GalaxyView(this), FrameLayout.LayoutParams(-1, -1))
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 22, 24, 32) }
        scroll.addView(content)

        fun text(t: String, size: Float, color: Int = Color.rgb(242, 236, 255)) = TextView(this).apply {
            this.text = t; textSize = size; setTextColor(color); setPadding(2, 5, 2, 5)
        }
        fun card() = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(18, 14, 18, 14)
            background = GradientDrawable().apply { setColor(0xCC120A27.toInt()); cornerRadius = 28f; setStroke(1, 0x665F35A8.toInt()) }
            layoutParams = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 8, 0, 8) }
        }

        content.addView(text("✦ DANIEL MASTER STUDIO ✦", 25f, Color.rgb(220, 175, 255)).apply { gravity = android.view.Gravity.CENTER })
        content.addView(text("PRO MASTERING  •  2026", 12f, Color.rgb(165, 145, 205)).apply { gravity = android.view.Gravity.CENTER })

        val inputCard = card()
        inputCard.addView(text("🎵  AUDIO", 16f, Color.rgb(210, 185, 255)))
        val pick = Button(this).apply { text = "＋  SELECCIONAR AUDIO"; isAllCaps = false }
        inputCard.addView(pick); content.addView(inputCard)

        val profileCard = card()
        profileCard.addView(text("🎛️  PERFIL DE MASTER", 16f, Color.rgb(210, 185, 255)))
        profile = Spinner(this)
        profile.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            listOf("Automático / Equilibrado", "Corrido tumbado", "Rap / Trap", "Reguetón", "Cumbia", "Pop / Urbano"))
        profileCard.addView(profile); content.addView(profileCard)

        val controlCard = card()
        controlCard.addView(text("🎤  VOZ", 16f, Color.rgb(210, 185, 255)))
        voiceClarity = slider(controlCard, "Claridad / presencia")
        voiceAir = slider(controlCard, "Brillo / aire")
        controlCard.addView(text("🥁  BEAT", 16f, Color.rgb(210, 185, 255)))
        beatBass = slider(controlCard, "Graves / peso")
        beatBrightness = slider(controlCard, "Brillo / detalle")
        content.addView(controlCard)

        val actionCard = card()
        masterButton = Button(this).apply { text = "⚡  MASTERIZAR"; isEnabled = false; isAllCaps = false; textSize = 16f }
        exportButton = Button(this).apply { text = "⇩  EXPORTAR WAV 24-BIT"; isEnabled = false; isAllCaps = false }
        actionCard.addView(masterButton); actionCard.addView(exportButton); content.addView(actionCard)

        val previewCard = card()
        previewCard.addView(text("🎧  VISTA PREVIA A / B", 16f, Color.rgb(210, 185, 255)))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        originalButton = Button(this).apply { text = "▶ ORIGINAL"; isAllCaps = false; isEnabled = false }
        masterPreviewButton = Button(this).apply { text = "▶ MASTER"; isAllCaps = false; isEnabled = false }
        row.addView(originalButton, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(masterPreviewButton, LinearLayout.LayoutParams(0, -2, 1f))
        previewCard.addView(row)
        seek = SeekBar(this).apply { max = 1000; isEnabled = false }
        previewCard.addView(seek)
        timeLabel = text("00:00  /  00:00", 12f, Color.rgb(175, 160, 205)).apply { gravity = android.view.Gravity.CENTER }
        previewCard.addView(timeLabel); content.addView(previewCard)

        status = text("Carga una canción para comenzar.", 13f, Color.rgb(190, 180, 215)); content.addView(status)
        content.addView(text("✓ Limpieza  •  EQ  •  voz  •  dinámica  •  estéreo  •  loudness  •  limiter", 11f, Color.rgb(145, 130, 175)))
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1).apply { setMargins(14, 24, 14, 10) })
        setContentView(root)

        pick.setOnClickListener { stopPlayer(); startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "audio/*"; addCategory(Intent.CATEGORY_OPENABLE) }, REQUEST_OPEN) }
        masterButton.setOnClickListener { runMaster() }
        exportButton.setOnClickListener { saveMaster() }
        originalButton.setOnClickListener { togglePreview(false) }
        masterPreviewButton.setOnClickListener { togglePreview(true) }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(s: SeekBar) { userSeeking = true }
            override fun onStopTrackingTouch(s: SeekBar) { userSeeking = false; player?.let { if (it.duration > 0) it.seekTo((it.duration * s.progress) / 1000) } }
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {}
        })
    }

    private fun slider(parent: LinearLayout, name: String): SeekBar {
        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val label = TextView(this).apply { text = name + "  50%"; textSize = 12f; setTextColor(Color.rgb(185, 175, 210)) }
        val bar = SeekBar(this).apply { max = 100; progress = 50 }
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) { label.text = name + "  " + p + "%" }
        })
        row.addView(label); row.addView(bar); parent.addView(row); return bar
    }

    private fun runMaster() {
        val uri = selected ?: return
        masterButton.isEnabled = false; exportButton.isEnabled = false; originalButton.isEnabled = false; masterPreviewButton.isEnabled = false
        stopPlayer(); status.text = "🔎 Analizando mezcla y preparando master…"
        Thread {
            try {
                val input = File(cacheDir, "decoded_" + System.currentTimeMillis() + ".wav")
                AudioDecoder.decodeToWav(this, uri, input); decodedOriginal = input
                val output = File(cacheDir, "DanielMaster_" + System.currentTimeMillis() + ".wav")
                var settings = Profiles.forName(profile.selectedItem.toString())
                fun delta(bar: SeekBar, range: Float): Float = ((bar.progress - 50) / 50f) * range
                settings = settings.copy(
                    presenceDb = settings.presenceDb + delta(voiceClarity, 1.5f),
                    airDb = settings.airDb + delta(voiceAir, 1.5f),
                    lowShelfDb = settings.lowShelfDb + delta(beatBass, 1.5f),
                    mudDb = settings.mudDb + delta(beatBrightness, 1.0f)
                )
                WavProcessor.master(input, output, settings) { p -> runOnUiThread { status.text = "⚡ Masterizando… " + p + "%" } }
                mastered = output
                runOnUiThread {
                    status.text = String.format(Locale.US, "✓ Master listo • WAV 24-bit • %s", profile.selectedItem.toString())
                    masterButton.isEnabled = true; exportButton.isEnabled = true; originalButton.isEnabled = true; masterPreviewButton.isEnabled = true; seek.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "✕ No se pudo procesar: " + e.message; masterButton.isEnabled = true }
            }
        }.start()
    }

    private fun togglePreview(master: Boolean) {
        val file = if (master) mastered else decodedOriginal
        if (master && file == null) return
        try {
            val current = player?.currentPosition ?: 0
            stopPlayer()
            val mp = if (!master) MediaPlayer.create(this, selected) else MediaPlayer().apply { setDataSource(file!!.absolutePath); prepare() }
            mp ?: error("No se pudo reproducir el audio.")
            player = mp
            mp.seekTo(current.coerceAtMost(mp.duration.coerceAtLeast(0)))
            mp.setOnCompletionListener { originalButton.text = "▶ ORIGINAL"; masterPreviewButton.text = "▶ MASTER" }
            mp.start()
            if (master) masterPreviewButton.text = "⏸ MASTER" else originalButton.text = "⏸ ORIGINAL"
            status.text = if (master) "🎧 Escuchando MASTER • usa A/B para comparar" else "🎧 Escuchando ORIGINAL • usa A/B para comparar"
            updateProgress()
        } catch (e: Exception) { status.text = "No se pudo reproducir: " + e.message }
    }

    private fun updateProgress() {
        val mp = player ?: return
        if (mp.isPlaying) {
            if (mp.duration > 0) seek.progress = (mp.currentPosition * 1000 / mp.duration).coerceIn(0, 1000)
            timeLabel.text = fmt(mp.currentPosition) + "  /  " + fmt(mp.duration)
            seek.postDelayed({ updateProgress() }, 250)
        }
    }

    private fun fmt(ms: Int): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
    }

    private fun stopPlayer() {
        player?.let { try { it.stop() } catch (_: Exception) {}; it.release() }
        player = null
        if (::originalButton.isInitialized) originalButton.text = "▶ ORIGINAL"
        if (::masterPreviewButton.isInitialized) masterPreviewButton.text = "▶ MASTER"
    }

    private fun saveMaster() {
        if (mastered == null) return
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type = "audio/wav"; putExtra(Intent.EXTRA_TITLE, "DanielMaster_" + System.currentTimeMillis() + ".wav"); addCategory(Intent.CATEGORY_OPENABLE) }, REQUEST_SAVE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OPEN && resultCode == RESULT_OK) {
            selected = data?.data; mastered = null; decodedOriginal = null; exportButton.isEnabled = false; masterPreviewButton.isEnabled = false
            originalButton.isEnabled = selected != null; masterButton.isEnabled = selected != null
            status.text = if (selected != null) "✓ Audio cargado. Presiona MASTERIZAR." else "No se seleccionó ningún archivo."
        } else if (requestCode == REQUEST_SAVE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return; val file = mastered ?: return
            try { contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { ins -> ins.copyTo(out) } } ?: error("No se pudo crear el archivo."); status.text = "✓ Exportación completada." }
            catch (e: Exception) { status.text = "✕ Error al exportar: " + e.message }
        }
    }

    override fun onDestroy() { stopPlayer(); super.onDestroy() }
    companion object { private const val REQUEST_OPEN = 10; private const val REQUEST_SAVE = 20 }
}

class GalaxyView(context: android.content.Context) : View(context) {
    private val stars = Array(95) { floatArrayOf(Math.random().toFloat(), Math.random().toFloat(), (1.0 + Math.random() * 2.4).toFloat(), Math.random().toFloat()) }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas); canvas.drawColor(Color.rgb(8, 3, 24))
        val w = width.toFloat(); val h = height.toFloat(); paint.style = Paint.Style.FILL
        for (s in stars) {
            val twinkle = (0.55 + 0.45 * sin((java.lang.System.nanoTime() / 1e9 * s[3]).toDouble()).coerceIn(-1.0, 1.0)).toFloat()
            paint.color = Color.argb((150 + 90 * twinkle).toInt().coerceIn(0, 255), 220, 200, 255)
            canvas.drawCircle(s[0] * w, s[1] * h, s[2], paint)
        }
        paint.color = Color.argb(45, 150, 70, 255); canvas.drawCircle(w * .78f, h * .18f, w * .34f, paint)
        paint.color = Color.argb(32, 80, 20, 180); canvas.drawCircle(w * .12f, h * .72f, w * .42f, paint)
        postInvalidateDelayed(80)
    }
}
