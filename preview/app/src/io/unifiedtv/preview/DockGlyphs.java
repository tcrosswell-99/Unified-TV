package io.unifiedtv.preview;
import android.graphics.*;
import android.graphics.drawable.Drawable;

/**
 * The dock's glyphs (DockLabels.glyph names them; UX-ICON-01..03, UX-ICON-07): original, simple vector symbols drawn in
 * white, at the six-place tile's design size (about 100 px across, centred on cx, cy). No brand artwork is copied. Home
 * draws them on its glass tiles; Build My TV shows the same glyph beside each service through drawable().
 *
 * SmartTube (UX-ICON-07, SET-BUILD-05): a rounded play triangle with three motion lines trailing it, open on a bare tile.
 * It borrows only the play-button idea; it is not the official YouTube mark (no red rounded rectangle) and it is not the
 * glyph YouTube itself gets here (a screen on a stand with a play triangle), so the two stay apart at viewing distance.
 *
 * An app without a glyph of its own shows its installed icon small and in greyscale, so it never reads as a full-colour
 * square; with no icon at all, a plain ring, never letters.
 */
final class DockGlyphs {
    private DockGlyphs(){}
    static final ColorMatrixColorFilter GREY=new ColorMatrixColorFilter(new ColorMatrix(new float[]{
        .30f,.59f,.11f,0,20, .30f,.59f,.11f,0,20, .30f,.59f,.11f,0,20, 0,0,0,.9f,0}));
    static void paint(Paint icon,int alpha,boolean fill){
        icon.setColor(0xFFFAFAFC);icon.setAlpha(alpha);icon.setStyle(fill?Paint.Style.FILL:Paint.Style.STROKE);
        icon.setStrokeWidth(3);icon.setStrokeCap(Paint.Cap.ROUND);icon.setStrokeJoin(Paint.Join.ROUND);
        icon.setShadowLayer(1.5f,0,1,0x80000000);   // a microscopic shadow keeps it legible on bright pictures
    }
    /** Draws glyph g centred on cx, cy; {@code path} and {@code rect} are reused scratch objects (nothing allocated per frame). */
    static void draw(Canvas c,String g,float cx,float cy,Paint icon,Path path,RectF rect,Drawable fallback){
        paint(icon,240,false);icon.setStrokeWidth(5);
        switch(g){
            case "shelf":   // Library: three posters on a shelf
                for(int k=-1;k<=1;k++){rect.set(cx+k*30-12,cy-34+Math.abs(k)*6,cx+k*30+12,cy+28);c.drawRoundRect(rect,4,4,icon);}
                c.drawLine(cx-52,cy+40,cx+52,cy+40,icon);break;
            case "play":   // a screen on a stand with a play triangle (YouTube, YouTube Kids)
                rect.set(cx-44,cy-32,cx+44,cy+28);c.drawRoundRect(rect,12,12,icon);c.drawLine(cx-18,cy+40,cx+18,cy+40,icon);
                paint(icon,240,true);path.reset();path.moveTo(cx-10,cy-16);path.lineTo(cx+16,cy-2);path.lineTo(cx-10,cy+12);path.close();c.drawPath(path,icon);break;
            case "smarttube":   // SmartTube: a rounded play triangle with three motion lines, no frame
                paint(icon,240,true);icon.setStyle(Paint.Style.FILL_AND_STROKE);icon.setStrokeWidth(9);
                path.reset();path.moveTo(cx-10,cy-25);path.lineTo(cx+32,cy);path.lineTo(cx-10,cy+25);path.close();c.drawPath(path,icon);
                paint(icon,240,false);icon.setStrokeWidth(7);
                c.drawLine(cx-46,cy-15,cx-28,cy-15,icon);c.drawLine(cx-54,cy,cx-28,cy,icon);c.drawLine(cx-46,cy+15,cx-28,cy+15,icon);break;
            case "n":   // a plain geometric N
                icon.setStrokeWidth(13);icon.setStrokeCap(Paint.Cap.SQUARE);path.reset();path.moveTo(cx-20,cy+38);path.lineTo(cx-20,cy-38);path.lineTo(cx+20,cy+38);path.lineTo(cx+20,cy-38);c.drawPath(path,icon);break;
            case "arcplus":   // an arc over a plus
                rect.set(cx-46,cy-30,cx+46,cy+50);c.drawArc(rect,200,140,false,icon);
                c.drawLine(cx,cy-4,cx,cy+32,icon);c.drawLine(cx-18,cy+14,cx+18,cy+14,icon);break;
            case "blocks":   // three square blocks
                paint(icon,240,true);for(int k=-1;k<=1;k++){rect.set(cx+k*32-13,cy-13,cx+k*32+13,cy+13);c.drawRoundRect(rect,3,3,icon);}break;
            case "broadcast":   // a mast with waves
                paint(icon,240,true);c.drawCircle(cx,cy-6,8,icon);paint(icon,240,false);icon.setStrokeWidth(5);
                rect.set(cx-26,cy-32,cx+26,cy+20);c.drawArc(rect,200,140,false,icon);rect.set(cx-42,cy-48,cx+42,cy+36);c.drawArc(rect,205,130,false,icon);
                c.drawLine(cx,cy+2,cx-16,cy+40,icon);c.drawLine(cx,cy+2,cx+16,cy+40,icon);break;
            case "note":   // a beamed pair of notes
                paint(icon,240,true);c.drawCircle(cx-20,cy+26,11,icon);c.drawCircle(cx+22,cy+18,11,icon);
                paint(icon,240,false);icon.setStrokeWidth(6);c.drawLine(cx-10,cy+26,cx-10,cy-30,icon);c.drawLine(cx+32,cy+18,cx+32,cy-38,icon);c.drawLine(cx-10,cy-30,cx+32,cy-38,icon);break;
            case "singlenote":   // one note with a flag
                paint(icon,240,true);c.drawCircle(cx-10,cy+24,13,icon);
                paint(icon,240,false);icon.setStrokeWidth(6);c.drawLine(cx+3,cy+24,cx+3,cy-36,icon);
                path.reset();path.moveTo(cx+3,cy-36);path.quadTo(cx+8,cy-16,cx+28,cy-12);c.drawPath(path,icon);break;
            case "eq":   // four level bars
                icon.setStrokeWidth(12);float[] h={40,70,52,28};for(int k=0;k<4;k++)c.drawLine(cx-30+k*20,cy+34,cx-30+k*20,cy+34-h[k],icon);break;
            case "vinyl":   // a record: rings round a centre
                c.drawCircle(cx,cy,40,icon);icon.setStrokeWidth(3);c.drawCircle(cx,cy,24,icon);paint(icon,240,true);c.drawCircle(cx,cy,7,icon);break;
            case "controller":   // a game pad
                rect.set(cx-46,cy-22,cx+46,cy+26);c.drawRoundRect(rect,22,22,icon);
                c.drawLine(cx-28,cy+2,cx-12,cy+2,icon);c.drawLine(cx-20,cy-6,cx-20,cy+10,icon);
                paint(icon,240,true);c.drawCircle(cx+18,cy-2,5,icon);c.drawCircle(cx+30,cy+8,5,icon);break;
            case "screen":   // a TV
                rect.set(cx-44,cy-32,cx+44,cy+26);c.drawRoundRect(rect,10,10,icon);c.drawLine(cx-20,cy+40,cx+20,cy+40,icon);break;
            case "playcircle":
                c.drawCircle(cx,cy,40,icon);paint(icon,240,true);path.reset();path.moveTo(cx-12,cy-18);path.lineTo(cx+20,cy);path.lineTo(cx-12,cy+18);path.close();c.drawPath(path,icon);break;
            case "chevron":
                icon.setStrokeWidth(10);path.reset();path.moveTo(cx-14,cy-34);path.lineTo(cx+18,cy);path.lineTo(cx-14,cy+34);c.drawPath(path,icon);break;
            case "cast":   // a screen with a cast arc in its corner
                rect.set(cx-44,cy-32,cx+44,cy+28);c.drawRoundRect(rect,10,10,icon);
                rect.set(cx-60,cy+4,cx-20,cy+44);c.drawArc(rect,270,90,false,icon);paint(icon,240,true);c.drawCircle(cx-36,cy+20,5,icon);break;
            case "photos":   // a picture: a frame with a hill and a sun
                rect.set(cx-44,cy-32,cx+44,cy+32);c.drawRoundRect(rect,10,10,icon);
                path.reset();path.moveTo(cx-36,cy+24);path.lineTo(cx-8,cy-4);path.lineTo(cx+10,cy+12);path.lineTo(cx+20,cy+2);path.lineTo(cx+36,cy+24);c.drawPath(path,icon);
                paint(icon,240,true);c.drawCircle(cx+20,cy-16,6,icon);break;
            case "house":   // a house
                path.reset();path.moveTo(cx-40,cy-2);path.lineTo(cx,cy-38);path.lineTo(cx+40,cy-2);c.drawPath(path,icon);
                rect.set(cx-30,cy-8,cx+30,cy+38);c.drawRoundRect(rect,6,6,icon);rect.set(cx-9,cy+14,cx+9,cy+38);c.drawRect(rect,icon);break;
            default:
                if(fallback!=null){Drawable d=fallback.mutate();d.setColorFilter(GREY);d.setBounds(Math.round(cx-34),Math.round(cy-34),Math.round(cx+34),Math.round(cy+34));d.draw(c);}
                else c.drawCircle(cx,cy,30,icon);
        }
        icon.setStrokeWidth(3);icon.setStrokeCap(Paint.Cap.ROUND);icon.setStyle(Paint.Style.STROKE);
    }
    /** The glyph as a Drawable for an ordinary view (Build My TV): the design box scaled to the bounds. */
    static Drawable drawable(String g,Drawable fallback){
        return new Drawable(){
            final Paint icon=new Paint(Paint.ANTI_ALIAS_FLAG);final Path path=new Path();final RectF rect=new RectF();
            @Override public void draw(Canvas c){Rect b=getBounds();float s=Math.min(b.width(),b.height())/130f;c.save();c.translate(b.exactCenterX(),b.exactCenterY());c.scale(s,s);DockGlyphs.draw(c,g,0,0,icon,path,rect,fallback);c.restore();}
            @Override public void setAlpha(int alpha){}
            @Override public void setColorFilter(ColorFilter filter){}
            @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
            @Override public int getIntrinsicWidth(){return 64;}
            @Override public int getIntrinsicHeight(){return 64;}
        };
    }
}
