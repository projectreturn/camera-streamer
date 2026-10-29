package de.local.tapoviewer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.exoplayer.*;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.rtsp.RtspMediaSource;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import java.io.*;
import java.net.*;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class StreamEngine {
    static ExoPlayer player(Context context,Camera c) {
        DefaultTrackSelector tracks=new DefaultTrackSelector(context);
        if(!c.codec.equals("Auto")) tracks.setParameters(tracks.buildUponParameters().setPreferredVideoMimeType(c.codec.equals("H.264")?MimeTypes.VIDEO_H264:MimeTypes.VIDEO_H265));
        ExoPlayer.Builder builder=new ExoPlayer.Builder(context).setTrackSelector(tracks);
        if(c.lowLatency) builder.setLoadControl(new DefaultLoadControl.Builder().setBufferDurationsMs(400,2500,200,400).build());
        return builder.build();
    }
    static Uri uri(Camera c,SecureCredentialsStore.Credentials auth,int stream) {
        if(stream==2 && !c.subPlaybackUrl.isEmpty()) return Uri.parse(c.subPlaybackUrl);
        if(!c.playbackUrl.isEmpty()) return Uri.parse(c.playbackUrl);
        String path=stream==2&&!c.subPath.isEmpty()?c.subPath:c.mainPath;
        if(!path.startsWith("/")) path="/"+path;
        String scheme=(c.protocol.equals("HLS")||c.protocol.equals("MJPEG"))?"http":"rtsp";
        return Uri.parse(scheme+"://"+c.ip+":"+c.port+path);
    }
    static MediaSource source(Context context,Camera c,SecureCredentialsStore.Credentials auth,int stream) {
        Uri uri=uri(c,auth,stream);
        if("rtsp".equals(uri.getScheme())) {
            if(!auth.username.isEmpty() && uri.getUserInfo()==null) uri=uri.buildUpon().encodedAuthority(Uri.encode(auth.username)+":"+Uri.encode(auth.password)+"@"+uri.getEncodedAuthority()).build();
            return new RtspMediaSource.Factory().setForceUseRtpTcp(c.transport.equals("TCP"))
                    .setTimeoutMs(c.transport.equals("Auto")?4000:10000).createMediaSource(MediaItem.fromUri(uri));
        }
        auth=httpAuth(uri,auth);
        uri=withoutUserInfo(uri);
        DefaultHttpDataSource.Factory data=new DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(false).setConnectTimeoutMs(8000).setReadTimeoutMs(8000);
        if(!auth.username.isEmpty()) data.setDefaultRequestProperties(java.util.Collections.singletonMap("Authorization",basic(auth)));
        return new HlsMediaSource.Factory(data).createMediaSource(MediaItem.fromUri(uri));
    }
    static String basic(SecureCredentialsStore.Credentials auth) { return "Basic "+android.util.Base64.encodeToString((auth.username+":"+auth.password).getBytes(java.nio.charset.StandardCharsets.UTF_8),android.util.Base64.NO_WRAP); }
    private static SecureCredentialsStore.Credentials httpAuth(Uri uri,SecureCredentialsStore.Credentials auth) {
        if(auth.username.isEmpty() && uri.getUserInfo()!=null) {
            String[] parts=uri.getUserInfo().split(":",2); return new SecureCredentialsStore.Credentials(parts[0],parts.length>1?parts[1]:"");
        }
        return auth;
    }
    private static Uri withoutUserInfo(Uri uri) {
        if(uri.getUserInfo()==null) return uri;
        String authority=uri.getEncodedAuthority(); return uri.buildUpon().encodedAuthority(authority.substring(authority.lastIndexOf('@')+1)).build();
    }
    static HttpURLConnection open(Camera c,SecureCredentialsStore.Credentials auth) throws Exception {
        Uri uri=uri(c,auth,1); auth=httpAuth(uri,auth);
        HttpURLConnection connection=(HttpURLConnection)new URL(withoutUserInfo(uri).toString()).openConnection();
        connection.setConnectTimeout(5000); connection.setReadTimeout(5000); connection.setInstanceFollowRedirects(false);
        if(!auth.username.isEmpty()) connection.setRequestProperty("Authorization",basic(auth));
        if(connection.getResponseCode()!=200) { connection.disconnect(); throw new IOException("HTTP request failed"); }
        return connection;
    }
    static Bitmap readJpeg(InputStream in) throws Exception {
        ByteArrayOutputStream image=new ByteArrayOutputStream(); int previous=-1,b; boolean started=false;
        int scanned=0;
        while((b=in.read())!=-1) {
            if(++scanned>8*1024*1024) throw new IOException("Frame too large");
            if(!started) { if(previous==255 && b==216) { image.write(255); image.write(216); started=true; } }
            else { image.write(b); if(previous==255 && b==217) {
                byte[] bytes=image.toByteArray(); BitmapFactory.Options opts=new BitmapFactory.Options(); opts.inJustDecodeBounds=true;
                BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);
                opts.inSampleSize=Math.max(1,Math.max(opts.outWidth/1920,opts.outHeight/1080)); opts.inJustDecodeBounds=false;
                Bitmap result=BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts); if(result==null) throw new IOException("Invalid image"); return result;
            } }
            previous=b;
        }
        throw new EOFException();
    }
    static void checkMjpeg(Camera c,SecureCredentialsStore.Credentials auth) throws Exception {
        HttpURLConnection connection=open(c,auth);
        try(InputStream in=new BufferedInputStream(connection.getInputStream())) { Bitmap b=readJpeg(in); b.recycle(); } finally { connection.disconnect(); }
    }
}
