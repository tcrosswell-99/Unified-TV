package io.unifiedtv.preview;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/**
 * Pictures decoded at the size they are shown (spec 14: bounded memory). A picture is never decoded larger than its box,
 * and never smaller than the box allows: the decoder reads it at the largest power-of-two step that stays at least as big,
 * then scales it to fit exactly, in one pass and without a second copy. On a 2 GB TV (MemoryBudget), JPEGs, which are
 * always opaque, are kept at 16 bits a pixel: half the memory, and nothing transparent to lose.
 */
final class Images {
    private Images(){}
    static Bitmap decode(byte[] data,int width,int height){return decode(data,width,height,false);}
    static Bitmap decode(byte[] data,int width,int height,boolean opaque565){
        BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,opts);
        if(opts.outWidth<=0||opts.outHeight<=0)return null;
        int[] plan=plan(opts.outWidth,opts.outHeight,width,height);
        boolean jpeg="image/jpeg".equals(opts.outMimeType);
        opts=new BitmapFactory.Options();opts.inSampleSize=plan[0];
        if(plan[3]!=plan[1]){opts.inScaled=true;opts.inDensity=plan[3];opts.inTargetDensity=plan[1];}
        if(opaque565&&jpeg){opts.inPreferredConfig=Bitmap.Config.RGB_565;opts.inDither=true;}
        Bitmap out=BitmapFactory.decodeByteArray(data,0,data.length,opts);
        // The size step above uses the decoder's density scaling; the picture itself keeps the screen's density as before.
        if(out!=null&&opts.inScaled&&opts.inDensity>0)out.setDensity(android.content.res.Resources.getSystem().getDisplayMetrics().densityDpi);
        return out;
    }
    /**
     * Pure: {sample size, final width, final height, width after sampling} for a {@code w} x {@code h} picture shown within
     * {@code maxW} x {@code maxH} (aspect kept; never enlarged). The sample step keeps the picture at least as large as it
     * will be shown, so the final scale only ever shrinks it.
     */
    static int[] plan(int w,int h,int maxW,int maxH){
        double fit=Math.min(1.0,Math.min(maxW/(double)w,maxH/(double)h));
        int outW=Math.max(1,(int)Math.round(w*fit)),outH=Math.max(1,(int)Math.round(h*fit));
        int sample=1;while(w/(sample*2)>=outW&&h/(sample*2)>=outH)sample*=2;
        return new int[]{sample,outW,outH,w/sample};
    }
}
