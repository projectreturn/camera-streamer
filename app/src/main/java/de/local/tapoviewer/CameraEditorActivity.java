package de.local.tapoviewer;

import android.os.Bundle;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.util.UUID;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class CameraEditorActivity extends ViewerActivity {
    private Camera existing;
    private EditText name,host,port,user,pass,main,sub,url,subUrl,onvif;
    private Spinner brand,protocol,codec,transport;
    private Spinner gridQuality,fullQuality,aspect,volume;
    private Switch muted;
    private Switch low,favorite,enabled;
    private TextView status;
    private Camera probe;
    private LinearLayout form,scanPanel,results,hostGroup,pathsGroup,urlsGroup,onvifGroup,rtspOptions;
    private TextView scanStatus,formTitle;
    private Button scanButton;
    private final java.util.List<Button> navigation=new java.util.ArrayList<>();
    private final java.util.Map<String,Camera> discovered=new java.util.LinkedHashMap<>();
    private CameraScanner scanner;
    private boolean alive=true;
    private int scanGeneration;
    private boolean rtspDone,onvifDone;
    private int scanChecked;
    private androidx.media3.exoplayer.ExoPlayer tester;
    private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        existing=new CameraRepository(this).find(getIntent().getStringExtra("id"));
        Camera c=existing==null?new Camera("","Neue Kamera","Manuell",true):existing;
        SecureCredentialsStore.Credentials auth=new SecureCredentialsStore(this).load(c.id);
        LinearLayout root=Ui.page(this,existing==null?"Kamera hinzufügen":"Kamera bearbeiten");
        LinearLayout body=Ui.row(this); body.setGravity(48); root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout nav=Ui.column(this); nav.setPadding(0,0,Ui.dp(this,20),0); body.addView(nav,Ui.lp(Ui.dp(this,210),-1));
        Ui.add(nav,Ui.heading(this,"Anbindung"),-2);
        String[] choices={"Netzwerk suchen","RTSP","ONVIF","HLS","MJPEG"};
        for(int i=0;i<choices.length;i++) {final int choice=i; Button b=Ui.button(this,choices[i],()->showConnection(choice)); navigation.add(b); Ui.add(nav,b,46);}
        Ui.add(nav,Ui.text(this,"1  Suchen oder Anbindung wählen\n\n2  Kamera und Account eintragen\n\n3  Testen und speichern",13,ViewerStyle.MUTED),-2);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); body.addView(scroll,new LinearLayout.LayoutParams(0,-1,1));
        LinearLayout content=Ui.column(this); content.setPadding(Ui.dp(this,12),0,Ui.dp(this,16),Ui.dp(this,30)); scroll.addView(content);
        scanPanel=Ui.column(this); Ui.add(content,scanPanel,-2);
        Ui.add(scanPanel,Ui.heading(this,"Kamera im lokalen Netzwerk finden"),-2);
        Ui.add(scanPanel,Ui.text(this,"RTSP und ONVIF werden gesucht. Ein Treffer ist noch keine gespeicherte Kamera: Gerät auswählen, Account eingeben und speichern. Andere Ports und URLs lassen sich links manuell eintragen.",15,ViewerStyle.MUTED),-2);
        scanButton=Ui.button(this,"Netzwerksuche starten",this::scanNetwork); Ui.add(scanPanel,scanButton,46);
        scanStatus=Ui.text(this,"Bereit zur Suche",14,ViewerStyle.ACCENT); Ui.add(scanPanel,scanStatus,-2);
        results=Ui.column(this); Ui.add(scanPanel,results,-2);
        form=Ui.column(this); Ui.add(content,form,-2); formTitle=Ui.heading(this,"Verbindung"); Ui.add(form,formTitle,-2);
        // The protocol selector is controlled by the left navigation, never duplicated in the form.
        protocol=Ui.select(Ui.column(this),"Protokoll",c.protocol,"RTSP","ONVIF","HLS","MJPEG");
        name=Ui.field(form,"Anzeigename",c.name,false);
        hostGroup=Ui.column(this); Ui.add(form,hostGroup,-2);
        host=Ui.field(hostGroup,"Host / IP",c.ip,false);
        port=Ui.field(hostGroup,"RTSP-Port",Integer.toString(c.port),false); port.setInputType(2);
        Ui.add(form,Ui.heading(this,"Kamera-Account"),-2);
        user=Ui.field(form,"Benutzername",auth.username,false); pass=Ui.field(form,"Passwort",auth.password,true);
        Ui.add(form,Ui.text(this,"Wird für diese Kamera verschlüsselt auf dem Receiver gespeichert.",12,ViewerStyle.MUTED),-2);
        onvifGroup=Ui.column(this); Ui.add(form,onvifGroup,-2);
        onvif=Ui.field(onvifGroup,"ONVIF-Gerätedienst (optional; sonst automatisch)",c.onvifUrl,false);
        Ui.add(onvifGroup,Ui.button(this,"ONVIF-Streamprofile laden",this::importProfiles),46);
        pathsGroup=Ui.column(this); Ui.add(form,pathsGroup,-2);
        brand=Ui.select(pathsGroup,"Herstellerprofil",c.brand,"Tapo","Generic RTSP","Generic ONVIF","Hikvision","Hikvision NVR","Dahua","Dahua NVR/XVR","Amcrest","Reolink","Axis");
        main=Ui.field(pathsGroup,"Hauptstream-Pfad",c.mainPath,false);
        sub=Ui.field(pathsGroup,"Nebenstream-Pfad",c.subPath,false);
        urlsGroup=Ui.column(this); Ui.add(form,urlsGroup,-2);
        url=Ui.field(urlsGroup,"Vollständige Stream-URL · bei RTSP optional",c.playbackUrl,true);
        subUrl=Ui.field(urlsGroup,"Nebenstream-URL (optional)",c.subPlaybackUrl,true);
        rtspOptions=Ui.column(this); Ui.add(form,rtspOptions,-2);
        codec=Ui.select(rtspOptions,"Video-Codec",c.codec,"Auto","H.264","H.265");
        transport=Ui.select(rtspOptions,"RTSP-Transport",c.transport,"TCP","UDP","Auto");
        low=Ui.toggle(rtspOptions,"Niedrige Latenz",c.lowLatency);
        Ui.add(form,Ui.heading(this,"Anzeige"),-2);
        gridQuality=Ui.select(form,"Stream im Raster",c.gridStream==1?"Hauptstream":"Nebenstream","Hauptstream","Nebenstream");
        fullQuality=Ui.select(form,"Stream im Vollbild",c.fullStream==2?"Nebenstream":"Hauptstream","Hauptstream","Nebenstream");
        String[] aspects={"Vollständig anzeigen","Bildfüllend zuschneiden","Originalgröße"};
        aspect=Ui.select(form,"Bildformat",aspects[Math.max(0,Math.min(2,c.aspectMode))],aspects);
        muted=Ui.toggle(form,"Ton zunächst stumm",c.muted);
        volume=Ui.select(form,"Kameralautstärke",(Math.max(0,Math.min(100,c.volume))/10*10)+" %","0 %","10 %","20 %","30 %","40 %","50 %","60 %","70 %","80 %","90 %","100 %");
        favorite=Ui.toggle(form,"Favorit",c.favorite); enabled=Ui.toggle(form,"In der Live-Ansicht aktiv",c.enabled);
        if(existing!=null && new CameraRepository(this).loadViewport(existing.id)!=null)
            Ui.add(form,Ui.button(this,"Gespeicherte Zoomansicht zurücksetzen",()->{new CameraRepository(this).clearViewport(existing.id);Ui.toast(this,"Gespeicherte Zoomansicht entfernt");}),46);
        Ui.add(form,Ui.button(this,"Verbindung testen",this::test),46);
        Button save=Ui.button(this,"Speichern",this::save); Ui.add(form,save,46);
        status=Ui.text(this,"",14,ViewerStyle.ACCENT); Ui.add(form,status,-2);
        protocol.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){ public void onNothingSelected(AdapterView<?> p){} public void onItemSelected(AdapterView<?> p,View v,int i,long id){if(form.getVisibility()==View.VISIBLE) showConnection(i+1);} });
        brand.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            boolean initial=true;
            @Override public void onNothingSelected(AdapterView<?> a) { }
            @Override public void onItemSelected(AdapterView<?> a,View v,int p,long id) {
                if(initial) { initial=false; return; }
                String choice=brand.getSelectedItem().toString();
                String m="/stream1",s="/stream2";
                if(choice.startsWith("Hikvision")) { m="/Streaming/Channels/101"; s="/Streaming/Channels/102"; }
                if(choice.startsWith("Dahua")||choice.equals("Amcrest")) { m="/cam/realmonitor?channel=1&subtype=0"; s="/cam/realmonitor?channel=1&subtype=1"; }
                if(choice.equals("Reolink")) { m="/h264Preview_01_main"; s="/h264Preview_01_sub"; }
                if(choice.equals("Axis")) { m="/axis-media/media.amp"; s=m; }
                main.setText(m); sub.setText(s);
                if(choice.equals("Generic ONVIF")) protocol.setSelection(1);
            }
        });
        setContentView(root);
        if(existing==null) {showConnection(0); navigation.get(0).requestFocus(); if(getIntent().getBooleanExtra("scan_first",false)) scanNetwork();}
        else {showConnection(protocol.getSelectedItemPosition()+1); name.requestFocus(); name.selectAll();}
    }
    private void showConnection(int choice) {
        for(int i=0;i<navigation.size();i++) navigation.get(i).setSelected(i==choice);
        stopTest(); scanPanel.setVisibility(choice==0?View.VISIBLE:View.GONE); form.setVisibility(choice==0?View.GONE:View.VISIBLE);
        if(choice==0) return;
        if(protocol.getSelectedItemPosition()!=choice-1) protocol.setSelection(choice-1);
        String type=new String[]{"RTSP","ONVIF","HLS","MJPEG"}[choice-1]; formTitle.setText(type+" · Kamera einrichten");
        boolean rtsp=choice<=2;
        hostGroup.setVisibility(rtsp?View.VISIBLE:View.GONE); pathsGroup.setVisibility(choice==1?View.VISIBLE:View.GONE);
        onvifGroup.setVisibility(choice==2?View.VISIBLE:View.GONE); rtspOptions.setVisibility(rtsp?View.VISIBLE:View.GONE);
        // Keep entered values when switching type. Only fields relevant to that type are shown.
    }
    private void scanNetwork() {
        String prefix=NetworkUtil.subnetPrefix(NetworkUtil.findLocalIpv4());
        if(prefix==null) {scanStatus.setText("Keine lokale IPv4-Verbindung. Netzwerk am Receiver prüfen."); return;}
        final int generation=++scanGeneration;
        rtspDone=false; onvifDone=false; scanChecked=0;
        discovered.clear(); results.removeAllViews(); scanButton.setEnabled(false); updateScanStatus();
        if(scanner!=null) scanner.cancel(); scanner=new CameraScanner();
        scanner.scan(prefix,new CameraScanner.Listener(){
            public void onCamera(Camera c){if(alive && generation==scanGeneration) found(c);}
            public void onProgress(int done,int total){if(alive && generation==scanGeneration) {scanChecked=done; updateScanStatus();}}
            public void onFinished(){if(alive && generation==scanGeneration) {rtspDone=true; updateScanStatus();}}
        });
        worker.submit(()->{try {for(Camera c:OnvifClient.discover(this)) runOnUiThread(()->{if(alive && generation==scanGeneration) found(c);});} catch(Exception ignored) { }
            finally {runOnUiThread(()->{if(alive && generation==scanGeneration) {onvifDone=true; updateScanStatus();}});}});
    }
    private void updateScanStatus() {
        boolean done=rtspDone && onvifDone; scanButton.setEnabled(done);
        String count=discovered.size()+(discovered.size()==1?" Gerät":" Geräte");
        scanStatus.setText(done?count+" gefunden. Gerät auswählen oder links manuell einrichten.":"RTSP: "+scanChecked+" / 254 · ONVIF "+(onvifDone?"beendet":"sucht …")+" · "+count);
    }
    private void found(Camera c) {
        Camera previous=discovered.get(c.ip); if(previous!=null && c.onvifUrl.isEmpty()) return;
        discovered.put(c.ip,c); results.removeAllViews(); updateScanStatus();
        for(Camera camera:discovered.values()) {
            Camera saved=new CameraRepository(this).find(camera.ip);
            String label=(saved==null?camera.name:saved.name)+" · "+camera.ip+"  · "+(camera.onvifUrl.isEmpty()?"RTSP":"RTSP / ONVIF")+(saved==null?"":" · gespeichert");
            Ui.add(results,Ui.button(this,label,()->{
                if(saved!=null) {startActivity(new Intent(this,CameraEditorActivity.class).putExtra("id",saved.id)); finish(); return;}
                host.setText(camera.ip); name.setText(camera.name); onvif.setText(camera.onvifUrl); enabled.setChecked(true);
                showConnection(camera.onvifUrl.isEmpty()?1:2); name.requestFocus(); name.selectAll();
            }),52);
        }
    }
    private Camera collect() {
        String h=host.getText().toString().trim();
        String playback=url.getText().toString().trim();
        String type=protocol.getSelectedItem().toString();
        if(type.equals("HLS")||type.equals("MJPEG")) {
            if(playback.isEmpty()) throw new IllegalArgumentException("Vollständige HTTP(S)-Stream-URL eingeben");
            android.net.Uri http=android.net.Uri.parse(playback);
            if(!"http".equals(http.getScheme())&&!"https".equals(http.getScheme())) throw new IllegalArgumentException("Für "+type+" eine HTTP(S)-URL eingeben");
            h=http.getHost();
        }
        if(h.isEmpty() && !playback.isEmpty()) h=android.net.Uri.parse(playback).getHost();
        if(h==null || !h.matches("[a-zA-Z0-9._-]+")) throw new IllegalArgumentException("Gültigen Host oder eine IP eingeben");
        int p=Integer.parseInt(port.getText().toString().trim());
        if(p<1||p>65535) throw new IllegalArgumentException("Port muss zwischen 1 und 65535 liegen");
        Camera c=new Camera(h,name.getText().toString().trim(),"Manuell konfiguriert",enabled.isChecked());
        if(c.name.isEmpty()) throw new IllegalArgumentException("Kameranamen eingeben");
        c.id=existing==null?UUID.randomUUID().toString():existing.id;
        c.port=p; c.brand=brand.getSelectedItem().toString(); c.protocol=protocol.getSelectedItem().toString();
        c.codec=codec.getSelectedItem().toString(); c.transport=transport.getSelectedItem().toString();
        c.mainPath=main.getText().toString().trim(); c.subPath=sub.getText().toString().trim();
        c.lowLatency=low.isChecked(); c.favorite=favorite.isChecked(); c.playbackUrl=playback;
        c.gridStream=gridQuality.getSelectedItemPosition()+1;c.fullStream=fullQuality.getSelectedItemPosition()+1;
        c.aspectMode=aspect.getSelectedItemPosition();c.muted=muted.isChecked();c.volume=volume.getSelectedItemPosition()*10;
        c.subPlaybackUrl=subUrl.getText().toString().trim();
        if(c.mainPath.contains("://")) { c.playbackUrl=c.mainPath; c.mainPath="/"; }
        if(c.subPath.contains("://")) { c.subPlaybackUrl=c.subPath; c.subPath="/"; }
        c.onvifUrl=onvif.getText().toString().trim();
        if(!c.onvifUrl.isEmpty()) validateHttp(c.onvifUrl);
        if(!c.playbackUrl.isEmpty()) {
            android.net.Uri u=android.net.Uri.parse(c.playbackUrl);
            String scheme=u.getScheme();
            if(!"rtsp".equals(scheme)&&!"http".equals(scheme)&&!"https".equals(scheme)) throw new IllegalArgumentException("URL muss rtsp://, http:// oder https:// verwenden");
            if(u.getHost()==null) throw new IllegalArgumentException("Wiedergabe-URL ist ungültig");
        }
        if(!c.subPlaybackUrl.isEmpty()) {
            android.net.Uri u=android.net.Uri.parse(c.subPlaybackUrl);
            if(u.getHost()==null || !("rtsp".equals(u.getScheme())||"http".equals(u.getScheme())||"https".equals(u.getScheme()))) throw new IllegalArgumentException("Nebenstream-URL ist ungültig");
        }
        if(existing!=null) { c.profileToken=existing.profileToken; c.ptzUrl=existing.ptzUrl; }
        return c;
    }
    static void validateHttp(String value) {
        android.net.Uri u=android.net.Uri.parse(value);
        if(!"http".equals(u.getScheme())&&!"https".equals(u.getScheme()) || u.getHost()==null || u.getUserInfo()!=null)
            throw new IllegalArgumentException("HTTP(S)-Dienst-URL ohne Zugangsdaten eingeben");
    }
    private SecureCredentialsStore.Credentials auth() { return new SecureCredentialsStore.Credentials(user.getText().toString(),pass.getText().toString()); }
    private void save() {
        try {
            Camera c=collect(); SecureCredentialsStore secure=new SecureCredentialsStore(this);
            secure.save(c.id,auth().username,auth().password); secure.saveSecret(c.id+"_url",c.playbackUrl); secure.saveSecret(c.id+"_suburl",c.subPlaybackUrl);
            new CameraRepository(this).upsert(c); finish();
        } catch(Exception e) { status.setText(e instanceof IllegalArgumentException?e.getMessage():"Speichern fehlgeschlagen"); }
    }
    private void test() {
        try {
            Camera c=collect(); SecureCredentialsStore.Credentials credentials=auth();
            status.setText("Stream wird geprüft …");
            if(tester!=null) tester.release();
            if(c.protocol.equals("MJPEG")) {
                worker.submit(()-> { try { StreamEngine.checkMjpeg(c,credentials); runOnUiThread(()->status.setText("MJPEG-Bild erfolgreich empfangen")); }
                    catch(Exception e) { runOnUiThread(()->status.setText("Kein MJPEG-Bild. URL und Anmeldung prüfen.")); } }); return;
            }
            tester=StreamEngine.player(this,c);
            tester.addListener(new androidx.media3.common.Player.Listener(){
                @Override public void onPlaybackStateChanged(int state) {
                    if(state==androidx.media3.common.Player.STATE_READY) { status.setText("Stream erfolgreich geöffnet"); stopTest(); }
                }
                @Override public void onPlayerError(androidx.media3.common.PlaybackException e) { status.setText("Verbindung fehlgeschlagen. URL, Codec und Account prüfen."); stopTest(); }
            });
            tester.setMediaSource(StreamEngine.source(this,c,credentials,1)); tester.prepare(); tester.play();
            handler.removeCallbacksAndMessages(null); handler.postDelayed(()->{ if(tester!=null) { status.setText("Zeitüberschreitung beim Verbindungstest"); stopTest(); } },12000);
        } catch(Exception e) { status.setText(e instanceof IllegalArgumentException?e.getMessage():"Verbindungstest fehlgeschlagen"); }
    }
    private void stopTest() { handler.post(()->{ if(tester!=null) { tester.release(); tester=null; } }); }
    private void importProfiles() {
        try {
            Camera c=collect(); SecureCredentialsStore.Credentials credentials=auth(); status.setText("ONVIF-Profile werden geladen …");
            worker.submit(()-> {
                try {
                    java.util.List<OnvifClient.Profile> profiles=OnvifClient.profiles(c,credentials);
                    runOnUiThread(()-> {
                        if(isFinishing()) return;
                        String[] labels=new String[profiles.size()]; for(int i=0;i<labels.length;i++) labels[i]=profiles.get(i).name;
                        new android.app.AlertDialog.Builder(this).setTitle("ONVIF-Profil wählen").setItems(labels,(d,index)-> {
                            OnvifClient.Profile p=profiles.get(index); url.setText(p.uri); protocol.setSelection(1);
                            if(existing==null) existing=c;
                            existing.profileToken=p.token; existing.ptzUrl=p.ptzUrl; onvif.setText(p.deviceUrl);
                            status.setText("Profil importiert. Speichern übernimmt die Konfiguration.");
                            new android.app.AlertDialog.Builder(this).setTitle("Profil für Nebenstream wählen").setItems(labels,(subDialog,subIndex)->subUrl.setText(profiles.get(subIndex).uri)).setNegativeButton("Kein separater Nebenstream",null).show();
                        }).show();
                    });
                } catch(Exception e) { runOnUiThread(()->status.setText("ONVIF nicht verfügbar. Dienst-URL, Account und Kamerauhr prüfen.")); }
            });
        } catch(Exception e) { status.setText("Host / IP und gültigen Port eingeben."); }
    }
    @Override protected void onStop() { stopTest(); super.onStop(); }
    @Override protected void onDestroy() { alive=false; if(scanner!=null) scanner.cancel(); handler.removeCallbacksAndMessages(null); if(tester!=null) tester.release(); tester=null; worker.shutdownNow(); super.onDestroy(); }
}
