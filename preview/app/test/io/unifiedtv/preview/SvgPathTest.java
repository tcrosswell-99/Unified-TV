package io.unifiedtv.preview;
import org.junit.Test;
import static org.junit.Assert.*;

/** The SVG path reader behind the owner-supplied brand marks (OD-20). */
public class SvgPathTest {
    /** Records every point so a test can check where a mark lands. */
    static final class Bounds implements SvgPath.Sink {
        float minX=Float.MAX_VALUE,minY=Float.MAX_VALUE,maxX=-Float.MAX_VALUE,maxY=-Float.MAX_VALUE;int moves,curves,closes;
        void add(float x,float y){minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);}
        public void moveTo(float x,float y){moves++;add(x,y);}public void lineTo(float x,float y){add(x,y);}
        public void cubicTo(float a,float b,float c,float d,float x,float y){curves++;add(x,y);}public void quadTo(float a,float b,float x,float y){add(x,y);}public void close(){closes++;}
    }
    @Test public void compactNumbersSplitWhereSvgSaysTheyDo(){
        int[] at={0};String d="-.0039.00391-4.76172.5e1";
        assertEquals(-.0039f,SvgPath.num(d,at),1e-6);assertEquals(.00391f,SvgPath.num(d,at),1e-6);assertEquals(-4.76172f,SvgPath.num(d,at),1e-6);assertEquals(5f,SvgPath.num(d,at),1e-6);
    }
    @Test public void relativeCommandsAndImplicitRepeats(){
        Bounds b=new Bounds();SvgPath.parse("m10 10 5 0 0 5h-5z M0 0 L1 1 C1 2 3 4 5 6 s1 1 2 2",b);
        assertEquals(2,b.moves);assertEquals(1,b.closes);assertEquals(2,b.curves);assertEquals(0,b.minX,0);assertEquals(15,b.maxX,0);assertEquals(15,b.maxY,0);
    }
    @Test public void unknownCommandsAreRefused(){assertThrows(IllegalArgumentException.class,()->SvgPath.parse("M0 0 A1 1 0 0 1 2 2",new Bounds()));}
    @Test public void theDisneyMarkSitsInsideItsViewBox(){
        Bounds b=new Bounds();for(String d:BrandMarks.DISNEY)SvgPath.parse(d,b);
        assertEquals("one shape per SVG path, plus inner shapes",true,b.moves>=9);
        assertTrue(b.minX>=0&&b.minY>=-1);assertTrue(b.maxX<=BrandMarks.DISNEY_W+1&&b.maxY<=BrandMarks.DISNEY_H+1);
        assertTrue("it spans the width of the wordmark",b.maxX-b.minX>BrandMarks.DISNEY_W*.9f);
    }
}
