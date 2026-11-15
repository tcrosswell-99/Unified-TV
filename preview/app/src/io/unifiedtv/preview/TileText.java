package io.unifiedtv.preview;
import java.util.*;
import java.util.function.ToDoubleFunction;

/**
 * A title laid out inside a poster tile that has no picture yet (owner's TCL, build run 99: "Guardians of the Galaxy Vol. 3"
 * ran outside Home's tile). At most {@link #LINES} lines, each measured to fit the tile's inner width, the last one
 * ending in an ellipsis when the title is longer; a word too long for a line is broken. Used by Home's row and the
 * library's tiles (PosterView), so a title looks the same in both and never leaves its card. Pure, so it is tested
 * without a screen; {@code measure} is the Paint's own measureText at the size being drawn.
 */
final class TileText {
    private TileText(){}
    static final int LINES=2;
    static final String ELLIPSIS="…";

    /** The lines for {@code text} at most {@code width} wide each, at most {@code max} of them. */
    static List<String> lines(String text,float width,int max,ToDoubleFunction<String> measure){
        List<String> out=new ArrayList<>();String t=text==null?"":text.trim().replaceAll("\\s+"," ");
        if(t.isEmpty()||width<=0||max<=0)return out;
        String[] words=t.split(" ");int i=0;
        while(i<words.length&&out.size()<max){
            StringBuilder line=new StringBuilder();
            while(i<words.length){
                String next=line.length()==0?words[i]:line+" "+words[i];
                if(measure.applyAsDouble(next)<=width){line.setLength(0);line.append(next);i++;continue;}
                if(line.length()==0){   // one word wider than the line: as much of it as fits
                    String w=words[i];int n=fit(w,width,measure);line.append(w,0,n);words[i]=w.substring(n);if(words[i].isEmpty())i++;}
                break;
            }
            out.add(line.toString());
        }
        if(i<words.length&&!out.isEmpty()){   // more than fits: the last line ends in an ellipsis
            StringBuilder rest=new StringBuilder(out.get(out.size()-1));for(int k=i;k<words.length;k++)rest.append(' ').append(words[k]);
            out.set(out.size()-1,ellipsise(rest.toString(),width,measure));
        }
        return out;
    }
    /** The longest start of {@code s} that, with an ellipsis, fits {@code width}. */
    static String ellipsise(String s,float width,ToDoubleFunction<String> measure){
        if(measure.applyAsDouble(s)<=width)return s;
        int n=s.length();
        while(n>0&&measure.applyAsDouble(s.substring(0,n).trim()+ELLIPSIS)>width)n--;
        return n<=0?(measure.applyAsDouble(ELLIPSIS)<=width?ELLIPSIS:""):s.substring(0,n).trim()+ELLIPSIS;
    }
    /** How many characters of {@code w} fit (at least one, so a line is never empty). */
    private static int fit(String w,float width,ToDoubleFunction<String> measure){
        int n=w.length();while(n>1&&measure.applyAsDouble(w.substring(0,n))>width)n--;return Math.max(1,n);
    }
    /**
     * The largest size from {@code from} down to {@code to} (in steps of 2) at which the whole title fits in {@link #LINES}
     * lines; at the smallest size it is ellipsised. {@code measureAt} measures at a given size.
     */
    static float size(String text,float width,float from,float to,java.util.function.BiFunction<Float,String,Double> measureAt){
        for(float s=from;s>to;s-=2){float size=s;List<String> l=lines(text,width,LINES,x->measureAt.apply(size,x));if(!ellipsised(l))return s;}
        return to;
    }
    static boolean ellipsised(List<String> lines){return !lines.isEmpty()&&lines.get(lines.size()-1).endsWith(ELLIPSIS);}
}
