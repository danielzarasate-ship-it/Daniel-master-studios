package com.daniel.masterstudio

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.widget.*
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
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
            setBackgroundColor(0xFF09090B.toInt())
        }
        fun label(text: String, size: Float) = TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 8, 0, 8)
        }

        root.addView(label("DANIEL MASTER STUDIO", 24f))
        root.addView(label("Mastering personal • voz clara • potencia • compatibilidad", 14f))

        val pick = Button(this).apply { text = "SELECCIONAR WAV" }
        root.addView(pick)

        profile = Spinner(this)
        profile.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("Automático / Equilibrado", "Corrido tumbado", "Rap / Trap", "Reguetón", "Cumbia", "Pop / Urbano")
        )
        root.addView(profile)

        masterButton = Button(this).apply { text = "MASTERIZAR"; isEnabled = false }
        root.addView(masterButton)

        exportButton = Button(this).apply { text = "EXPORTAR WAV 24-BIT"; isEnabled = false }
        root.addView(exportButton)

        status = label("Carga un WAV PCM para comenzar.", 14f)
        root.addView(status)
        root.addView(label("Cadena: limpieza → EQ tonal → de-esser → dinámica → estéreo → loudness → limiter", 12f))
        setContentView(root)

        pick.setOnClickListener {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "audio/wav"
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
                val input = File(cacheDir, "input_${System.currentTimeMillis()}.wav")
                contentResolver.openInputStream(uri)?.use { ins ->
                    input.outputStream().use { outs -> ins.copyTo(outs) }
                } ?: error("No se pudo abrir el audio.")

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
            type = "audio/wav"
            putExtra(Intent.EXTRA_TITLE, "DanielMaster_${System.currentTimeMillis()}.wav")
            addCategory(Intent.CATEGORY_OPENABLE)
        }, REQUEST_SAVE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OPEN && resultCode == RESULT_OK) {
            selected = data?.data
            masterButton.isEnabled = selected != null
            status.text = if (selected != null) "WAV cargado. Selecciona perfil y MASTERIZAR." else "No se seleccionó ningún archivo."
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
