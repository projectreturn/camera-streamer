package de.local.tapoviewer;

import androidx.media3.common.*;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.exoplayer.*;
import androidx.media3.exoplayer.source.*;
import androidx.media3.exoplayer.upstream.Allocator;
import java.lang.reflect.*;
import java.util.*;

/** Observes original compressed samples without modifying player buffers. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class RecordingSource extends WrappingMediaSource {
    private final OriginalRecorder.Tap tap;
    private final Map<MediaPeriod,MediaPeriod> periods=new IdentityHashMap<>();
    RecordingSource(MediaSource source,OriginalRecorder.Tap tap){super(source);this.tap=tap;}
    @Override public MediaPeriod createPeriod(MediaSource.MediaPeriodId id,Allocator allocator,long start){
        MediaPeriod child=mediaSource.createPeriod(id,allocator,start);MediaPeriod[] self=new MediaPeriod[1];
        MediaPeriod proxy=(MediaPeriod)Proxy.newProxyInstance(MediaPeriod.class.getClassLoader(),new Class<?>[]{MediaPeriod.class},(p,method,args)->{
            try{
                if(method.getName().equals("prepare")){MediaPeriod.Callback callback=(MediaPeriod.Callback)args[0];child.prepare(new MediaPeriod.Callback(){public void onPrepared(MediaPeriod period){callback.onPrepared(self[0]);}public void onContinueLoadingRequested(MediaPeriod period){callback.onContinueLoadingRequested(self[0]);}},(Long)args[1]);return null;}
                if(method.getName().equals("selectTracks")){SampleStream[] streams=(SampleStream[])args[2];SampleStream[] previous=streams.clone();for(int i=0;i<streams.length;i++)if(streams[i] instanceof Observed)streams[i]=((Observed)streams[i]).child;Object value=method.invoke(child,args);for(int i=0;i<streams.length;i++)if(streams[i]!=null)streams[i]=previous[i] instanceof Observed&&((Observed)previous[i]).child==streams[i]?previous[i]:new Observed(streams[i],tap);return value;}
                return method.invoke(child,args);
            }catch(InvocationTargetException e){throw e.getCause();}
        });self[0]=proxy;periods.put(proxy,child);return proxy;
    }
    @Override public void releasePeriod(MediaPeriod period){MediaPeriod child=periods.remove(period);if(child!=null)mediaSource.releasePeriod(child);}
    private static final class Observed implements SampleStream {
        final SampleStream child;final OriginalRecorder.Tap tap;Format format;
        Observed(SampleStream child,OriginalRecorder.Tap tap){this.child=child;this.tap=tap;}
        public boolean isReady(){return child.isReady();}public void maybeThrowError()throws java.io.IOException{child.maybeThrowError();}public int skipData(long position){return child.skipData(position);}public int getFlags(){return child.getFlags();}
        public int readData(FormatHolder holder,DecoderInputBuffer buffer,int flags){int result=child.readData(holder,buffer,flags);if(result==C.RESULT_FORMAT_READ){format=holder.format;tap.format(format);}else if(result==C.RESULT_BUFFER_READ && (flags&(FLAG_PEEK|FLAG_OMIT_SAMPLE_DATA))==0 && !buffer.isEndOfStream() && !buffer.isEncrypted() && format!=null && buffer.data!=null){tap.sample(format,buffer);}return result;}
    }
}
