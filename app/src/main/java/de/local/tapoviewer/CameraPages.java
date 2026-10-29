package de.local.tapoviewer;

import java.util.ArrayList;
import java.util.List;

final class CameraPages {
    static int nextLayout(int current) {
        int[] layouts={1,2,-2,4,6,9,16};
        for(int i=0;i<layouts.length;i++) if(layouts[i]==current) return layouts[(i+1)%layouts.length];
        return 1;
    }
    static List<Camera> visible(List<Camera> cameras, boolean favorites) {
        List<Camera> result=new ArrayList<>();
        for(Camera c:cameras) if(c.enabled && (!favorites || c.favorite)) result.add(c);
        return result;
    }
    static int size(int layout) { return Math.max(1, Math.abs(layout)); }
    static int count(int cameras,int layout) { return (cameras+size(layout)-1)/size(layout); }
    static int columns(int layout) { int n=size(layout); return layout==-2 || n==1?1:n<=4?2:n<=9?3:4; }
}
