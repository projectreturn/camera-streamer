package de.local.tapoviewer;

import android.app.*;
import android.content.*;
import android.view.*;
import android.graphics.Bitmap;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import java.lang.reflect.*;
import java.io.File;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PlayerRemoteDeviceTest {
    private Instrumentation instrumentation;
    private PlayerActivity screen;
    private Context context;
    private String testPrefix;
    @Before public void openConfiguredCamera() throws Exception {
        instrumentation=InstrumentationRegistry.getInstrumentation(); context=instrumentation.getTargetContext();
        Camera camera=null; for(Camera c:new CameraRepository(context).loadCameras()) if(c.enabled){camera=c; break;}
        Assume.assumeNotNull(camera);
        screen=(PlayerActivity)instrumentation.startActivitySync(new Intent(context,PlayerActivity.class).putExtra(PlayerActivity.EXTRA_IP,camera.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        testPrefix="RemoteKeyTest_"+UUID.randomUUID().toString().replace("-","");
        Field name=PlayerActivity.class.getDeclaredField("cameraName"); name.setAccessible(true);
        instrumentation.runOnMainSync(()->{try{name.set(screen,testPrefix);}catch(Exception e){throw new RuntimeException(e);}});
        awaitFrame();
        call("resetZoom");
    }
    @After public void finishAndRemoveOnlyTestCaptures(){
        if(screen!=null) instrumentation.runOnMainSync(screen::finish);
        if(context!=null && testPrefix!=null) for(File file:LocalMedia.list(context)) if(file.getName().startsWith(testPrefix+"_")) LocalMedia.delete(context,file);
    }
    @Test public void okShowsControlsWhileZoomConfirmationAndBackKeepTheirMeaning() throws Exception {
        assertEquals(View.GONE,((View)field("controls")).getVisibility());
        key(KeyEvent.KEYCODE_CHANNEL_UP); assertEquals(1,field("zoomIndex"));
        key(KeyEvent.KEYCODE_DPAD_CENTER); assertEquals(true,field("zoomConfirmed"));
        assertEquals(View.GONE,((View)field("controls")).getVisibility());
        key(KeyEvent.KEYCODE_DPAD_RIGHT); assertTrue((Float)field("offsetX")<0);
        key(KeyEvent.KEYCODE_BACK); assertEquals(0,field("zoomIndex"));
        key(KeyEvent.KEYCODE_DPAD_CENTER); assertEquals(View.VISIBLE,((View)field("controls")).getVisibility());
        key(KeyEvent.KEYCODE_BACK); assertEquals(View.GONE,((View)field("controls")).getVisibility()); assertFalse(screen.isFinishing());
    }
    @Test public void blueChangesStreamAndInfoStaysOpenWithoutExposingCredentials() throws Exception {
        int previous=(Integer)field("stream"); key(KeyEvent.KEYCODE_PROG_BLUE); assertNotEquals(previous,field("stream")); awaitFrame();
        key(KeyEvent.KEYCODE_INFO);
        AlertDialog dialog=(AlertDialog)field("technicalDialog"); assertNotNull(dialog); assertTrue(dialog.isShowing());
        String details=((android.widget.TextView)field("technicalText")).getText().toString();
        assertTrue(details.contains("Videodecoder")); assertTrue(details.contains("Auflösung")); assertTrue(details.contains("Puffer"));
        Camera camera=(Camera)field("camera"); SecureCredentialsStore.Credentials auth=new SecureCredentialsStore(context).load(camera.id);
        if(!auth.password.isEmpty()) assertFalse(details.contains(auth.password));
        if(!auth.username.isEmpty()) assertFalse(details.contains(auth.username));
        instrumentation.runOnMainSync(()->{dialog.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_INFO)); dialog.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_INFO));});
        instrumentation.waitForIdleSync();
        assertNull(field("technicalDialog"));
        assertEquals("rtsp://camera.local:554",StreamDiagnostics.origin(android.net.Uri.parse("rtsp://test-user:test-secret@camera.local:554/private-token?key=secret#hidden")));
    }
    @Test public void yellowSavesPhotoRedRecordsAndGreenOpensLibrary() throws Exception {
        CameraRepository originalPrefs=(CameraRepository)field("preferences");String previousMode=originalPrefs.option("record_mode","original");originalPrefs.setOption("record_mode","preview");
        try {
        key(KeyEvent.KEYCODE_PROG_YELLOW); awaitCapture(".jpg");
        key(KeyEvent.KEYCODE_PROG_RED); PreviewRecorder recorder=(PreviewRecorder)field("recorder"); assertNotNull(recorder);
        Field frames=PreviewRecorder.class.getDeclaredField("frames"); frames.setAccessible(true);
        long recordingDeadline=android.os.SystemClock.elapsedRealtime()+15000;
        while(android.os.SystemClock.elapsedRealtime()<recordingDeadline && frames.getInt(recorder)<4 && field("recorder")!=null) Thread.sleep(100);
        assertTrue("Wait for four encoded frames before stopping the test clip",frames.getInt(recorder)>=4);
        key(KeyEvent.KEYCODE_PROG_RED);
        long until=android.os.SystemClock.elapsedRealtime()+10000;
        while(android.os.SystemClock.elapsedRealtime()<until && !((android.widget.Button)field("recordButton")).isEnabled()) Thread.sleep(100);
        File clip=awaitCapture(".mp4"); assertTrue(clip.length()>100);
        Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(MediaLibraryActivity.class.getName(),null,false);
        try { key(KeyEvent.KEYCODE_PROG_GREEN); Activity media=instrumentation.waitForMonitorWithTimeout(monitor,5000); assertNotNull(media); instrumentation.runOnMainSync(media::finish); }
        finally {instrumentation.removeMonitor(monitor);}
        } finally {originalPrefs.setOption("record_mode",previousMode);}
    }
    @Test public void recordsOriginalResolutionAndSupportedAudio() throws Exception {
        OriginalRecorder.Tap tap=(OriginalRecorder.Tap)field("recordingTap");assertNotNull(tap.video);
        CameraRepository prefs=(CameraRepository)field("preferences");String previous=prefs.option("record_mode","original");prefs.setOption("record_mode","original");
        try{key(KeyEvent.KEYCODE_PROG_RED);assertNotNull(field("originalRecorder"));Thread.sleep(5000);key(KeyEvent.KEYCODE_PROG_RED);long until=android.os.SystemClock.elapsedRealtime()+12000;while(!((android.widget.Button)field("recordButton")).isEnabled()&&android.os.SystemClock.elapsedRealtime()<until)Thread.sleep(100);File file=awaitCapture(".mp4");android.media.MediaExtractor extractor=new android.media.MediaExtractor();try{extractor.setDataSource(file.getAbsolutePath());boolean video=false,audio=false;for(int i=0;i<extractor.getTrackCount();i++){android.media.MediaFormat f=extractor.getTrackFormat(i);String mime=f.getString(android.media.MediaFormat.KEY_MIME);if(mime.startsWith("video/")){video=true;assertEquals(tap.video.width,f.getInteger(android.media.MediaFormat.KEY_WIDTH));assertEquals(tap.video.height,f.getInteger(android.media.MediaFormat.KEY_HEIGHT));extractor.selectTrack(i);assertTrue(extractor.readSampleData(java.nio.ByteBuffer.allocate(2*1024*1024),0)>0);}if(mime.startsWith("audio/")){audio=true;assertEquals("audio/mp4a-latm",mime);}}assertTrue(video);assertEquals(tap.audio!=null&&!new CameraRepository(context).option("record_audio","auto").equals("none"),audio);System.out.println("Recording source audio: "+(tap.audio==null?"none":tap.audio.sampleMimeType+" / "+tap.audio.sampleRate+" Hz"));}finally{extractor.release();}android.media.MediaMetadataRetriever retriever=new android.media.MediaMetadataRetriever();try{retriever.setDataSource(file.getAbsolutePath());Bitmap image=retriever.getFrameAtTime(0);assertNotNull("Original recording must decode",image);image.recycle();}finally{retriever.release();}}
        finally{prefs.setOption("record_mode",previous);}
    }
    @Test public void convertsLiveCameraAudioToAacWithoutReencodingVideo()throws Exception {
        CameraRepository prefs=(CameraRepository)field("preferences");String previous=prefs.option("record_audio","auto");prefs.setOption("record_audio","aac");
        try{recordsOriginalResolutionAndSupportedAudio();}finally{prefs.setOption("record_audio",previous);}
    }
    @Test public void streamInfoAutoHidesAndSupportsDisabledAndPermanentModes() throws Exception {
        Context isolated=new ContextWrapper(context){
            @Override public Context getApplicationContext(){return this;}
            @Override public SharedPreferences getSharedPreferences(String name,int mode){return context.getSharedPreferences("stream_info_test_"+name,mode);}
        };
        CameraRepository prefs=new CameraRepository(isolated); prefs.streamInfoEnabled(true); prefs.streamInfoSeconds(2);
        Field settings=PlayerActivity.class.getDeclaredField("preferences"); settings.setAccessible(true);
        instrumentation.runOnMainSync(()->{try{settings.set(screen,prefs);}catch(Exception e){throw new RuntimeException(e);}});
        try {
            assertEquals(2,prefs.streamInfoSeconds());
            // Complete any initial player callbacks before checking the independent UI deadline.
            Thread.sleep(700); instrumentation.waitForIdleSync();
            call("showStreamInfo"); assertEquals(View.GONE,((View)field("cameraLabel")).getVisibility());
            key(KeyEvent.KEYCODE_DPAD_CENTER); assertEquals(View.VISIBLE,((View)field("help")).getVisibility());
            assertEquals("Wiedergabe pausieren",((android.widget.TextView)field("help")).getText().toString());
            key(KeyEvent.KEYCODE_DPAD_RIGHT); assertEquals("Vorherige Kamera",((android.widget.TextView)field("help")).getText().toString());
            Thread.sleep(2400); instrumentation.waitForIdleSync(); assertEquals(View.GONE,((View)field("help")).getVisibility()); key(KeyEvent.KEYCODE_BACK);
            Thread.sleep(2400); instrumentation.waitForIdleSync(); assertEquals(View.GONE,((View)field("cameraLabel")).getVisibility());
            assertEquals(View.GONE,((View)field("connectionBadge")).getVisibility()); assertEquals(View.GONE,((View)field("info")).getVisibility());
            key(KeyEvent.KEYCODE_INFO); assertNotNull(field("technicalDialog")); Thread.sleep(2400); instrumentation.waitForIdleSync(); assertNull(field("technicalDialog"));
            prefs.streamInfoEnabled(false); call("showStreamInfo"); key(KeyEvent.KEYCODE_INFO);
            assertEquals(View.GONE,((View)field("cameraLabel")).getVisibility()); assertNotNull(field("technicalDialog"));key(KeyEvent.KEYCODE_INFO);
            key(KeyEvent.KEYCODE_DPAD_CENTER); assertEquals(View.VISIBLE,((View)field("controls")).getVisibility()); assertEquals(View.GONE,((View)field("info")).getVisibility()); key(KeyEvent.KEYCODE_BACK);
            prefs.streamInfoEnabled(true); prefs.streamInfoSeconds(0); call("showStreamInfo"); key(KeyEvent.KEYCODE_INFO);
            Thread.sleep(2400); instrumentation.waitForIdleSync(); assertEquals(View.GONE,((View)field("cameraLabel")).getVisibility()); assertNotNull(field("technicalDialog"));
        } finally {isolated.getSharedPreferences("camera_configuration",0).edit().clear().commit();}
    }
    @Test public void longOkSavesViewportWithoutOpeningMenuAndRestoresIt() throws Exception {
        Context isolated=new ContextWrapper(context){
            @Override public Context getApplicationContext(){return this;}
            @Override public SharedPreferences getSharedPreferences(String name,int mode){return context.getSharedPreferences("viewport_test_"+name,mode);}
        };
        isolated.getSharedPreferences("camera_configuration",0).edit().clear().commit();
        CameraRepository repo=new CameraRepository(isolated);Field setting=PlayerActivity.class.getDeclaredField("preferences");setting.setAccessible(true);
        instrumentation.runOnMainSync(()->{try{setting.set(screen,repo);}catch(Exception e){throw new RuntimeException(e);}});
        try {
            key(KeyEvent.KEYCODE_CHANNEL_UP);key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_DPAD_DOWN);
            instrumentation.runOnMainSync(()->screen.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER)));
            Thread.sleep(850);
            instrumentation.runOnMainSync(()->screen.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER)));instrumentation.waitForIdleSync();
            Camera camera=(Camera)field("camera");CameraRepository.SavedViewport saved=repo.loadViewport(camera.id);
            assertNotNull(saved);assertEquals(1,saved.zoomIndex);assertTrue(saved.x<0);assertTrue(saved.y<0);
            assertEquals(View.GONE,((View)field("controls")).getVisibility());assertEquals(true,field("zoomConfirmed"));
            float x=(Float)field("offsetX"),y=(Float)field("offsetY");
            call("resetZoom");assertEquals(0,field("zoomIndex"));call("restoreSavedViewport");assertEquals(1,field("zoomIndex"));assertEquals(x,(Float)field("offsetX"),0.1f);assertEquals(y,(Float)field("offsetY"),0.1f);
            key(KeyEvent.KEYCODE_BACK);assertEquals(0,field("zoomIndex"));assertNotNull(repo.loadViewport(camera.id));assertNull(repo.loadViewport("unrelated-camera"));
        } finally {isolated.getSharedPreferences("camera_configuration",0).edit().clear().commit();}
    }
    private void call(String name) throws Exception {Method m=PlayerActivity.class.getDeclaredMethod(name); m.setAccessible(true); instrumentation.runOnMainSync(()->{try{m.invoke(screen);}catch(Exception e){throw new RuntimeException(e);}}); instrumentation.waitForIdleSync();}
    private Object field(String name) throws Exception {Field f=PlayerActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(screen);}
    private void key(int code){instrumentation.runOnMainSync(()->{screen.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,code)); screen.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,code));}); instrumentation.waitForIdleSync();}
    private void awaitFrame() throws Exception {
        Method method=PlayerActivity.class.getDeclaredMethod("currentFrame"); method.setAccessible(true);
        long until=android.os.SystemClock.elapsedRealtime()+16000;
        while(android.os.SystemClock.elapsedRealtime()<until) {
            java.util.concurrent.atomic.AtomicBoolean ready=new java.util.concurrent.atomic.AtomicBoolean();
            instrumentation.runOnMainSync(()->{try {Bitmap b=(Bitmap)method.invoke(screen); if(b!=null){ready.set(true); b.recycle();}}catch(Exception e){throw new RuntimeException(e);}});
            if(ready.get()) return; Thread.sleep(150);
        }
        fail("Configured camera did not provide a live frame");
    }
    private File awaitCapture(String extension) throws Exception {
        long until=android.os.SystemClock.elapsedRealtime()+10000;
        while(android.os.SystemClock.elapsedRealtime()<until) {for(File file:LocalMedia.list(context)) if(file.getName().startsWith(testPrefix+"_")&&file.getName().endsWith(extension)&&file.length()>0) return file; Thread.sleep(100);}
        throw new AssertionError("Test capture was not saved: "+extension);
    }
}
