package de.local.tapoviewer;

import android.content.Context;
import android.media.*;
import androidx.media3.common.*;
import androidx.media3.decoder.DecoderInputBuffer;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Copies original H.264/H.265 video; copies AAC or converts supported audio to AAC. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class OriginalRecorder {
    static final class Tap {
        volatile Format video,audio;volatile OriginalRecorder recorder;
        void format(Format format){if(format==null)return;if(MimeTypes.isVideo(format.sampleMimeType))video=format;else if(MimeTypes.isAudio(format.sampleMimeType))audio=format;}
        void sample(Format format,DecoderInputBuffer buffer){OriginalRecorder current=recorder;if(current!=null)current.accept(format,buffer);}
    }
    private static final class Sample {
        final byte[] bytes;final long time;final boolean video,key;
        Sample(byte[] bytes,long time,boolean video,boolean key){this.bytes=bytes;this.time=time;this.video=video;this.key=key;}
    }
    private final Context context;private final File file;private final PreviewRecorder.Callback callback;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private final AtomicLong queued=new AtomicLong();
    private final Format video,audio;private final boolean converting;private final ArrayDeque<Sample> pending=new ArrayDeque<>();
    private AudioToAac converter;private MediaMuxer muxer;private int videoTrack,audioTrack=-1,frames,pendingBytes,audioFrames;
    private long startUs=Long.MIN_VALUE,lastVideo=-1,lastAudio=-1,lastSpaceCheck;
    private volatile boolean finishing;private volatile String failure;
    OriginalRecorder(Context context,String name,Tap tap,PreviewRecorder.Callback callback)throws Exception{
        this.context=context.getApplicationContext();this.callback=callback;video=tap.video;
        if(video==null||(!MimeTypes.VIDEO_H264.equals(video.sampleMimeType)&&!MimeTypes.VIDEO_H265.equals(video.sampleMimeType)))throw new IOException("Originalaufnahme benötigt H.264 oder H.265. Vorschau ist in den Einstellungen wählbar.");
        String mode=new CameraRepository(context).option("record_audio","auto");
        audio=mode.equals("none")?null:tap.audio;
        converting=audio!=null&&(mode.equals("aac")||!MimeTypes.AUDIO_AAC.equals(audio.sampleMimeType));
        LocalMedia.ensureSpace(context,16*1048576L);file=LocalMedia.create(context,name,".mp4");LocalMedia.pin(file);
        worker.execute(()->{try{
            if(converting)converter=new AudioToAac(audio,new AudioToAac.Output(){
                public void format(MediaFormat format)throws Exception{openMuxer(format);}
                public void sample(ByteBuffer bytes,MediaCodec.BufferInfo info)throws Exception{byte[] data=new byte[bytes.remaining()];bytes.get(data);write(new Sample(data,info.presentationTimeUs,false,false));}
            });
            else openMuxer(audio==null?null:mediaFormat(audio));
        }catch(Exception e){failure="Ton/Aufnahme konnte nicht gestartet werden: "+safeMessage(e);finish();}});
    }
    boolean hasAudio(){return audio!=null;}
    String audioDescription(){return audio==null?"ohne Ton":converting?"Ton wird nach AAC umgewandelt":"AAC-Ton wird übernommen";}
    private static String safeMessage(Exception error){return error.getMessage()==null?"Codecfehler":error.getMessage();}
    private static MediaFormat mediaFormat(Format format){MediaFormat result=MimeTypes.isVideo(format.sampleMimeType)?MediaFormat.createVideoFormat(format.sampleMimeType,format.width,format.height):MediaFormat.createAudioFormat(format.sampleMimeType,format.sampleRate,format.channelCount);for(int i=0;i<format.initializationData.size();i++)result.setByteBuffer("csd-"+i,ByteBuffer.wrap(format.initializationData.get(i)));if(format.frameRate>0)result.setFloat(MediaFormat.KEY_FRAME_RATE,format.frameRate);return result;}
    private void openMuxer(MediaFormat sound)throws Exception{
        if(muxer!=null)return;
        MediaMuxer created=new MediaMuxer(file.getAbsolutePath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        try{audioTrack=-1;videoTrack=created.addTrack(mediaFormat(video));if(sound!=null)audioTrack=created.addTrack(sound);created.start();muxer=created;}
        catch(Exception e){created.release();throw e;}
        while(!pending.isEmpty())write(pending.removeFirst());pendingBytes=0;
    }
    private void write(Sample sample)throws Exception{
        if(muxer==null){pendingBytes+=sample.bytes.length;if(pendingBytes>8*1048576)throw new IOException("Tonencoder liefert noch keine AAC-Daten");pending.addLast(sample);return;}
        if(!sample.video&&audioTrack<0)return;
        long previous=sample.video?lastVideo:lastAudio;long pts=Math.max(Math.max(0,sample.time),previous+1);
        MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();info.set(0,sample.bytes.length,pts,sample.video&&sample.key?MediaCodec.BUFFER_FLAG_KEY_FRAME:0);
        muxer.writeSampleData(sample.video?videoTrack:audioTrack,ByteBuffer.wrap(sample.bytes),info);
        if(sample.video){frames++;lastVideo=pts;}else{audioFrames++;lastAudio=pts;}
    }
    private synchronized void accept(Format format,DecoderInputBuffer buffer){
        if(finishing||failure!=null)return;boolean isVideo=MimeTypes.isVideo(format.sampleMimeType);if(!isVideo&&audio==null)return;
        if(!format.sampleMimeType.equals(isVideo?video.sampleMimeType:audio.sampleMimeType)){failure="Streamformat hat sich geändert";finish();return;}
        ByteBuffer source=buffer.data.duplicate();int size=source.position();if(size<=0)return;
        if(queued.addAndGet(size)>32*1048576L){queued.addAndGet(-size);failure="Speicher oder Tonencoder zu langsam für Originalaufnahme";finish();return;}
        source.flip();byte[] data=new byte[size];source.get(data);long time=buffer.timeUs;boolean key=buffer.isKeyFrame();
        try{worker.execute(()->{try{
            if(failure!=null)return;
            if(startUs==Long.MIN_VALUE){if(!isVideo||!key)return;startUs=time;}
            long pts=time-startUs;if(pts<0)return;
            if(android.os.SystemClock.elapsedRealtime()-lastSpaceCheck>3000){LocalMedia.ensureSpace(context,8*1048576L);lastSpaceCheck=android.os.SystemClock.elapsedRealtime();}
            if(!isVideo&&converter!=null)converter.input(data,pts);else write(new Sample(data,pts,isVideo,key));
        }catch(Exception e){failure="Aufnahme unterbrochen: "+safeMessage(e);finish();}finally{queued.addAndGet(-size);}});
        }catch(RejectedExecutionException ignored){queued.addAndGet(-size);}
    }
    synchronized void finish(){
        if(finishing)return;finishing=true;
        worker.execute(()->{
            boolean valid=false;
            try{
                if(converter!=null&&failure==null)converter.finish();
                if(muxer==null&&!pending.isEmpty()){if(failure==null)failure="Keine verwendbare Tonspur empfangen";openMuxer(null);}
                if(muxer!=null){muxer.stop();valid=frames>=2;}
                if(audio!=null&&audioFrames==0&&failure==null)failure="Tonspur enthielt keine verwendbaren Audiodaten";
            }catch(Exception e){if(failure==null)failure="Aufnahme konnte nicht abgeschlossen werden: "+safeMessage(e);try{if(muxer==null&&!pending.isEmpty())openMuxer(null);if(muxer!=null){muxer.stop();valid=frames>=2;}}catch(Exception ignored){}}
            finally{
                if(converter!=null)converter.close();
                if(muxer!=null)try{muxer.release();}catch(Exception ignored){}
                pending.clear();LocalMedia.unpin(file);
                if(frames<2&&failure==null)failure="Noch kein vollständiges Kamerabild für die Aufnahme empfangen";
                if(!valid)file.delete();else LocalMedia.export(context,file);
                String error=failure==null?null:valid?"Aufnahme mit Hinweis gespeichert · "+failure:failure;File result=valid?file:null;
                new android.os.Handler(android.os.Looper.getMainLooper()).post(()->callback.finished(result,error));worker.shutdown();
            }
        });
    }
}
