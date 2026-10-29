package de.local.tapoviewer;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.*;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.concurrent.*;

/** Encodes a local preview (640x360, 5 fps, no audio) from rendered frames. */
final class PreviewRecorder {
    interface Callback { void finished(File file,String error); }
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Context context;
    private final File file;
    private final Callback callback;
    private MediaCodec encoder;
    private MediaMuxer muxer;
    private int track=-1,frames;
    private boolean started,failed;
    private String failure="";
    volatile boolean finishing,busy;
    PreviewRecorder(Context context,String name,Callback callback) {
        this.context=context.getApplicationContext(); this.file=LocalMedia.create(context,name,".mp4"); this.callback=callback;LocalMedia.pin(file);
    }
    void frame(Bitmap bitmap) {
        if(finishing||busy) { bitmap.recycle(); return; }
        busy=true;
        worker.submit(()->{ try {
            if(encoder==null) {
                String software=null;
                for(MediaCodecInfo info:new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
                    if(!info.isEncoder()) continue;
                    for(String type:info.getSupportedTypes()) if(type.equalsIgnoreCase("video/avc") &&
                            (info.getName().startsWith("c2.android.")||info.getName().startsWith("OMX.google."))) software=info.getName();
                }
                encoder=software==null?MediaCodec.createEncoderByType("video/avc"):MediaCodec.createByCodecName(software);
                MediaFormat format=MediaFormat.createVideoFormat("video/avc",640,360);
                format.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
                format.setInteger(MediaFormat.KEY_BIT_RATE,800000); format.setInteger(MediaFormat.KEY_FRAME_RATE,5); format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);
                encoder.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE); encoder.start();
                muxer=new MediaMuxer(file.getAbsolutePath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            }
            int index=encoder.dequeueInputBuffer(20000);
            if(index>=0) {
                Image image=encoder.getInputImage(index); if(image==null) throw new IllegalStateException("Encoder has no YUV input");
                Bitmap scaled=Bitmap.createScaledBitmap(bitmap,640,360,true);
                int[] pixels=new int[640*360]; scaled.getPixels(pixels,0,640,0,0,640,360);
                if(scaled!=bitmap) scaled.recycle();
                Image.Plane[] planes=image.getPlanes();
                byte[] yuv=new byte[640*360*3/2];
                for(int y=0;y<360;y++) for(int x=0;x<640;x++) {
                    int color=pixels[y*640+x],r=(color>>16)&255,g=(color>>8)&255,b=color&255;
                    yuv[y*640+x]=clamp(((66*r+129*g+25*b+128)>>8)+16);
                    if((x&1)==0&&(y&1)==0) {
                        int chroma=y/2*320+x/2;
                        yuv[640*360+chroma]=clamp(((-38*r-74*g+112*b+128)>>8)+128);
                        yuv[640*360+640*360/4+chroma]=clamp(((112*r-94*g-18*b+128)>>8)+128);
                    }
                }
                copy(planes[0],yuv,0,640,360);
                copy(planes[1],yuv,640*360,320,180);
                copy(planes[2],yuv,640*360*5/4,320,180);
                encoder.queueInputBuffer(index,0,640*360*3/2,frames++*200000L,0);
            }
            drain(false);
        } catch(Exception e) { failure=e.getClass().getSimpleName()+": "+e.getMessage(); failed=true; finish(); }
        finally { bitmap.recycle(); busy=false; } });
    }
    private static byte clamp(int value) { return (byte)Math.max(0,Math.min(255,value)); }
    private static void copy(Image.Plane p,byte[] source,int offset,int width,int height) {
        ByteBuffer buffer=p.getBuffer(); int rowStride=p.getRowStride(),pixelStride=p.getPixelStride();
        for(int y=0;y<height;y++) {
            if(pixelStride==1) { buffer.position(y*rowStride); buffer.put(source,offset+y*width,width); }
            else for(int x=0;x<width;x++) buffer.put(y*rowStride+x*pixelStride,source[offset+y*width+x]);
        }
    }
    private void drain(boolean ending) throws Exception {
        MediaCodec.BufferInfo info=new MediaCodec.BufferInfo(); int attempts=0;
        while(attempts++<(ending?80:8)) {
            int output=encoder.dequeueOutputBuffer(info,ending?10000:0);
            if(output==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { track=muxer.addTrack(encoder.getOutputFormat()); muxer.start(); started=true; }
            else if(output>=0) {
                ByteBuffer buffer=encoder.getOutputBuffer(output);
                if((info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0 && info.size>0 && started) { buffer.position(info.offset); buffer.limit(info.offset+info.size); muxer.writeSampleData(track,buffer,info); }
                encoder.releaseOutputBuffer(output,false);
                if((info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0) return;
            } else if(!ending) return;
        }
    }
    void finish() {
        if(finishing) return; finishing=true;
        worker.submit(()->{
            try {
                if(encoder!=null) {
                    int index=encoder.dequeueInputBuffer(100000);
                    if(index>=0) encoder.queueInputBuffer(index,0,0,frames*200000L,MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    drain(true);
                }
            } catch(Exception e) { failure=e.getClass().getSimpleName()+": "+e.getMessage(); failed=true; }
            finally {
                if(encoder!=null) { try { encoder.stop(); } catch(Exception ignored) { } encoder.release(); }
                if(muxer!=null) { try { if(started) muxer.stop(); } catch(Exception e) { failure="Muxer: "+e.getMessage(); failed=true; } muxer.release(); }
                if(!started||frames<2) failed=true;
                LocalMedia.unpin(file);if(failed) file.delete(); else LocalMedia.export(context,file);
                new android.os.Handler(android.os.Looper.getMainLooper()).post(()->callback.finished(failed?null:file,failed?"Aufnahme fehlgeschlagen ("+frames+" Bilder, "+failure+")":null));
                worker.shutdown();
            }
        });
    }
}
