package io.unifiedtv.preview;
import android.graphics.*;
import java.util.*;

/**
 * Full-screen scenes, rendered once and cached small: a dissolve is then two bitmap draws, not two full-screen gradient
 * meshes through an off-screen layer each frame (spec 4.12, the slower TCL and Sony). The vignette is baked in.
 */
final class Backdrops {
    static final int SW=640,SH=360;
    private final LinkedHashMap<String,Bitmap> cache=new LinkedHashMap<String,Bitmap>(8,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,Bitmap> e){return size()>6;}
    };
    /** A still frame of the focused app's ambient mesh, for the lightweight mode (spec 32.5 fallback). */
    synchronized Bitmap app(String id){
        Bitmap b=cache.get("app:"+id);if(b!=null)return b;
        b=Bitmap.createBitmap(SW,SH,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);MaterialScene.mesh(c,SW,SH,id);cache.put("app:"+id,b);return b;
    }
    synchronized Bitmap get(String key){return cache.get(key);}
    synchronized void put(String key,Bitmap b){cache.put(key,b);}
    /**
     * Spec 4.6 source order, third step: a soft, darkened, blurred expansion of the same title's own poster. Never another
     * title's image. Runs off the UI thread; blur is a cheap down-and-up scale, not a per-pixel filter.
     */
    static Bitmap fromPoster(Bitmap poster){
        Bitmap tiny=Bitmap.createBitmap(32,18,Bitmap.Config.ARGB_8888);Canvas t=new Canvas(tiny);Paint p=new Paint(Paint.FILTER_BITMAP_FLAG);
        float scale=Math.max(32f/poster.getWidth(),18f/poster.getHeight());float w=poster.getWidth()*scale,h=poster.getHeight()*scale;
        t.drawBitmap(poster,null,new RectF((32-w)/2,(18-h)/2,(32+w)/2,(18+h)/2),p);
        Bitmap out=Bitmap.createBitmap(SW,SH,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(out);
        c.drawBitmap(Bitmap.createScaledBitmap(tiny,160,90,true),null,new RectF(0,0,SW,SH),p);
        Paint dim=new Paint();dim.setColor(0x73060a12);c.drawRect(0,0,SW,SH,dim);vignette(c);return out;
    }
    static void vignette(Canvas c){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(SW*.5f,SH*.42f,SW*.72f,new int[]{0x00000000,0x00000000,0x8c03060c},new float[]{0,.55f,1},Shader.TileMode.CLAMP));
        c.drawRect(0,0,SW,SH,p);
    }
}
