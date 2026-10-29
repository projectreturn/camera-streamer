package de.local.tapoviewer;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

/** Focusable thumbnail gallery; never loads full-resolution images on the UI thread. */
public final class MediaLibraryActivity extends ViewerActivity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final android.util.LruCache<String,Bitmap> images=new android.util.LruCache<String,Bitmap>(8*1024*1024){@Override protected int sizeOf(String key,Bitmap bitmap){return bitmap.getAllocationByteCount();}};
    private RecyclerView grid;private TextView empty;private List<File> files=new ArrayList<>();private String selected="";private boolean alive=true;
    @Override protected void onCreate(Bundle state){super.onCreate(state);LinearLayout root=Ui.page(this,"Aufnahmen");Ui.add(root,Ui.text(this,"Mit den Pfeiltasten auswählen · OK: groß öffnen · Langes OK: löschen",14,ViewerStyle.MUTED),-2);FrameLayout content=new FrameLayout(this);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));grid=new RecyclerView(this);grid.setLayoutManager(new GridLayoutManager(this,3));grid.setHasFixedSize(true);content.addView(grid,new FrameLayout.LayoutParams(-1,-1));empty=Ui.text(this,"Noch keine Aufnahmen.\nIm Livebild mit Gelb ein Foto oder mit Rot ein Video speichern.",20,ViewerStyle.MUTED);empty.setGravity(Gravity.CENTER);content.addView(empty,new FrameLayout.LayoutParams(-1,-1));setContentView(root);}
    @Override protected void onResume(){super.onResume();refresh();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==40&&result==RESULT_OK&&data!=null)selected=data.getStringExtra("file");}
    private void refresh(){files=LocalMedia.list(this);empty.setVisibility(files.isEmpty()?View.VISIBLE:View.GONE);grid.setAdapter(new RecyclerView.Adapter<Card>(){
        @Override public Card onCreateViewHolder(ViewGroup parent,int type){LinearLayout card=Ui.column(MediaLibraryActivity.this);card.setPadding(10,10,10,10);card.setBackgroundResource(R.drawable.focusable_panel);card.setFocusable(true);card.setFocusableInTouchMode(true);card.setClickable(true);ImageView image=new ImageView(MediaLibraryActivity.this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackgroundColor(ViewerStyle.PANEL);card.addView(image,Ui.lp(-1,Ui.dp(MediaLibraryActivity.this,132)));TextView title=Ui.text(MediaLibraryActivity.this,"",14,-1);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);card.addView(title,Ui.lp(-1,Ui.dp(MediaLibraryActivity.this,25)));TextView details=Ui.text(MediaLibraryActivity.this,"",12,ViewerStyle.MUTED);card.addView(details,Ui.lp(-1,Ui.dp(MediaLibraryActivity.this,22)));RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(-1,Ui.dp(MediaLibraryActivity.this,195));lp.setMargins(6,6,6,6);card.setLayoutParams(lp);card.setOnFocusChangeListener((v,focus)->{title.setTextColor(focus?ViewerStyle.BACKGROUND:-1);details.setTextColor(focus?ViewerStyle.BACKGROUND:ViewerStyle.MUTED);});return new Card(card,image,title,details);}
        @Override public void onBindViewHolder(Card holder,int position){if(holder.task!=null)holder.task.cancel(false);File file=files.get(position);String key=file.getName()+":"+file.lastModified()+":"+file.length();holder.image.setTag(key);holder.image.setImageBitmap(null);holder.title.setText(file.getName());holder.details.setText((file.getName().endsWith(".mp4")?"Video":"Foto")+" · "+new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm",Locale.GERMANY).format(new Date(file.lastModified())));holder.itemView.setContentDescription((file.getName().endsWith(".mp4")?"Video öffnen · ":"Foto öffnen · ")+file.getName());holder.itemView.setOnClickListener(v->{selected=file.getName();startActivityForResult(new Intent(MediaLibraryActivity.this,MediaViewerActivity.class).putExtra("file",file.getName()),40);});holder.itemView.setOnLongClickListener(v->{new AlertDialog.Builder(MediaLibraryActivity.this).setTitle("Aufnahme löschen?").setMessage(file.getName()).setNegativeButton("Abbrechen",null).setPositiveButton("Löschen",(d,w)->{LocalMedia.delete(MediaLibraryActivity.this,file);images.remove(key);refresh();}).show();return true;});Bitmap cached=images.get(key);if(cached!=null)holder.image.setImageBitmap(cached);else holder.task=worker.submit(()->{try{Bitmap bitmap=MediaImages.load(file,480,270);if(bitmap==null)return;images.put(key,bitmap);runOnUiThread(()->{if(alive&&key.equals(holder.image.getTag()))holder.image.setImageBitmap(bitmap);});}catch(Exception ignored){runOnUiThread(()->{if(alive&&key.equals(holder.image.getTag()))holder.image.setContentDescription("Vorschau nicht verfügbar");});}});if(file.getName().equals(selected))holder.itemView.post(holder.itemView::requestFocus);}
        @Override public int getItemCount(){return files.size();}
        @Override public void onViewRecycled(Card holder){holder.image.setTag(null);holder.image.setImageBitmap(null);if(holder.task!=null)holder.task.cancel(false);super.onViewRecycled(holder);}
    });if(selected!=null)for(int i=0;i<files.size();i++)if(files.get(i).getName().equals(selected)){grid.scrollToPosition(i);break;}}
    @Override protected void onDestroy(){alive=false;worker.shutdownNow();images.evictAll();super.onDestroy();}
    private static final class Card extends RecyclerView.ViewHolder{final ImageView image;final TextView title,details;Future<?> task;Card(View view,ImageView image,TextView title,TextView details){super(view);this.image=image;this.title=title;this.details=details;}}
}
