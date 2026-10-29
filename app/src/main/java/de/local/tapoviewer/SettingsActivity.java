package de.local.tapoviewer;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.recyclerview.widget.*;
import java.util.*;

public final class SettingsActivity extends ViewerActivity {
    public static final String EXTRA_FIRST_RUN="first_run";
    private final List<Camera> cameras=new ArrayList<>();
    private CameraRepository repository;
    private RecyclerView list;
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved); repository=new CameraRepository(this);
        LinearLayout root=Ui.page(this,"Kameras verwalten");
        LinearLayout tools=Ui.row(this);
        tools.addView(Ui.text(this,"Aktiv / Favorit direkt · ✎ bearbeiten · ✎ lange drücken: Reihenfolge",13,ViewerStyle.MUTED),new LinearLayout.LayoutParams(0,-2,1));
        tools.addView(Ui.button(this,"＋ Kamera hinzufügen",()->edit(null)),Ui.lp(Ui.dp(this,220),Ui.dp(this,46))); Ui.add(root,tools,54);
        list=new RecyclerView(this); list.setLayoutManager(new GridLayoutManager(this,2));
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);
        refresh();
    }
    @Override protected void onResume() { super.onResume(); if(repository!=null) refresh(); }
    private void refresh() {
        cameras.clear(); boolean favorites=getIntent().getBooleanExtra("favorites",false);
        for(Camera c:repository.loadCameras()) if(!favorites||c.favorite) cameras.add(c);
        list.setAdapter(new RecyclerView.Adapter<Holder>() {
            @Override public Holder onCreateViewHolder(ViewGroup parent,int type) {
                LinearLayout card=Ui.column(SettingsActivity.this); card.setPadding(Ui.dp(SettingsActivity.this,8),Ui.dp(SettingsActivity.this,6),Ui.dp(SettingsActivity.this,8),Ui.dp(SettingsActivity.this,6));
                card.setBackgroundResource(R.drawable.tile_border);
                TextView label=Ui.text(SettingsActivity.this,"",15,Color.WHITE); label.setSingleLine(true); label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                card.addView(label,Ui.lp(-1,Ui.dp(SettingsActivity.this,19)));
                TextView detail=Ui.text(SettingsActivity.this,"",11,ViewerStyle.MUTED); detail.setSingleLine(true); detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
                card.addView(detail,Ui.lp(-1,Ui.dp(SettingsActivity.this,16)));
                LinearLayout actions=Ui.row(SettingsActivity.this); card.addView(actions,Ui.lp(-1,Ui.dp(SettingsActivity.this,36)));
                Switch active=new Switch(SettingsActivity.this); active.setText("Aktiv"); active.setTextColor(Color.WHITE); active.setTextSize(12); active.setSwitchPadding(Ui.dp(SettingsActivity.this,8));
                active.setThumbTintList(new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_checked},{}},new int[]{ViewerStyle.ACCENT,ViewerStyle.MUTED}));
                LinearLayout.LayoutParams activeLp=Ui.lp(Ui.dp(SettingsActivity.this,110),-1); activeLp.rightMargin=Ui.dp(SettingsActivity.this,12); actions.addView(active,activeLp);
                Button play=Ui.button(SettingsActivity.this,"▶",()->{}),favorite=Ui.button(SettingsActivity.this,"♡",()->{}),edit=Ui.button(SettingsActivity.this,"✎",()->{}),delete=Ui.button(SettingsActivity.this,"⌫",()->{});
                for(Button b:new Button[]{play,favorite,edit,delete}) { b.setMinWidth(0); b.setMinimumWidth(0); b.setPadding(0,0,0,0); b.setTextSize(17); actions.addView(b,new LinearLayout.LayoutParams(0,Ui.dp(SettingsActivity.this,34),1)); }
                RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(-1,Ui.dp(SettingsActivity.this,86)); int margin=Ui.dp(SettingsActivity.this,2); lp.setMargins(margin,margin,margin,margin); card.setLayoutParams(lp);
                return new Holder(card,label,detail,active,play,favorite,edit,delete);
            }
            @Override public void onBindViewHolder(Holder h,int position) {
                Camera c=cameras.get(position);
                h.label.setText(c.name);
                h.detail.setText(c.ip+"  ·  "+c.protocol+"  ·  "+(new SecureCredentialsStore(SettingsActivity.this).hasCredentials(c.id)?"Konto gespeichert":"Kein Konto"));
                h.itemView.setContentDescription(c.name+", "+c.ip+", "+c.protocol);
                h.active.setContentDescription(c.name+" aktivieren oder deaktivieren");
                h.active.setOnCheckedChangeListener(null); h.active.setChecked(c.enabled);
                h.active.setOnCheckedChangeListener((b,on)->{ c.enabled=on; repository.upsert(c); });
                h.favorite.setText(c.favorite?"♥":"♡"); h.favorite.setSelected(c.favorite);
                h.favorite.setContentDescription(c.name+" als Favorit markieren");
                h.favorite.setOnClickListener(v->{ c.favorite=!c.favorite; repository.upsert(c); h.favorite.setText(c.favorite?"♥":"♡"); h.favorite.setSelected(c.favorite); });
                h.edit.setContentDescription("Kamera bearbeiten"); h.edit.setOnClickListener(v->edit(c));
                h.itemView.setOnLongClickListener(v->{new AlertDialog.Builder(SettingsActivity.this).setTitle(c.name+" verschieben").setItems(new String[]{"Eine Position nach vorn","Eine Position nach hinten"},(d,n)->{repository.move(c.id,n==0?-1:1);refresh();}).show();return true;});
                h.edit.setOnLongClickListener(v->{new AlertDialog.Builder(SettingsActivity.this).setTitle(c.name+" verschieben").setItems(new String[]{"Nach vorn","Nach hinten"},(d,n)->{repository.move(c.id,n==0?-1:1);refresh();}).show();return true;});
                h.play.setContentDescription("Livebild öffnen"); h.play.setOnClickListener(v->startActivity(new Intent(SettingsActivity.this,PlayerActivity.class).putExtra(PlayerActivity.EXTRA_IP,c.id)));
                h.delete.setContentDescription("Kamera löschen"); h.delete.setOnClickListener(v->new AlertDialog.Builder(SettingsActivity.this).setTitle("Kamera entfernen?").setMessage(c.name+" und die zugehörigen Zugangsdaten werden vom Receiver entfernt.")
                        .setNegativeButton("Abbrechen",null).setPositiveButton("Entfernen",(d,w)->{repository.remove(c.id); refresh();}).show());
            }
            @Override public int getItemCount() { return cameras.size(); }
        });
    }
    private void edit(Camera c) { Intent intent=new Intent(this,CameraEditorActivity.class); if(c!=null) intent.putExtra("id",c.id); startActivity(intent); }
    @Override public void finish() { if(repository!=null) repository.setSetupComplete(true); super.finish(); }
    private static final class Holder extends RecyclerView.ViewHolder {
        TextView label,detail; Switch active; Button play,favorite,edit,delete;
        Holder(View view,TextView label,TextView detail,Switch active,Button play,Button favorite,Button edit,Button delete) { super(view); this.label=label; this.detail=detail; this.active=active; this.play=play; this.favorite=favorite; this.edit=edit; this.delete=delete; }
    }
}
