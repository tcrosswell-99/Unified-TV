package io.unifiedtv.preview;
import java.util.Arrays;
/** Key-press-to-next-Home-draw samples (spec 14). Home adds them from onDraw; PerformanceActivity reads them. */
final class InputStats {
    private static final long[] samples=new long[128];private static int count,next;
    static synchronized void add(long ms){if(ms<0||ms>5000)return;samples[next++%samples.length]=ms;count=Math.min(samples.length,count+1);}
    /** How many samples have ever been added; pass it to since() to read only a measurement window. */
    static synchronized int total(){return next;}
    /** Samples added after mark=total(), oldest first; at most the last 128. */
    static synchronized long[] since(int mark){
        int n=Math.max(0,Math.min(Math.min(count,samples.length),next-mark));long[] out=new long[n];
        for(int i=0;i<n;i++)out[i]=samples[Math.floorMod(next-n+i,samples.length)];
        return out;
    }
    static synchronized String report(){if(count==0)return "no samples yet";long[] all=Arrays.copyOf(samples,count);
        return "p50 "+(long)FrameStats.percentile(all,50)+" ms, p95 "+(long)FrameStats.percentile(all,95)+" ms ("+count+" samples)";}
    /** For tests only. */
    static synchronized void reset(){Arrays.fill(samples,0);count=0;next=0;}
}
