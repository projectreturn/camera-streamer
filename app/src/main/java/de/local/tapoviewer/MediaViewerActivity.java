package de.local.tapoviewer;

import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

/** Local photo/video playback with the same icon language and zoom keys as live streams. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class MediaViewerActivity extends ViewerActivity {
    private FrameLayout root;private PlayerView video;private ImageView photo;
    private LinearLayout chrome,buttons,timeline;private TextView title,time,hint,error;private SeekBar seek;private Button play,mute;private CameraRepository preferences;
    private ExoPlayer player;private File file;private List<File> files;private int index,zoom,generation;
    private float x,y;private boolean running,muted,playing=true,dragging,confirm=true,okCaptured;private long position;private Bitmap image;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final float[] levels={1,1.5f,2,3,4,5};
    private android.window.OnBackInvokedCallback backCallback;
    private final Runnable hide=this::hideControls;
    private final Runnable hideHint=()->{if(hint!=null)hint.setVisibility(View.GONE);};
    private final Runnable tick=new Runnable(){public void run(){if(!running)return;if(player!=null){long duration=player.getDuration();if(duration>0&&!dragging){seek.setMax((int)Math.min(Integer.MAX_VALUE,duration));seek.setProgress((int)player.getCurrentPosition());time.setText(clock(player.getCurrentPosition())+" / "+clock(duration));}}ui.postDelayed(this,500);}};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);files=LocalMedia.list(this);String name=state==null?getIntent().getStringExtra("file"):state.getString("file",getIntent().getStringExtra("file"));
        index=-1;for(int i=0;i<files.size();i++)if(files.get(i).getName().equals(name))index=i;
        if(index<0){finish();return;}
        if(state!=null){position=state.getLong("position",0);zoom=Math.max(0,Math.min(5,state.getInt("zoom",0)));x=state.getFloat("x",0);y=state.getFloat("y",0);muted=state.getBoolean("muted",false);playing=state.getBoolean("playing",true);}
        file=files.get(index);preferences=new CameraRepository(this);build();
        if(preferences.keepAwake())getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(Build.VERSION.SDK_INT>=33){backCallback=this::back;getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,backCallback);}
    }
    private void build(){
        root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);root.setFocusableInTouchMode(true);
        video=(PlayerView)getLayoutInflater().inflate(R.layout.zoom_player,root,false);video.setUseController(false);root.addView(video,new FrameLayout.LayoutParams(-1,-1));
        photo=new ImageView(this);photo.setScaleType(ImageView.ScaleType.FIT_CENTER);root.addView(photo,new FrameLayout.LayoutParams(-1,-1));
        error=text("",18);error.setGravity(Gravity.CENTER);root.addView(error,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));
        hint=text("",14);FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);hp.bottomMargin=Ui.dp(this,150);root.addView(hint,hp);
        chrome=Ui.column(this);chrome.setPadding(20,12,20,12);chrome.setBackground(ViewerStyle.rounded(this,Color.argb(225,3,10,22),18));
        title=Ui.text(this,"",14,-1);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);chrome.addView(title,Ui.lp(-1,Ui.dp(this,22)));
        timeline=Ui.row(this);seek=new SeekBar(this);seek.setMax(100);seek.setKeyProgressIncrement(10000);timeline.addView(seek,new LinearLayout.LayoutParams(0,Ui.dp(this,30),1));
        time=Ui.text(this,"",13,ViewerStyle.MUTED);time.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);timeline.addView(time,Ui.lp(Ui.dp(this,140),-1));chrome.addView(timeline,Ui.lp(-1,Ui.dp(this,32)));
        buttons=Ui.row(this);buttons.setGravity(Gravity.CENTER);chrome.addView(buttons,Ui.lp(-1,Ui.dp(this,46)));
        button("‹","Zurück zu den Aufnahmen",this::finish);
        button("≪","Vorherige Aufnahme",()->neighbor(-1));
        play=button("▶ / Ⅱ","Wiedergabe / Pause",this::togglePlay);
        button("≫","Nächste Aufnahme",()->neighbor(1));
        button("rewind","10 Sekunden zurück",()->skip(-10000));button("forward","10 Sekunden vor",()->skip(10000));
        button("−","Verkleinern",()->changeZoom(-1));button("＋","Vergrößern",()->changeZoom(1));button("↺","Normalzoom",this::resetZoom);
        mute=button("Ton","Ton ein / aus",()->{muted=!muted;if(player!=null)player.setVolume(muted?0:1);mute.setSelected(muted);showHint(muted?"Ton aus":"Ton ein");});
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);cp.setMargins(Ui.dp(this,28),0,Ui.dp(this,28),Ui.dp(this,16));root.addView(chrome,cp);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){dragging=true;ui.removeCallbacks(hide);}public void onStopTrackingTouch(SeekBar s){dragging=false;if(player!=null)player.seekTo(s.getProgress());scheduleHide();}public void onProgressChanged(SeekBar s,int progress,boolean user){if(user&&player!=null){player.seekTo(progress);time.setText(clock(progress)+" / "+clock(player.getDuration()));scheduleHide();}}});
        seek.setOnFocusChangeListener((v,focus)->{if(focus)showHint("Abspielposition · Links/Rechts zum Spulen");});
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->transform());setContentView(root);
    }
    private Button button(String label,String description,Runnable action){Button button=label.equals("rewind")||label.equals("forward")?Ui.iconButton(this,label,description,action):Ui.button(this,label,action);button.setContentDescription(description);button.setOnFocusChangeListener((v,focused)->{if(focused)showHint(description);});LinearLayout.LayoutParams lp=Ui.lp(Ui.dp(this,52),Ui.dp(this,42));lp.setMargins(5,0,5,0);buttons.addView(button,lp);return button;}
    private TextView text(String value,int size){TextView result=Ui.text(this,value,size,-1);result.setPadding(24,12,24,12);result.setBackground(ViewerStyle.rounded(this,Color.argb(210,3,10,22),18));return result;}
    @Override protected void onStart(){super.onStart();if(file==null)return;running=true;load();ui.post(tick);}
    private void load(){
        release();int current=++generation;boolean movie=file.getName().endsWith(".mp4");title.setText(file.getName());video.setVisibility(movie?View.VISIBLE:View.GONE);photo.setVisibility(movie?View.GONE:View.VISIBLE);seek.setVisibility(movie?View.VISIBLE:View.GONE);time.setVisibility(movie?View.VISIBLE:View.GONE);play.setVisibility(movie?View.VISIBLE:View.GONE);mute.setVisibility(movie?View.VISIBLE:View.GONE);buttons.getChildAt(4).setVisibility(movie?View.VISIBLE:View.GONE);buttons.getChildAt(5).setVisibility(movie?View.VISIBLE:View.GONE);timeline.setVisibility(movie?View.VISIBLE:View.GONE);((FrameLayout.LayoutParams)hint.getLayoutParams()).bottomMargin=Ui.dp(this,movie?150:112);buttons.getChildAt(1).setEnabled(files.size()>1);buttons.getChildAt(3).setEnabled(files.size()>1);error.setVisibility(View.GONE);time.setText("00:00 / 00:00");seek.setProgress(0);
        if(movie){player=new ExoPlayer.Builder(this).build();video.setPlayer(player);player.setAudioAttributes(AudioAttributes.DEFAULT,true);player.setVolume(muted?0:1);player.addListener(new Player.Listener(){public void onPlaybackStateChanged(int state){if(state==Player.STATE_ENDED){showControls();showHint("Aufnahme beendet · Wiedergabe startet erneut");}}public void onPlayerError(PlaybackException e){error.setText("Aufnahme kann nicht abgespielt werden");error.setVisibility(View.VISIBLE);showControls();}});player.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)));player.seekTo(position);player.prepare();player.setPlayWhenReady(playing);}
        else{File requested=file;worker.submit(()->{try{Bitmap bitmap=MediaImages.load(requested,1920,1080);ui.post(()->{if(!running||generation!=current){if(bitmap!=null)bitmap.recycle();return;}image=bitmap;photo.setImageBitmap(bitmap);if(bitmap==null){error.setText("Bild konnte nicht geladen werden");error.setVisibility(View.VISIBLE);}});}catch(Exception e){ui.post(()->{if(running&&generation==current){error.setText("Bild konnte nicht geladen werden");error.setVisibility(View.VISIBLE);}});}});}
        mute.setSelected(muted);transform();showControls();
    }
    private void togglePlay(){if(player==null)return;boolean ended=player.getPlaybackState()==Player.STATE_ENDED;if(ended)player.seekTo(0);player.setPlayWhenReady(ended||!player.getPlayWhenReady());playing=player.getPlayWhenReady();showHint(playing?"Wiedergabe läuft":"Wiedergabe pausiert");scheduleHide();}
    private void skip(long delta){if(player==null)return;long duration=player.getDuration();player.seekTo(Math.max(0,Math.min(duration>0?duration:Long.MAX_VALUE,player.getCurrentPosition()+delta)));scheduleHide();}
    private void neighbor(int direction){if(files.size()<2){showHint("Keine weitere Aufnahme");return;}index=Math.floorMod(index+direction,files.size());file=files.get(index);position=0;playing=true;zoom=0;x=y=0;confirm=true;load();}
    private void changeZoom(int delta){hideControls();zoom=Math.max(0,Math.min(levels.length-1,zoom+delta));confirm=false;transform();showHint("Zoom "+levels[zoom]+"× · OK bestätigen · Pfeile bewegen");scheduleHide();}
    private void resetZoom(){zoom=0;x=y=0;confirm=true;transform();}
    private void transform(){if(root==null)return;float factor=(levels[zoom]-1)/2;x=Math.max(-root.getWidth()*factor,Math.min(root.getWidth()*factor,x));y=Math.max(-root.getHeight()*factor,Math.min(root.getHeight()*factor,y));for(View view:new View[]{video,photo}){view.setScaleX(levels[zoom]);view.setScaleY(levels[zoom]);view.setTranslationX(x);view.setTranslationY(y);}}
    private void showControls(){chrome.setVisibility(View.VISIBLE);if(play.getVisibility()==View.VISIBLE)play.requestFocus();else buttons.getChildAt(7).requestFocus();scheduleHide();}
    private void hideControls(){ui.removeCallbacks(hide);ui.removeCallbacks(hideHint);chrome.setVisibility(View.GONE);hint.setVisibility(View.GONE);root.requestFocus();}
    private void showHint(String value){ui.removeCallbacks(hideHint);if(!preferences.streamInfoEnabled()){hint.setVisibility(View.GONE);return;}hint.setText(value);hint.setVisibility(View.VISIBLE);int seconds=preferences.streamInfoSeconds();if(seconds>0)ui.postDelayed(hideHint,seconds*1000L);}
    private void scheduleHide(){ui.removeCallbacks(hide);ui.postDelayed(hide,7000);}
    @Override @android.annotation.SuppressLint("GestureBackNavigation") public boolean dispatchKeyEvent(KeyEvent event){
        int key=event.getKeyCode();boolean ok=key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER;
        if(key==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP)back();return true;}
        if(ok&&okCaptured&&event.getAction()==KeyEvent.ACTION_UP){okCaptured=false;return true;}
        if(event.getAction()==KeyEvent.ACTION_DOWN){
            if(key==KeyEvent.KEYCODE_CHANNEL_UP||key==KeyEvent.KEYCODE_CHANNEL_DOWN){hideControls();changeZoom(key==KeyEvent.KEYCODE_CHANNEL_UP?1:-1);return true;}
            if(key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE){togglePlay();return true;}
            if(key==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD||key==KeyEvent.KEYCODE_MEDIA_REWIND){skip(key==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD?10000:-10000);return true;}
            if(ok&&chrome.getVisibility()!=View.VISIBLE){okCaptured=true;if(!confirm){confirm=true;hint.setVisibility(View.GONE);}else showControls();return true;}
            if(chrome.getVisibility()!=View.VISIBLE&&zoom>0){float step=Ui.dp(this,30);if(key==KeyEvent.KEYCODE_DPAD_LEFT)x+=step;else if(key==KeyEvent.KEYCODE_DPAD_RIGHT)x-=step;else if(key==KeyEvent.KEYCODE_DPAD_UP)y+=step;else if(key==KeyEvent.KEYCODE_DPAD_DOWN)y-=step;else return super.dispatchKeyEvent(event);transform();return true;}
            if(chrome.getVisibility()==View.VISIBLE){scheduleHide();if(seek.hasFocus()&&key==KeyEvent.KEYCODE_DPAD_DOWN){play.requestFocus();return true;}if(!seek.hasFocus()&&seek.getVisibility()==View.VISIBLE&&key==KeyEvent.KEYCODE_DPAD_UP){seek.requestFocus();return true;}if(!seek.hasFocus()&&(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT)){int current=0;for(int i=0;i<buttons.getChildCount();i++)if(buttons.getChildAt(i).hasFocus())current=i;int direction=key==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1;for(int i=current+direction;i>=0&&i<buttons.getChildCount();i+=direction)if(buttons.getChildAt(i).getVisibility()==View.VISIBLE&&buttons.getChildAt(i).isEnabled()){buttons.getChildAt(i).requestFocus();break;}return true;}}
        }
        return super.dispatchKeyEvent(event);
    }
    private void back(){if(chrome.getVisibility()==View.VISIBLE)hideControls();else if(zoom>0)resetZoom();else finish();}
    @Override public void finish(){if(file!=null)setResult(RESULT_OK,new android.content.Intent().putExtra("file",file.getName()));super.finish();}
    private static String clock(long ms){long seconds=Math.max(0,ms/1000);return seconds>=3600?String.format(Locale.ROOT,"%d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60):String.format(Locale.ROOT,"%02d:%02d",seconds/60,seconds%60);}
    private void release(){if(player!=null){player.release();video.setPlayer(null);player=null;}if(photo!=null)photo.setImageDrawable(null);if(image!=null){image.recycle();image=null;}}
    @Override protected void onStop(){running=false;generation++;if(player!=null){position=player.getCurrentPosition();playing=player.getPlayWhenReady();}ui.removeCallbacksAndMessages(null);release();super.onStop();}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("file",file==null?"":file.getName());state.putLong("position",player==null?position:player.getCurrentPosition());state.putInt("zoom",zoom);state.putFloat("x",x);state.putFloat("y",y);state.putBoolean("muted",muted);state.putBoolean("playing",player==null?playing:player.getPlayWhenReady());super.onSaveInstanceState(state);}
    @Override protected void onDestroy(){if(Build.VERSION.SDK_INT>=33&&backCallback!=null)getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);worker.shutdownNow();ui.removeCallbacksAndMessages(null);super.onDestroy();}
}
