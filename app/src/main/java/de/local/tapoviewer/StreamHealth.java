package de.local.tapoviewer;

import androidx.media3.common.PlaybackException;

final class StreamHealth {
    static long retryDelay(int attempt){return Math.min(60000L,2000L*(1L<<Math.min(5,Math.max(0,attempt))));}
    static String error(Throwable error){
        StringBuilder detail=new StringBuilder();for(Throwable t=error;t!=null;t=t.getCause())detail.append(' ').append(t.getClass().getSimpleName()).append(' ').append(t.getMessage());
        String s=detail.toString().toLowerCase(java.util.Locale.ROOT);
        if(s.contains("401")||s.contains("403")||s.contains("unauthorized"))return "Anmeldung fehlgeschlagen · Kamera-Account prüfen";
        if(s.contains("404")||s.contains("not found"))return "Stream nicht vorhanden · Pfad oder Streamprofil prüfen";
        if(s.contains("decoder")||s.contains("unsupported")||s.contains("parsing"))return "Streamformat nicht unterstützt · H.264 oder anderen Stream versuchen";
        if(s.contains("timeout")||s.contains("timed out")||s.contains("connect")||s.contains("host")||s.contains("socket"))return "Kamera nicht erreichbar · Netzwerk, IP und Port prüfen";
        if(error instanceof PlaybackException && ((PlaybackException)error).errorCode>=4000)return "Wiedergabeformat nicht unterstützt · anderen Stream versuchen";
        return "Stream unterbrochen · Verbindung oder Streamprofil prüfen";
    }
}
