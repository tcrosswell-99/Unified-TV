package io.unifiedtv.preview;
import android.graphics.Path;

/**
 * A small reader for SVG path data (M, L, H, V, C, S, Q, T, Z, absolute and relative, with implicit repeats and compact
 * numbers such as "-.0039.00391"), for the owner-supplied brand marks. Arcs (A) are not needed and are refused.
 */
final class SvgPath {
    private SvgPath(){}
    /** Where the commands go: a Path on the TV, a recorder in tests. */
    interface Sink {void moveTo(float x,float y);void lineTo(float x,float y);void cubicTo(float x1,float y1,float x2,float y2,float x,float y);void quadTo(float x1,float y1,float x,float y);void close();}
    static final class ToPath implements Sink {
        final Path p;ToPath(Path p){this.p=p;}
        public void moveTo(float x,float y){p.moveTo(x,y);}public void lineTo(float x,float y){p.lineTo(x,y);}
        public void cubicTo(float a,float b,float c,float d,float x,float y){p.cubicTo(a,b,c,d,x,y);}public void quadTo(float a,float b,float x,float y){p.quadTo(a,b,x,y);}public void close(){p.close();}
    }
    static void parse(String d,Sink out){
        int[] at={0};char cmd=0;float x=0,y=0,sx=0,sy=0,cx=0,cy=0;char last=0;
        while(true){
            skip(d,at);if(at[0]>=d.length())return;
            char ch=d.charAt(at[0]);
            if(Character.isLetter(ch)){cmd=ch;at[0]++;}else if(cmd==0)throw new IllegalArgumentException("path data must start with a command");
            boolean rel=Character.isLowerCase(cmd);float ox=rel?x:0,oy=rel?y:0;
            switch(Character.toUpperCase(cmd)){
                case 'M':{x=ox+num(d,at);y=oy+num(d,at);sx=x;sy=y;out.moveTo(x,y);cmd=rel?'l':'L';last='M';break;}
                case 'L':{x=ox+num(d,at);y=oy+num(d,at);out.lineTo(x,y);last='L';break;}
                case 'H':{x=ox+num(d,at);out.lineTo(x,y);last='L';break;}
                case 'V':{y=oy+num(d,at);out.lineTo(x,y);last='L';break;}
                case 'C':{float x1=ox+num(d,at),y1=oy+num(d,at),x2=ox+num(d,at),y2=oy+num(d,at);x=ox+num(d,at);y=oy+num(d,at);out.cubicTo(x1,y1,x2,y2,x,y);cx=x2;cy=y2;last='C';break;}
                case 'S':{float x1=last=='C'?2*x-cx:x,y1=last=='C'?2*y-cy:y,x2=ox+num(d,at),y2=oy+num(d,at);x=ox+num(d,at);y=oy+num(d,at);out.cubicTo(x1,y1,x2,y2,x,y);cx=x2;cy=y2;last='C';break;}
                case 'Q':{float x1=ox+num(d,at),y1=oy+num(d,at);x=ox+num(d,at);y=oy+num(d,at);out.quadTo(x1,y1,x,y);cx=x1;cy=y1;last='Q';break;}
                case 'T':{float x1=last=='Q'?2*x-cx:x,y1=last=='Q'?2*y-cy:y;x=ox+num(d,at);y=oy+num(d,at);out.quadTo(x1,y1,x,y);cx=x1;cy=y1;last='Q';break;}
                case 'Z':{out.close();x=sx;y=sy;last='Z';cmd=0;break;}
                default:throw new IllegalArgumentException("unsupported path command "+cmd);
            }
        }
    }
    private static void skip(String d,int[] at){while(at[0]<d.length()&&(Character.isWhitespace(d.charAt(at[0]))||d.charAt(at[0])==','))at[0]++;}
    /** One number: optional sign, digits, at most one point, optional exponent; a second point starts the next number. */
    static float num(String d,int[] at){
        skip(d,at);int i=at[0],n=d.length();if(i<n&&(d.charAt(i)=='-'||d.charAt(i)=='+'))i++;boolean dot=false;
        while(i<n){char c=d.charAt(i);if(Character.isDigit(c))i++;else if(c=='.'&&!dot){dot=true;i++;}else break;}
        if(i<n&&(d.charAt(i)=='e'||d.charAt(i)=='E')){int j=i+1;if(j<n&&(d.charAt(j)=='-'||d.charAt(j)=='+'))j++;if(j<n&&Character.isDigit(d.charAt(j))){i=j;while(i<n&&Character.isDigit(d.charAt(i)))i++;}}
        if(i==at[0])throw new IllegalArgumentException("number expected at "+at[0]);
        float v=Float.parseFloat(d.substring(at[0],i));at[0]=i;return v;
    }
}
