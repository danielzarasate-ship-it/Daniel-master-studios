package com.daniel.masterstudio

import java.io.*
import kotlin.math.*

data class MasterSettings(
    val intensity: Float = .70f, val targetLufs: Float = -10.0f, val ceilingDbtp: Float = -1.0f,
    val hpHz: Float = 28f, val lowShelfDb: Float = 0f, val mudDb: Float = -1.0f,
    val presenceDb: Float = .8f, val airDb: Float = .7f, val compRatio: Float = 1.8f,
    val compThresholdDb: Float = -18f, val deEss: Float = .35f, val stereoWidth: Float = 1.03f
)
data class WavData(val sampleRate:Int,val channels:Int,val samples:FloatArray,val bits:Int)
data class MasterAnalysis(val sampleRate:Int,val channels:Int,val bits:Int,val peakDbfs:Float,val rmsDbfs:Float,val dynamicRangeDb:Float,val clippedSamples:Long,val readyForDistribution:Boolean)

object WavProcessor {
    fun analyze(input:File): MasterAnalysis {
        val w=Wav.read(input)
        var peak=0f
        var sum=0.0
        var clipped=0L
        for (v in w.samples) {
            val a=abs(v)
            if (a>peak) peak=a
            sum += v.toDouble()*v.toDouble()
            if (a >= 0.9995f) clipped++
        }
        val rms=sqrt(sum/max(1,w.samples.size).toDouble()).toFloat()
        val peakDb=(20f*log10(max(peak,1e-9f)))
        val rmsDb=(20f*log10(max(rms,1e-9f)))
        val dynamic=(peakDb-rmsDb).coerceAtLeast(0f)
        val ready=peakDb <= -1.0f && clipped==0L && rmsDb in -20f..-8f && dynamic >= 6f && w.sampleRate >= 44100
        return MasterAnalysis(w.sampleRate,w.channels,w.bits,peakDb,rmsDb,dynamic,clipped,ready)
    }

    fun master(input:File, output:File, s:MasterSettings, progress:(Int)->Unit) {
        val w=Wav.read(input)
        require(w.channels in 1..2) { "Solo se admiten WAV mono o estéreo." }
        require(w.sampleRate in 8000..192000) { "Frecuencia de muestreo no compatible." }
        val x=w.samples; val sr=w.sampleRate
        progress(5)
        processSamples(x, sr, w.channels, s); progress(94)
        Wav.write24(output,sr,w.channels,x); progress(100)
    }
    fun masterPreview(input:File, output:File, s:MasterSettings, positionSec:Float) {
        val w=Wav.read(input)
        require(w.channels in 1..2) { "Solo se admiten WAV mono o estéreo." }
        val sr=w.sampleRate
        val start=((positionSec-1f).coerceAtLeast(0f)*sr*w.channels).toInt().coerceIn(0,w.samples.size)
        val end=(start+10f*sr*w.channels).toInt().coerceAtMost(w.samples.size)
        require(end>start) { "No hay suficiente audio para el preview." }
        val x=w.samples.copyOfRange(start,end)
        processSamples(x,sr,w.channels,s)
        Wav.write24(output,sr,w.channels,x)
    }

    private fun processSamples(x:FloatArray,sr:Int,ch:Int,s:MasterSettings) {
        filter(x,ch,Biquad.highPass(sr,s.hpHz,.707f))
        filter(x,ch,Biquad.peaking(sr,250f,.75f,s.mudDb*s.intensity))
        filter(x,ch,Biquad.peaking(sr,3200f,.9f,s.presenceDb*s.intensity))
        filter(x,ch,Biquad.highShelf(sr,10500f,.65f,s.airDb*s.intensity))
        DeEsser.process(x,sr,ch,s.deEss*s.intensity)
        Multiband.process(x,sr,ch,s.intensity)
        if(ch==2) Stereo.process(x,s.stereoWidth)
        BusCompressor.process(x,sr,s.compThresholdDb,s.compRatio,.55f+.35f*s.intensity)
        val measured=Loudness.kWeightedDb(x,sr,ch)
        val gainDb=((s.targetLufs-measured)*.82f).coerceIn(-2.0f,5.0f)
        val gain=10f.pow(gainDb/20f)
        for(i in x.indices) x[i]*=gain
        SoftClip.process(x,.985f)
        TruePeakLimiter.process(x,sr,ch,s.ceilingDbtp)
    }

    private fun filter(x:FloatArray,ch:Int,b:Biquad) {
        val z1=FloatArray(ch); val z2=FloatArray(ch)
        for(i in x.indices){val c=i%ch;val v=x[i];val y=b.b0*v+z1[c];z1[c]=b.b1*v-b.a1*y+z2[c];z2[c]=b.b2*v-b.a2*y;x[i]=y}
    }
}
data class Biquad(val b0:Float,val b1:Float,val b2:Float,val a1:Float,val a2:Float) {
    companion object {
        private fun make(sr:Int,f:Float,q:Float,g:Float,type:Int):Biquad {
            val A=10f.pow(g/40f); val w=2f*PI.toFloat()*f/sr; val c=cos(w); val sn=sin(w); val alpha=sn/(2f*q)
            val v=when(type){
                0->floatArrayOf((1+c)/2f,-(1+c),(1+c)/2f,1+alpha,-2*c,1-alpha)
                1->floatArrayOf((1-c)/2f,1-c,(1-c)/2f,1+alpha,-2*c,1-alpha)
                2->{val beta=2f*sqrt(A)*alpha;floatArrayOf(1+beta,-2*c,1-beta,1+alpha/A,-2*c,1-alpha/A)}
                else->{val beta=2f*sqrt(A)*alpha;floatArrayOf(
                    A*((A+1)-(A-1)*c+beta),2*A*((A-1)-(A+1)*c),A*((A+1)-(A-1)*c-beta),
                    (A+1)+(A-1)*c+beta,-2*((A-1)+(A+1)*c),(A+1)+(A-1)*c-beta)}
            }
            return Biquad(v[0]/v[3],v[1]/v[3],v[2]/v[3],v[4]/v[3],v[5]/v[3])
        }
        fun highPass(sr:Int,f:Float,q:Float)=make(sr,f,q,0f,0)
        fun lowPass(sr:Int,f:Float,q:Float)=make(sr,f,q,0f,1)
        fun peaking(sr:Int,f:Float,q:Float,g:Float)=make(sr,f,q,g,2)
        fun highShelf(sr:Int,f:Float,q:Float,g:Float)=make(sr,f,q,g,4)
    }
}
object DeEsser {
    fun process(x:FloatArray,sr:Int,ch:Int,amount:Float){
        val hp=Biquad.highPass(sr,5200f,.707f); val lp=Biquad.lowPass(sr,11000f,.707f)
        val h1=FloatArray(ch);val h2=FloatArray(ch);val l1=FloatArray(ch);val l2=FloatArray(ch);var env=0f
        val att=exp(-1f/(.0015f*sr));val rel=exp(-1f/(.07f*sr))
        for(i in x.indices){val c=i%ch;val v=x[i]
            val hpv=hp.b0*v+h1[c];h1[c]=hp.b1*v-hp.a1*hpv+h2[c];h2[c]=hp.b2*v-hp.a2*hpv
            val band=lp.b0*hpv+l1[c];l1[c]=lp.b1*hpv-lp.a1*band+l2[c];l2[c]=lp.b2*hpv-lp.a2*band
            val m=abs(band);env=if(m>env)att*env+(1-att)*m else rel*env+(1-rel)*m
            val over=((env-.08f)/.42f).coerceAtLeast(0f);val reduction=(amount*.42f*over).coerceIn(0f,.28f);x[i]=v*(1f-reduction)
        }
    }
}
object Multiband {
    fun process(x:FloatArray,sr:Int,ch:Int,intensity:Float){
        val low=Biquad.lowPass(sr,140f,.707f);val high=Biquad.highPass(sr,4200f,.707f)
        val zl1=FloatArray(ch);val zl2=FloatArray(ch);val zh1=FloatArray(ch);val zh2=FloatArray(ch);var lowEnv=0f;var highEnv=0f
        for(i in x.indices){val c=i%ch;val v=x[i]
            val l=low.b0*v+zl1[c];zl1[c]=low.b1*v-low.a1*l+zl2[c];zl2[c]=low.b2*v-low.a2*l
            val h=high.b0*v+zh1[c];zh1[c]=high.b1*v-high.a1*h+zh2[c];zh2[c]=high.b2*v-high.a2*h
            lowEnv=.995f*lowEnv+.005f*abs(l);highEnv=.997f*highEnv+.003f*abs(h)
            val lowOver=((lowEnv-.34f)/.35f).coerceAtLeast(0f);val highOver=((highEnv-.24f)/.30f).coerceAtLeast(0f)
            val reduction=(lowOver*.16f+highOver*.08f)*intensity;x[i]*=(1f-reduction).coerceIn(.80f,1f)
        }
    }
}
object BusCompressor {
    fun process(x:FloatArray,sr:Int,thresholdDb:Float,ratio:Float,amount:Float){
        val threshold=10f.pow(thresholdDb/20f);var env=0f;val attack=exp(-1f/(.018f*sr));val release=exp(-1f/(.18f*sr))
        for(i in x.indices){val m=abs(x[i]);env=if(m>env)attack*env+(1-attack)*m else release*env+(1-release)*m
            if(env>threshold){val over=20f*log10(env/threshold);val gr=over-over/ratio;x[i]*=10f.pow(-gr*amount/20f)}
        }
    }
}
object Stereo {
    fun process(x:FloatArray,width:Float){var i=0;while(i+1<x.size){val l=x[i];val r=x[i+1];val mid=(l+r)*.5f;val side=(l-r)*.5f*width;x[i]=mid+side;x[i+1]=mid-side;i+=2}}
}
object Loudness {
    fun kWeightedDb(x:FloatArray,sr:Int,ch:Int):Float{
        val hp=Biquad.highPass(sr,38f,.5f);val shelf=Biquad.highShelf(sr,1680f,.707f,4f)
        val a1=FloatArray(ch);val a2=FloatArray(ch);val b1=FloatArray(ch);val b2=FloatArray(ch);var sum=0.0
        for(i in x.indices){val c=i%ch;val v=x[i]
            val y1=hp.b0*v+a1[c];a1[c]=hp.b1*v-hp.a1*y1+a2[c];a2[c]=hp.b2*v-hp.a2*y1
            val y2=shelf.b0*y1+b1[c];b1[c]=shelf.b1*y1-shelf.a1*y2+b2[c];b2[c]=shelf.b2*y1-shelf.a2*y2
            sum+=y2.toDouble()*y2
        }
        return (10.0*log10(max(sum/max(1,x.size),1e-12))).toFloat()
    }
}
object SoftClip { fun process(x:FloatArray,ceiling:Float){for(i in x.indices){val v=x[i];x[i]=tanh((v/ceiling).toDouble()).toFloat()*ceiling}} }
object TruePeakLimiter {
    fun process(x:FloatArray,sr:Int,ch:Int,ceilingDb:Float){
        val ceiling=10f.pow(ceilingDb/20f);var gain=1f;val releaseCoeff=exp(-1f/(.065f*sr))
        for(i in x.indices){val p=abs(x[i]);val next=if(i+ch<x.size)abs((x[i]+x[i+ch])*.5f) else p;val peak=max(p,next);val wanted=if(peak>ceiling)ceiling/peak else 1f
            gain=if(wanted<gain)wanted else releaseCoeff*gain+(1f-releaseCoeff);x[i]*=gain
        }
    }
}
object Wav {
    fun read(f:File):WavData {
        DataInputStream(BufferedInputStream(FileInputStream(f))).use { d ->
            fun u16():Int=java.lang.Short.toUnsignedInt(java.lang.Short.reverseBytes(d.readShort()))
            fun i32():Int=Integer.reverseBytes(d.readInt())
            val riff=ByteArray(4);d.readFully(riff);require(String(riff)=="RIFF"){"No es un WAV RIFF."};i32()
            val wave=ByteArray(4);d.readFully(wave);require(String(wave)=="WAVE"){"Formato WAV no válido."}
            var sr=0;var ch=0;var bits=0;var fmt=1;var data=ByteArray(0)
            while(true){val id=ByteArray(4);if(d.read(id)<4)break;val n=i32();when(String(id)){
                "fmt "->{fmt=u16();ch=u16();sr=i32();i32();u16();bits=u16();if(n>16)d.skipBytes(n-16)}
                "data"->{data=ByteArray(n);d.readFully(data)}
                else->d.skipBytes(n+(n and 1))}
                if(data.isNotEmpty())break
            }
            require(fmt==1){"El archivo debe ser WAV PCM."};require(bits==16||bits==24||bits==32){"WAV PCM 16/24/32-bit requerido."}
            val bytesPer=bits/8;val out=FloatArray(data.size/bytesPer)
            for(i in out.indices){val off=i*bytesPer;var v=0L;for(b in 0 until bytesPer)v=v or ((data[off+b].toInt() and 255).toLong() shl (8*b))
                if(bits==16)out[i]=v.toShort()/32768f else if(bits==24){val signed=if((v and 0x800000L)!=0L)v or -0x1000000L else v;out[i]=signed.toInt()/8388608f}else out[i]=v.toInt()/2147483648f}
            return WavData(sr,ch,out,bits)
        }
    }
    fun write24(f:File,sr:Int,ch:Int,x:FloatArray){
        DataOutputStream(BufferedOutputStream(FileOutputStream(f))).use { d ->val bytes=x.size*3
            fun i(v:Int) { d.writeInt(Integer.reverseBytes(v)) }
            fun s(v:Int) { d.writeShort(java.lang.Short.reverseBytes(v.toShort()).toInt()) }
            d.writeBytes("RIFF");i(36+bytes);d.writeBytes("WAVE");d.writeBytes("fmt ");i(16);s(1);s(ch);i(sr);i(sr*ch*3);s(ch*3);s(24);d.writeBytes("data");i(bytes)
            val rng=0x7FFFFF;for(v0 in x){val v=(v0.coerceIn(-1f,.9999999f)*rng).roundToInt();d.writeByte(v and 255);d.writeByte((v shr 8) and 255);d.writeByte((v shr 16) and 255)}
        }
    }
}
