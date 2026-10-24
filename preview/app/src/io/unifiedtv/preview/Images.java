package io.unifiedtv.preview;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
final class Images {
    static Bitmap decode(byte[] data,int width,int height){
        BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,opts);
        if(opts.outWidth<=0||opts.outHeight<=0)return null;
        opts.inJustDecodeBounds=false;opts.inSampleSize=1;
        while(opts.outWidth/opts.inSampleSize>width||opts.outHeight/opts.inSampleSize>height)opts.inSampleSize*=2;
        return BitmapFactory.decodeByteArray(data,0,data.length,opts);
    }
}
