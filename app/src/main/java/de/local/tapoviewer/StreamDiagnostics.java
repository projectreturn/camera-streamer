package de.local.tapoviewer;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.DecoderCounters;
import androidx.media3.exoplayer.ExoPlayer;
import java.util.Locale;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class StreamDiagnostics {
    private static final String UNKNOWN="nicht gemeldet";
    /** Never expose user info, paths, query tokens or fragments from a saved stream URL. */
    static String origin(android.net.Uri uri) {
        return value(uri.getScheme())+"://"+value(uri.getHost())+(uri.getPort()>0?":"+uri.getPort():"");
    }
    static String describe(Camera camera,int stream,ExoPlayer player,String videoDecoder,String audioDecoder,MjpegView mjpeg) {
        StringBuilder b=new StringBuilder();
        line(b,"Quelle",origin(StreamEngine.uri(camera,new SecureCredentialsStore.Credentials("",""),stream)));
        line(b,"Anbindung",camera.protocol); line(b,"Stream",stream==1?"Hauptstream":"Nebenstream");
        line(b,"Transportvorgabe",camera.protocol.equals("RTSP")||camera.protocol.equals("ONVIF")?camera.transport+" · tatsächlicher Fallback wird nicht gemeldet":"HTTP(S)");
        line(b,"Niedrige Latenz",camera.lowLatency?"aktiv":"aus");
        if(mjpeg!=null) {
            b.append("\nVIDEO\n"); line(b,"Codec / MIME","JPEG · image/jpeg");
            line(b,"Angezeigtes Bild",mjpeg.frameWidth()>0?mjpeg.frameWidth()+" × "+mjpeg.frameHeight():UNKNOWN);
            line(b,"Decoder","Android BitmapFactory"); line(b,"Bildrate / Bitrate",UNKNOWN);
            b.append("\nAUDIO\nMJPEG liefert keinen Audiostream.\n");
        } else if(player!=null) {
            line(b,"Playerstatus",state(player.getPlaybackState())+(player.isPlaying()?" · läuft":player.getPlayWhenReady()?" · wartet":" · pausiert"));
            line(b,"Puffer",player.getTotalBufferedDuration()+" ms");
            long offset=player.getCurrentLiveOffset(); line(b,"Live-Abstand",offset==C.TIME_UNSET?UNKNOWN:offset+" ms");
            line(b,"Wiedergabegeschwindigkeit",player.getPlaybackParameters().speed+"×");
            b.append("\nVIDEO\n"); Format video=player.getVideoFormat();
            if(video==null) b.append("Noch kein Videoformat verfügbar.\n");
            else {
                format(b,video); line(b,"Auflösung",video.width>0&&video.height>0?video.width+" × "+video.height:UNKNOWN);
                line(b,"Bildrate",video.frameRate>0?String.format(Locale.GERMANY,"%.2f Bilder/s",video.frameRate):UNKNOWN);
                line(b,"Pixelseitenverhältnis",video.pixelWidthHeightRatio>0?Float.toString(video.pixelWidthHeightRatio):UNKNOWN);
                line(b,"Drehung",video.rotationDegrees+"°");
                line(b,"Farbraum / Dynamik / Bittiefe",video.colorInfo==null?UNKNOWN:video.colorInfo.toLogString());
            }
            line(b,"Videodecoder",value(videoDecoder)); counters(b,player.getVideoDecoderCounters());
            b.append("\nAUDIO\n"); Format audio=player.getAudioFormat();
            if(audio==null) b.append("Kein aktiver Audiostream.\n");
            else {format(b,audio); line(b,"Kanäle",number(audio.channelCount)); line(b,"Abtastrate",audio.sampleRate>0?audio.sampleRate+" Hz":UNKNOWN); line(b,"Sprache",value(audio.language)); line(b,"Audiodecoder",value(audioDecoder)); counters(b,player.getAudioDecoderCounters());}
            b.append("\nANGEBOTENE SPUREN\n");
            for(androidx.media3.common.Tracks.Group group:player.getCurrentTracks().getGroups()) for(int i=0;i<group.length;i++) {
                Format f=group.getTrackFormat(i);
                line(b,(group.isTrackSelected(i)?"● ":"○ ")+(group.getType()==C.TRACK_TYPE_VIDEO?"Video":group.getType()==C.TRACK_TYPE_AUDIO?"Audio":"Andere Spur"),value(f.sampleMimeType)+" · "+value(f.codecs)+(f.width>0?" · "+f.width+" × "+f.height:""));
            }
        } else b.append("\nVerbindung wird aufgebaut; technische Daten folgen nach Empfang.\n");
        b.append("\nWerte werden jede Sekunde aktualisiert. Nicht vom Stream oder Player gemeldete Werte (z. B. Netzwerkbitrate, Paketverlust oder RTT) werden nicht geschätzt. Zugangsdaten und URL-Tokens bleiben verborgen.");
        return b.toString();
    }
    private static void format(StringBuilder b,Format f) {
        line(b,"Codec-Kennung",value(f.codecs)); line(b,"MIME",value(f.sampleMimeType)); line(b,"Container",value(f.containerMimeType));
        line(b,"Bitrate Ø / Spitze",bitrate(f.averageBitrate)+" / "+bitrate(f.peakBitrate));
    }
    private static void counters(StringBuilder b,DecoderCounters c) {
        if(c==null) return; c.ensureUpdated();
        line(b,"Ausgegebene / verworfene Puffer",c.renderedOutputBufferCount+" / "+c.droppedBufferCount);
        line(b,"Übersprungene Puffer",Integer.toString(c.skippedOutputBufferCount));
    }
    private static String state(int s){return s==Player.STATE_READY?"Bereit":s==Player.STATE_BUFFERING?"Puffert":s==Player.STATE_ENDED?"Beendet":"Leerlauf";}
    private static String bitrate(int n){return n>0?String.format(Locale.GERMANY,"%.0f kbit/s",n/1000d):UNKNOWN;}
    private static String number(int n){return n>0?Integer.toString(n):UNKNOWN;}
    private static String value(String s){return s==null||s.isEmpty()?UNKNOWN:s;}
    private static void line(StringBuilder b,String label,String value){b.append(label).append(": ").append(value).append('\n');}
}
