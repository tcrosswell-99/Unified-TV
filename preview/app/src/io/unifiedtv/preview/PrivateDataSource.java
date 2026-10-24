package io.unifiedtv.preview;

import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.datasource.*;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.util.*;

/** Stream only from the explicitly configured server. Redirects cannot forward credentials. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class PrivateDataSource extends BaseDataSource {
    private final JSONObject config; private HttpURLConnection connection; private InputStream in; private Uri uri; private long left;private boolean transferring;
    PrivateDataSource(JSONObject config){super(true);this.config=config;}
    @Override public long open(DataSpec spec)throws IOException{
        transferInitializing(spec);uri=spec.uri;
        return connect(spec);
    }
    private long connect(DataSpec spec)throws IOException{
        try{
            java.net.URI target=java.net.URI.create(spec.uri.toString());
            String path=target.getRawPath()+(target.getRawQuery()==null?"":"?"+target.getRawQuery());
            if(!Endpoint.resolve(config.getString("base"),path).toString().equals(target.toString()))throw new IOException("Foreign stream host refused");
            connection=LocalApi.open(config,path,"GET");
            if(spec.position>0||spec.length!=C.LENGTH_UNSET)connection.setRequestProperty("Range","bytes="+spec.position+"-"+(spec.length==C.LENGTH_UNSET?"":spec.position+spec.length-1));
            int code=connection.getResponseCode();
            if(code!=200&&code!=206)throw new IOException("Stream unavailable");
            in=connection.getInputStream();
            if(spec.position>0&&code==200){long skip=spec.position;while(skip>0){long n=in.skip(skip);if(n==0){if(in.read()==-1)throw new EOFException();n=1;}skip-=n;}}
            long content=connection.getContentLengthLong();left=spec.length!=C.LENGTH_UNSET?spec.length:content<0?C.LENGTH_UNSET:Math.max(0,content-(code==200?spec.position:0));
            transferStarted(spec);transferring=true;return left;
        }catch(Exception e){close();throw new IOException("Unable to open private media stream");}
    }
    @Override public int read(byte[] b,int offset,int length)throws IOException{
        if(length==0)return 0;if(left==0)return C.RESULT_END_OF_INPUT;
        int n=in.read(b,offset,left==C.LENGTH_UNSET?length:(int)Math.min(length,left));
        if(n==-1){if(left>0)throw new EOFException("Media stream ended early");return C.RESULT_END_OF_INPUT;}if(left!=C.LENGTH_UNSET)left-=n;bytesTransferred(n);return n;
    }
    @Override public Uri getUri(){return uri;}
    @Override public Map<String,List<String>> getResponseHeaders(){return connection==null?Collections.emptyMap():connection.getHeaderFields();}
    @Override public void close(){try{if(in!=null)in.close();}catch(IOException ignored){}in=null;if(connection!=null)connection.disconnect();connection=null;uri=null;if(transferring){transferring=false;transferEnded();}}
}
