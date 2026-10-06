package com.daniel.masterstudio

import java.io.*
import kotlin.math.*

data class MasterSettings(
    val intensity: Float = .72f, val targetLufs: Float = -10.5f,
    val ceilingDbtp: Float = -1.0f, val hpHz: Float = 28f,
    val lowShelfDb: Float = 0f, val mudDb: Float = -1.2f,
    val presenceDb: Float = 0.9f, val airDb: Float = 0.8f,
    val compRatio: Float = 2.0f, val compThresholdDb: Float = -18f,
    val deEss: Float = .45f, val stereoWidth: Float = 1.03f
)
data class WavData(val sampleRate:Int,val channels:Int,val samples:FloatArray,val bits:Int)

object WavProcessor {
    fun master(input:File,output:File,s:MasterSettings,progress:(Int)->Unit) {
        val w=Wav.read(input); require(w.channels in 1..2) { "Solo se admiten WAV mono o estéreo." }
        val x=w.samples; val sr=w.sampleRate
        progress(5)
        filter(x,w.channels,Biquad.highPass(sr,s.hpHz,.707f)); progress(12)
        filter(x,w.channels,Biquad.peaking(sr,250f,.75f,s.mudDb*s.intensity))
        filter(x,w.channels,Biquad.peaking(sr,3200f,.9f,s.presenceDb*s.intensity))
        filter(x,w.channels,Biquad.highShelf(sr,10500f,.65f,s.airDb*s.intensity)); progress(22)
        DeEsser.process(x,sr,w.channels,s.deEss*s.intensity); progress(34)
        Multiband.process(x,sr,w.channels,s.intensity); progress(52)
        if(w.channels==2) Stereo.process(x,sr,s.stereoWidth); progress(60)
        BusCompressor.process(x,sr,w.channels,s.compThresholdDb,s.compRatio,.8f+.6f*s.intensity); progress(70)
        val measured=Loudness.rmsDb(x); val desired=s.targetLufs+10f
        var gainDb=(desired-measured).coerceIn(-3f,8f); gainDb *= (.65f+.35f*s.intensity)
        for(i in x.indices) x[i]*=10f.pow(gainDb/20f)
        progress(80); TruePeakLimiter.process(x,sr,w.channels,s.ceilingDbtp,.995f); progress(94)
        Wav.write24(output,sr,w.channels,x); progress(100)
    }
    private fun filter(x:FloatArray,ch:Int,b:Biquad) {
        val z1=FloatArray(ch); val z2=FloatArray(ch)
        for(i in x.indices){val c=i%ch;val v=x[i];val y=b.b0*v+z1[c];z1[c]=b.b1*v-b.a1*y+z2[c];z2[c]=b.b2*v-b.a2*y;x[i]=y}
    }
}
data class Biquad(val b0:Float,val b1:Float,val b2:Float,val a1:Float,val a2:Float) {
    companion object {
        private fun make(sr:Int,f:Float,q:Float,g:Float,type:Int):Biquad {
            val A=10f.pow(g/40f);val w=2f*PI.toFloat()*f/sr;val c=cos(w);val sn=sin(w);val alpha=sn/(2f*q)
            val v = when(type){
                0->floatArrayOf((1+c)/2f,-(1+c),(1+c)/2f,1+alpha,-2*c,1-alpha)
                1->floatArrayOf((1-c)/2f,1-c,(1-c)/2f,1+alpha,-2*c,1-alpha)
                2->{val beta=2f*sqrt(A)*alpha;floatArrayOf(1+beta,-2*c,1-beta,1+alpha/A,-2*c,1-alpha/A)}
                else->{val beta=2f*sqrt(A)*alpha;floatArrayOf(A*((A+1)-(A-1)*c+beta),2*A*((A-1)-(A+1)*c),A*((A+1)-(A-1)*c-beta),(A+1)+(A-1)*c+beta,-2*((A-1)+(A+1)*c),(A+1)+(A-1)*c-beta)}
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
        val hp=Biquad.highPass(sr,5200f,.707f);val lp=Biquad.lowPass(sr,11000f,.707f)
        val h1=FloatArray(ch);val h2=FloatArray(ch);var env=0f
        val att=exp(-1f/(.0015f*sr));val rel=exp(-1f/(.07f*sr))
        for(i in x.indices){val c=i%ch;val v=x[i];val hpv=hp.b0*v+h1[c];h1[c]=hp.b1*v-hp.a1*hpv+h2[c];h2[c]=hp.b2*v-hp.a2*hpv
            val band=lp.b0*hpv;val m=abs(band);env=if(m>env)att*env+(1-att)*m else rel*env+(1-rel)*m
            val reduction=(1f-(amount*.55f)*((env-.12f).coerceAtLeast(0f)/(.55f+env))).coerceAtLeast(.65f)
            x[i]=v*(1f-(1f-reduction)*if(abs(band)>.08f)1f else 0f)
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
            val lowGr=(lowEnv-.32f).coerceAtLeast(0f)*.9f*intensity;val highGr=(highEnv-.22f).coerceAtLeast(0f)*.55f*intensity
            x[i]*=1f-lowGr-highGr
        }
    }
}
object BusCompressor {
    fun process(x:FloatArray,sr:Int,ch:Int,thresholdDb:Float,ratio:Float,amount:Float){
        val threshold=10f.pow(thresholdDb/20f);var env=0f;val attack=exp(-1f/(.012f*sr));val release=exp(-1f/(.16f*sr))
        for(i in x.indices){val m=abs(x[i]);env=if(m>env)attack*env+(1-attack)*m else release*env+(1-release)*m
            if(env>threshold){val over=20f*log10(env/threshold);val gr=over-over/ratio;x[i]*=10f.pow(-gr*amount/20f)}}
    }
}
object Stereo {
    fun process(x:FloatArray,sr:Int,width:Float){var i=0;while(i+1<x.size){val l=x[i];val r=x[i+1];val mid=(l+r)*.5f;val side=(l-r)*.5f*width
        x[i]=(mid+side).coerceIn(-1f,1f);x[i+1]=(mid-side).coerceIn(-1f,1f);i+=2}}
}
object Loudness { fun rmsDb(x:FloatArray):Float {var sum=0.0;for(v in x)sum+=v.toDouble()*v;val rms=sqrt(sum/max(1,x.size));return (20.0*log10(max(rms,1e-9))).toFloat()} }
object TruePeakLimiter {
    fun process(x:FloatArray,sr:Int,ch:Int,ceilingDb:Float,release:Float){
        val ceiling=10f.pow(ceilingDb/20f);var gain=1f
        for(i in x.indices){val p=abs(x[i]);val next=if(i+1<x.size)abs((x[i]+x[i+1])*.5f) else p;val peak=max(p,next)
            val wanted=if(peak>ceiling)ceiling/peak else 1f;gain=if(wanted<gain)wanted else min(1f,gain/release+(1f-1f/release))
            x[i]=tanh((x[i]*gain*1.01f).toDouble()).toFloat()*ceiling}
    }
}
object Wav {
    fun read(f:File):WavData {
        DataInputStream(BufferedInputStream(FileInputStream(f))).use { d ->
            fun u16():Int=java.lang.Short.toUnsignedInt(java.lang.Short.reverseBytes(d.readShort()))
            fun i32():Int=Integer.reverseBytes(d.readInt())
            val riff=ByteArray(4);d.readFully(riff);require(String(riff)=="RIFF");i32()
            val wave=ByteArray(4);d.readFully(wave);require(String(wave)=="WAVE")
            var sr=0;var ch=0;var bits=0;var fmt=1;var data=ByteArray(0)
            while(true){val id=ByteArray(4);if(d.read(id)<4)break;val n=i32();when(String(id)){
                "fmt "->{fmt=u16();ch=u16();sr=i32();i32();u16();bits=u16();if(n>16)d.skipBytes(n-16)}
                "data"->{data=ByteArray(n);d.readFully(data)}
                else->d.skipBytes(n+(n and 1))}
                if(data.isNotEmpty())break
            }
            require(fmt==1){"El archivo debe ser WAV PCM."};require(bits==16||bits==24||bits==32){"WAV 16/24/32-bit requerido."}
            val bytesPer=bits/8;val out=FloatArray(data.size/bytesPer)
            for(i in out.indices){val off=i*bytesPer;var v=0L;for(b in 0 until bytesPer)v=v or ((data[off+b].toInt() and 255).toLong() shl (8*b))
                if(bits==16)out[i]=v.toShort()/32768f else if(bits==24){val s=if((v and 0x800000L)!=0L)v or -0x1000000L else v;out[i]=s.toInt()/8388608f}else out[i]=v.toInt()/2147483648f}
            return WavData(sr,ch,out,bits)
        }
    }
    fun write24(f:File,sr:Int,ch:Int,x:FloatArray){
        DataOutputStream(BufferedOutputStream(FileOutputStream(f))).use { d ->val bytes=x.size*3
            fun i(v:Int){d.writeInt(Integer.reverseBytes(v))};fun s(v:Int){d.writeShort(java.lang.Short.reverseBytes(v.toShort()).toInt())}
            d.writeBytes("RIFF");i(36+bytes);d.writeBytes("WAVE");d.writeBytes("fmt ");i(16);s(1);s(ch);i(sr);i(sr*ch*3);s(ch*3);s(24);d.writeBytes("data");i(bytes)
            val rng=0x7FFFFF;for(v0 in x){val v=(v0.coerceIn(-1f,.9999999f)*rng).roundToInt();d.writeByte(v and 255);d.writeByte((v shr 8) and 255);d.writeByte((v shr 16) and 255)}
        }
    }
}
