package io.unifiedtv.preview;
import android.graphics.*;
import java.util.*;

/**
 * Full-screen scenes, rendered once and cached small: a dissolve is then two bitmap draws, not two full-screen gradient
 * meshes through an off-screen layer each frame (spec 4.12, the slower TCL and Sony). The vignette is baked in.
 */
final class Backdrops {
    /**
     * Size of a scene: 640 x 360, or 320 x 180 on a 2 GB TV (MemoryBudget). Either is a soft picture magnified to the
     * screen (a mesh still, or a 32 x 18 poster expanded), so the smaller one looks the same and costs a quarter.
     */
    static int width(){return MemoryBudget.current().backdropWidth();}
    static int height(){return width()*9/16;}
    private final int kept=MemoryBudget.current().backdropsKept();
    private final LinkedHashMap<String,Bitmap> cache=new LinkedHashMap<String,Bitmap>(8,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,Bitmap> e){return size()>kept;}
    };
    /** A still frame of the focused app's ambient mesh (an app or light id), for the lightweight mode (spec 32.5 fallback). */
    synchronized Bitmap app(String id){
        Bitmap b=cache.get("app:"+id);if(b!=null)return b;
        int w=width(),h=height();b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);MaterialScene.mesh(c,w,h,id);cache.put("app:"+id,b);return b;
    }
    synchronized Bitmap get(String key){return cache.get(key);}
    synchronized void put(String key,Bitmap b){cache.put(key,b);}
    /** onTrimMemory: forget every scene but the one on screen ({@code keep}: a scene key, an app id, or null for none). */
    synchronized void trim(String keep){cache.keySet().removeIf(k->keep==null||!(k.equals(keep)||k.equals("app:"+keep)||k.startsWith("app:"+keep+"~")));}
    synchronized int size(){return cache.size();}
    /**
     * Spec 4.6 source order, third step: a soft, darkened, blurred expansion of the same title's own poster. Never another
     * title's image. Runs off the UI thread; blur is a cheap down-and-up scale, not a per-pixel filter.
     */
    static Bitmap fromPoster(Bitmap poster){
        Bitmap tiny=Bitmap.createBitmap(32,18,Bitmap.Config.ARGB_8888);Canvas t=new Canvas(tiny);Paint p=new Paint(Paint.FILTER_BITMAP_FLAG);
        float scale=Math.max(32f/poster.getWidth(),18f/poster.getHeight());float w=poster.getWidth()*scale,h=poster.getHeight()*scale;
        t.drawBitmap(poster,null,new RectF((32-w)/2,(18-h)/2,(32+w)/2,(18+h)/2),p);
        int sw=width(),sh=height();Bitmap out=Bitmap.createBitmap(sw,sh,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(out);
        Bitmap mid=Bitmap.createScaledBitmap(tiny,160,90,true);c.drawBitmap(mid,null,new RectF(0,0,sw,sh),p);mid.recycle();tiny.recycle();
        Paint dim=new Paint();dim.setColor(0x73060a12);c.drawRect(0,0,sw,sh,dim);vignette(c,sw,sh);return out;
    }
    static void vignette(Canvas c,int sw,int sh){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(sw*.5f,sh*.42f,sw*.72f,new int[]{0x00000000,0x00000000,0x8c03060c},new float[]{0,.55f,1},Shader.TileMode.CLAMP));
        c.drawRect(0,0,sw,sh,p);
    }
}
