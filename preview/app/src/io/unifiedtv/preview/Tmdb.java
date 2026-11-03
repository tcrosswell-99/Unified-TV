package io.unifiedtv.preview;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Posters by title from TMDB, with the household's own free key (owner decision, 25 October 2026). Only two hosts are
 * ever contacted: api.themoviedb.org (search) and image.tmdb.org (pictures), over HTTPS with redirects refused. A search
 * sends only a cleaned title and, when known, a year: never a file name, folder, server name or address.
 *
 * The key is read from the Vault ("tmdb", field "key"), put there by the phone setup (TmdbKey). A v3 API key goes as the
 * api_key parameter (TMDB's own scheme for v3 keys); a v4 read-access token goes as a Bearer header. Neither is logged.
 *
 * Matching is deliberately cautious (AGENTS rule 4: no poster is better than the wrong one): the normalised title must
 * match TMDB's, and a year that is known must agree (one year either way, for country release dates). With no year, an
 * exact title is accepted only when it is the clear favourite among same-named titles.
 *
 * This product uses the TMDB API but is not endorsed or certified by TMDB.
 */
final class Tmdb {
    static final String API_HOST="api.themoviedb.org",IMAGE_HOST="image.tmdb.org";
    static final String API="https://"+API_HOST+"/3",IMAGES="https://"+IMAGE_HOST+"/t/p/";
    static final String ATTRIBUTION="This product uses the TMDB API but is not endorsed or certified by TMDB.";
    static final int MAX_JSON=1024*1024,MAX_IMAGE=3*1024*1024;

    static final class Response {final int code;final byte[] body;final long retryAfterMs;
        Response(int code,byte[] body,long retryAfterMs){this.code=code;this.body=body==null?new byte[0]:body;this.retryAfterMs=retryAfterMs;}}
    /** The network, replaceable in tests (no network there). */
    interface Transport{Response get(String url,Map<String,String> headers,int limit)throws IOException;}
    /** TMDB is rate-limited; it said "slow down" (HTTP 429). */
    static final class Busy extends IOException{final long waitMs;Busy(long waitMs){super("busy");this.waitMs=waitMs;}}
    /** TMDB answered with another HTTP status: 401 or 403 means the key was refused ({@link #refused}). */
    static final class HttpError extends IOException{final int code;HttpError(String what,int code){super(what+" "+code);this.code=code;}
        boolean refused(){return code==401||code==403;}}

    /** One accepted match. */
    static final class Match {
        final String type;final long id;final String poster,backdrop,title,year;String overview="";
        Match(String type,long id,String poster,String backdrop,String title,String year){this.type=type;this.id=id;this.poster=poster==null?"":poster;this.backdrop=backdrop==null?"":backdrop;this.title=title==null?"":title;this.year=year==null?"":year;}
    }

    final String key;final boolean bearer;final Transport transport;
    Tmdb(String key,Transport transport){this.key=key==null?"":key.trim();this.bearer=isToken(this.key);this.transport=transport==null?Tmdb::https:transport;}
    /** A v4 read-access token is a long JWT ("eyJ..."); a v3 key is 32 hexadecimal characters. */
    static boolean isToken(String key){return key!=null&&key.startsWith("eyJ")&&key.length()>60;}
    boolean usable(){return !key.isEmpty()&&key.matches("[A-Za-z0-9._\\-]{16,1024}");}

    /** Only the two TMDB hosts, HTTPS on the standard port, nothing hidden in the address. */
    static boolean allowed(String url){
        try{URI u=new URI(url);String h=u.getHost();
            return "https".equals(u.getScheme())&&(API_HOST.equals(h)||IMAGE_HOST.equals(h))&&u.getUserInfo()==null&&(u.getPort()==-1||u.getPort()==443)&&u.getFragment()==null;}
        catch(Exception e){return false;}
    }
    /** The search address: the title, the year when known, the language and the key (v3 only). Nothing else. */
    String searchUrl(boolean tv,String title,String year){
        StringBuilder u=new StringBuilder(API).append(tv?"/search/tv":"/search/movie").append("?query=").append(enc(title)).append("&include_adult=false&language=en-GB&page=1");
        if(year!=null&&year.matches("\\d{4}"))u.append(tv?"&first_air_date_year=":"&year=").append(year);
        if(!bearer)u.append("&api_key=").append(enc(key));
        return u.toString();
    }
    private Map<String,String> headers(){Map<String,String> h=new HashMap<>();h.put("Accept","application/json");if(bearer)h.put("Authorization","Bearer "+key);return h;}
    /** Search results; throws Busy on 429, IOException when TMDB couldn't be reached or refused the key. */
    JSONArray search(boolean tv,String title,String year)throws IOException{
        String url=searchUrl(tv,title,year);if(!allowed(url))throw new IOException("not allowed");
        Response r=transport.get(url,headers(),MAX_JSON);
        if(r.code==429)throw new Busy(Math.max(1000,r.retryAfterMs));
        if(r.code!=200)throw new HttpError("TMDB",r.code);
        try{return new JSONObject(new String(r.body,StandardCharsets.UTF_8)).optJSONArray("results");}catch(JSONException e){throw new IOException("TMDB reply unreadable");}
    }
    /** Looks a title up and returns the cautious match, or null for no match. */
    Match find(boolean tv,String title,String year)throws IOException{
        if(MediaName.normal(title).isEmpty())return null;
        Match m=pick(search(tv,title,year),tv,title,year);
        // TMDB's year filter is exact, and a release can be dated a year apart by country: one more search without the
        // filter, still scored against the year. A year out by more than one finds nothing; the search never guesses wider.
        if(m==null&&year!=null&&year.matches("\\d{4}"))m=pick(search(tv,title,""),tv,title,year);
        return m;
    }
    /** A picture, by TMDB's path ("/abc.jpg") and size ("w342", "w780"). */
    byte[] image(String path,String size)throws IOException{
        if(path==null||!path.matches("/[A-Za-z0-9_\\-]{1,80}\\.(jpg|png)")||!size.matches("w\\d{2,4}|original"))throw new IOException("bad image path");
        String url=IMAGES+size+path;if(!allowed(url))throw new IOException("not allowed");
        Response r=transport.get(url,Collections.emptyMap(),MAX_IMAGE);
        if(r.code==429)throw new Busy(Math.max(1000,r.retryAfterMs));
        if(r.code!=200)throw new HttpError("TMDB image",r.code);
        return r.body;
    }

    // --- choosing a match (pure)
    /**
     * Best result, or null. Title: exact (normalised) 100, nearly all the same words (three or more) 70. Year, when the
     * name gave one: the same +20, out by one +5, otherwise refused. Accepted from 85: an exact title with a matching or
     * close year, a near title only with the same year, and with no year an exact title only.
     */
    static Match pick(JSONArray results,boolean tv,String title,String year){
        if(results==null)return null;String want=MediaName.normal(title);if(want.isEmpty())return null;
        boolean hasYear=year!=null&&year.matches("\\d{4}");int wantYear=hasYear?Integer.parseInt(year):0;
        List<Object[]> scored=new ArrayList<>();
        for(int i=0;i<Math.min(20,results.length());i++){JSONObject r=results.optJSONObject(i);if(r==null||r.optBoolean("adult"))continue;
            String name=r.optString(tv?"name":"title"),original=r.optString(tv?"original_name":"original_title");
            int s=Math.max(titleScore(want,MediaName.normal(name)),titleScore(want,MediaName.normal(original)));if(s==0)continue;
            String date=r.optString(tv?"first_air_date":"release_date");int y=date.matches("\\d{4}.*")?Integer.parseInt(date.substring(0,4)):0;
            if(hasYear){if(y==0)continue;int d=Math.abs(y-wantYear);if(d>1)continue;s+=d==0?20:5;}
            else if(s<100)continue;
            scored.add(new Object[]{s,r.optDouble("vote_count",0),r,y});
        }
        if(scored.isEmpty())return null;
        scored.sort((a,b)->{int c=Integer.compare((Integer)b[0],(Integer)a[0]);return c!=0?c:Double.compare((Double)b[1],(Double)a[1]);});
        Object[] best=scored.get(0);int bestScore=(Integer)best[0];
        if(bestScore<85)return null;
        if(scored.size()>1&&(Integer)scored.get(1)[0]==bestScore){
            // Two equally good titles: only a clear favourite is taken (many more votes); otherwise nothing.
            double first=(Double)best[1],second=(Double)scored.get(1)[1];
            if(!(first>=50&&first>=3*Math.max(1,second)))return null;
        }
        JSONObject r=(JSONObject)best[2];int y=(Integer)best[3];
        String poster=r.isNull("poster_path")?"":r.optString("poster_path"),backdrop=r.isNull("backdrop_path")?"":r.optString("backdrop_path");
        Match m=new Match(tv?"tv":"movie",r.optLong("id"),poster,backdrop,r.optString(tv?"name":"title"),y>0?String.valueOf(y):"");
        // The search reply already carries a short summary: shown for the highlighted title, no further request.
        String o=r.optString("overview","").trim();m.overview=o.length()>400?o.substring(0,397).trim()+"…":o;
        return m;
    }
    /**
     * SET-034: the first {@code max} results as candidates for the household to choose from by eye ("Wrong picture?"), in
     * TMDB's order except that titles from the known year (one either way) come first. Adult titles are never offered;
     * results with neither a poster nor a backdrop are left out, since choosing one would show nothing.
     */
    static List<Match> candidates(JSONArray results,boolean tv,String year,int max){
        List<Match> near=new ArrayList<>(),rest=new ArrayList<>();if(results==null)return near;
        boolean hasYear=year!=null&&year.matches("\\d{4}");int want=hasYear?Integer.parseInt(year):0;
        for(int i=0;i<Math.min(20,results.length());i++){JSONObject r=results.optJSONObject(i);if(r==null||r.optBoolean("adult"))continue;
            String poster=r.isNull("poster_path")?"":r.optString("poster_path"),backdrop=r.isNull("backdrop_path")?"":r.optString("backdrop_path");
            if(poster.isEmpty()&&backdrop.isEmpty())continue;
            String date=r.optString(tv?"first_air_date":"release_date");int y=date.matches("\\d{4}.*")?Integer.parseInt(date.substring(0,4)):0;
            Match m=new Match(tv?"tv":"movie",r.optLong("id"),poster,backdrop,r.optString(tv?"name":"title"),y>0?String.valueOf(y):"");
            String o=r.optString("overview","").trim();m.overview=o.length()>400?o.substring(0,397).trim()+"…":o;
            (hasYear&&y>0&&Math.abs(y-want)<=1?near:rest).add(m);
        }
        near.addAll(rest);return near.size()>max?new ArrayList<>(near.subList(0,max)):near;
    }
    static int titleScore(String want,String have){
        if(have.isEmpty())return 0;if(want.equals(have))return 100;
        // "Alien" against "Alien: Romulus" is no match, nor "Mission Impossible" against "Mission Impossible Fallout":
        // only a near-identical longer title ("Harry Potter and the Philosopher's Stone" / "...Philosophers Stone") counts.
        Set<String> a=new HashSet<>(Arrays.asList(want.split(" "))),b=new HashSet<>(Arrays.asList(have.split(" ")));
        Set<String> both=new HashSet<>(a);both.retainAll(b);Set<String> any=new HashSet<>(a);any.addAll(b);
        double j=any.isEmpty()?0:both.size()/(double)any.size();
        if(a.size()>=3&&j>=.8)return 70;
        return 0;
    }

    // --- the network (Android and JVM alike)
    private static String enc(String s){try{return URLEncoder.encode(s==null?"":s,"UTF-8");}catch(UnsupportedEncodingException e){throw new IllegalStateException(e);}}
    static Response https(String url,Map<String,String> headers,int limit)throws IOException{
        if(!allowed(url))throw new IOException("not allowed");
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(5000);c.setReadTimeout(8000);c.setUseCaches(false);
        for(Map.Entry<String,String> h:headers.entrySet())c.setRequestProperty(h.getKey(),h.getValue());
        try{int code=c.getResponseCode();long wait=0;String ra=c.getHeaderField("Retry-After");if(ra!=null)try{wait=Long.parseLong(ra.trim())*1000;}catch(NumberFormatException ignored){}
            if(code!=200)return new Response(code,null,wait);
            long length=c.getContentLengthLong();if(length>limit)throw new IOException("too large");
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1){if(out.size()+n>limit)throw new IOException("too large");out.write(b,0,n);}return new Response(200,out.toByteArray(),0);}
        }finally{c.disconnect();}
    }
}
