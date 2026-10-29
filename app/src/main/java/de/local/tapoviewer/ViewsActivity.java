package de.local.tapoviewer;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import org.json.*;
import java.util.*;

public final class ViewsActivity extends ViewerActivity {
    private CameraRepository repo;private LinearLayout content;
    static final int[] LAYOUTS={1,2,-2,4,6,9,16};
    static final String[] LABELS={"1 Kamera","2 nebeneinander","2 untereinander","4 Kameras · 2 × 2","6 Kameras · 3 × 2","9 Kameras · 3 × 3","16 Kameras · 4 × 4"};
    static String layoutName(int layout){for(int i=0;i<LAYOUTS.length;i++)if(layout==LAYOUTS[i])return LABELS[i];return "Ansicht";}
    @Override public void onCreate(Bundle state){super.onCreate(state);repo=new CameraRepository(this);LinearLayout root=Ui.page(this,"Gespeicherte Ansichten");ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));content=Ui.column(this);scroll.addView(content);setContentView(root);refresh();}
    private void refresh(){content.removeAllViews();
        Ui.add(content,Ui.button(this,"Alle aktiven Kameras anzeigen",()->{repo.selectView("");repo.favoritesOnly(false);finish();}),46);
        Ui.add(content,Ui.button(this,"Neue Ansicht speichern",()->edit(null)),46);
        Ui.add(content,Ui.text(this,"Eigene Kameraauswahl und Raster speichern. Langes OK auf dem Rasterbutton öffnet diese Seite. Für einen automatischen Vollbildwechsel zuerst eine Ansicht auswählen, dann den Wechsel in den Einstellungen aktivieren.",14,ViewerStyle.MUTED),-2);
        JSONArray all=repo.views();for(int i=0;i<all.length();i++){JSONObject v=all.optJSONObject(i);if(v==null)continue;String name=v.optString("name");LinearLayout row=Ui.row(this);
            row.addView(Ui.button(this,(name.equals(repo.option("selected_view",""))?"✓ ":"")+name+" · "+layoutName(v.optInt("layout",4)),()->{repo.selectView(name);finish();}),new LinearLayout.LayoutParams(0,Ui.dp(this,46),1));
            row.addView(Ui.button(this,"Bearbeiten",()->edit(v)),Ui.lp(Ui.dp(this,130),Ui.dp(this,46)));
            row.addView(Ui.button(this,"Löschen",()->new AlertDialog.Builder(this).setTitle("Ansicht löschen?").setMessage(name).setNegativeButton("Abbrechen",null).setPositiveButton("Löschen",(d,w)->{JSONArray next=new JSONArray();JSONArray current=repo.views();for(int n=0;n<current.length();n++)if(!current.optJSONObject(n).optString("name").equals(name))next.put(current.optJSONObject(n));repo.setOption("views",next.toString());if(name.equals(repo.option("selected_view","")))repo.selectView("");refresh();}).show()),Ui.lp(Ui.dp(this,110),Ui.dp(this,46)));Ui.add(content,row,50);
        }
    }
    private void edit(JSONObject existing){LinearLayout form=Ui.column(this);form.setPadding(24,16,24,16);EditText name=Ui.field(form,"Name",existing==null?"Neue Ansicht":existing.optString("name"),false);int layout=existing==null?repo.getTileCount():existing.optInt("layout",4);Spinner raster=Ui.select(form,"Raster",layoutName(layout),LABELS);List<Camera> cameras=repo.loadCameras();boolean[] chosen=new boolean[cameras.size()];JSONArray previous=existing==null?null:existing.optJSONArray("ids");
        List<CheckBox> checks=new ArrayList<>();for(int i=0;i<cameras.size();i++){Camera c=cameras.get(i);boolean selected=existing==null?c.enabled:false;if(previous!=null)for(int n=0;n<previous.length();n++)if(c.id.equals(previous.optString(n)))selected=true;chosen[i]=selected;CheckBox check=new CheckBox(this);check.setText(c.name+(c.enabled?"":" (deaktiviert)"));check.setTextColor(-1);check.setChecked(selected);form.addView(check);checks.add(check);}
        ScrollView scroll=new ScrollView(this);scroll.addView(form);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Ansicht speichern").setView(scroll).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{String label=name.getText().toString().trim();if(label.isEmpty()){name.setError("Namen eingeben");return;}List<String> ids=new ArrayList<>();for(int i=0;i<checks.size();i++)if(checks.get(i).isChecked())ids.add(cameras.get(i).id);if(ids.isEmpty()){Ui.toast(this,"Mindestens eine Kamera wählen");return;}try{repo.saveView(label,ids,LAYOUTS[raster.getSelectedItemPosition()],false);if(existing!=null&&!label.equals(existing.optString("name"))){String oldName=existing.optString("name");JSONArray remaining=new JSONArray();JSONArray current=repo.views();for(int i=0;i<current.length();i++)if(!current.optJSONObject(i).optString("name").equals(oldName))remaining.put(current.optJSONObject(i));repo.setOption("views",remaining.toString());if(repo.option("selected_view","").equals(oldName))repo.selectView(label);}dialog.dismiss();refresh();}catch(Exception e){Ui.toast(this,e.getMessage());}}));dialog.show();}
}
