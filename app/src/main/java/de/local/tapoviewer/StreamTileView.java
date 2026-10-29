package de.local.tapoviewer;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.rtsp.RtspMediaSource;
import androidx.media3.ui.PlayerView;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class StreamTileView extends FrameLayout {
    private final Camera camera;
    private final PlayerView playerView;
    private final TextView state;
    private final TextView badge;
    private ExoPlayer player;
    private MjpegView mjpeg;
    private final android.os.Handler retry=new android.os.Handler(android.os.Looper.getMainLooper());
    private volatile long lastFrame;
    private boolean released;
    private int attempts;
    private boolean retryPending;
    private boolean audible;
    public String cameraId(){return camera.id;}
    public boolean supportsAudio(){return !"MJPEG".equals(camera.protocol);}
    public void setAudible(boolean enabled){audible=enabled&&supportsAudio();if(player!=null){player.setVolume(audible?Math.max(0,Math.min(100,camera.volume))/100f:0f);player.setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT,audible);}}
    private final Runnable retryTask=()->{retryPending=false;restart();};

    public StreamTileView(Context context, Camera camera) {
        super(context);
        this.camera = camera;
        setFocusable(true);
        setClickable(true);
        setBackgroundResource(R.drawable.tile_border);
        setClipToOutline(true);
        setPadding(dp(3), dp(3), dp(3), dp(3));
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);

        playerView = (PlayerView) android.view.LayoutInflater.from(context).inflate(R.layout.zoom_player, this, false);
        playerView.setUseController(false);
        playerView.setResizeMode(camera.aspectMode==1?androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM:camera.aspectMode==2?androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH:androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setKeepContentOnPlayerReset(true);
        addView(playerView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        state = overlay("Verbinde …", 16);
        state.setGravity(Gravity.CENTER);
        addView(state, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        badge = overlay("● Verbinden", 11);
        badge.setBackground(ViewerStyle.rounded(context, Color.argb(190, 3, 10, 20), 20));
        LayoutParams badgeLp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        badgeLp.setMargins(dp(10), dp(10), 0, 0);
        addView(badge, badgeLp);

        TextView name = overlay(camera.name + "\n" + camera.ip, 13);
        name.setGravity(Gravity.END);
        name.setTextColor(Color.rgb(180, 231, 242));
        name.setBackground(ViewerStyle.rounded(context, Color.argb(180, 3, 10, 20), 7));
        LayoutParams nameLp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
        nameLp.setMargins(0, 0, dp(10), dp(10));
        addView(name, nameLp);
    }

    public void start() {
        released=false;
        SecureCredentialsStore.Credentials credentials = new SecureCredentialsStore(getContext()).load(camera.id);
        if(camera.protocol.equals("MJPEG")) {
            mjpeg=new MjpegView(getContext()); addView(mjpeg,0,new LayoutParams(-1,-1)); playerView.setVisibility(GONE);
            mjpeg.start(camera,credentials,online->{
                state.setVisibility(online?GONE:VISIBLE); badge.setText(online?"● Online":"● Offline");
                badge.setTextColor(online?Color.rgb(48,216,139):Color.rgb(255,114,114));
                if(online){attempts=0;retry.removeCallbacks(retryTask);retryPending=false;}else scheduleRetry();
            });
            return;
        }
        player = StreamEngine.player(getContext(),camera);
        setAudible(audible);
        lastFrame=android.os.SystemClock.elapsedRealtime();
        player.setVideoFrameMetadataListener((time,release,format,media)->lastFrame=android.os.SystemClock.elapsedRealtime());
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override public void onVideoSizeChanged(androidx.media3.common.VideoSize size){if(camera.aspectMode==2&&size.width>0&&size.height>0)playerView.setLayoutParams(new LayoutParams(size.width,size.height,Gravity.CENTER));}
            @Override public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    state.setVisibility(View.GONE);
                    attempts=0;retry.removeCallbacks(retryTask);retryPending=false;
                    badge.setText("● Online");
                    badge.setTextColor(Color.rgb(48, 216, 139));
                } else if (playbackState == Player.STATE_BUFFERING) {
                    badge.setText("● Verbinden");
                    badge.setTextColor(ViewerStyle.MUTED);
                } else if(playbackState==Player.STATE_ENDED)scheduleRetry();
            }
            @Override public void onPlayerError(PlaybackException error) {
                state.setText(StreamHealth.error(error)+"\nOK: Vollbild");
                state.setVisibility(View.VISIBLE);
                badge.setText("● Offline");
                badge.setTextColor(Color.rgb(255, 114, 114));
                scheduleRetry();
            }
        });
        player.setMediaSource(StreamEngine.source(getContext(),camera,credentials,camera.gridStream));
        player.prepare();
        player.play();
        if(new CameraRepository(getContext()).recover()) retry.postDelayed(new Runnable(){
            @Override public void run() {
                if(released) return;
                if(android.os.SystemClock.elapsedRealtime()-lastFrame>30000) { scheduleRetry(); }
                retry.postDelayed(this,5000);
            }
        },5000);
    }

    private void restart() { if(released) return; release(); state.setVisibility(VISIBLE); state.setText("Verbinde neu …"); start(); }
    private void scheduleRetry(){if(released||retryPending||!new CameraRepository(getContext()).recover())return;retryPending=true;retry.postDelayed(retryTask,StreamHealth.retryDelay(attempts++));}

    public void release() {
        released=true; retry.removeCallbacksAndMessages(null);
        retryPending=false;
        if(mjpeg!=null) { mjpeg.stop(); removeView(mjpeg); mjpeg=null; }
        if (player != null) {
            player.setVolume(0f);player.setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT,false);
            playerView.setPlayer(null);
            player.release();
            player = null;
        }
    }

    private TextView overlay(String value, int sp) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(Color.WHITE);
        view.setPadding(dp(10), dp(5), dp(10), dp(5));
        view.setBackgroundColor(Color.argb(180, 5, 12, 28));
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
