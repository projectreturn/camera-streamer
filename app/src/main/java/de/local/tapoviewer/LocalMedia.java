package de.local.tapoviewer;

import android.content.Context;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

final class LocalMedia {
    private static final java.util.Set<File> active=java.util.concurrent.ConcurrentHashMap.newKeySet();
    static void pin(File file){active.add(file);}static void unpin(File file){active.remove(file);}
    static final String EXPORT_FOLDER="Download/camera streamer";
    static File directory(Context context) {
        File parent=context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES);
        if(parent==null) parent=context.getFilesDir();
        File folder=new File(parent,"captures"); if(!folder.exists()) folder.mkdirs(); return folder;
    }
    static File create(Context context,String name,String extension) {
        String safe=name.replaceAll("[^\\p{L}\\p{N}_-]","_");
        return new File(directory(context),safe+"_"+new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US).format(new Date())+extension);
    }
    static String storageSummary(Context context){File folder=directory(context);long used=0;for(File f:list(context))used+=f.length();return String.format(java.util.Locale.GERMANY,"App-Medien: %.1f MB · frei: %.1f GB",used/1048576.0,folder.getUsableSpace()/1073741824.0);}
    static void ensureSpace(Context context,long reserve)throws IOException{
        CameraRepository repo=new CameraRepository(context);long limit;try{limit=Long.parseLong(repo.option("storage_mb","0"))*1048576L;}catch(Exception e){limit=0;}
        List<File> files=list(context);long used=0;for(File f:files)used+=f.length();for(File f:active)used+=f.length();
        if(repo.option("auto_delete","false").equals("true"))for(int i=files.size()-1;i>=0 && ((limit>0&&used+reserve>limit)||directory(context).getUsableSpace()<reserve+32*1048576L);i--){File f=files.get(i);if(active.contains(f))continue;long size=f.length();delete(context,f);if(!f.exists())used-=size;}
        if((limit>0&&used+reserve>limit)||directory(context).getUsableSpace()<reserve+32*1048576L)throw new IOException("Speicherlimit erreicht oder zu wenig freier Speicher");
    }
    static File snapshot(Context context,String name,Bitmap bitmap) throws Exception {
        ensureSpace(context,4*1048576L);
        File file=create(context,name,".jpg");
        try(OutputStream out=new FileOutputStream(file)) { if(!bitmap.compress(Bitmap.CompressFormat.JPEG,92,out)) throw new IOException(); }
        export(context,file); return file;
    }
    static List<File> list(Context context) {
        File[] files=directory(context).listFiles((d,n)->n.endsWith(".jpg")||n.endsWith(".mp4"));
        List<File> result=new ArrayList<>(); if(files!=null) Collections.addAll(result,files);
        result.removeIf(active::contains);
        result.sort((a,b)->Long.compare(b.lastModified(),a.lastModified())); return result;
    }
    static void export(Context context,File file) {
        if(Build.VERSION.SDK_INT<29) return;
        Uri uri=null;
        try {
            ContentValues values=new ContentValues(); values.put(MediaStore.MediaColumns.DISPLAY_NAME,file.getName());
            values.put(MediaStore.MediaColumns.MIME_TYPE,file.getName().endsWith(".jpg")?"image/jpeg":"video/mp4");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,EXPORT_FOLDER); values.put(MediaStore.MediaColumns.IS_PENDING,1);
            uri=context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
            if(uri==null) return;
            try(InputStream in=new FileInputStream(file);OutputStream out=context.getContentResolver().openOutputStream(uri)) {
                byte[] buffer=new byte[16384]; int n; while((n=in.read(buffer))!=-1) out.write(buffer,0,n);
            }
            values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING,0); context.getContentResolver().update(uri,values,null,null);
            context.getSharedPreferences("media_exports",0).edit().putString(file.getName(),uri.toString()).apply();
        } catch(Exception e) { if(uri!=null) try { context.getContentResolver().delete(uri,null,null); } catch(Exception ignored) { } }
    }
    static void delete(Context context,File file) {
        // Only a selected file directly inside the app's capture directory can be removed.
        if(!file.getParentFile().equals(directory(context))) return;
        String exported=context.getSharedPreferences("media_exports",0).getString(file.getName(),"");
        if(!exported.isEmpty()) try { context.getContentResolver().delete(Uri.parse(exported),null,null); } catch(Exception ignored) { }
        if(file.delete()) context.getSharedPreferences("media_exports",0).edit().remove(file.getName()).apply();
    }
}
