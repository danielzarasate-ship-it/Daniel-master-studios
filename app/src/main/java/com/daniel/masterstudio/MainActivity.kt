package com.daniel.masterstudio

import android.app.*
import android.os.Bundle
import android.content.*
import android.net.Uri
import android.view.Gravity
import android.widget.*
import java.io.File

class MainActivity : Activity() {
    private var selected: Uri? = null
    private var mastered: File? = null
    private lateinit var status: TextView
    private lateinit var masterButton: Button
    private lateinit var exportButton: Button
    private lateinit var profile: Spinner

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(28,28,28,28); setBackgroundColor(0xFF09090B.toInt()) }
        fun tv(t:String,size:Float)=TextView(this).apply { text=t; textSize=size; setTextColor(0xFFFFFFFF.toInt()); setPadding(0,8,0,8) }
        root.addView(tv("DANIEL MASTER STUDIO",24f))
        root.addView(tv("Motor de mastering • voz clara • potencia • compatibilidad",14f).apply { setTextColor(0xFFB8B8C2.toInt()) })
        val pick=Button(this).apply{text="SELECCIONAR AUDIO"}; root.addView(pick)
        profile=Spinner(this); profile.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,listOf("Automático / Equilibrado","Corrido tumbado","Rap / Trap","Reguetón","Cumbia","Pop / Urbano")); root.addView(profile)
        masterButton=Button(this).apply{text="MASTERIZAR";isEnabled=false}; root.addView(masterButton)
        exportButton=Button(this).apply{text="EXPORTAR WAV 24-BIT";isEnabled=false}; root.addView(exportButton)
        status=tv("Carga una canción WAV PCM para comenzar.",14f); root.addView(status)
        root.addView(tv("Cadena: subsonic → EQ dinámica → de-esser → multibanda → imagen estéreo → bus → loudness → true-peak limiter",12f).apply{setTextColor(0xFF8E8E98.toInt())})
        setContentView(root)
        pick.setOnClickListener { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="audio/wav";addCategory(Intent.CATEGORY_OPENABLE}},10) }
        masterButton.setOnClickListener { runMaster() }
        exportButton.setOnClickListener { saveMaster() }
    }
    private fun runMaster(){
        val u=selected ?: return
        masterButton.isEnabled=false; exportButton.isEnabled=false; status.text="Analizando mezcla…"
        Thread {
            try {
                val input=File(cacheDir,"input_${System.currentTimeMillis()}.wav")
                contentResolver.openInputStream(u)!!.use{ins->input.outputStream().use{outs->ins.copyTo(outs)}}
                val out=File(cacheDir,"DanielMaster_${System.currentTimeMillis()}.wav")
                val settings=Profiles.forName(profile.selectedItem.toString())
                WavProcessor.master(input,out,settings){p->runOnUiThread{status.text="Masterizando… $p%"}}
                mastered=out
                runOnUiThread{status.text="Master terminado. WAV 24-bit listo.";masterButton.isEnabled=true;exportButton.isEnabled=true}
            }catch(e:Exception){runOnUiThread{status.text="No se pudo procesar: ${e.message}";masterButton.isEnabled=true}}
        }.start()
    }
    private fun saveMaster(){
        val f=mastered ?: return
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{type="audio/wav";putExtra(Intent.EXTRA_TITLE,"DanielMaster.wav");addCategory(Intent.CATEGORY_OPENABLE)},20)
    }
    override fun onActivityResult(r:Int,c:Int,d:Intent?){
        super.onActivityResult(r,c,d)
        if(r==10&&c==RESULT_OK){selected=d?.data;masterButton.isEnabled=selected!=null;status.text="Audio cargado. Selecciona perfil y MASTERIZAR."}
        if(r==20&&c==RESULT_OK){
            val uri=d?.data ?: return; val f=mastered ?: return
            try{contentResolver.openOutputStream(uri)!!.use{out->f.inputStream().use{ins->ins.copyTo(out)}};status.text="Exportación completada."}
            catch(e:Exception){status.text="Error al exportar: ${e.message}"}
        }
    }
}
