package io.unifiedtv.preview;
import org.junit.Test;
import static org.junit.Assert.*;

public class FrameStatsTest {
    private static final long MS=1_000_000L;
    private static FrameStats steady(int frames,long intervalMs){FrameStats f=new FrameStats();for(int i=0;i<=frames;i++)f.frame(1000*MS+i*intervalMs*MS);return f;}

    @Test public void nearestRankPercentiles(){
        double[] v={5,1,4,2,3};
        assertEquals(3,FrameStats.percentile(v,50),0);
        assertEquals(5,FrameStats.percentile(v,95),0);
        assertEquals(1,FrameStats.percentile(v,0),0);
        assertEquals(5,FrameStats.percentile(v,100),0);
        assertArrayEquals("the input is left as it was",new double[]{5,1,4,2,3},v,0);
        long[] hundred=new long[100];for(int i=0;i<100;i++)hundred[i]=i+1;
        assertEquals(95,FrameStats.percentile(hundred,95),0);
        assertEquals(50,FrameStats.percentile(hundred,50),0);
    }
    @Test public void emptyInputIsNotANumberAndReportsPlainly(){
        assertTrue(Double.isNaN(FrameStats.percentile(new double[0],50)));
        assertTrue(Double.isNaN(FrameStats.percentile((long[])null,50)));
        FrameStats f=new FrameStats();
        assertEquals(0,f.frames());assertEquals(0,f.janky());assertEquals(0,f.worstMs(),0);assertEquals(0,f.spanSeconds(),0);
        String r=FrameStats.report("",0,f,new long[0],-1,-1,0,0);
        assertTrue(r.contains("Frames: none were measured"));
        assertTrue(r.contains("Focus: no button presses reached Home"));
        assertFalse(r.contains("Memory"));
    }
    @Test public void steadySixtyHzHasNoJank(){
        FrameStats f=steady(600,16);
        assertEquals(600,f.frames());assertEquals(0,f.janky());
        assertEquals(16,f.medianMs(),0.001);assertEquals(16,f.percentileMs(95),0.001);
        assertEquals(9.6,f.spanSeconds(),0.001);
    }
    @Test public void jankIsCountedAboveThirtyTwoMilliseconds(){
        FrameStats f=new FrameStats();long t=0;
        long[] gaps={16,16,33,16,32,100,16};
        f.frame(t);for(long g:gaps){t+=g*MS;f.frame(t);}
        assertEquals(7,f.frames());
        assertEquals("33 and 100 ms are janky; exactly 32 ms is not",2,f.janky());
        assertEquals(100,f.worstMs(),0.001);
        assertEquals(16,f.medianMs(),0.001);
    }
    @Test public void repeatedOrBackwardsFrameTimesAreIgnored(){
        FrameStats f=new FrameStats();f.frame(100*MS);f.frame(100*MS);f.frame(90*MS);f.frame(116*MS);
        assertEquals(1,f.frames());assertEquals(16,f.worstMs(),0.001);
    }
    @Test public void reportCarriesTheNumbersTheOwnerNeeds(){
        FrameStats f=new FrameStats();long t=0;f.frame(t);for(int i=0;i<99;i++){t+=16*MS;f.frame(t);}t+=50*MS;f.frame(t);
        String r=FrameStats.report("Unified TV 0.5 (io.unifiedtv.launcher)\nTV: TCL 55C735, Android 11, build V8",10,f,new long[]{20,30,40},80*1024,84*1024,24*1024,256*1024);
        assertTrue(r.startsWith("Home measurement (AM-07, spec section 14)\nUnified TV 0.5"));
        assertTrue(r.contains("Frames: 100 (10.0 per second)"));
        assertTrue(r.contains("median 16.0 ms, 95th percentile 16.0 ms, worst 50.0 ms"));
        assertTrue(r.contains("Janky frames (over 32 ms): 1 (1.0%)"));
        assertTrue(r.contains("median 30 ms, 95th percentile 40 ms, worst 40 ms (3 presses)"));
        assertTrue(r.contains("fewer"));
        assertTrue(r.contains("Memory: app 84 MB (PSS), up 4 MB during the run; Java heap 24 of 256 MB"));
    }
    @Test public void inputStatsWindowReturnsOnlyNewSamples(){
        InputStats.reset();
        InputStats.add(10);InputStats.add(20);
        int mark=InputStats.total();
        InputStats.add(30);InputStats.add(-1);InputStats.add(40);
        assertArrayEquals(new long[]{30,40},InputStats.since(mark));
        for(int i=0;i<300;i++)InputStats.add(i);
        assertEquals("never more than the ring holds",128,InputStats.since(mark).length);
        assertEquals(299,InputStats.since(mark)[127]);
        assertTrue(InputStats.report().contains("128 samples"));
        InputStats.reset();assertEquals("no samples yet",InputStats.report());
    }
}
