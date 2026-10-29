package de.local.tapoviewer;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Explicit read-only integration probe; never changes saved camera data. */
@RunWith(AndroidJUnit4.class)
public class OnvifCameraProbe {
    @Test public void inspectConfiguredCameras() {
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        for(Camera camera:new CameraRepository(context).loadCameras()) {
            if(!camera.enabled) continue;
            String result;
            try {
                java.util.List<OnvifClient.Profile> profiles=OnvifClient.profiles(camera,new SecureCredentialsStore(context).load(camera.id));
                result=camera.ip+": "+profiles.size()+" ONVIF-Profile, PTZ="+profiles.stream().anyMatch(p->!p.ptzUrl.isEmpty());
            } catch(Exception e) { result=camera.ip+": "+e.getClass().getSimpleName()+" "+e.getMessage(); }
            android.os.Bundle output=new android.os.Bundle(); output.putString("stream",result+"\n");
            InstrumentationRegistry.getInstrumentation().sendStatus(0,output);
        }
    }
}
