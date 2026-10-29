package de.local.tapoviewer;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class FeatureDeviceTest {
    private Context isolated;
    @Before public void setup() {
        Context base=InstrumentationRegistry.getInstrumentation().getTargetContext();
        isolated=new ContextWrapper(base){
            @Override public Context getApplicationContext() { return this; }
            @Override public SharedPreferences getSharedPreferences(String name,int mode) { return base.getSharedPreferences("feature_test_"+name,mode); }
            @Override public File getExternalFilesDir(String type) { File dir=new File(base.getCacheDir(),"feature_test_media"); dir.mkdirs(); return dir; }
        };
        for(String name:new String[]{"camera_configuration","secure_camera_settings","media_exports"}) isolated.getSharedPreferences(name,0).edit().clear().commit();
    }
    @Test public void migratesOldCamerasAndPersistsNewOptions() throws Exception {
        isolated.getSharedPreferences("camera_configuration",0).edit().putString("cameras","[{\"ip\":\"192.0.2.31\",\"name\":\"Testkamera\",\"enabled\":true}]").commit();
        CameraRepository repo=new CameraRepository(isolated); Camera c=repo.loadCameras().get(0);
        assertEquals("192.0.2.31",c.id); assertEquals("/stream1",c.mainPath); assertEquals("TCP",c.transport);
        c.favorite=true; c.transport="Auto"; c.protocol="HLS"; c.port=8080;
        repo.upsert(c); Camera loaded=repo.find(c.id);
        assertTrue(loaded.favorite); assertEquals("Auto",loaded.transport); assertEquals("HLS",loaded.protocol); assertEquals(8080,loaded.port);
    }
    @Test public void encryptsEachAccountAndFullUrlsSeparately() throws Exception {
        SecureCredentialsStore secure=new SecureCredentialsStore(isolated);
        secure.save("one","camera-user-one","secret-password-one"); secure.save("two","camera-user-two","secret-password-two");
        secure.saveSecret("one_url","rtsp://user:secret@192.0.2.1/live");
        assertEquals("secret-password-one",secure.load("one").password); assertEquals("camera-user-two",secure.load("two").username);
        assertTrue(secure.loadSecret("one_url").contains("rtsp://"));
        String raw=isolated.getSharedPreferences("secure_camera_settings",0).getAll().toString();
        assertFalse(raw.contains("camera-user")); assertFalse(raw.contains("secret-password")); assertFalse(raw.contains("rtsp://"));
        secure.remove("one"); assertFalse(secure.hasCredentials("one")); assertTrue(secure.hasCredentials("two"));
    }
    @Test public void persistsDisplayAndScrollingWithoutChangingCameras() {
        CameraRepository repo=new CameraRepository(isolated);
        assertFalse(repo.autoScroll()); assertEquals(15,repo.scrollSeconds()); assertFalse(repo.favoritesOnly());
        repo.setTileCount(-2); repo.autoScroll(true); repo.scrollSeconds(30); repo.favoritesOnly(true);
        CameraRepository reopened=new CameraRepository(isolated);
        assertEquals(-2,reopened.getTileCount()); assertTrue(reopened.autoScroll()); assertEquals(30,reopened.scrollSeconds()); assertTrue(reopened.favoritesOnly());
        assertTrue(reopened.loadCameras().isEmpty()); repo.scrollSeconds(0); assertEquals(5,repo.scrollSeconds());
        assertTrue(repo.streamInfoEnabled()); assertEquals(5,repo.streamInfoSeconds());
        repo.streamInfoEnabled(false); repo.streamInfoSeconds(15);
        assertFalse(new CameraRepository(isolated).streamInfoEnabled()); assertEquals(15,new CameraRepository(isolated).streamInfoSeconds());
        repo.streamInfoSeconds(0); assertEquals(0,repo.streamInfoSeconds()); repo.streamInfoSeconds(-1); assertEquals(2,repo.streamInfoSeconds());
    }
    @Test public void preservesStreamQueriesAndCustomUrls() {
        Camera c=new Camera("192.0.2.10","Test"); c.port=8554;
        c.mainPath="/cam/realmonitor?channel=2&subtype=0"; c.subPath="/cam/realmonitor?channel=2&subtype=1";
        SecureCredentialsStore.Credentials auth=new SecureCredentialsStore.Credentials("dummy","dummy");
        assertEquals("rtsp://192.0.2.10:8554/cam/realmonitor?channel=2&subtype=1",StreamEngine.uri(c,auth,2).toString());
        c.protocol="HLS"; c.playbackUrl="https://camera.local/live/playlist.m3u8?token=example";
        assertEquals(c.playbackUrl,StreamEngine.uri(c,auth,1).toString());
        c.subPlaybackUrl="https://camera.local/sub/playlist.m3u8";
        assertEquals(c.subPlaybackUrl,StreamEngine.uri(c,auth,2).toString());
    }
    @Test public void storesZoomViewPerCameraAndRemovesOnlyTheRequestedView() {
        CameraRepository repo=new CameraRepository(isolated);
        assertNull(repo.loadViewport("first"));repo.saveViewport("first",3,-0.4f,0.7f);repo.saveViewport("second",1,0.2f,-0.1f);
        CameraRepository reopened=new CameraRepository(isolated);CameraRepository.SavedViewport saved=reopened.loadViewport("first");
        assertNotNull(saved);assertEquals(3,saved.zoomIndex);assertEquals(-0.4f,saved.x,0.001f);assertEquals(0.7f,saved.y,0.001f);
        repo.saveViewport("first",5,-20,20);assertEquals(-1,repo.loadViewport("first").x,0.001f);assertEquals(1,repo.loadViewport("first").y,0.001f);
        repo.clearViewport("first");assertNull(repo.loadViewport("first"));assertNotNull(repo.loadViewport("second"));
        repo.remove("second");assertNull(repo.loadViewport("second"));
        try{repo.saveViewport("first",1,Float.NaN,0);fail("Invalid viewport accepted");}catch(IllegalArgumentException expected){}
    }
    @Test public void rendersIconButtonsWithoutDependingOnFontSymbols() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            android.widget.Button media=Ui.iconButton(isolated,"media","Aufnahmen und Fotos öffnen",()->{});
            assertTrue(media instanceof IconButton);assertEquals("",media.getText().toString());assertEquals("Aufnahmen und Fotos öffnen",media.getContentDescription().toString());
            assertTrue(Ui.button(isolated,"✎",()->{}) instanceof IconButton);
            android.widget.Button playerIcon=Ui.button(isolated,"▶ / Ⅱ",()->{});playerIcon.setSingleLine(true);playerIcon.layout(0,0,120,88);playerIcon.scrollTo(-40,120);
            assertEquals(0,playerIcon.getScrollX());assertEquals(0,playerIcon.getScrollY());
            Bitmap control=Bitmap.createBitmap(120,88,Bitmap.Config.ARGB_8888);playerIcon.draw(new android.graphics.Canvas(control));
            int[] buttonPixels=new int[120*88];control.getPixels(buttonPixels,0,120,0,0,120,88);boolean visible=false;for(int pixel:buttonPixels)if(android.graphics.Color.red(pixel)>180&&android.graphics.Color.green(pixel)>180&&android.graphics.Color.blue(pixel)>180){visible=true;break;}control.recycle();assertTrue("Player icon remains visible after text-layout scrolling",visible);
            for(String icon:new String[]{"media","heart","gear","playpause","previous","next","quality","zoomIn","zoomOut","reset","photo","record","refresh","edit","trash","move"}){
                Bitmap bitmap=Bitmap.createBitmap(48,48,Bitmap.Config.ARGB_8888);android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);canvas.scale(2,2);
                android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);paint.setColor(android.graphics.Color.WHITE);StreamIcon.draw(canvas,paint,icon,false);
                int[] pixels=new int[48*48];bitmap.getPixels(pixels,0,48,0,0,48,48);boolean drawn=false;for(int pixel:pixels)if(android.graphics.Color.alpha(pixel)>0){drawn=true;break;}bitmap.recycle();assertTrue("Icon must render: "+icon,drawn);
            }
        });
    }
    @Test public void decodesMultipartJpegAndRejectsInvalidFrames() throws Exception {
        Bitmap bitmap=Bitmap.createBitmap(32,24,Bitmap.Config.ARGB_8888); bitmap.eraseColor(android.graphics.Color.RED);
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream(); bytes.write("--frame\r\nContent-Type: image/jpeg\r\n\r\n".getBytes()); bitmap.compress(Bitmap.CompressFormat.JPEG,85,bytes); bitmap.recycle();
        Bitmap decoded=StreamEngine.readJpeg(new java.io.ByteArrayInputStream(bytes.toByteArray())); assertEquals(32,decoded.getWidth()); assertEquals(24,decoded.getHeight()); decoded.recycle();
        try { StreamEngine.readJpeg(new java.io.ByteArrayInputStream("not a jpeg".getBytes())); fail("Invalid frame accepted"); } catch(java.io.EOFException expected) { }
    }
    @Test public void opensAuthenticatedMjpegOverHttp() throws Exception {
        java.net.ServerSocket server=new java.net.ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1"));
        java.util.concurrent.atomic.AtomicReference<String> request=new java.util.concurrent.atomic.AtomicReference<>("");
        Thread response=new Thread(()-> {
            try(java.net.Socket socket=server.accept()) {
                java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream()));
                StringBuilder headers=new StringBuilder(); String line;
                while((line=reader.readLine())!=null&&!line.isEmpty()) headers.append(line).append('\n'); request.set(headers.toString());
                Bitmap image=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888); java.io.ByteArrayOutputStream jpeg=new java.io.ByteArrayOutputStream(); image.compress(Bitmap.CompressFormat.JPEG,90,jpeg); image.recycle();
                java.io.OutputStream out=socket.getOutputStream(); out.write(("HTTP/1.1 200 OK\r\nContent-Type: multipart/x-mixed-replace; boundary=frame\r\n\r\n--frame\r\nContent-Type: image/jpeg\r\n\r\n").getBytes()); out.write(jpeg.toByteArray()); out.flush();
            } catch(Exception e) { request.set("error"); }
        }); response.start();
        try {
            Camera c=new Camera("127.0.0.1","Test"); c.protocol="MJPEG"; c.port=server.getLocalPort(); c.mainPath="/video";
            SecureCredentialsStore.Credentials auth=new SecureCredentialsStore.Credentials("test-user","test-pass");
            StreamEngine.checkMjpeg(c,auth); response.join(2000);
            assertTrue(request.get().contains("GET /video")); assertTrue(request.get().contains(StreamEngine.basic(auth)));
        } finally { server.close(); }
    }
    @Test public void parsesOnvifAndRejectsExternalEntities() throws Exception {
        java.lang.reflect.Method parse=OnvifClient.class.getDeclaredMethod("parse",String.class); parse.setAccessible(true);
        org.w3c.dom.Document doc=(org.w3c.dom.Document)parse.invoke(null,"<s:Envelope xmlns:s='http://www.w3.org/2003/05/soap-envelope'><s:Body><Uri>rtsp://camera/live</Uri></s:Body></s:Envelope>");
        assertEquals("rtsp://camera/live",OnvifClient.text(doc,"Uri"));
        try { parse.invoke(null,"<!DOCTYPE x [<!ENTITY ext SYSTEM 'file:///data/local/tmp/secret'>]><x>&ext;</x>"); fail("External entity accepted"); }
        catch(java.lang.reflect.InvocationTargetException expected) { assertTrue(expected.getCause() instanceof java.io.IOException); }
    }
    @Test public void recordsPlayablePreviewMp4() throws Exception {
        CountDownLatch done=new CountDownLatch(1); File[] result={null}; String[] error={null};
        PreviewRecorder recorder=new PreviewRecorder(isolated,"feature_test",(file,message)->{result[0]=file;error[0]=message;done.countDown();});
        for(int i=0;i<10;i++) {
            Bitmap frame=Bitmap.createBitmap(640,360,Bitmap.Config.ARGB_8888); frame.eraseColor(i%2==0?android.graphics.Color.BLUE:android.graphics.Color.GREEN); recorder.frame(frame);
            long deadline=System.currentTimeMillis()+10000;
            while(recorder.busy && System.currentTimeMillis()<deadline) Thread.sleep(25);
            Thread.sleep(200);
        }
        recorder.finish(); assertTrue("Encoder timed out",done.await(15,TimeUnit.SECONDS)); assertNull(error[0]); assertNotNull(result[0]);
        android.media.MediaMetadataRetriever retriever=new android.media.MediaMetadataRetriever(); retriever.setDataSource(result[0].getAbsolutePath());
        assertEquals("640",retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
        assertEquals("360",retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
        Bitmap decoded=retriever.getFrameAtTime(); assertNotNull("MP4 must decode",decoded); decoded.recycle(); retriever.release();
        LocalMedia.delete(isolated,result[0]);
    }
}
