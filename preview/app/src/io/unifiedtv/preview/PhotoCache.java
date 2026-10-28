package io.unifiedtv.preview;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * "Keep a copy for when the NAS is off" (My Photos, off by default): screen-sized copies in the app's private storage,
 * never backed up (allowBackup is off) and never shared. Bounded to about 200 MB; the oldest copies go first. File
 * names are hashes, so no folder or picture name is written anywhere. Switching the option off, or choosing other
 * folders, empties it, so it can only ever hold pictures from the folders chosen now (WP-03, WP-10).
 */
final class PhotoCache {
    static final long LIMIT=200L*1024*1024;
    final File dir;final long limit;
    PhotoCache(File dir,long limit){this.dir=dir;this.limit=limit;}

    interface Writer{void write(OutputStream out)throws IOException;}

    static String name(String key){
        try{byte[] d=MessageDigest.getInstance("SHA-256").digest((key==null?"":key).getBytes(StandardCharsets.UTF_8));
            StringBuilder s=new StringBuilder();for(int i=0;i<16;i++)s.append(String.format(Locale.ROOT,"%02x",d[i]&255));return s.append(".jpg").toString();}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    File file(String key){return new File(dir,name(key));}
    boolean has(String key){return file(key).isFile();}

    /** Kept copies, oldest first (last written or shown). */
    synchronized List<File> all(){
        File[] files=dir.listFiles((d,n)->n.endsWith(".jpg"));if(files==null)return new ArrayList<>();
        List<File> out=new ArrayList<>(Arrays.asList(files));
        out.sort(Comparator.comparingLong(File::lastModified).thenComparing(File::getName));return out;
    }
    synchronized long size(){long n=0;for(File f:all())n+=f.length();return n;}

    /** Writes a copy whole or not at all (a half-written file is never shown), then trims to the limit. */
    synchronized File put(String key,Writer writer)throws IOException{
        if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("No cache folder");
        File tmp=new File(dir,"incoming.tmp"),out=file(key);
        try(OutputStream o=new BufferedOutputStream(new FileOutputStream(tmp))){writer.write(o);}
        catch(IOException|RuntimeException e){tmp.delete();throw e;}
        if(out.exists()&&!out.delete()){tmp.delete();throw new IOException("Cannot replace copy");}
        if(!tmp.renameTo(out)){tmp.delete();throw new IOException("Cannot keep copy");}
        out.setLastModified(System.currentTimeMillis());
        trim();return out;
    }
    /** A copy that was just shown counts as new, so the pictures still in the folders outlive ones that have gone. */
    synchronized void touch(File f){if(f.isFile())f.setLastModified(System.currentTimeMillis());}
    /** Deletes the oldest copies until the rest fit the limit; a stray partial write goes too. */
    synchronized void trim(){
        new File(dir,"incoming.tmp").delete();
        List<File> files=all();long total=0;for(File f:files)total+=f.length();
        for(Iterator<File> it=files.iterator();it.hasNext()&&total>limit;){File f=it.next();long n=f.length();if(f.delete())total-=n;}
    }
    synchronized void clear(){File[] files=dir.listFiles();if(files!=null)for(File f:files)if(f.isFile())f.delete();}
}
