package de.local.tapoviewer;

import android.content.Context;
import android.graphics.Bitmap;
import android.widget.ImageView;
import java.io.*;
import java.net.HttpURLConnection;

final class MjpegView extends ImageView {
    private volatile boolean running;
    private volatile HttpURLConnection connection;
    private Bitmap frame;
    interface Status { void update(boolean online); }
    MjpegView(Context c) { super(c); setScaleType(ScaleType.FIT_CENTER); }
    void start(Camera c,SecureCredentialsStore.Credentials auth,Status status) {
        setScaleType(c.aspectMode==1?ScaleType.CENTER_CROP:c.aspectMode==2?ScaleType.CENTER:ScaleType.FIT_CENTER);
        running=true;
        new Thread(()->{
            try {
                connection=StreamEngine.open(c,auth);
                try(InputStream in=new BufferedInputStream(connection.getInputStream())) {
                    while(running) {
                        Bitmap next=StreamEngine.readJpeg(in);
                        post(()->{ if(!running) { next.recycle(); return; } frame=next; setImageBitmap(next); status.update(true); });
                        Thread.sleep(100);
                    }
                }
            } catch(Exception e) { if(running) post(()->status.update(false)); }
            finally { if(connection!=null) connection.disconnect(); }
        },"mjpeg").start();
    }
    Bitmap snapshot() { return frame==null?null:frame.copy(Bitmap.Config.ARGB_8888,false); }
    int frameWidth(){return frame==null?0:frame.getWidth();}
    int frameHeight(){return frame==null?0:frame.getHeight();}
    void stop() { running=false; if(connection!=null) connection.disconnect(); setImageDrawable(null); frame=null; }
}
