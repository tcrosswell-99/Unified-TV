package io.unifiedtv.preview;
import java.util.Arrays;
final class InputStats {
    private static final long[] samples=new long[128];private static int count,next;
    static synchronized void add(long ms){if(ms<0||ms>5000)return;samples[next++%samples.length]=ms;count=Math.min(samples.length,count+1);}
    static synchronized String report(){if(count==0)return "no samples yet";long[] sorted=Arrays.copyOf(samples,count);Arrays.sort(sorted);return "p50 "+sorted[count/2]+" ms, p95 "+sorted[Math.min(count-1,(int)Math.ceil(count*.95)-1)]+" ms ("+count+" samples)";}
}
