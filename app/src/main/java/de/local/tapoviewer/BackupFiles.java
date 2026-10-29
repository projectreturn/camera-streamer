package de.local.tapoviewer;

import android.content.*;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import java.io.*;
import java.util.*;

final class BackupFiles {
    static File directory(Context context){File folder=new File(context.getFilesDir(),"backups");if(!folder.exists()&&!folder.mkdirs())throw new IllegalStateException("Sicherungsordner nicht verfügbar");return folder;}
    static List<File> list(Context context){File[] files=directory(context).listFiles((dir,name)->name.endsWith(".csb"));List<File> result=new ArrayList<>();if(files!=null)Collections.addAll(result,files);result.sort((a,b)->Long.compare(b.lastModified(),a.lastModified()));return result;}
    static boolean save(Context context,byte[] encrypted)throws Exception{
        String name="camera-streamer-"+new java.text.SimpleDateFormat("yyyyMMdd-HHmmss-SSS",Locale.US).format(new Date())+".csb";File local=new File(directory(context),name);try(OutputStream out=new FileOutputStream(local)){out.write(encrypted);}
        if(Build.VERSION.SDK_INT<29)return false;Uri uri=null;try{ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.DISPLAY_NAME,name);values.put(MediaStore.MediaColumns.MIME_TYPE,"application/x-camera-streamer-backup");values.put(MediaStore.MediaColumns.RELATIVE_PATH,LocalMedia.EXPORT_FOLDER);values.put(MediaStore.MediaColumns.IS_PENDING,1);uri=context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);if(uri==null)return false;try(OutputStream out=context.getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException();out.write(encrypted);}values.clear();values.put(MediaStore.MediaColumns.IS_PENDING,0);context.getContentResolver().update(uri,values,null,null);return true;}catch(Exception e){if(uri!=null)try{context.getContentResolver().delete(uri,null,null);}catch(Exception ignored){}return false;}
    }
}
