package io.unifiedtv.preview;
import android.content.Context;
import android.graphics.*;
import android.text.*;
import android.view.View;
import java.util.List;

/**
 * One poster tile in the library. Draws the title's own picture (centre-cropped into a 2:3 card) or, with no picture,
 * a calm title card: the cleaned name sized to fit (at most two lines inside the card, then an ellipsis: TileText) and
 * its year on a small line of its own, on a dark glass tint taken from the name, never a grey box. Episodes add their number and name in a band; Continue Watching adds a progress line.
 * An "All" tile is the same shape with a count. Focus lifts the tile a little and draws a clear white ring (not colour
 * alone); with Reduce motion the lift is immediate.
 */
final class PosterView extends View {
    LibraryCatalogue.Card card;String action="",actionDetail="";
    private Bitmap art;private boolean calm;
    private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG),ring=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint ink=new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box=new RectF();private final Path clip=new Path();
    private final float unit;
    private List<String> nameLines;private float nameSize;private int nameWidth=-1;

    PosterView(Context c,float unit,boolean calm){
        super(c);this.unit=unit;this.calm=calm;setFocusable(true);setFocusableInTouchMode(true);setClickable(true);
        ring.setStyle(Paint.Style.STROKE);ring.setColor(0xFFFFFFFF);ring.setStrokeWidth(Math.max(2,4*unit));
    }
    void show(LibraryCatalogue.Card c){card=c;action="";art=null;nameLines=null;
        setContentDescription(c==null?"":describe(c));invalidate();}
    void showAction(String title,String detail){card=null;action=title;actionDetail=detail==null?"":detail;art=null;nameLines=null;setContentDescription(title+(detail==null||detail.isEmpty()?"":", "+detail));invalidate();}
    void art(Bitmap b){art=b;invalidate();}
    boolean hasArt(){return art!=null;}
    static String describe(LibraryCatalogue.Card c){
        String s=c.name()+(c.year().isEmpty()?"":", "+c.year());if(!c.detail().isEmpty())s+=", "+c.detail();
        if(c.resume>0)s+=", resume from "+PlaybackPolicy.clock(c.resume);return s;
    }
    @Override protected void onFocusChanged(boolean focused,int direction,Rect previous){
        super.onFocusChanged(focused,direction,previous);
        float to=focused?1.08f:1f;animate().cancel();
        if(calm){setScaleX(to);setScaleY(to);}else animate().scaleX(to).scaleY(to).setDuration(140).start();
        setZ(focused?2:0);invalidate();
    }
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){nameLines=null;}

    @Override protected void onDraw(Canvas c){
        int w=getWidth(),h=getHeight();if(w==0||h==0)return;float r=14*unit,pad=4*unit;
        box.set(pad,pad,w-pad,h-pad);clip.reset();clip.addRoundRect(box,r,r,Path.Direction.CW);
        c.save();c.clipPath(clip);
        if(art!=null){
            float s=Math.max(box.width()/art.getWidth(),box.height()/art.getHeight()),aw=art.getWidth()*s,ah=art.getHeight()*s;
            fill.setShader(null);fill.setColor(0xFFFFFFFF);c.drawBitmap(art,null,new RectF(box.centerX()-aw/2,box.centerY()-ah/2,box.centerX()+aw/2,box.centerY()+ah/2),fill);
        }else titleCard(c);
        if(card!=null&&!card.detail().isEmpty())band(c,card.detail());
        if(card!=null&&card.resume>0&&card.duration>0){
            float f=Math.max(.03f,Math.min(1,card.resume/(float)card.duration)),y=box.bottom-8*unit;
            fill.setShader(null);fill.setColor(0x99000000);c.drawRect(box.left,y-2*unit,box.right,box.bottom,fill);
            fill.setColor(0xFFE6EFFC);c.drawRect(box.left,y,box.left+box.width()*f,box.bottom,fill);
        }
        c.restore();
        if(isFocused()){c.drawRoundRect(box,r,r,ring);}
    }
    /** A title card: a tint from the name (the same name always gets the same tint), the name to fit, the year below. */
    private void titleCard(Canvas c){
        String name=card!=null?card.name():action,year=card!=null?card.year():actionDetail;
        int hue=(name.hashCode()&0x7fffffff)%360;
        // Dark glass: a deep tint from the name, a soft sheen across the top and a fine light edge (never a flat grey box).
        int top=Color.HSVToColor(0xE6,new float[]{hue,.30f,.26f}),bottom=Color.HSVToColor(0xF2,new float[]{(hue+40)%360,.40f,.10f});
        if(card==null){top=0x3326354A;bottom=0x33101827;}
        fill.setShader(new LinearGradient(0,box.top,0,box.bottom,top,bottom,Shader.TileMode.CLAMP));c.drawRect(box,fill);
        fill.setShader(new LinearGradient(0,box.top,0,box.top+box.height()*.4f,0x29FFFFFF,0x00FFFFFF,Shader.TileMode.CLAMP));c.drawRect(box,fill);fill.setShader(null);
        fill.setColor(card==null?0x66FFFFFF:0x38FFFFFF);fill.setStyle(Paint.Style.STROKE);fill.setStrokeWidth(Math.max(1,(card==null?2:1.5f)*unit));c.drawRoundRect(box,14*unit,14*unit,fill);fill.setStyle(Paint.Style.FILL);
        int inner=Math.round(box.width()-28*unit);if(inner<=0)return;
        ink.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));ink.setColor(0xFFF4F7FB);
        if(nameLines==null||nameWidth!=inner){
            // The largest size (26 px at 1080p down to 18) at which the name fits in two lines; at 18 it is ellipsised.
            nameSize=TileText.size(name,inner,26*unit,18*unit,(size,t)->{ink.setTextSize(size);return (double)ink.measureText(t);});
            ink.setTextSize(nameSize);nameLines=TileText.lines(name,inner,TileText.LINES,t->ink.measureText(t));nameWidth=inner;
        }
        ink.setTextSize(nameSize);ink.setTextAlign(Paint.Align.CENTER);
        float line=nameSize*1.2f,textH=line*nameLines.size(),yearH=year.isEmpty()?0:24*unit;
        float y=box.centerY()-(textH+yearH)/2;
        for(int i=0;i<nameLines.size();i++)c.drawText(nameLines.get(i),box.centerX(),y+line*i+nameSize,ink);
        ink.setTextAlign(Paint.Align.LEFT);
        if(!year.isEmpty()){TextPaint p=new TextPaint(ink);p.setTypeface(Typeface.DEFAULT);p.setTextSize(20*unit);p.setColor(0xFFC3CEDD);
            String y2=TextUtils.ellipsize(year,p,inner,TextUtils.TruncateAt.END).toString();c.drawText(y2,box.centerX()-p.measureText(y2)/2,y+textH+20*unit,p);}
    }
    /** For tests: the name's lines as drawn on a card with no picture, and the width they must fit in (after a draw). */
    List<String> nameLines(){return nameLines;}
    int nameWidth(){return nameWidth;}
    /** A dark band at the foot with an episode's number and name, one line, ellipsised. */
    private void band(Canvas c,String text){
        float hgt=48*unit;fill.setShader(new LinearGradient(0,box.bottom-hgt*1.6f,0,box.bottom,0x00000000,0xE6000000,Shader.TileMode.CLAMP));
        c.drawRect(box.left,box.bottom-hgt*1.6f,box.right,box.bottom,fill);fill.setShader(null);
        TextPaint p=new TextPaint(Paint.ANTI_ALIAS_FLAG);p.setColor(0xFFF4F7FB);p.setTextSize(19*unit);
        String t=TextUtils.ellipsize(text,p,box.width()-20*unit,TextUtils.TruncateAt.END).toString();
        c.drawText(t,box.left+10*unit,box.bottom-16*unit,p);
    }
}
