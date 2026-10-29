package de.local.tapoviewer;

import android.media.*;
import androidx.media3.common.*;
import java.io.IOException;
import java.nio.*;

/** Worker-thread audio conversion. Compressed video is never decoded or re-encoded. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class AudioToAac implements AutoCloseable {
    interface Output {void format(MediaFormat format)throws Exception;void sample(ByteBuffer bytes,MediaCodec.BufferInfo info)throws Exception;}
    private final Format source;private final Output output;private MediaCodec decoder,encoder;
    private int sampleRate,channels,pcmEncoding=C.ENCODING_PCM_16BIT;private long lastInput,lastEncoderInput,firstPcmTime=Long.MIN_VALUE;private boolean encoderEnded,decoderEnded;
    AudioToAac(Format source,Output output)throws Exception{
        this.source=source;this.output=output;
        if(source.sampleRate<=0||source.channelCount<1||source.channelCount>2)throw new IOException("Tonumwandlung benötigt eine Mono- oder Stereo-Tonspur mit bekannter Abtastrate");
        if(MimeTypes.AUDIO_RAW.equals(source.sampleMimeType)){pcmEncoding=source.pcmEncoding<=0?C.ENCODING_PCM_16BIT:source.pcmEncoding;startEncoder(source.sampleRate,source.channelCount);}
        else if(MimeTypes.AUDIO_ALAW.equals(source.sampleMimeType)||MimeTypes.AUDIO_MLAW.equals(source.sampleMimeType))startEncoder(source.sampleRate,source.channelCount);
        else{MediaFormat format=MediaFormat.createAudioFormat(source.sampleMimeType,source.sampleRate,source.channelCount);for(int i=0;i<source.initializationData.size();i++)format.setByteBuffer("csd-"+i,ByteBuffer.wrap(source.initializationData.get(i)));try{decoder=MediaCodec.createDecoderByType(source.sampleMimeType);decoder.configure(format,null,null,0);decoder.start();}catch(Exception e){close();throw new IOException("Tonformat kann auf diesem Receiver nicht dekodiert werden: "+source.sampleMimeType,e);}}
    }
    private void startEncoder(int rate,int count)throws Exception{
        if(encoder!=null){if(rate!=sampleRate||count!=channels)throw new IOException("Tonformat hat sich während der Aufnahme geändert");return;}
        if(count<1||count>2)throw new IOException("Tonumwandlung unterstützt Mono und Stereo");sampleRate=rate;channels=count;
        MediaFormat format=MediaFormat.createAudioFormat(MimeTypes.AUDIO_AAC,rate,count);format.setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC);format.setInteger(MediaFormat.KEY_BIT_RATE,rate<=16000?32000:count==1?64000:128000);format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,32768);
        try{encoder=MediaCodec.createEncoderByType(MimeTypes.AUDIO_AAC);encoder.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);encoder.start();}catch(Exception e){close();throw new IOException("AAC-Tonencoder unterstützt dieses Tonformat nicht",e);}
    }
    void input(byte[] data,long pts)throws Exception{
        lastInput=pts;
        if(MimeTypes.AUDIO_ALAW.equals(source.sampleMimeType)||MimeTypes.AUDIO_MLAW.equals(source.sampleMimeType)){
            ByteBuffer pcm=ByteBuffer.allocate(data.length*2).order(ByteOrder.LITTLE_ENDIAN);boolean alaw=MimeTypes.AUDIO_ALAW.equals(source.sampleMimeType);for(byte value:data)pcm.putShort(alaw?decodeAlaw(value&255):decodeMulaw(value&255));pcm.flip();encode(pcm,pts,C.ENCODING_PCM_16BIT);return;
        }
        if(decoder==null){encode(ByteBuffer.wrap(data),pts,pcmEncoding);return;}
        int index=decoderInput();ByteBuffer buffer=decoder.getInputBuffer(index);if(buffer==null||buffer.capacity()<data.length)throw new IOException("Audioblock ist zu groß");buffer.clear();buffer.put(data);decoder.queueInputBuffer(index,0,data.length,pts,0);drainDecoder(false);
    }
    private int decoderInput()throws Exception{for(int tries=0;tries<100;tries++){int index=decoder.dequeueInputBuffer(10000);if(index>=0)return index;drainDecoder(false);}throw new IOException("Tondecoder reagiert nicht");}
    private void drainDecoder(boolean ending)throws Exception{
        MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();for(int tries=0;tries<(ending?250:100);tries++){
            int index=decoder.dequeueOutputBuffer(info,ending?10000:0);
            if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){MediaFormat format=decoder.getOutputFormat();pcmEncoding=format.containsKey(MediaFormat.KEY_PCM_ENCODING)?format.getInteger(MediaFormat.KEY_PCM_ENCODING):C.ENCODING_PCM_16BIT;startEncoder(format.getInteger(MediaFormat.KEY_SAMPLE_RATE),format.getInteger(MediaFormat.KEY_CHANNEL_COUNT));}
            else if(index>=0){try{if(info.size>0&&(info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0){ByteBuffer buffer=decoder.getOutputBuffer(index);if(buffer==null)throw new IOException("Tondecoder liefert keine Daten");buffer.position(info.offset);buffer.limit(info.offset+info.size);if(encoder==null)startEncoder(source.sampleRate,source.channelCount);encode(buffer.slice(),info.presentationTimeUs,pcmEncoding);}if((info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0){decoderEnded=true;return;}}finally{decoder.releaseOutputBuffer(index,false);}}
            else if(!ending)return;
        }
        if(ending&&!decoderEnded)throw new IOException("Tondecoder konnte nicht abgeschlossen werden");
    }
    private void encode(ByteBuffer raw,long pts,int encoding)throws Exception{
        ByteBuffer pcm=pcm16(raw,encoding);int total=pcm.remaining(),frameSize=channels*2;if(total%frameSize!=0)throw new IOException("Unvollständiger PCM-Tonblock");if(firstPcmTime==Long.MIN_VALUE&&total>0)firstPcmTime=Math.max(0,pts);int consumed=0;
        while(pcm.hasRemaining()){
            int index=-1;for(int tries=0;tries<100&&index<0;tries++){index=encoder.dequeueInputBuffer(10000);if(index<0)drainEncoder(false);}if(index<0)throw new IOException("AAC-Encoder reagiert nicht");ByteBuffer input=encoder.getInputBuffer(index);if(input==null)throw new IOException("AAC-Encoder hat keinen Eingabepuffer");input.clear();int count=Math.min(input.remaining(),pcm.remaining());count-=count%frameSize;if(count==0)throw new IOException("AAC-Eingabepuffer zu klein");int limit=pcm.limit();pcm.limit(pcm.position()+count);input.put(pcm);pcm.limit(limit);lastEncoderInput=Math.max(0,pts)+consumed/(long)frameSize*1000000L/sampleRate;encoder.queueInputBuffer(index,0,count,lastEncoderInput,0);consumed+=count;drainEncoder(false);
        }
    }
    private void drainEncoder(boolean ending)throws Exception{
        MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();for(int tries=0;tries<(ending?250:100);tries++){
            int index=encoder.dequeueOutputBuffer(info,ending?10000:0);
            if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED)output.format(encoder.getOutputFormat());
            else if(index>=0){try{if(info.size>0&&(info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0&&info.presentationTimeUs>=firstPcmTime){ByteBuffer bytes=encoder.getOutputBuffer(index);if(bytes==null)throw new IOException("AAC-Encoder liefert keine Daten");bytes.position(info.offset);bytes.limit(info.offset+info.size);MediaCodec.BufferInfo copy=new MediaCodec.BufferInfo();copy.set(0,info.size,Math.max(0,info.presentationTimeUs),info.flags&~MediaCodec.BUFFER_FLAG_END_OF_STREAM);output.sample(bytes.slice(),copy);}if((info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0){encoderEnded=true;return;}}finally{encoder.releaseOutputBuffer(index,false);}}
            else if(!ending)return;
        }
        if(ending&&!encoderEnded)throw new IOException("AAC-Tonspur konnte nicht abgeschlossen werden");
    }
    void finish()throws Exception{
        if(decoder!=null){int index=decoderInput();decoder.queueInputBuffer(index,0,0,lastInput+1,MediaCodec.BUFFER_FLAG_END_OF_STREAM);drainDecoder(true);}
        if(encoder!=null){int index=-1;for(int tries=0;tries<100&&index<0;tries++){index=encoder.dequeueInputBuffer(10000);if(index<0)drainEncoder(false);}if(index<0)throw new IOException("AAC-Encoder konnte nicht beendet werden");encoder.queueInputBuffer(index,0,0,lastEncoderInput+1,MediaCodec.BUFFER_FLAG_END_OF_STREAM);drainEncoder(true);}
    }
    static ByteBuffer pcm16(ByteBuffer input,int encoding)throws IOException{
        ByteBuffer raw=input.duplicate().order(ByteOrder.LITTLE_ENDIAN);if(encoding==C.ENCODING_PCM_16BIT)return raw;
        int bytes=encoding==C.ENCODING_PCM_8BIT?1:encoding==C.ENCODING_PCM_24BIT?3:encoding==C.ENCODING_PCM_32BIT||encoding==C.ENCODING_PCM_FLOAT?4:0;if(bytes==0||raw.remaining()%bytes!=0)throw new IOException("PCM-Tonformat wird nicht unterstützt");ByteBuffer out=ByteBuffer.allocate(raw.remaining()/bytes*2).order(ByteOrder.LITTLE_ENDIAN);
        while(raw.hasRemaining()){short sample;if(encoding==C.ENCODING_PCM_8BIT)sample=(short)(((raw.get()&255)-128)<<8);else if(encoding==C.ENCODING_PCM_FLOAT){float value=raw.getFloat();sample=(short)Math.round(Math.max(-1,Math.min(1,Float.isNaN(value)?0:value))*32767);}else if(encoding==C.ENCODING_PCM_24BIT){raw.get();int lo=raw.get()&255;sample=(short)(lo|(raw.get()<<8));}else sample=(short)(raw.getInt()>>16);out.putShort(sample);}out.flip();return out;
    }
    static short decodeAlaw(int code){int a=code^0x55;int value=(a&15)<<4;int exponent=(a&0x70)>>4;if(exponent==0)value+=8;else{value+=0x108;if(exponent>1)value<<=exponent-1;}return(short)((a&0x80)!=0?value:-value);}
    static short decodeMulaw(int code){int u=(~code)&255;int value=((u&15)<<3)+132;value<<=(u&0x70)>>4;return(short)((u&0x80)!=0?132-value:value-132);}
    @Override public void close(){if(decoder!=null){try{decoder.stop();}catch(Exception ignored){}try{decoder.release();}catch(Exception ignored){}decoder=null;}if(encoder!=null){try{encoder.stop();}catch(Exception ignored){}try{encoder.release();}catch(Exception ignored){}encoder=null;}}
}
