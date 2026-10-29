package de.local.tapoviewer;
import android.graphics.*;
import android.media.MediaMetadataRetriever;
import java.io.File;
final class MediaImages {
    static Bitmap load(File file,int width,int height)throws Exception {
        if(file.getName().endsWith(".jpg")){BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getAbsolutePath(),options);options.inSampleSize=1;while(options.outWidth/options.inSampleSize>width*2||options.outHeight/options.inSampleSize>height*2)options.inSampleSize*=2;options.inJustDecodeBounds=false;return BitmapFactory.decodeFile(file.getAbsolutePath(),options);}
        MediaMetadataRetriever reader=new MediaMetadataRetriever();try{reader.setDataSource(file.getAbsolutePath());if(android.os.Build.VERSION.SDK_INT>=27)return reader.getScaledFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,width,height);Bitmap original=reader.getFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC);if(original==null)return null;float scale=Math.min(1f,Math.min(width/(float)original.getWidth(),height/(float)original.getHeight()));Bitmap result=Bitmap.createScaledBitmap(original,Math.max(1,Math.round(original.getWidth()*scale)),Math.max(1,Math.round(original.getHeight()*scale)),true);if(result!=original)original.recycle();return result;}finally{reader.release();}
    }
}
