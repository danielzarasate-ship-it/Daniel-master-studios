package com.daniel.masterstudio

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.widget.*
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.sin
import java.io.File
import java.util.Locale

class MainActivity : Activity() {
    private var selected: Uri? = null
    private var mastered: File? = null
    private lateinit var status: TextView
    private lateinit var masterButton: Button
    private lateinit var exportButton: Button
    private lateinit var profile: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(8, 3, 24)) }
        val galaxy = GalaxyView(this)
        root.addView(galaxy, FrameLayout.LayoutParams(-1, -1))
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
            setBackgroundColor(0xFF09090B.toInt())
        }
        fun label(text: String, size: Float) = TextView(this).apply {
            this.text = text
            textSize = size
            if (text.startsWith("✦")) { setTextColor(Color.rgb(220, 170, 255)) }
            setTextColor(Color.rgb(245, 240, 255))
            setPadding(0, 8, 0, 8)
        }

        content.addView(label("✦ DANIEL MASTER STUDIO ✦", 24f))
        content.addView(label("Mastering personal • voz clara • potencia • compatibilidad", 14f))

        val pick = Button(this).apply { text = "SELECCIONAR AUDIO" }
        content.addView(pick)

        profile = Spinner(this)
        profile.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("Automático / Equilibrado", "Corrido tumbado", "Rap / Trap", "Reguetón", "Cumbia", "Pop / Urbano")
        )
        content.addView(profile)

        masterButton = Button(this).apply { text = "MASTERIZAR"; isEnabled = false }
        content.addView(masterButton)

        exportButton = Button(this).apply { text = "EXPORTAR WAV 24-BIT"; isEnabled = false }
        content.addView(exportButton)

        status = label("Carga WAV, MP3, M4A u otro audio compatible para comenzar.", 14f)
        content.addView(status)
        content.addView(label("Cadena: limpieza → EQ → voz → dinámica → estéreo → loudness → limiter", 12f))
        val params = FrameLayout.LayoutParams(-1, -1).apply { setMargins(22, 38, 22, 22) }
        root.addView(content, params)
        setContentView(root)

        pick.setOnClickListener {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "audio/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }, REQUEST_OPEN)
        }
        masterButton.setOnClickListener { runMaster() }
        exportButton.setOnClickListener { saveMaster() }
    }

    private fun runMaster() {
        val uri = selected ?: return
        masterButton.isEnabled = false
        exportButton.isEnabled = false
        status.text = "Analizando mezcla…"

        Thread {
            try {
                val source = File(cacheDir, "source_" + System.currentTimeMillis())
                contentResolver.openInputStream(uri)?.use { ins ->
                    source.outputStream().use { outs -> ins.copyTo(outs) }
                } ?: error("No se pudo abrir el audio.")
                val input = File(cacheDir, "decoded_" + System.currentTimeMillis() + ".wav")
                AudioDecoder.decodeToWav(this, uri, input)
                val output = File(cacheDir, "DanielMaster_${System.currentTimeMillis()}.wav")
                val settings = Profiles.forName(profile.selectedItem.toString())

                WavProcessor.master(input, output, settings) { p ->
                    runOnUiThread { status.text = "Masterizando… $p%" }
                }

                mastered = output
                runOnUiThread {
                    status.text = String.format(Locale.US, "Master terminado • WAV 24-bit • perfil %s", profile.selectedItem.toString())
                    masterButton.isEnabled = true
                    exportButton.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "No se pudo procesar: ${e.message}"
                    masterButton.isEnabled = true
                }
            }
        }.start()
    }

    private fun saveMaster() {
        if (mastered == null) return
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_TITLE, "DanielMaster_${System.currentTimeMillis()}.wav")
            addCategory(Intent.CATEGORY_OPENABLE)
        }, REQUEST_SAVE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OPEN && resultCode == RESULT_OK) {
            selected = data?.data
            masterButton.isEnabled = selected != null
            status.text = if (selected != null) "Audio cargado. Selecciona perfil y MASTERIZAR." else "No se seleccionó ningún archivo."
        } else if (requestCode == REQUEST_SAVE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val file = mastered ?: return
            try {
                contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { ins -> ins.copyTo(out) }
                } ?: error("No se pudo crear el archivo.")
                status.text = "Exportación completada."
            } catch (e: Exception) {
                status.text = "Error al exportar: ${e.message}"
            }
        }
    }

    companion object {
        private const val REQUEST_OPEN = 10
        private const val REQUEST_SAVE = 20
    }
}


class GalaxyView(context: android.content.Context) : View(context) {
    private val stars = Array(95) { floatArrayOf(Math.random().toFloat(), Math.random().toFloat(), (1.0 + Math.random() * 2.4).toFloat(), Math.random().toFloat()) }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(8, 3, 24))
        val w=width.toFloat(); val h=height.toFloat()
        paint.style=Paint.Style.FILL
        for(s in stars){
            val twinkle=(0.55 + 0.45*sin((System.nanoTime()/1e9*s[3]).toDouble()).coerceIn(-1.0,1.0)).toFloat()
            paint.color=Color.argb((150+90*twinkle).toInt().coerceIn(0,255), 220, 200, 255)
            canvas.drawCircle(s[0]*w,s[1]*h,s[2],paint)
        }
        paint.color=Color.argb(45,150,70,255)
        canvas.drawCircle(w*.78f,h*.18f,w*.34f,paint)
        paint.color=Color.argb(32,80,20,180)
        canvas.drawCircle(w*.12f,h*.72f,w*.42f,paint)
        postInvalidateDelayed(80)
    }
}
