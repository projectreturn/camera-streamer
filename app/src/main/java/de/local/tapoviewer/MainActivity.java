package de.local.tapoviewer;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class MainActivity extends ViewerActivity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final List<StreamTileView> streams=new ArrayList<>();
    private final List<GridLayout> grids=new ArrayList<>();
    private CameraRepository repo;
    private List<Camera> cameras=new ArrayList<>();
    private LinearLayout strip;
    private HorizontalScrollView pager;
    private TextView counter,empty;
    private Button favorite,layoutButton,audioButton;
    private String focusedCamera="";
    private int page,width,layout;
    private boolean resumed,dragging;
    private boolean launchedStart;
    private final Runnable automatic=()->changePage(page+1,false,true);
    private final Runnable settle=()->{
        if(width>0 && !grids.isEmpty()) changePage(Math.round(pager.getScrollX()/(float)width),false,false);
    };
    @Override protected void onCreate(Bundle state) { super.onCreate(state); repo=new CameraRepository(this); build(); }
    @Override protected void onResume() {
        super.onResume(); resumed=true;
        if(repo.keepAwake()) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(!repo.isSetupComplete()) {
            repo.setSetupComplete(true);
            startActivity(new Intent(this,CameraEditorActivity.class).putExtra("scan_first",true)); return;
        }
        reload();
        if(!launchedStart){launchedStart=true;String start=repo.option("start","");if(start.equals("favorites")){repo.selectView("");repo.favoritesOnly(true);reload();}else if(start.startsWith("camera:")){Camera c=repo.find(start.substring(7));if(c!=null&&c.enabled)startActivity(new Intent(this,PlayerActivity.class).putExtra(PlayerActivity.EXTRA_IP,c.id));}}
    }
    @Override protected void onPause() { resumed=false; handler.removeCallbacksAndMessages(null); release(); super.onPause(); }
    private void build() {
        LinearLayout root=Ui.column(this); root.setPadding(Ui.dp(this,24),Ui.dp(this,16),Ui.dp(this,24),Ui.dp(this,16)); ViewerStyle.background(root);
        FrameLayout header=new FrameLayout(this);
        LinearLayout top=Ui.row(this);
        TextView title=Ui.text(this,getString(R.string.app_name),24,android.graphics.Color.WHITE);
        title.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams titleLp=new FrameLayout.LayoutParams(-2,-1,Gravity.START|Gravity.CENTER_VERTICAL); header.addView(title,titleLp);
        counter=Ui.text(this,"",14,ViewerStyle.MUTED); counter.setGravity(Gravity.CENTER);
        header.addView(counter,new FrameLayout.LayoutParams(-2,-1,Gravity.CENTER));
        layoutButton=Ui.iconButton(this,"grid","Ansicht wechseln",()->{repo.setTileCount(CameraPages.nextLayout(repo.getTileCount()));page=0;reload();layoutButton.requestFocus();Ui.toast(this,ViewsActivity.layoutName(repo.getTileCount()));});
        layoutButton.setOnLongClickListener(v->{startActivity(new Intent(this,ViewsActivity.class));return true;});
        top.addView(layoutButton,Ui.lp(Ui.dp(this,52),Ui.dp(this,44)));
        audioButton=Ui.iconButton(this,"audio","Rasterton auswählen",this::chooseAudio);top.addView(audioButton,Ui.lp(Ui.dp(this,52),Ui.dp(this,44)));
        Button media=Ui.iconButton(this,"media","Aufnahmen öffnen",()->startActivity(new Intent(this,MediaLibraryActivity.class)));
        LinearLayout.LayoutParams mediaLp=Ui.lp(Ui.dp(this,52),Ui.dp(this,44)); mediaLp.rightMargin=Ui.dp(this,8); top.addView(media,mediaLp);
        favorite=Ui.button(this,"♡",()->{repo.favoritesOnly(!repo.favoritesOnly()); page=0; reload(); favorite.requestFocus();});
        favorite.setContentDescription("Nur Favoriten anzeigen"); top.addView(favorite,Ui.lp(Ui.dp(this,52),Ui.dp(this,44)));
        Button settings=Ui.button(this,"⚙",()->startActivity(new Intent(this,AppSettingsActivity.class)));
        settings.setContentDescription("Einstellungen öffnen"); top.addView(settings,Ui.lp(Ui.dp(this,52),Ui.dp(this,44)));
        header.addView(top,new FrameLayout.LayoutParams(-2,-1,Gravity.END|Gravity.CENTER_VERTICAL)); Ui.add(root,header,48);
        FrameLayout content=new FrameLayout(this); root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        pager=new HorizontalScrollView(this){
            @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) { super.onSizeChanged(w,h,oldw,oldh); if(w!=width) {width=w; post(()->reload());} }
            @Override protected void onScrollChanged(int x,int y,int ox,int oy) { super.onScrollChanged(x,y,ox,oy); handler.removeCallbacks(settle); if(!dragging) handler.postDelayed(settle,220); }
            @Override public boolean onTouchEvent(MotionEvent event) {
                if(event.getActionMasked()==MotionEvent.ACTION_DOWN) {dragging=true; handler.removeCallbacks(automatic); handler.removeCallbacks(settle);}
                boolean handled=super.onTouchEvent(event);
                if(event.getActionMasked()==MotionEvent.ACTION_UP || event.getActionMasked()==MotionEvent.ACTION_CANCEL) {dragging=false; handler.postDelayed(settle,220); schedule();}
                return handled;
            }
        };
        pager.setSmoothScrollingEnabled(true); pager.setHorizontalScrollBarEnabled(false); pager.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        strip=Ui.row(this); pager.addView(strip,new HorizontalScrollView.LayoutParams(-2,-1)); content.addView(pager,new FrameLayout.LayoutParams(-1,-1));
        empty=Ui.text(this,"",21,ViewerStyle.MUTED); empty.setGravity(Gravity.CENTER); content.addView(empty,new FrameLayout.LayoutParams(-1,-1)); setContentView(root);
    }
    private void reload() {
        if(!resumed || width==0) return;
        handler.removeCallbacks(settle); release(); strip.removeAllViews(); grids.clear();
        layout=repo.getTileCount(); cameras=repo.liveCameras();
        favorite.setText(repo.favoritesOnly()?"♥":"♡"); favorite.setSelected(repo.favoritesOnly());
        layoutButton.setContentDescription("Ansicht wechseln · "+(layout==-2?"2 untereinander":layout+" Kameras"));
        int count=CameraPages.count(cameras.size(),layout); page=Math.max(0,Math.min(page,count-1));
        empty.setText(repo.favoritesOnly()?"Keine aktiven Favoriten\nKameras unter ⚙ als Favorit markieren oder ♥ ausschalten.":"Keine aktiven Kameras\nKameras unter ⚙ hinzufügen oder aktivieren.");
        empty.setVisibility(count==0?View.VISIBLE:View.GONE);
        for(int p=0;p<count;p++) {
            GridLayout grid=new GridLayout(this); grid.setColumnCount(CameraPages.columns(layout)); grid.setRowCount((CameraPages.size(layout)+CameraPages.columns(layout)-1)/CameraPages.columns(layout));
            grids.add(grid); strip.addView(grid,new LinearLayout.LayoutParams(width,-1));
        }
        render(); pager.scrollTo(page*width,0); schedule();
    }
    private void render() {
        release(); int size=CameraPages.size(layout),cols=CameraPages.columns(layout);
        for(int p=0;p<grids.size();p++) {
            GridLayout grid=grids.get(p); grid.removeAllViews();
            for(int i=0;i<size;i++) {
                int index=p*size+i; View tile;
                if(index<cameras.size() && p==page && resumed) {
                    Camera c=cameras.get(index); StreamTileView live=new StreamTileView(this,c); live.setOnClickListener(v->startActivity(new Intent(this,PlayerActivity.class).putExtra(PlayerActivity.EXTRA_IP,c.id))); streams.add(live); tile=live;
                    live.setOnFocusChangeListener((v,focused)->{if(focused){focusedCamera=c.id;applyAudio();}});
                } else {
                    TextView label=Ui.text(this,index<cameras.size()?cameras.get(index).name:"Freier Platz",16,ViewerStyle.MUTED); label.setGravity(Gravity.CENTER); label.setBackgroundResource(R.drawable.tile_border); label.setFocusable(true); tile=label;
                }
                final int column=i%cols,row=i/cols;
                tile.setOnKeyListener((v,key,event)->{
                    if(event.getAction()!=KeyEvent.ACTION_DOWN) return false;
                    if(key==KeyEvent.KEYCODE_DPAD_RIGHT && column==cols-1 && page<grids.size()-1) {changePage(page+1,true,false); return true;}
                    if(key==KeyEvent.KEYCODE_DPAD_LEFT && column==0 && page>0) {changePage(page-1,true,false); return true;}
                    return false;
                });
                GridLayout.LayoutParams lp=new GridLayout.LayoutParams(GridLayout.spec(row,1,1f),GridLayout.spec(column,1,1f)); lp.width=0; lp.height=0; lp.setGravity(Gravity.FILL); lp.setMargins(4,4,4,4); grid.addView(tile,lp);
            }
        }
        for(StreamTileView stream:streams) stream.start();
        boolean focusVisible=false;for(StreamTileView stream:streams)if(stream.cameraId().equals(focusedCamera))focusVisible=true;
        if(!focusVisible)focusedCamera=streams.isEmpty()?"":streams.get(0).cameraId();applyAudio();
        counter.setText(grids.isEmpty()?"0 / 0":(page+1)+" / "+grids.size());
    }
    private void changePage(int target,boolean focus,boolean wrap) {
        if(grids.isEmpty() || !resumed) return;
        int dest=wrap?Math.floorMod(target,grids.size()):Math.max(0,Math.min(target,grids.size()-1));
        if(dest!=page) {page=dest; render();}
        pager.smoothScrollTo(page*width,0);
        if(focus && !streams.isEmpty()) streams.get(0).requestFocus();
        schedule();
    }
    private void schedule() {handler.removeCallbacks(automatic); if(resumed && repo.autoScroll() && grids.size()>1) handler.postDelayed(automatic,repo.scrollSeconds()*1000L);}
    @Override public boolean dispatchKeyEvent(KeyEvent event) {if(event.getAction()==KeyEvent.ACTION_DOWN) schedule(); return super.dispatchKeyEvent(event);}
    private void release() { for(StreamTileView stream:streams) stream.release(); streams.clear(); }
    private void applyAudio(){
        List<String> visible=new ArrayList<>();for(StreamTileView stream:streams)if(stream.supportsAudio())visible.add(stream.cameraId());
        String owner=GridAudioPolicy.owner(repo.option("grid_audio","off"),visible,focusedCamera);
        for(StreamTileView stream:streams)if(!stream.cameraId().equals(owner))stream.setAudible(false);
        for(StreamTileView stream:streams)if(stream.cameraId().equals(owner))stream.setAudible(true);
        if(audioButton!=null){audioButton.setSelected(!owner.isEmpty());Camera selected=null;for(Camera c:cameras)if(c.id.equals(owner))selected=c;audioButton.setContentDescription(selected==null?"Rasterton aus · Tonkamera auswählen":"Rasterton · "+selected.name+" · Tonkamera auswählen");}
    }
    private void chooseAudio(){List<String> labels=new ArrayList<>(Arrays.asList("Ton aus","Ton folgt der markierten Kamera")),values=new ArrayList<>(Arrays.asList("off","focus"));for(Camera c:cameras)if(!"MJPEG".equals(c.protocol)){labels.add("Nur "+c.name+" (wenn sichtbar)");values.add("camera:"+c.id);}int selected=Math.max(0,values.indexOf(repo.option("grid_audio","off")));new android.app.AlertDialog.Builder(this).setTitle("Ton im Kameraraster").setSingleChoiceItems(labels.toArray(new String[0]),selected,(d,n)->{repo.setOption("grid_audio",values.get(n));applyAudio();Ui.toast(this,labels.get(n));d.dismiss();}).setNegativeButton("Schließen",null).show();}
}
