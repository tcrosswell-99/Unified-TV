package io.unifiedtv.preview;
import android.graphics.*;
import android.graphics.drawable.Drawable;

/**
 * The dock's glyphs (DockLabels.glyph names them; UX-ICON-01..03, UX-ICON-07): original, simple vector symbols drawn in
 * white, at the six-place tile's design size (about 100 px across, centred on cx, cy). No brand artwork is copied. Home
 * draws them on its glass tiles; Build My TV shows the same glyph beside each service through drawable().
 *
 * Brand marks (owner, 25 October 2026: recognisable WHITE marks for YouTube, Netflix, Disney+ and BBC iPlayer): simple
 * monochrome shapes drawn here in code, used only to identify which app a tile opens; no image file is copied and no
 * colour is used. YouTube's rounded play button (also on SmartTube's tile, which the household uses as its YouTube),
 * Netflix's N ribbon, Disney+'s arc and plus, BBC's three lettered blocks. The names and marks belong to their owners
 * (preview/THIRD_PARTY.md). Live TV is a television on a stand; Music a bold pair of beamed notes.
 *
 * Drawn for 10-foot viewing (owner, 25 October 2026: Music looked "janky", Live TV "not perfect"): one stroke weight
 * (5 to 6 design px, round ends), whole shapes rather than thin details, symmetric where the idea allows, and every glyph
 * optically centred in the same box, so none looks heavier or off-centre on the glass at viewing distance.
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
    private static final Typeface BOLD=Typeface.create("sans-serif",Typeface.BOLD);
    /** The BBC blocks' letters: one shared paint (drawn on the main thread only). */
    private static final Paint text=new Paint(Paint.ANTI_ALIAS_FLAG);
    static void draw(Canvas c,String g,float cx,float cy,Paint icon,Path path,RectF rect,Drawable fallback){
        paint(icon,240,false);icon.setStrokeWidth(5);
        switch(g){
            case "shelf":   // Library: three posters on a shelf
                for(int k=-1;k<=1;k++){rect.set(cx+k*30-12,cy-34+Math.abs(k)*6,cx+k*30+12,cy+28);c.drawRoundRect(rect,4,4,icon);}
                c.drawLine(cx-52,cy+40,cx+52,cy+40,icon);break;
            case "play":   // YouTube (and SmartTube's tile, named YouTube): the rounded play button, its triangle cut out
                paint(icon,245,true);path.reset();path.setFillType(Path.FillType.EVEN_ODD);
                rect.set(cx-46,cy-32,cx+46,cy+32);path.addRoundRect(rect,20,20,Path.Direction.CW);
                path.moveTo(cx-12,cy-16);path.lineTo(cx+18,cy);path.lineTo(cx-12,cy+16);path.close();c.drawPath(path,icon);path.setFillType(Path.FillType.WINDING);break;
            case "n":   // Netflix: the N ribbon, two shaded uprights behind one bright diagonal band
                paint(icon,170,true);rect.set(cx-25,cy-40,cx-11,cy+40);c.drawRect(rect,icon);rect.set(cx+11,cy-40,cx+25,cy+40);c.drawRect(rect,icon);
                paint(icon,250,true);path.reset();path.moveTo(cx-25,cy-40);path.lineTo(cx-11,cy-40);path.lineTo(cx+25,cy+40);path.lineTo(cx+11,cy+40);path.close();c.drawPath(path,icon);break;
            case "arcplus":   // Disney+: Disney's own 2024 wordmark (owner-supplied SVG, OD-20), filled white, fitted to the tile
                paint(icon,245,true);c.save();{float w=112f,sc=w/BrandMarks.DISNEY_W;c.translate(cx-w/2,cy-BrandMarks.DISNEY_H*sc/2);c.scale(sc,sc);c.drawPath(BrandMarks.disney(),icon);}c.restore();break;
            case "blocks":   // BBC: three square blocks, each with its letter
                paint(icon,240,true);text.setTextAlign(Paint.Align.CENTER);text.setTextSize(26);text.setTypeface(BOLD);text.setColor(0xFF172030);
                for(int k=-1;k<=1;k++){rect.set(cx+k*34-15,cy-15,cx+k*34+15,cy+15);c.drawRect(rect,icon);c.drawText(k==1?"C":"B",cx+k*34,cy+9.5f,text);}break;
            case "tvset":   // Live TV: a television set, its screen on a stand, with two short aerials (never a mast)
                icon.setStrokeWidth(6);rect.set(cx-44,cy-28,cx+44,cy+26);c.drawRoundRect(rect,10,10,icon);
                c.drawLine(cx-4,cy-30,cx-17,cy-45,icon);c.drawLine(cx+4,cy-30,cx+17,cy-45,icon);
                c.drawLine(cx,cy+26,cx,cy+36,icon);c.drawLine(cx-22,cy+40,cx+22,cy+40,icon);break;
            case "note":   // Music: two bold beamed notes, solid tilted heads, stems on the right, one thick rising beam
                paint(icon,245,true);
                rect.set(cx-8,cy-26,cx,cy+28);c.drawRect(rect,icon);rect.set(cx+34,cy-36,cx+42,cy+18);c.drawRect(rect,icon);
                path.reset();path.moveTo(cx-8,cy-28);path.lineTo(cx+42,cy-40);path.lineTo(cx+42,cy-25);path.lineTo(cx-8,cy-13);path.close();c.drawPath(path,icon);
                for(int k=0;k<2;k++){float hx=k==0?cx-18:cx+24,hy=k==0?cy+29:cy+19;c.save();c.rotate(-22,hx,hy);rect.set(hx-16,hy-11.5f,hx+16,hy+11.5f);c.drawOval(rect,icon);c.restore();}
                break;
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
