package de.local.tapoviewer;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.rtsp.RtspMediaSource;
import androidx.media3.ui.PlayerView;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class PlayerActivity extends ViewerActivity {
    public static final String EXTRA_IP = "camera_ip";
    public static final String EXTRA_NAME = "camera_name";
    public static final String EXTRA_STREAM = "camera_stream";
    private static final float[] ZOOM_LEVELS = {1f, 1.5f, 2f, 3f, 4f, 5f};

    private ExoPlayer player;
    private PlayerView playerView;
    private TextView message;
    private TextView info;
    private TextView connectionBadge;
    private TextView cameraLabel,recordingBadge;
    private CameraRepository preferences;
    private ProgressBar progress;
    private String ip;
    private String cameraName;
    private int stream;
    private int zoomIndex;
    private boolean zoomConfirmed = true;
    private float offsetX;
    private float offsetY;
    private Camera camera;
    private MjpegView mjpeg;
    private FrameLayout root;
    private PreviewRecorder recorder;
    private OriginalRecorder originalRecorder;
    private boolean recordingFinalizing;
    private OriginalRecorder.Tap recordingTap=new OriginalRecorder.Tap();
    private int retryAttempts;
    private boolean retryPending;
    private long lastInteraction=android.os.SystemClock.elapsedRealtime();
    private final Runnable retryTask=()->{retryPending=false;if(!this.stopped)startPlayer();};
    private final Runnable rotateTask=new Runnable(){public void run(){if(stopped)return;if(preferences.option("rotate_fullscreen","false").equals("true")&&controls.getVisibility()!=View.VISIBLE&&zoomIndex==0&&!isRecording()&&technicalDialog==null&&recordButton.isEnabled()&&preferences.liveCameras().size()>1&&(player==null||player.getPlayWhenReady())&&hasWindowFocus()&&android.os.SystemClock.elapsedRealtime()-lastInteraction>=preferences.scrollSeconds()*1000L){neighbor(1);return;}overlays.postDelayed(this,1000);}};
    private android.widget.Button recordButton;
    private final android.os.Handler timers=new android.os.Handler(android.os.Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private volatile long lastFrame;
    private long recordStart;
    private boolean stopped,readyInfoShown;
    private android.widget.LinearLayout controls;
    private TextView help;
    private android.widget.Button firstControl;
    private android.app.AlertDialog technicalDialog;
    private TextView technicalText;
    private String videoDecoder="",audioDecoder="";
    private android.window.OnBackInvokedCallback backCallback;
    private boolean okHeld,okSaved;
    private CameraRepository.SavedViewport pendingViewport;
    private final Runnable saveHeldOk=()->{
        if(okHeld && zoomIndex>0 && controls.getVisibility()!=View.VISIBLE){okSaved=true;saveCurrentViewport();}
    };
    private final android.os.Handler overlays=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable hideChrome=this::hideControls;
    private final Runnable hideStreamInfoTask=this::hideStreamInfo;
    private final Runnable closeTechnical=()->{if(technicalDialog!=null) technicalDialog.dismiss();};
    private final Runnable refreshTechnical=new Runnable(){@Override public void run(){
        if(technicalDialog==null || !technicalDialog.isShowing()) return;
        technicalText.setText(StreamDiagnostics.describe(camera,stream,player,videoDecoder,audioDecoder,mjpeg));
        overlays.postDelayed(this,1000);
    }};

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        ip = getIntent().getStringExtra(EXTRA_IP);
        preferences=new CameraRepository(this);
        camera=preferences.find(ip);
        if(camera!=null) ip=camera.ip;
        cameraName = getIntent().getStringExtra(EXTRA_NAME);
        stream = getIntent().getIntExtra(EXTRA_STREAM, 1);
        if (camera==null) {
            finish();
            return;
        }
        stream=getIntent().getIntExtra(EXTRA_STREAM,camera.fullStream);
        cameraName=camera.name;
        setContentView(buildUi());
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->applyPendingViewport());
        restoreSavedViewport();
        if(android.os.Build.VERSION.SDK_INT>=33) {
            backCallback=this::backAction;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,backCallback);
        }
        if(new CameraRepository(this).keepAwake()) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private View buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setFocusableInTouchMode(true);

        playerView = (PlayerView) getLayoutInflater().inflate(R.layout.zoom_player, root, false);
        playerView.setUseController(false);
        playerView.setResizeMode(camera.aspectMode==1?androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM:camera.aspectMode==2?androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH:androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER);
        root.addView(playerView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        cameraLabel = overlay(cameraName, 17);
        root.addView(cameraLabel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        connectionBadge = overlay("● Verbinden", 13);
        FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        badgeLp.setMargins(dp(20), dp(16), 0, 0);
        root.addView(connectionBadge, badgeLp);
        cameraLabel.setTranslationY(dp(16));

        info = overlay("", 14);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        ilp.setMargins(0, dp(16), dp(20), 0);
        root.addView(info, ilp);
        recordingBadge=overlay("● REC",14); recordingBadge.setTextColor(Color.rgb(255,90,90)); recordingBadge.setVisibility(View.GONE);
        FrameLayout.LayoutParams rlp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.START); rlp.setMargins(dp(20),0,0,dp(20)); root.addView(recordingBadge,rlp);

        message = overlay("Verbindung wird hergestellt …", 20);
        message.setGravity(Gravity.CENTER);
        root.addView(message, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        progress = new ProgressBar(this);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER);
        plp.topMargin = dp(90);
        root.addView(progress, plp);

        help = overlay("", 14);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hlp.bottomMargin = dp(82);
        root.addView(help, hlp);

        controls=Ui.row(this);
        controls.setPadding(dp(8),dp(6),dp(8),dp(6));
        controls.setBackground(ViewerStyle.rounded(this,Color.argb(210,3,10,22),20));
        firstControl=control(controls,"▶ / Ⅱ","Wiedergabe pausieren",()->{if(player!=null){player.setPlayWhenReady(!player.getPlayWhenReady());firstControl.setContentDescription(player.getPlayWhenReady()?"Wiedergabe pausieren":"Wiedergabe fortsetzen");showControlHint(firstControl);}});
        control(controls,"≪","Vorherige Kamera",()->neighbor(-1));
        control(controls,"≫","Nächste Kamera",()->neighbor(1));
        control(controls,"HD / SD","Hauptstream / Nebenstream wechseln",this::changeQuality);
        control(controls,"−","Verkleinern",()->changeZoom(-1));
        control(controls,"＋","Vergrößern",()->changeZoom(1));
        control(controls,"↺","Normalzoom",this::resetZoom);
        control(controls,"▣","Schnappschuss speichern",this::snapshot);
        recordButton=control(controls,"●","Aufnahme starten oder stoppen",this::record);
        control(controls,"⟳","Neu verbinden",this::startPlayer);
        control(controls,"▤","Aufnahmen öffnen",()->startActivity(new android.content.Intent(this,MediaLibraryActivity.class)));
        control(controls,"Position","Gespeicherte Bildpositionen",this::positions);
        control(controls,"Ton","Ton und Lautstärke",this::audioSettings);
        if(!camera.ptzUrl.isEmpty()&&!camera.profileToken.isEmpty())control(controls,"PTZ","ONVIF-Kamerasteuerung",this::ptz);
        FrameLayout.LayoutParams clp=new FrameLayout.LayoutParams(-2,dp(58),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        clp.bottomMargin=dp(18); root.addView(controls,clp);
        controls.setVisibility(View.GONE); help.setVisibility(View.GONE);
        root.requestFocus();
        updateInfo();
        return root;
    }

    private boolean handleKey(int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        if (keyCode == KeyEvent.KEYCODE_CHANNEL_UP || keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) {
            if (event.getRepeatCount() == 0) {
                changeZoom(keyCode == KeyEvent.KEYCODE_CHANNEL_UP ? 1 : -1);
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if(!zoomConfirmed) {zoomConfirmed = true; updateInfo();}
            else showControls();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            changeQuality();
            return true;
        }
        if (zoomIndex > 0) {
            float step = 70f;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) offsetX += step;
            else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) offsetX -= step;
            else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) offsetY += step;
            else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) offsetY -= step;
            else return false;
            clampOffsets();
            applyTransform();
            return true;
        }
        return false;
    }

    private void changeZoom(int delta) {
        hideControls();
        zoomIndex=Math.max(0,Math.min(ZOOM_LEVELS.length-1,zoomIndex+delta)); zoomConfirmed=false;
        clampOffsets(); applyTransform();
    }
    private void resetZoom() { zoomIndex=0; offsetX=0; offsetY=0; zoomConfirmed=true; applyTransform(); root.requestFocus(); }

    // Physical TV keys remain supported alongside the registered Android 13+ back callback.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if(event.getAction()==KeyEvent.ACTION_DOWN)lastInteraction=android.os.SystemClock.elapsedRealtime();
        int code = event.getKeyCode();
        if((code==KeyEvent.KEYCODE_DPAD_CENTER || code==KeyEvent.KEYCODE_ENTER) && (okHeld || controls==null || controls.getVisibility()!=View.VISIBLE)) {
            if(event.getAction()==KeyEvent.ACTION_DOWN) {
                if(!okHeld && event.getRepeatCount()==0){okHeld=true;okSaved=false;overlays.postDelayed(saveHeldOk,Math.max(600,android.view.ViewConfiguration.getLongPressTimeout()));}
                if(okHeld && !okSaved && event.isLongPress()){overlays.removeCallbacks(saveHeldOk);saveHeldOk.run();}
                return true;
            }
            if(event.getAction()==KeyEvent.ACTION_UP){
                overlays.removeCallbacks(saveHeldOk);boolean shortPress=okHeld&&!okSaved&&!event.isCanceled();okHeld=false;
                if(shortPress){if(!zoomConfirmed){zoomConfirmed=true;updateInfo();}else showControls();}
                return true;
            }
        }
        if(code==KeyEvent.KEYCODE_BACK) {if(event.getAction()==KeyEvent.ACTION_UP) backAction(); return true;}
        if(code==KeyEvent.KEYCODE_PROG_RED || code==KeyEvent.KEYCODE_PROG_YELLOW || code==KeyEvent.KEYCODE_PROG_BLUE || code==KeyEvent.KEYCODE_PROG_GREEN || code==KeyEvent.KEYCODE_INFO) {
            if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0) {
                if(code==KeyEvent.KEYCODE_PROG_RED) {if(recordButton.isEnabled()) record();}
                else if(code==KeyEvent.KEYCODE_PROG_YELLOW) snapshot();
                else if(code==KeyEvent.KEYCODE_PROG_BLUE) changeQuality();
                else if(code==KeyEvent.KEYCODE_PROG_GREEN) startActivity(new android.content.Intent(this,MediaLibraryActivity.class));
                else showTechnical();
            }
            return true;
        }
        if(controls!=null && controls.getVisibility()==View.VISIBLE) {
            if(event.getAction()==KeyEvent.ACTION_DOWN) scheduleHide();
            if(code==KeyEvent.KEYCODE_DPAD_LEFT || code==KeyEvent.KEYCODE_DPAD_RIGHT) {
                if(event.getAction()==KeyEvent.ACTION_DOWN) {
                    int current=0; for(int i=0;i<controls.getChildCount();i++)if(controls.getChildAt(i).hasFocus()){current=i;break;}
                    int direction=code==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1;
                    for(int i=current+direction;i>=0 && i<controls.getChildCount();i+=direction) {
                        View candidate=controls.getChildAt(i);
                        if(candidate.isEnabled() && candidate.getVisibility()==View.VISIBLE){candidate.requestFocus();showControlHint((android.widget.Button)candidate);break;}
                    }
                }
                return true;
            }
            if((code>=KeyEvent.KEYCODE_DPAD_UP && code<=KeyEvent.KEYCODE_DPAD_RIGHT) || code==KeyEvent.KEYCODE_DPAD_CENTER || code==KeyEvent.KEYCODE_ENTER) return super.dispatchKeyEvent(event);
        }
        if(code==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0 && player!=null) player.setPlayWhenReady(!player.getPlayWhenReady());
            return true;
        }
        boolean control = code == KeyEvent.KEYCODE_CHANNEL_UP || code == KeyEvent.KEYCODE_CHANNEL_DOWN
                || code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || (code >= KeyEvent.KEYCODE_DPAD_UP && code <= KeyEvent.KEYCODE_DPAD_RIGHT && zoomIndex > 0);
        if (control) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) handleKey(code, event);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void backAction() {
        if(controls!=null && controls.getVisibility()==View.VISIBLE) hideControls();
        else if(zoomIndex>0) resetZoom();
        else finish();
    }

    private void saveCurrentViewport() {
        if(root.getWidth()==0||root.getHeight()==0||zoomIndex==0)return;
        float factor=(ZOOM_LEVELS[zoomIndex]-1)/2;
        try {
            preferences.saveViewport(camera.id,zoomIndex,offsetX/(root.getWidth()*factor),offsetY/(root.getHeight()*factor));
            zoomConfirmed=true;updateInfo();Ui.toast(this,"Zoom und Bildposition für "+camera.name+" gespeichert");
        } catch(Exception e){Ui.toast(this,"Bildposition konnte nicht gespeichert werden");}
    }
    private void restoreSavedViewport(){pendingViewport=preferences.loadViewport(camera.id);applyPendingViewport();}
    private void applyPendingViewport(){
        if(pendingViewport==null||root.getWidth()==0||root.getHeight()==0)return;
        CameraRepository.SavedViewport saved=pendingViewport;pendingViewport=null;zoomIndex=saved.zoomIndex;zoomConfirmed=true;
        float factor=(ZOOM_LEVELS[zoomIndex]-1)/2;offsetX=saved.x*root.getWidth()*factor;offsetY=saved.y*root.getHeight()*factor;clampOffsets();applyTransform();
    }

    private void showControls() {
        controls.setVisibility(View.VISIBLE); firstControl.requestFocus(); showControlHint(firstControl); updateInfo(); scheduleHide();
    }
    private final Runnable hideControlHint=()->{if(help!=null)help.setVisibility(View.GONE);};
    private void showControlHint(android.widget.Button button) {
        overlays.removeCallbacks(hideControlHint);
        if(controls.getVisibility()!=View.VISIBLE || !preferences.streamInfoEnabled()){help.setVisibility(View.GONE);return;}
        if(button==recordButton)button.setContentDescription(isRecording()?"Aufnahme stoppen und speichern":"Aufnahme starten");
        help.setText(button.getContentDescription()); help.setVisibility(View.VISIBLE);
        int seconds=preferences.streamInfoSeconds(); if(seconds>0)overlays.postDelayed(hideControlHint,seconds*1000L);
    }
    private void scheduleHide() {overlays.removeCallbacks(hideChrome); overlays.postDelayed(hideChrome,7000);}
    private void hideControls() {
        overlays.removeCallbacks(hideChrome);
        overlays.removeCallbacks(hideControlHint);
        if(controls!=null) controls.setVisibility(View.GONE);
        if(help!=null) help.setVisibility(View.GONE);
        if(recordingBadge!=null)recordingBadge.setVisibility(View.GONE);
        if(root!=null) root.requestFocus();
    }
    private void changeQuality() {if(isRecording()){Ui.toast(this,"Aufnahme zuerst beenden");return;}stream=stream==1?2:1;retryAttempts=0;startPlayer();Ui.toast(this,stream==1?"Hauptstream":"Nebenstream");}
    private void showStreamInfo() {
        overlays.removeCallbacks(hideStreamInfoTask);
        hideStreamInfo();
    }
    private void hideStreamInfo() {
        if(cameraLabel!=null) cameraLabel.setVisibility(View.GONE);
        if(connectionBadge!=null) connectionBadge.setVisibility(View.GONE);
        if(info!=null) info.setVisibility(View.GONE);
    }
    private void scheduleTechnicalClose() {
        overlays.removeCallbacks(closeTechnical);
        int seconds=preferences.streamInfoSeconds(); if(seconds>0) overlays.postDelayed(closeTechnical,seconds*1000L);
    }
    private void showTechnical() {
        if(technicalDialog!=null && technicalDialog.isShowing()) {technicalDialog.dismiss(); return;}
        hideControls();
        android.widget.ScrollView scroll=new android.widget.ScrollView(this);
        technicalText=Ui.text(this,"",14,Color.WHITE); technicalText.setPadding(dp(20),dp(8),dp(20),dp(16)); scroll.addView(technicalText);
        technicalDialog=new android.app.AlertDialog.Builder(this).setTitle("Technische Streamdaten · "+cameraName).setView(scroll).setPositiveButton("Schließen",null).create();
        technicalDialog.setOnDismissListener(d->{overlays.removeCallbacks(refreshTechnical); overlays.removeCallbacks(closeTechnical); technicalDialog=null; root.requestFocus();});
        technicalDialog.setOnKeyListener((d,key,event)->{if(key==KeyEvent.KEYCODE_INFO) {if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0) d.dismiss(); return true;} if(event.getAction()==KeyEvent.ACTION_DOWN) scheduleTechnicalClose(); return false;});
        technicalDialog.show(); refreshTechnical.run(); scheduleTechnicalClose();
    }

    private void clampOffsets() {
        float zoom = ZOOM_LEVELS[zoomIndex];
        float maxX = root.getWidth() * (zoom - 1f) / 2f;
        float maxY = root.getHeight() * (zoom - 1f) / 2f;
        offsetX = Math.max(-maxX, Math.min(maxX, offsetX));
        offsetY = Math.max(-maxY, Math.min(maxY, offsetY));
    }

    private void applyTransform() {
        float zoom = ZOOM_LEVELS[zoomIndex];
        playerView.animate().scaleX(zoom).scaleY(zoom).translationX(offsetX).translationY(offsetY).setDuration(160).start();
        if(mjpeg!=null) mjpeg.animate().scaleX(zoom).scaleY(zoom).translationX(offsetX).translationY(offsetY).setDuration(160).start();
        updateInfo();
    }

    private void updateInfo() {
        if (info == null) return;
        info.setText((stream == 1 ? "HD" : "SD") + "   ⊕ " + ZOOM_LEVELS[zoomIndex] + "×" + (zoomConfirmed ? "" : "  ·  OK bestätigen"));
        recordingBadge.setVisibility(isRecording() && controls.getVisibility()==View.VISIBLE?View.VISIBLE:View.GONE);
        showStreamInfo();
    }

    @Override protected void onStart() {
        super.onStart();
        if(camera==null)return;
        startPlayer();
        lastInteraction=android.os.SystemClock.elapsedRealtime();overlays.removeCallbacks(rotateTask);overlays.postDelayed(rotateTask,1000);
    }

    private void startPlayer() {
        releasePlayer();
        stopped=false;
        readyInfoShown=false;
        videoDecoder=""; audioDecoder="";
        updateInfo();
        SecureCredentialsStore.Credentials credentials = new SecureCredentialsStore(this).load(camera.id);
        if(camera.protocol.equals("MJPEG")) {
            mjpeg=new MjpegView(this); root.addView(mjpeg,1,new FrameLayout.LayoutParams(-1,-1)); playerView.setVisibility(View.GONE);
            mjpeg.start(camera,credentials,online->{
                if(online) { boolean first=message.getVisibility()!=View.GONE; message.setVisibility(View.GONE); progress.setVisibility(View.GONE); connectionBadge.setText("● Online"); connectionBadge.setTextColor(Color.rgb(48,216,139)); if(first) showStreamInfo(); }
                else { showError("MJPEG-Verbindung fehlgeschlagen"); scheduleRetry(); }
            });
            applyTransform();
            return;
        }

        updateInfo();
        message.setText("Verbindung mit " + cameraName + " …");
        message.setVisibility(View.VISIBLE);
        progress.setVisibility(View.VISIBLE);
        player = StreamEngine.player(this,camera);
        player.setVolume(camera.muted?0:camera.volume/100f);
        recordingTap=new OriginalRecorder.Tap();
        player.addAnalyticsListener(new androidx.media3.exoplayer.analytics.AnalyticsListener(){
            @Override public void onVideoDecoderInitialized(EventTime time,String name,long timestamp,long duration){videoDecoder=name;}
            @Override public void onAudioDecoderInitialized(EventTime time,String name,long timestamp,long duration){audioDecoder=name;}
        });
        lastFrame=android.os.SystemClock.elapsedRealtime();
        player.setVideoFrameMetadataListener((time,release,format,media)->lastFrame=android.os.SystemClock.elapsedRealtime());
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override public void onVideoSizeChanged(androidx.media3.common.VideoSize size){if(camera.aspectMode==2&&size.width>0&&size.height>0)playerView.setLayoutParams(new FrameLayout.LayoutParams(size.width,size.height,Gravity.CENTER));}
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    connectionBadge.setText("● Online");
                    connectionBadge.setTextColor(Color.rgb(48, 216, 139));
                    message.setVisibility(View.GONE);
                    progress.setVisibility(View.GONE);
                    retryAttempts=0;retryPending=false;timers.removeCallbacks(retryTask);
                    if(!readyInfoShown) {readyInfoShown=true; showStreamInfo();}
                } else if (state == Player.STATE_BUFFERING) progress.setVisibility(View.VISIBLE);
                else if(state==Player.STATE_ENDED)scheduleRetry();
            }

            @Override public void onPlayerError(PlaybackException error) {
                showError(friendlyError(error));
                scheduleRetry();
            }
        });
        player.setMediaSource(new RecordingSource(StreamEngine.source(this,camera,credentials,stream),recordingTap));
        player.prepare();
        player.play();
        if(new CameraRepository(this).recover()) timers.postDelayed(new Runnable(){
            @Override public void run() {
                if(stopped||player==null) return;
                if(player.getPlayWhenReady() && android.os.SystemClock.elapsedRealtime()-lastFrame>30000) { scheduleRetry(); }
                timers.postDelayed(this,5000);
            }
        },5000);
    }

    public static Uri buildRtspUri(String ip, SecureCredentialsStore.Credentials credentials, int stream) {
        return new Uri.Builder()
                .scheme("rtsp")
                .encodedAuthority(Uri.encode(credentials.username) + ":" + Uri.encode(credentials.password) + "@" + ip + ":554")
                .appendPath("stream" + stream)
                .build();
    }

    private String friendlyError(PlaybackException error) {
        return StreamHealth.error(error);
    }
    private void scheduleRetry(){if(stopped||retryPending||!preferences.recover())return;retryPending=true;timers.postDelayed(retryTask,StreamHealth.retryDelay(retryAttempts++));}

    private void showError(String text) {
        connectionBadge.setText("● Offline");
        connectionBadge.setTextColor(Color.rgb(255, 114, 114));
        progress.setVisibility(View.GONE);
        message.setText(text);
        message.setVisibility(View.VISIBLE);
        showStreamInfo();
    }

    private void releasePlayer() {
        stopOriginalRecording();retryPending=false;
        if(recorder!=null) { recorder.finish(); recorder=null; if(recordButton!=null) recordButton.setText("●"); }
        timers.removeCallbacksAndMessages(null);
        if(mjpeg!=null) { mjpeg.stop(); root.removeView(mjpeg); mjpeg=null; }
        if (player != null) {
            playerView.setPlayer(null);
            player.release();
            player = null;
        }
    }

    @Override protected void onStop() {
        okHeld=false;okSaved=false;overlays.removeCallbacks(saveHeldOk);
        hideControls(); if(technicalDialog!=null) technicalDialog.dismiss(); overlays.removeCallbacksAndMessages(null);
        stopped=true;
        if(recorder!=null) { recorder.finish(); recorder=null; }
        releasePlayer();
        super.onStop();
    }
    @Override protected void onPause(){okHeld=false;okSaved=false;overlays.removeCallbacks(saveHeldOk);super.onPause();}

    private android.widget.Button control(android.widget.LinearLayout controls,String label,String description,Runnable action) {
        android.widget.Button button=Ui.button(this,label,action); button.setContentDescription(description);
        button.setFocusableInTouchMode(true);
        button.setOnFocusChangeListener((v,focused)->{if(focused)showControlHint(button);});
        if(!(button instanceof IconButton))button.setSingleLine(true); button.setTextSize(14); button.setPadding(dp(4),0,dp(4),0);
        android.widget.LinearLayout.LayoutParams lp=new android.widget.LinearLayout.LayoutParams(dp(label.length()>3?76:48),dp(44)); lp.setMargins(dp(3),0,dp(3),0); controls.addView(button,lp); return button;
    }
    private android.graphics.Bitmap currentFrame() {
        return currentFrame(false);
    }
    private android.graphics.Bitmap currentFrame(boolean fullSize) {
        if(mjpeg!=null) return mjpeg.snapshot();
        View surface=playerView.getVideoSurfaceView();
        if(!(surface instanceof android.view.TextureView)||!((android.view.TextureView)surface).isAvailable()||player==null||player.getPlaybackState()!=Player.STATE_READY) return null;
        return fullSize?((android.view.TextureView)surface).getBitmap():((android.view.TextureView)surface).getBitmap(640,360);
    }
    private void neighbor(int delta) {
        java.util.List<Camera> enabled=new java.util.ArrayList<>();
        enabled.addAll(preferences.liveCameras());
        if(enabled.size()<2) { Ui.toast(this,"Keine weitere aktive Kamera"); return; }
        int index=0; for(int i=0;i<enabled.size();i++) if(enabled.get(i).id.equals(camera.id)) index=i;
        Camera next=enabled.get((index+delta+enabled.size())%enabled.size());
        startActivity(new android.content.Intent(this,PlayerActivity.class).putExtra(EXTRA_IP,next.id)); finish();
    }
    private void snapshot() {
        android.graphics.Bitmap frame=currentFrame(true); if(frame==null) { Ui.toast(this,"Noch kein Livebild verfügbar"); return; }
        worker.submit(()-> { try { LocalMedia.snapshot(this,cameraName,frame); runOnUiThread(()->Ui.toast(this,"Schnappschuss gespeichert · "+LocalMedia.EXPORT_FOLDER)); }
            catch(Exception e) { runOnUiThread(()->Ui.toast(this,"Schnappschuss konnte nicht gespeichert werden")); } finally { frame.recycle(); } });
    }
    private void record() {
        if(recordingFinalizing)return;
        if(originalRecorder!=null){stopOriginalRecording();recordButton.setEnabled(false);updateInfo();Ui.toast(this,"Aufnahme wird gespeichert");return;}
        if(recorder==null&&!camera.protocol.equals("MJPEG")&&!preferences.option("record_mode","original").equals("preview")){
            try{originalRecorder=new OriginalRecorder(this,cameraName,recordingTap,(file,error)->{recordingFinalizing=false;recordingTap.recorder=null;originalRecorder=null;recordButton.setEnabled(true);recordButton.setText("●");ViewerStyle.button(recordButton);updateInfo();Ui.toast(this,error==null?"Originalaufnahme gespeichert":error);});recordingTap.recorder=originalRecorder;recordButton.setText("■");updateInfo();Ui.toast(this,"Originalaufnahme gestartet · "+originalRecorder.audioDescription());long max=preferences.recordingDurationMs();timers.postDelayed(()->{if(originalRecorder!=null)record();},max);}catch(Exception e){Ui.toast(this,e.getMessage());}return;
        }
        if(recorder!=null) { recorder.finish(); recorder=null; recordButton.setEnabled(false); updateInfo(); return; }
        try{LocalMedia.ensureSpace(this,16*1048576L);}catch(Exception e){Ui.toast(this,e.getMessage());return;}
        android.graphics.Bitmap frame=currentFrame(); if(frame==null) { Ui.toast(this,"Noch kein Livebild verfügbar"); return; }
        recorder=new PreviewRecorder(this,cameraName,(file,error)->{
            if(recorder!=null&&recorder.finishing) recorder=null;
            recordButton.setText("●"); recordButton.setEnabled(true); ViewerStyle.button(recordButton); updateInfo();
            Ui.toast(this,error==null?"Aufnahme gespeichert · "+LocalMedia.EXPORT_FOLDER:error);
        });
        recordStart=android.os.SystemClock.elapsedRealtime(); recordButton.setText("■"); recordButton.setTextColor(Color.rgb(255,90,90)); recorder.frame(frame); updateInfo();
        Ui.toast(this,"Vorschauaufnahme gestartet · ohne Ton");
        timers.postDelayed(new Runnable(){ @Override public void run() {
            if(recorder==null) return;
            if(android.os.SystemClock.elapsedRealtime()-recordStart>=preferences.recordingDurationMs()) { record(); return; }
            try{LocalMedia.ensureSpace(PlayerActivity.this,2*1048576L);}catch(Exception e){record();Ui.toast(PlayerActivity.this,e.getMessage());return;}
            android.graphics.Bitmap next=currentFrame(); if(next!=null) recorder.frame(next);
            timers.postDelayed(this,200);
        } },200);
    }
    private boolean isRecording(){return recorder!=null||originalRecorder!=null;}
    private void stopOriginalRecording(){recordingTap.recorder=null;if(originalRecorder!=null){recordingFinalizing=true;if(recordButton!=null)recordButton.setEnabled(false);OriginalRecorder current=originalRecorder;originalRecorder=null;current.finish();}}
    private void audioSettings(){hideControls();android.widget.LinearLayout form=Ui.column(this);form.setPadding(dp(20),dp(10),dp(20),dp(10));android.widget.Switch mute=Ui.toggle(form,"Stumm",camera.muted);android.widget.SeekBar volume=new android.widget.SeekBar(this);volume.setMax(100);volume.setProgress(camera.volume);Ui.add(form,volume,48);mute.setOnCheckedChangeListener((b,on)->{camera.muted=on;if(player!=null)player.setVolume(on?0:camera.volume/100f);preferences.upsert(camera);});volume.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(android.widget.SeekBar b){}public void onStopTrackingTouch(android.widget.SeekBar b){preferences.upsert(camera);}public void onProgressChanged(android.widget.SeekBar b,int value,boolean user){camera.volume=value;if(player!=null)player.setVolume(camera.muted?0:value/100f);}});new android.app.AlertDialog.Builder(this).setTitle("Ton · "+camera.name).setView(form).setPositiveButton("Schließen",(d,w)->preferences.upsert(camera)).show();}
    private void positions(){hideControls();org.json.JSONObject positions=preferences.positions(camera.id);java.util.List<String> names=new java.util.ArrayList<>();java.util.Iterator<String> keys=positions.keys();while(keys.hasNext())names.add(keys.next());java.util.List<String> items=new java.util.ArrayList<>();items.add("Aktuellen Ausschnitt unter neuem Namen speichern");items.add("Gespeicherte Position löschen");items.addAll(names);new android.app.AlertDialog.Builder(this).setTitle("Bildpositionen · "+camera.name).setItems(items.toArray(new String[0]),(d,n)->{if(n==0){if(zoomIndex==0){Ui.toast(this,"Zuerst mit P+ vergrößern und den Ausschnitt wählen");return;}android.widget.EditText name=new android.widget.EditText(this);name.setSingleLine();name.setSelectAllOnFocus(true);name.setText("Position "+(names.size()+1));new android.app.AlertDialog.Builder(this).setTitle("Bildposition speichern").setView(name).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",(a,w)->{String label=name.getText().toString().trim();if(label.isEmpty())return;float factor=(ZOOM_LEVELS[zoomIndex]-1)/2;preferences.saveNamedViewport(camera.id,label,zoomIndex,offsetX/(root.getWidth()*factor),offsetY/(root.getHeight()*factor));Ui.toast(this,"Bildposition gespeichert");}).show();}else if(n==1){new android.app.AlertDialog.Builder(this).setTitle("Position löschen").setItems(names.toArray(new String[0]),(a,index)->{positions.remove(names.get(index));try{org.json.JSONObject all=new org.json.JSONObject(preferences.option("positions","{}"));all.put(camera.id,positions);preferences.setOption("positions",all.toString());}catch(Exception ignored){}}).show();}else{org.json.JSONObject v=positions.optJSONObject(names.get(n-2));if(v!=null){preferences.saveViewport(camera.id,v.optInt("zoom",0),(float)v.optDouble("x",0),(float)v.optDouble("y",0));restoreSavedViewport();}}}).show();}
    private void ptz() {
        if(camera.ptzUrl.isEmpty()||camera.profileToken.isEmpty()) { Ui.toast(this,"Zuerst ein ONVIF-Profil mit PTZ in den Kameraeinstellungen importieren. Feste Kameras unterstützen kein Schwenken."); return; }
        android.widget.LinearLayout controls=Ui.row(this);
        String[] labels={"◀","▲","▼","▶"}; float[] xs={-0.3f,0,0,0.3f},ys={0,0.3f,-0.3f,0};
        for(int i=0;i<labels.length;i++) { final int index=i;
            controls.addView(Ui.button(this,labels[i],()->worker.submit(()->{ try { OnvifClient.move(camera,new SecureCredentialsStore(this).load(camera.id),xs[index],ys[index]); }
                catch(Exception e) { runOnUiThread(()->Ui.toast(this,"Kamera unterstützt diese PTZ-Bewegung nicht oder antwortet nicht.")); } })),new android.widget.LinearLayout.LayoutParams(dp(85),dp(55)));
        }
        new android.app.AlertDialog.Builder(this).setTitle("ONVIF · Kamerabewegung").setView(controls).setNegativeButton("Schließen",null).show();
    }
    @Override protected void onDestroy() {
        if(android.os.Build.VERSION.SDK_INT>=33 && backCallback!=null) getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        overlays.removeCallbacksAndMessages(null); worker.shutdown(); super.onDestroy();
    }

    private TextView overlay(String value, int sp) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(Color.WHITE);
        view.setBackground(ViewerStyle.rounded(this, Color.argb(195, 3, 10, 22), 22));
        view.setPadding(dp(18), dp(10), dp(18), dp(10));
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
