package de.local.tapoviewer;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class SettingsAndAudioDeviceTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private final Context target=instrumentation.getTargetContext();
    @Test public void everySettingsTabRendersAndCanBeSelected()throws Exception{
        AppSettingsActivity screen=(AppSettingsActivity)instrumentation.startActivitySync(new Intent(target,AppSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try{Method show=AppSettingsActivity.class.getDeclaredMethod("showTab",int.class);show.setAccessible(true);for(int index=0;index<5;index++){final int tab=index;instrumentation.runOnMainSync(()->{try{show.invoke(screen,tab);}catch(Exception e){throw new RuntimeException(e);}});instrumentation.waitForIdleSync();Button[] tabs=(Button[])field(screen,"tabs");for(int i=0;i<tabs.length;i++)assertEquals(i==index,tabs[i].isSelected());instrumentation.runOnMainSync(()->{View root=screen.getWindow().getDecorView();Bitmap image=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);root.draw(new Canvas(image));try(OutputStream out=new FileOutputStream(new File(target.getCacheDir(),"settings-4.1-tab-"+tab+".png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}catch(Exception e){throw new RuntimeException(e);}finally{image.recycle();}});}}
        finally{instrumentation.runOnMainSync(screen::finish);}
    }
    @Test public void onlyOneVisibleGridPlayerCanBeAudible()throws Exception{
        List<Camera> cameras=CameraPages.visible(new CameraRepository(target).loadCameras(),false);cameras.removeIf(c->"MJPEG".equals(c.protocol));Assume.assumeTrue(cameras.size()>=2);
        Context isolated=new ContextWrapper(target){public Context getApplicationContext(){return this;}public SharedPreferences getSharedPreferences(String name,int mode){return target.getSharedPreferences("grid_audio_test_"+name,mode);}};
        isolated.getSharedPreferences("camera_configuration",0).edit().clear().commit();CameraRepository repo=new CameraRepository(isolated);repo.saveCameras(cameras.subList(0,2));repo.setSetupComplete(true);repo.setTileCount(2);
        MainActivity screen=(MainActivity)instrumentation.startActivitySync(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));instrumentation.waitForIdleSync();
        try{Field repository=MainActivity.class.getDeclaredField("repo");repository.setAccessible(true);instrumentation.runOnMainSync(()->{try{repository.set(screen,repo);}catch(Exception e){throw new RuntimeException(e);}});call(screen,"reload");List<?> streams=(List<?>)field(screen,"streams");assertEquals(2,streams.size());
            repo.setOption("grid_audio","camera:"+cameras.get(0).id);call(screen,"applyAudio");assertVolumes(streams,0);
            repo.setOption("grid_audio","camera:"+cameras.get(1).id);call(screen,"applyAudio");assertVolumes(streams,1);
            repo.setOption("grid_audio","off");call(screen,"applyAudio");assertVolumes(streams,-1);
            repo.setOption("grid_audio","focus");Field focus=MainActivity.class.getDeclaredField("focusedCamera");focus.setAccessible(true);instrumentation.runOnMainSync(()->{try{focus.set(screen,cameras.get(1).id);}catch(Exception e){throw new RuntimeException(e);}});call(screen,"applyAudio");assertVolumes(streams,1);
            repo.setTileCount(1);repo.setOption("grid_audio","camera:"+cameras.get(1).id);call(screen,"reload");streams=(List<?>)field(screen,"streams");assertEquals(1,streams.size());assertVolumes(streams,-1);
        }finally{instrumentation.runOnMainSync(screen::finish);isolated.getSharedPreferences("camera_configuration",0).edit().clear().commit();}
    }
    private void assertVolumes(List<?> streams,int owner){instrumentation.runOnMainSync(()->{for(int i=0;i<streams.size();i++)try{androidx.media3.exoplayer.ExoPlayer player=(androidx.media3.exoplayer.ExoPlayer)field(streams.get(i),"player");assertNotNull(player);assertEquals(i==owner?0.7f:0f,player.getVolume(),0.001f);}catch(Exception e){throw new RuntimeException(e);}});}
    @Test public void browserTransferRejectsWrongTokenAndRoundTripsEncryptedBytes()throws Exception{
        byte[] encrypted=BackupCodec.encrypt("{\"test\":true}","transfer-test-password".toCharArray());
        try(BackupTransfer download=new BackupTransfer("127.0.0.1",encrypted,bytes->{})){
            HttpURLConnection page=(HttpURLConnection)new URL(download.url()).openConnection();assertEquals(200,page.getResponseCode());page.disconnect();
            HttpURLConnection wrong=(HttpURLConnection)new URL(download.url()+"-wrong").openConnection();assertEquals(404,wrong.getResponseCode());wrong.disconnect();
            HttpURLConnection file=(HttpURLConnection)new URL(download.url()+"/download").openConnection();try(InputStream in=file.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);assertArrayEquals(encrypted,bytes.toByteArray());}finally{file.disconnect();}
        }
        CountDownLatch received=new CountDownLatch(1);byte[][] result=new byte[1][];
        try(BackupTransfer upload=new BackupTransfer("127.0.0.1",null,bytes->{result[0]=bytes;received.countDown();})){
            HttpURLConnection request=(HttpURLConnection)new URL(upload.url()).openConnection();request.setRequestMethod("POST");request.setDoOutput(true);request.setFixedLengthStreamingMode(encrypted.length);try(OutputStream out=request.getOutputStream()){out.write(encrypted);}assertEquals(200,request.getResponseCode());request.disconnect();assertTrue(received.await(3,TimeUnit.SECONDS));assertArrayEquals(encrypted,result[0]);
        }
    }
    private static Object field(Object instance,String name)throws Exception{Field field=instance.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(instance);}
    private void call(Object instance,String name)throws Exception{Method method=instance.getClass().getDeclaredMethod(name);method.setAccessible(true);instrumentation.runOnMainSync(()->{try{method.invoke(instance);}catch(Exception e){throw new RuntimeException(e);}});instrumentation.waitForIdleSync();}
}
