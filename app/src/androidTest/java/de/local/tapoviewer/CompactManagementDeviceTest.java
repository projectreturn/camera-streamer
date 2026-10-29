package de.local.tapoviewer;

import android.content.*;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.Assert.*;

/** Uses an injected isolated repository; never changes configured cameras or accounts. */
@RunWith(AndroidJUnit4.class)
public class CompactManagementDeviceTest {
    @Test public void showsEightCompleteEntriesAndKeepsDirectActionsFocusable() throws Exception {
        android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        Context base=instrumentation.getTargetContext();
        Context isolated=new ContextWrapper(base){
            @Override public Context getApplicationContext(){return this;}
            @Override public SharedPreferences getSharedPreferences(String name,int mode){return base.getSharedPreferences("compact_test_"+name,mode);}
        };
        CameraRepository repo=new CameraRepository(isolated);
        List<Camera> demo=new ArrayList<>();
        for(int i=1;i<=12;i++) demo.add(new Camera("192.0.2."+i,"Testkamera "+i,"Nur Layouttest",true));
        repo.saveCameras(demo);
        SettingsActivity activity=null;
        try {
            activity=(SettingsActivity)instrumentation.startActivitySync(new Intent(base,SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            final SettingsActivity screen=activity;
            Field repository=SettingsActivity.class.getDeclaredField("repository"); repository.setAccessible(true);
            Method refresh=SettingsActivity.class.getDeclaredMethod("refresh"); refresh.setAccessible(true);
            instrumentation.runOnMainSync(()->{try {repository.set(screen,repo); refresh.invoke(screen);} catch(Exception e){throw new RuntimeException(e);}});
            instrumentation.waitForIdleSync();
            Field listField=SettingsActivity.class.getDeclaredField("list"); listField.setAccessible(true);
            RecyclerView list=(RecyclerView)listField.get(screen);
            instrumentation.runOnMainSync(()->{
                GridLayoutManager manager=(GridLayoutManager)list.getLayoutManager();
                assertEquals(12,list.getAdapter().getItemCount());
                assertTrue("Eight complete camera entries must fit on the TV",manager.findLastCompletelyVisibleItemPosition()>=7);
                android.view.ViewGroup card=(android.view.ViewGroup)list.getChildAt(0);
                android.view.ViewGroup actions=(android.view.ViewGroup)card.getChildAt(2);
                assertEquals(5,actions.getChildCount());
                for(int i=0;i<actions.getChildCount();i++) assertTrue(actions.getChildAt(i).isFocusable());
                assertTrue(actions.getChildAt(0).requestFocus());
            });
        } finally {
            if(activity!=null) {SettingsActivity screen=activity; instrumentation.runOnMainSync(screen::finish);}
            isolated.getSharedPreferences("camera_configuration",0).edit().clear().commit();
        }
    }
}
