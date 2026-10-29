package de.local.tapoviewer;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import static org.junit.Assert.*;

/** Generates launcher-ready artwork using the app's actual vector resources and Android typography. */
@RunWith(AndroidJUnit4.class)
public class LauncherArtworkDeviceTest {
    @Test public void launcherResolvesAdaptiveIconAndRealTvBanner() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.content.pm.ActivityInfo activity=context.getPackageManager().getActivityInfo(new android.content.ComponentName(context,MainActivity.class),0);
        assertNotEquals(0,activity.icon);assertNotEquals(0,activity.banner);
        Drawable banner=activity.loadBanner(context.getPackageManager());assertTrue(banner instanceof android.graphics.drawable.BitmapDrawable);
        Bitmap image=((android.graphics.drawable.BitmapDrawable)banner).getBitmap();assertEquals(320,image.getWidth());assertEquals(180,image.getHeight());
        Drawable icon=activity.loadIcon(context.getPackageManager());assertNotNull(icon);
        if(android.os.Build.VERSION.SDK_INT>=26)assertTrue(icon instanceof android.graphics.drawable.AdaptiveIconDrawable);
        if(android.os.Build.VERSION.SDK_INT>=33)assertNotNull(((android.graphics.drawable.AdaptiveIconDrawable)icon).getMonochrome());
        assertEquals("camera streamer",context.getString(R.string.app_name));
    }
    @Test public void generateAndVerifyLauncherArtwork() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Bitmap banner=Bitmap.createBitmap(320,180,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(banner);
        paint.setShader(new LinearGradient(0,180,320,0,new int[]{Color.rgb(6,15,28),Color.rgb(16,43,62)},null,Shader.TileMode.CLAMP));canvas.drawRect(0,0,320,180,paint);paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);paint.setColor(Color.rgb(29,65,86));canvas.drawRect(0.5f,0.5f,319.5f,179.5f,paint);paint.setStyle(Paint.Style.FILL);
        Drawable foreground=context.getDrawable(R.drawable.app_icon_foreground);assertNotNull(foreground);foreground.setBounds(-18,7,148,173);foreground.draw(canvas);
        paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));paint.setTextSize(27);paint.setColor(Color.rgb(245,251,255));canvas.drawText("camera",132,84,paint);
        paint.setColor(ViewerStyle.ACCENT);canvas.drawText("streamer",132,116,paint);
        save(context,banner,"camera-streamer-banner.png");assertEquals(320,banner.getWidth());assertEquals(180,banner.getHeight());Bitmap packaged=((android.graphics.drawable.BitmapDrawable)context.getDrawable(R.drawable.tv_banner)).getBitmap();assertTrue("Packaged TV banner must match the clean artwork without a subtitle",banner.sameAs(packaged));banner.recycle();
        Bitmap icon=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888);canvas=new Canvas(icon);
        paint.setShader(new LinearGradient(0,512,512,0,new int[]{Color.rgb(6,15,28),Color.rgb(16,43,62)},null,Shader.TileMode.CLAMP));canvas.drawRoundRect(0,0,512,512,112,112,paint);paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setColor(Color.rgb(29,65,86));canvas.drawRoundRect(1,1,511,511,111,111,paint);paint.setStyle(Paint.Style.FILL);
        foreground.setBounds(-40,-40,552,552);foreground.draw(canvas);save(context,icon,"camera-streamer-app-icon.png");icon.recycle();
        Drawable launcher=context.getPackageManager().getApplicationIcon(context.getPackageName());assertNotNull(launcher);
        if(android.os.Build.VERSION.SDK_INT>=26)assertTrue(launcher instanceof android.graphics.drawable.AdaptiveIconDrawable);
    }
    private static void save(Context context,Bitmap bitmap,String name) throws Exception {
        try(OutputStream out=new FileOutputStream(new File(context.getCacheDir(),name))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}
    }
}
