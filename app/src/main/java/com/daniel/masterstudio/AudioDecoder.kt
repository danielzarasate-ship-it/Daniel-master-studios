package com.daniel.masterstudio
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteOrder
object AudioDecoder {
    fun decodeToWav(context: Context, uri: android.net.Uri, output: File) {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: error("No se pudo abrir el audio.")
        val extractor = MediaExtractor(); extractor.setDataSource(pfd.fileDescriptor); pfd.close()
        var track = -1
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) { track = i; break }
        }
        require(track >= 0) { "No se encontró una pista de audio." }
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: error("Formato no compatible.")
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0); codec.start()
        val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
        val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
        val pcm = java.io.ByteArrayOutputStream(); var inputDone=false; var outputDone=false
        val info=MediaCodec.BufferInfo()
        try {
            while (!outputDone) {
                if (!inputDone) {
                    val ii=codec.dequeueInputBuffer(10000)
                    if (ii>=0) {
                        val input=codec.getInputBuffer(ii) ?: error("Buffer de entrada no disponible.")
                        val size=extractor.readSampleData(input,0)
                        if (size<0) { codec.queueInputBuffer(ii,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone=true }
                        else { codec.queueInputBuffer(ii,0,size,extractor.sampleTime,0); extractor.advance() }
                    }
                }
                val oi=codec.dequeueOutputBuffer(info,10000)
                if (oi>=0) {
                    val out=codec.getOutputBuffer(oi)
                    if(out!=null && info.size>0){ val dup=out.duplicate().order(ByteOrder.LITTLE_ENDIAN); dup.position(info.offset); dup.limit(info.offset+info.size); val bytes=ByteArray(dup.remaining()); dup.get(bytes); pcm.write(bytes) }
                    codec.releaseOutputBuffer(oi,false)
                    if((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0) outputDone=true
                }
            }
        } finally { try{codec.stop()}catch(_:Exception){}; codec.release(); extractor.release() }
        writeWav(output,sampleRate,channels,pcm.toByteArray())
    }
    private fun writeWav(file:File,sampleRate:Int,channels:Int,pcm:ByteArray){
        require(channels in 1..2){"Solo se admiten audio mono o estéreo."}
        RandomAccessFile(file,"rw").use{r-> r.setLength(0)
            fun w16(v:Int){r.write(byteArrayOf((v and 255).toByte(),((v ushr 8) and 255).toByte()))}
            fun w32(v:Int){r.write(byteArrayOf((v and 255).toByte(),((v ushr 8) and 255).toByte(),((v ushr 16) and 255).toByte(),((v ushr 24) and 255).toByte()))}
            r.writeBytes("RIFF");w32(36+pcm.size);r.writeBytes("WAVE");r.writeBytes("fmt ");w32(16);w16(1);w16(channels);w32(sampleRate);w32(sampleRate*channels*2);w16(channels*2);w16(16);r.writeBytes("data");w32(pcm.size);r.write(pcm)
        }
    }
}