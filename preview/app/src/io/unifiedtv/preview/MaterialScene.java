package io.unifiedtv.preview;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Original ambient mesh and cached, low-resolution frosted material for API 28+. */
final class MaterialScene {
    // One small blurred frame per scene, kept (about 57 KB each), so moving along the dock never re-blurs on the UI thread.
    private static final java.util.LinkedHashMap<String,Bitmap> frost=new java.util.LinkedHashMap<String,Bitmap>(16,.75f,true){
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String,Bitmap> e){return size()>12;}
    };
    private static Bitmap wallpaper;
    static void wallpaper(Bitmap image){wallpaper=Bitmap.createScaledBitmap(image,160,90,true);frost.remove("neutral");}
    /** A still frame of the app's own ambient mesh (spec 32), so glass and Settings carry the same light as Home. */
    static void mesh(Canvas c,float w,float h,String key){AmbientMesh.still(c,w,h,key);}
    private static Bitmap frosted(String key){
        Bitmap kept=frost.get(key);if(kept!=null)return kept;
        Bitmap next=Bitmap.createBitmap(160,90,Bitmap.Config.ARGB_8888);
        if("neutral".equals(key)&&wallpaper!=null)new Canvas(next).drawBitmap(wallpaper,null,new RectF(0,0,160,90),new Paint(3));
        else mesh(new Canvas(next),160,90,key);
        // A small separable box blur is cached per scene, never recomputed per animation frame.
        int w=160,h=90;int[] a=new int[w*h],b=new int[a.length];next.getPixels(a,0,w,0,0,w,h);
        for(int pass=0;pass<3;pass++){
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){int rr=0,gg=0,bb=0,n=0;for(int dx=-4;dx<=4;dx++){int v=a[y*w+Math.max(0,Math.min(w-1,x+dx))];rr+=(v>>16)&255;gg+=(v>>8)&255;bb+=v&255;n++;}b[y*w+x]=Color.rgb(rr/n,gg/n,bb/n);}
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){int rr=0,gg=0,bb=0,n=0;for(int dy=-4;dy<=4;dy++){int v=b[Math.max(0,Math.min(h-1,y+dy))*w+x];rr+=(v>>16)&255;gg+=(v>>8)&255;bb+=v&255;n++;}a[y*w+x]=Color.rgb(rr/n,gg/n,bb/n);}
        }
        next.setPixels(a,0,w,0,0,w,h);frost.put(key,next);return next;
    }
    static void glass(Canvas c,RectF bounds,float radius,String key,boolean opaque){
        Paint p=new Paint(3);Path clip=new Path();clip.addRoundRect(bounds,radius,radius,Path.Direction.CW);
        c.save();c.clipPath(clip);
        p.setAlpha(opaque?0:215);c.drawBitmap(frosted(key),null,new RectF(0,0,1920,1080),p);p.setAlpha(255);
        p.setShader(new LinearGradient(0,bounds.top,0,bounds.bottom,new int[]{opaque?0xff192334:0x664e6477,opaque?0xff192334:0x8a14202f},null,Shader.TileMode.CLAMP));c.drawRect(bounds,p);
        p.setShader(new LinearGradient(bounds.left,bounds.top,bounds.right,bounds.top,new int[]{0x05ffffff,0x66ffffff,0x12ffffff},null,Shader.TileMode.CLAMP));
        c.drawRect(bounds.left,bounds.top,bounds.right,bounds.top+1.4f,p);c.restore();
        p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(0x30dce8f3);c.drawRoundRect(bounds,radius,radius,p);
    }
    static Drawable backdrop(Context context){
        String key=context.getSharedPreferences("unified-tv-preview",0).getString("scene","neutral");
        return new Drawable(){
            @Override public void draw(Canvas canvas){Rect b=getBounds();canvas.save();canvas.translate(b.left,b.top);canvas.scale(b.width()/1920f,b.height()/1080f);Paint p=new Paint(3);p.setAlpha(255);canvas.drawBitmap(frosted(key),null,new RectF(0,0,1920,1080),p);p.setColor(0xa909111d);canvas.drawRect(0,0,1920,1080,p);canvas.restore();}
            @Override public void setAlpha(int alpha){}
            @Override public void setColorFilter(ColorFilter filter){}
            @Override public int getOpacity(){return PixelFormat.OPAQUE;}
        };
    }
    static Drawable panel(Context context){
        Drawable scene=backdrop(context);
        return new Drawable(){
            @Override public void draw(Canvas c){RectF box=new RectF(getBounds());Path path=new Path();path.addRoundRect(box,28,28,Path.Direction.CW);c.save();c.clipPath(path);scene.setBounds(getBounds());scene.draw(c);c.restore();Paint p=new Paint(3);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);p.setColor(0x65cfdfef);c.drawRoundRect(box,28,28,p);}
            @Override public void setAlpha(int alpha){}
            @Override public void setColorFilter(ColorFilter filter){}
            @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
        };
    }
}
