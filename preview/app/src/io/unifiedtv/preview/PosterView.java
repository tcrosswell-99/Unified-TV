package io.unifiedtv.preview;
import android.content.Context;
import android.graphics.*;
import android.text.*;
import android.view.View;

/**
 * One poster tile in the library. Draws the title's own picture (centre-cropped into a 2:3 card) or, with no picture,
 * a calm title card: the cleaned name sized to fit (two or three lines, then an ellipsis) and its year, on a tint taken
 * from the name, never a grey box. Episodes add their number and name in a band; Continue Watching adds a progress line.
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
    private StaticLayout nameLayout;private int nameWidth=-1;

    PosterView(Context c,float unit,boolean calm){
        super(c);this.unit=unit;this.calm=calm;setFocusable(true);setFocusableInTouchMode(true);setClickable(true);
        ring.setStyle(Paint.Style.STROKE);ring.setColor(0xFFFFFFFF);ring.setStrokeWidth(Math.max(2,4*unit));
    }
    void show(LibraryCatalogue.Card c){card=c;action="";art=null;nameLayout=null;
        setContentDescription(c==null?"":describe(c));invalidate();}
    void showAction(String title,String detail){card=null;action=title;actionDetail=detail==null?"":detail;art=null;nameLayout=null;setContentDescription(title+(detail==null||detail.isEmpty()?"":", "+detail));invalidate();}
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
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){nameLayout=null;}

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
        int top=Color.HSVToColor(new float[]{hue,.35f,.32f}),bottom=Color.HSVToColor(new float[]{(hue+40)%360,.45f,.14f});
        if(card==null){top=0x3326354A;bottom=0x33101827;}
        fill.setShader(new LinearGradient(0,box.top,0,box.bottom,top,bottom,Shader.TileMode.CLAMP));c.drawRect(box,fill);fill.setShader(null);
        if(card==null){fill.setColor(0x66FFFFFF);fill.setStyle(Paint.Style.STROKE);fill.setStrokeWidth(Math.max(1,2*unit));c.drawRoundRect(box,14*unit,14*unit,fill);fill.setStyle(Paint.Style.FILL);}
        int inner=Math.round(box.width()-28*unit);if(inner<=0)return;
        if(nameLayout==null||nameWidth!=inner){
            ink.setColor(0xFFF4F7FB);ink.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
            // The largest size (28 px at 1080p down to 18) at which the name fits in three lines.
            StaticLayout l=null;
            for(float size=28;size>=18;size-=2){ink.setTextSize(size*unit);l=layout(name,inner,3);if(l.getLineCount()<=3&&!ellipsised(l))break;}
            nameLayout=l;nameWidth=inner;
        }
        float textH=nameLayout.getHeight(),yearH=year.isEmpty()?0:24*unit;
        float y=box.centerY()-(textH+yearH)/2;
        c.save();c.translate(box.left+14*unit,y);nameLayout.draw(c);c.restore();
        if(!year.isEmpty()){TextPaint p=new TextPaint(ink);p.setTypeface(Typeface.DEFAULT);p.setTextSize(20*unit);p.setColor(0xFFC3CEDD);
            String y2=TextUtils.ellipsize(year,p,inner,TextUtils.TruncateAt.END).toString();c.drawText(y2,box.centerX()-p.measureText(y2)/2,y+textH+20*unit,p);}
    }
    private StaticLayout layout(String text,int width,int lines){
        return StaticLayout.Builder.obtain(text,0,text.length(),ink,width).setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).setLineSpacing(0,1.05f).build();
    }
    private static boolean ellipsised(StaticLayout l){for(int i=0;i<l.getLineCount();i++)if(l.getEllipsisCount(i)>0)return true;return false;}
    /** A dark band at the foot with an episode's number and name, one line, ellipsised. */
    private void band(Canvas c,String text){
        float hgt=48*unit;fill.setShader(new LinearGradient(0,box.bottom-hgt*1.6f,0,box.bottom,0x00000000,0xE6000000,Shader.TileMode.CLAMP));
        c.drawRect(box.left,box.bottom-hgt*1.6f,box.right,box.bottom,fill);fill.setShader(null);
        TextPaint p=new TextPaint(Paint.ANTI_ALIAS_FLAG);p.setColor(0xFFF4F7FB);p.setTextSize(19*unit);
        String t=TextUtils.ellipsize(text,p,box.width()-20*unit,TextUtils.TruncateAt.END).toString();
        c.drawText(t,box.left+10*unit,box.bottom-16*unit,p);
    }
}
