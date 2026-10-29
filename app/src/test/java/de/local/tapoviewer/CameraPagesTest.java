package de.local.tapoviewer;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class CameraPagesTest {
    @Test public void layoutsCycleThroughEveryViewAndWrap() {
        int[] expected={1,2,-2,4,6,9,16,1};
        for(int i=0;i<expected.length-1;i++)assertEquals(expected[i+1],CameraPages.nextLayout(expected[i]));
        assertEquals(1,CameraPages.nextLayout(123));
    }
    @Test public void everyEnabledCameraAppearsExactlyOnceAcrossPages() {
        for(int layout:new int[]{1,2,-2,4,6,9,16}) for(int total=0;total<70;total++) {
            int pages=CameraPages.count(total,layout),size=CameraPages.size(layout),seen=0;
            for(int p=0;p<pages;p++) for(int i=0;i<size;i++) if(p*size+i<total) seen++;
            assertEquals(total,seen);
        }
    }
    @Test public void favoritesExcludeDisabledAndKeepOrder() {
        Camera a=new Camera("192.0.2.1","A"),b=new Camera("192.0.2.2","B"),c=new Camera("192.0.2.3","C");
        a.favorite=true; c.favorite=true; c.enabled=false;
        assertEquals(Arrays.asList(a,b),CameraPages.visible(Arrays.asList(a,b,c),false));
        assertEquals(Collections.singletonList(a),CameraPages.visible(Arrays.asList(a,b,c),true));
        assertEquals(1,CameraPages.columns(-2)); assertEquals(3,CameraPages.columns(6));
    }
}
