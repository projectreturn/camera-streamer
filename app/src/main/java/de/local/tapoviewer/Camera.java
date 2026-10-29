package de.local.tapoviewer;

import java.io.Serializable;

public final class Camera implements Serializable {
    public final String ip;
    public String name;
    public String description;
    public boolean enabled;
    public String id;
    public boolean favorite;
    public String brand = "Tapo";
    public String protocol = "RTSP";
    public int port = 554;
    public String mainPath = "/stream1";
    public String subPath = "/stream2";
    public String transport = "TCP";
    public String codec = "Auto";
    public boolean lowLatency = true;
    public String playbackUrl = "";
    public String subPlaybackUrl = "";
    public String onvifUrl = "";
    public String profileToken = "";
    public String ptzUrl = "";
    public int gridStream=2, fullStream=1, aspectMode=0;
    public boolean muted=true;
    public int volume=70;

    public Camera(String ip, String description) {
        this(ip, "Kamera " + ip.substring(ip.lastIndexOf('.') + 1), description, true);
    }

    public Camera(String ip, String name, String description, boolean enabled) {
        this.ip = ip;
        this.id = ip;
        this.name = name;
        this.description = description;
        this.enabled = enabled;
    }
}
