package de.local.tapoviewer;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Five shallow, remote-friendly sections instead of one long mixed settings page. */
public final class AppSettingsActivity extends ViewerActivity {
    private static final String[] TABS={"Kameras","Ansicht & Ton","Bedienung","Aufnahmen","System"};
    private CameraRepository repo;
    private LinearLayout content;
    private ScrollView scroll;
    private final Button[] tabs=new Button[TABS.length];
    private final int[] scrollPositions=new int[TABS.length];
    private int selected=-1;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);repo=new CameraRepository(this);
        LinearLayout root=Ui.page(this,"Einstellungen"),bar=Ui.row(this);
        for(int i=0;i<TABS.length;i++){
            final int index=i;Button tab=Ui.button(this,TABS[i],()->showTab(index));
            tab.setId(View.generateViewId());tab.setContentDescription("Einstellungen · "+TABS[i]);tabs[i]=tab;
            tab.setOnFocusChangeListener((v,focused)->{if(focused)showTab(index);});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,Ui.dp(this,44),1);lp.setMargins(3,0,3,0);bar.addView(tab,lp);
        }
        Ui.add(root,bar,48);scroll=new ScrollView(this);scroll.setFillViewport(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        content=Ui.column(this);content.setPadding(Ui.dp(this,6),Ui.dp(this,8),Ui.dp(this,12),Ui.dp(this,20));scroll.addView(content);
        setContentView(root);showTab(state==null?0:state.getInt("tab",0));
    }
    @Override protected void onSaveInstanceState(Bundle state){state.putInt("tab",Math.max(0,selected));super.onSaveInstanceState(state);}
    private void showTab(int index){
        index=Math.max(0,Math.min(TABS.length-1,index));if(selected==index)return;
        if(selected>=0)scrollPositions[selected]=scroll.getScrollY();selected=index;
        for(int i=0;i<tabs.length;i++)tabs[i].setSelected(i==index);
        content.removeAllViews();
        switch(index){case 0:cameras();break;case 1:display();break;case 2:controls();break;case 3:recordings();break;default:system();}
        final int restore=scrollPositions[index];scroll.post(()->scroll.scrollTo(0,restore));
    }
    private void heading(LinearLayout parent,String text){Ui.add(parent,Ui.heading(this,text),-2);}
    private void note(LinearLayout parent,String text){Ui.add(parent,Ui.text(this,text,14,ViewerStyle.MUTED),-2);}
    private void open(LinearLayout parent,String title,Class<?> screen){Ui.add(parent,Ui.button(this,title,()->startActivity(new Intent(this,screen))),46);}
    private LinearLayout[] columns(){LinearLayout row=Ui.row(this);row.setGravity(Gravity.TOP);Ui.add(content,row,-2);LinearLayout left=Ui.column(this),right=Ui.column(this);left.setPadding(0,0,Ui.dp(this,18),0);right.setPadding(Ui.dp(this,18),0,0,0);row.addView(left,new LinearLayout.LayoutParams(0,-2,1));row.addView(right,new LinearLayout.LayoutParams(0,-2,1));return new LinearLayout[]{left,right};}
    private void cameras(){
        heading(content,"Kameras einrichten und verwalten");
        LinearLayout row=Ui.row(this);String[] labels={"Kamera hinzufügen","Kameras verwalten","Gespeicherte Ansichten"};String[] icons={"plus","camera","grid"};Class<?>[] screens={CameraEditorActivity.class,SettingsActivity.class,ViewsActivity.class};
        for(int i=0;i<labels.length;i++){final Class<?> screen=screens[i];LinearLayout tile=Ui.column(this);tile.setGravity(Gravity.CENTER);tile.setPadding(8,12,8,12);tile.setBackgroundResource(R.drawable.focusable_panel);tile.setFocusable(true);tile.setFocusableInTouchMode(true);tile.setClickable(true);tile.setContentDescription(labels[i]);tile.setOnClickListener(v->startActivity(new Intent(this,screen)));tile.addView(new ActionIconView(this,icons[i]),Ui.lp(Ui.dp(this,40),Ui.dp(this,40)));TextView title=Ui.text(this,labels[i],17,-1);title.setGravity(Gravity.CENTER);tile.addView(title,Ui.lp(-1,Ui.dp(this,40)));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,Ui.dp(this,110),1);lp.setMargins(4,4,4,4);row.addView(tile,lp);}
        Ui.add(content,row,120);
        List<Camera> cameras=repo.loadCameras();int active=0;for(Camera c:cameras)if(c.enabled)active++;
        note(content,cameras.size()+" gespeichert · "+active+" aktiv");
        note(content,"Account, Streamauswahl, Bildformat und Kameralautstärke werden pro Kamera unter „Kameras verwalten“ festgelegt. Langes OK auf dem Bearbeiten-Symbol ändert die Reihenfolge.");
        note(content,"Der Netzwerkscan liegt unter „Kamera hinzufügen“. Gespeicherte Ansichten kombinieren Kameraauswahl und Raster. Langes OK auf dem Rasterbutton öffnet ebenfalls die Ansichten.");
    }
    private void display(){
        LinearLayout[] parts=columns();LinearLayout left=parts[0],right=parts[1];
        heading(left,"Raster und Start");
        Spinner raster=Ui.select(left,"Kameras gleichzeitig",ViewsActivity.layoutName(repo.getTileCount()),ViewsActivity.LABELS);raster.setOnItemSelectedListener(listener(p->repo.setTileCount(ViewsActivity.LAYOUTS[p])));
        List<String> labels=new ArrayList<>(Arrays.asList("Raster / letzte Ansicht","Favoriten")),values=new ArrayList<>(Arrays.asList("","favorites"));
        for(Camera c:repo.loadCameras())if(c.enabled){labels.add("Vollbild · "+c.name);values.add("camera:"+c.id);}
        Spinner start=Ui.select(left,"Beim App-Start",labels.get(Math.max(0,values.indexOf(repo.option("start","")))),labels.toArray(new String[0]));start.setOnItemSelectedListener(listener(p->repo.setOption("start",values.get(p))));
        open(left,"Gespeicherte Ansichten bearbeiten",ViewsActivity.class);
        heading(right,"Ton im Raster");
        List<String> audioLabels=new ArrayList<>(Arrays.asList("Stumm","Ton folgt der markierten Kamera")),audioValues=new ArrayList<>(Arrays.asList("off","focus"));
        for(Camera c:repo.loadCameras())if(c.enabled&&!"MJPEG".equals(c.protocol)){audioLabels.add("Nur "+c.name+" (wenn sichtbar)");audioValues.add("camera:"+c.id);}
        Spinner audio=Ui.select(right,"Tonkamera",audioLabels.get(Math.max(0,audioValues.indexOf(repo.option("grid_audio","off")))),audioLabels.toArray(new String[0]));audio.setOnItemSelectedListener(listener(p->repo.setOption("grid_audio",audioValues.get(p))));
        note(right,"Es ist höchstens eine sichtbare Kamera hörbar. Bei fest gewählter Kamera verstummt das Raster, sobald diese nicht mehr sichtbar ist. MJPEG hat keine Tonspur. Die Lautstärke stammt aus den jeweiligen Kameraeinstellungen.");
        note(right,"Der Lautsprecher oben in der Hauptansicht erlaubt dieselbe Auswahl direkt. Im Vollbild wird die eigene Toneinstellung der geöffneten Kamera verwendet.");
        heading(content,"Automatischer Wechsel");
        Switch grid=Ui.toggle(content,"Rasterseiten automatisch wechseln",repo.autoScroll());
        Switch full=Ui.toggle(content,"Kameras im Vollbild automatisch wechseln",repo.option("rotate_fullscreen","false").equals("true"));
        Spinner seconds=Ui.select(content,"Wechselabstand für Raster und Vollbild",repo.scrollSeconds()+" Sekunden","5 Sekunden","10 Sekunden","15 Sekunden","30 Sekunden","60 Sekunden");
        Runnable enabled=()->seconds.setEnabled(grid.isChecked()||full.isChecked());enabled.run();
        grid.setOnCheckedChangeListener((b,on)->{repo.autoScroll(on);enabled.run();});full.setOnCheckedChangeListener((b,on)->{repo.setOption("rotate_fullscreen",String.valueOf(on));enabled.run();});seconds.setOnItemSelectedListener(listener(p->repo.scrollSeconds(new int[]{5,10,15,30,60}[p])));
        note(content,"Im Vollbild pausiert der Wechsel bei Bedienung, Zoom, Pause, Dialogen und Aufnahme. Verwendet werden die Kameras der ausgewählten Ansicht. Weitere Rasterseiten bleiben mit Links/Rechts am Rasterrand erreichbar.");
    }
    private void controls(){
        heading(content,"Einblendungen");
        Ui.toggle(content,"Kurze Erklärung beim Auswählen eines Buttons",repo.streamInfoEnabled()).setOnCheckedChangeListener((b,on)->repo.streamInfoEnabled(on));
        int[] durations={2,3,5,10,15,30,60,0};String[] labels={"2 Sekunden","3 Sekunden","5 Sekunden","10 Sekunden","15 Sekunden","30 Sekunden","60 Sekunden","Bis zum Schließen"};int current=2;for(int i=0;i<durations.length;i++)if(durations[i]==repo.streamInfoSeconds())current=i;
        Spinner time=Ui.select(content,"Dauer von Button-Hinweisen und i-Streamdaten",labels[current],labels);time.setOnItemSelectedListener(listener(p->repo.streamInfoSeconds(durations[p])));
        note(content,"Die i-Taste bleibt auch bei abgeschalteten Button-Hinweisen verfügbar. Das normale Vollbild bleibt frei von Name, Online-Status und Qualitätsanzeigen. OK öffnet die Bedienleiste; nach sieben Sekunden ohne Bedienung wird sie ausgeblendet.");
        heading(content,"Fernbedienung");
        note(content,"Rot: Aufnahme · Gelb: Foto · Blau: Haupt-/Nebenstream · Grün: Medien · i: technische Daten\n\nP+/P−: Zoom · OK: bestätigen / Bedienleiste · Pfeile: Bildausschnitt bewegen · Langes OK: Startposition speichern · Zurück: Normalzoom / schließen");
        note(content,"Zielkreuz: benannte Bildpositionen · Lautsprecher: Ton und Lautstärke · PTZ erscheint nur bei einem unterstützten ONVIF-Profil.");
    }
    private void recordings(){
        LinearLayout[] parts=columns();LinearLayout left=parts[0],right=parts[1];
        heading(left,"Aufnahmen");open(left,"Aufnahmen öffnen",MediaLibraryActivity.class);
        String[] modes={"Originalstream","Vorschau / MJPEG"};Spinner mode=Ui.select(left,"Aufnahmemodus",modes[repo.option("record_mode","original").equals("preview")?1:0],modes);mode.setOnItemSelectedListener(listener(p->repo.setOption("record_mode",p==0?"original":"preview")));
        int[] minutes={5,15,30,60};String[] labels={"5 Minuten","15 Minuten","30 Minuten","60 Minuten"};int current=1;for(int i=0;i<minutes.length;i++)if(repo.recordingDurationMs()==minutes[i]*60000L)current=i;
        Spinner duration=Ui.select(left,"Maximale Dauer pro Aufnahme",labels[current],labels);duration.setOnItemSelectedListener(listener(p->repo.setOption("record_minutes",""+minutes[p])));
        String[] soundLabels={"Automatisch · AAC übernehmen, andere umwandeln","Ton immer nach AAC umwandeln","Ohne Ton"},soundValues={"auto","aac","none"};Spinner sound=Ui.select(left,"Ton bei Originalaufnahmen",soundLabels[Math.max(0,Arrays.asList(soundValues).indexOf(repo.option("record_audio","auto")))],soundLabels);sound.setOnItemSelectedListener(listener(p->repo.setOption("record_audio",soundValues[p])));
        note(left,"Das H.264/H.265-Video bleibt unverändert. AAC-Ton wird automatisch übernommen; andere unterstützte Mono-/Stereo-Tonspuren werden nach AAC umgewandelt. G.711 A-Law/µ-Law und PCM sind direkt unterstützt; komprimierte Formate benötigen einen passenden Receiver-Decoder. Bei Fehlern erscheint eine Meldung statt unbemerkt fehlendem Ton. MJPEG-Vorschau bleibt ohne Ton.");
        heading(right,"Speicher");note(right,LocalMedia.storageSummary(this));
        String[] sizes={"Kein App-Limit","512 MB","1 GB","2 GB","4 GB"},values={"0","512","1024","2048","4096"};Spinner limit=Ui.select(right,"Limit für die lokale Mediensammlung",sizes[Math.max(0,Arrays.asList(values).indexOf(repo.option("storage_mb","0")))],sizes);limit.setOnItemSelectedListener(listener(p->repo.setOption("storage_mb",values[p])));
        Switch remove=Ui.toggle(right,"Älteste Medien bei vollem Limit löschen",repo.option("auto_delete","false").equals("true"));
        remove.setOnCheckedChangeListener((b,on)->{if(on)new AlertDialog.Builder(this).setTitle("Automatisches Löschen aktivieren?").setMessage("Bei vollem Speicherlimit werden die ältesten App-Aufnahmen und zugehörigen Download-Kopien unwiderruflich gelöscht. Laufende Aufnahmen und fremde Dateien bleiben geschützt.").setNegativeButton("Abbrechen",(d,w)->b.setChecked(false)).setOnCancelListener(d->b.setChecked(false)).setPositiveButton("Aktivieren",(d,w)->repo.setOption("auto_delete","true")).show();else repo.setOption("auto_delete","false");});
        note(right,"Ohne automatische Löschung werden neue Aufnahmen bei vollem Limit abgelehnt oder beendet. Exportierte Kopien benötigen zusätzlich Speicher. Fotos und Videos liegen auch unter Download/camera streamer, soweit der Receiver den Export unterstützt.");
    }
    private void system(){
        heading(content,"Verbindung und Betrieb");
        Ui.toggle(content,"Betroffene Kamera automatisch neu verbinden",repo.recover()).setOnCheckedChangeListener((b,on)->repo.recover(on));
        note(content,"Wiederholte Fehler: 2, 4, 8, 16, 32 und höchstens 60 Sekunden Abstand. Stehende Videobilder werden überwacht. Anmeldung, Netzwerkfehler und nicht unterstützte Formate werden unterschieden.");
        Ui.toggle(content,"Bildschirm während der Wiedergabe eingeschaltet lassen",repo.keepAwake()).setOnCheckedChangeListener((b,on)->repo.keepAwake(on));
        heading(content,"Sicherung");open(content,"Sicherung & Wiederherstellung öffnen",BackupActivity.class);
        note(content,"Passwortverschlüsselte Sicherung von Kameras, Ansichten und Einstellungen. Konten und vollständige URLs sind optional. Fotos und Videos sind nicht enthalten; wichtige Sicherungsdateien zusätzlich außerhalb des Receivers aufbewahren.");
        heading(content,"App");note(content,"camera streamer "+BuildConfig.VERSION_NAME+" · Android TV / Google TV\nKeine Cloud-Anmeldung. Kamerazugänge liegen verschlüsselt auf diesem Receiver.");
    }
    private AdapterView.OnItemSelectedListener listener(java.util.function.IntConsumer consumer){return new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> parent){}public void onItemSelected(AdapterView<?> parent,View view,int position,long id){consumer.accept(position);}};}
}
