package io.unifiedtv.preview;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.IOException;
import java.util.*;

/** TMDB: two hosts only, title-only searches, cautious matches. A fake transport: no network. */
public class TmdbTest {
    static JSONObject movie(long id,String title,String date,double votes)throws JSONException{return new JSONObject().put("id",id).put("title",title).put("original_title",title).put("release_date",date).put("vote_count",votes).put("poster_path","/p"+id+".jpg").put("backdrop_path","/b"+id+".jpg").put("overview","About "+title);}
    static JSONObject show(long id,String name,String date)throws JSONException{return new JSONObject().put("id",id).put("name",name).put("original_name",name).put("first_air_date",date).put("vote_count",100).put("poster_path","/t"+id+".jpg");}
    static JSONArray of(JSONObject... r){JSONArray a=new JSONArray();for(JSONObject o:r)a.put(o);return a;}

    @Test public void onlyTheTwoTmdbHostsOverHttps(){
        assertTrue(Tmdb.allowed("https://api.themoviedb.org/3/search/movie?query=x"));assertTrue(Tmdb.allowed("https://image.tmdb.org/t/p/w342/a.jpg"));
        for(String bad:new String[]{"http://api.themoviedb.org/3/x","https://api.themoviedb.org.evil.example/3","https://user@api.themoviedb.org/3","https://api.themoviedb.org:8443/3","https://example.com/","https://image.tmdb.org/t/p/w342/a.jpg#x","ftp://image.tmdb.org/"})
            assertFalse(bad,Tmdb.allowed(bad));
    }
    @Test public void aSearchSendsTheTitleAndYearOnly(){
        Tmdb v3=new Tmdb("0123456789abcdef0123456789abcdef",null);
        String u=v3.searchUrl(false,"Blade Runner 2049","2017");
        assertTrue(u,u.startsWith("https://api.themoviedb.org/3/search/movie?query=Blade+Runner+2049&"));assertTrue(u.contains("&year=2017"));assertTrue(u.contains("api_key="));
        Set<String> params=new HashSet<>();for(String kv:u.substring(u.indexOf('?')+1).split("&"))params.add(kv.substring(0,kv.indexOf('=')));
        assertEquals(new HashSet<>(Arrays.asList("query","include_adult","language","page","year","api_key")),params);
        assertTrue(new Tmdb("0123456789abcdef0123456789abcdef",null).searchUrl(true,"Bluey","2018").contains("/search/tv?")&&v3.searchUrl(true,"Bluey","2018").contains("first_air_date_year=2018"));
        Tmdb v4=new Tmdb("eyJ"+"a".repeat(100),null);assertTrue(v4.bearer);assertFalse("a v4 token never goes in the address",v4.searchUrl(false,"Up","").contains("api_key"));
    }
    @Test public void bearerTokenGoesInTheHeaderAndBusyIsReported()throws Exception{
        List<Map<String,String>> seen=new ArrayList<>();
        Tmdb t=new Tmdb("eyJ"+"a".repeat(100),(url,headers,limit)->{seen.add(headers);return new Tmdb.Response(429,null,3000);});
        try{t.search(false,"Up","");fail();}catch(Tmdb.Busy b){assertEquals(3000,b.waitMs);}
        assertTrue(seen.get(0).get("Authorization").startsWith("Bearer eyJ"));
        Tmdb refused=new Tmdb("0123456789abcdef0123456789abcdef",(url,h,l)->new Tmdb.Response(401,null,0));
        try{refused.search(false,"Up","");fail();}catch(Tmdb.Busy b){fail();}catch(IOException expected){}
    }
    @Test public void exactTitleAndYearMatch()throws Exception{
        Tmdb.Match m=Tmdb.pick(of(movie(1,"Dune","1984-12-14",3000),movie(2,"Dune","2021-09-15",12000)),false,"Dune","1984");
        assertEquals(1,m.id);assertEquals("/p1.jpg",m.poster);assertEquals("About Dune",m.overview);
        assertEquals("a year out by one (country release dates) still matches",2,Tmdb.pick(of(movie(2,"Dune","2021-09-15",12000)),false,"Dune","2022").id);
        assertNull("a year out by more is no match",Tmdb.pick(of(movie(2,"Dune","2021-09-15",12000)),false,"Dune","2019"));
    }
    @Test public void noWrongPosterIsBetterThanAWrongOne()throws Exception{
        assertNull("a different film that starts the same",Tmdb.pick(of(movie(3,"Alien: Romulus","2024-08-16",4000)),false,"Alien",""));
        assertNull("no year and two films of the same name with similar votes",Tmdb.pick(of(movie(4,"Heat","1995-12-15",900),movie(5,"Heat","1986-01-01",600)),false,"Heat",""));
        assertEquals("no year, but one is the clear favourite",4,Tmdb.pick(of(movie(4,"Heat","1995-12-15",9000),movie(5,"Heat","1986-01-01",60)),false,"Heat","").id);
        assertEquals("punctuation doesn't matter",6,Tmdb.pick(of(movie(6,"Harry Potter and the Philosopher's Stone","2001-11-16",9000)),false,"Harry Potter and the Philosophers Stone","2001").id);
        JSONArray near=of(movie(9,"Alpha Beta Gamma Delta Epsilon Zeta","2010-01-01",500));
        assertEquals(70,Tmdb.titleScore("alpha beta gamma delta epsilon","alpha beta gamma delta epsilon zeta"));
        assertNull("a near title with no year",Tmdb.pick(near,false,"Alpha Beta Gamma Delta Epsilon",""));
        assertNull("a near title with a year out by one",Tmdb.pick(near,false,"Alpha Beta Gamma Delta Epsilon","2011"));
        assertEquals("a near title with the same year",9,Tmdb.pick(near,false,"Alpha Beta Gamma Delta Epsilon","2010").id);
        JSONObject adult=movie(7,"Up","2009-05-28",9000).put("adult",true);assertNull(Tmdb.pick(of(adult),false,"Up","2009"));
        assertNull(Tmdb.pick(null,false,"Up",""));assertNull(Tmdb.pick(of(movie(8,"Up","2009-05-28",9000)),false,"",""));
    }
    @Test public void programmesMatchByName()throws Exception{
        Tmdb.Match m=Tmdb.pick(of(show(10,"Bluey","2018-10-01")),true,"Bluey","");assertEquals("tv",m.type);assertEquals("/t10.jpg",m.poster);
        assertNull(Tmdb.pick(of(show(11,"Doctor Who","1963-11-23"),show(12,"Doctor Who","2005-03-26")),true,"Doctor Who",""));
        assertEquals(12,Tmdb.pick(of(show(11,"Doctor Who","1963-11-23"),show(12,"Doctor Who","2005-03-26")),true,"Doctor Who","2005").id);
    }
    @Test public void findRetriesWithoutTheYearFilterOnce()throws Exception{
        List<String> urls=new ArrayList<>();
        Tmdb t=new Tmdb("0123456789abcdef0123456789abcdef",(url,h,l)->{urls.add(url);
            try{JSONArray r=url.contains("year=")?new JSONArray():of(movie(2,"Dune","2021-09-15",12000));return new Tmdb.Response(200,new JSONObject().put("results",r).toString().getBytes("UTF-8"),0);}
            catch(JSONException e){throw new IOException(e);}});
        assertEquals(2,t.find(false,"Dune","2022").id);assertEquals(2,urls.size());
        urls.clear();assertNull(t.find(false,"Dune","2019"));assertEquals(2,urls.size());
    }
    @Test public void imagesOnlyByTmdbPath(){
        Tmdb t=new Tmdb("0123456789abcdef0123456789abcdef",(url,h,l)->{assertTrue(url.startsWith("https://image.tmdb.org/t/p/w342/"));return new Tmdb.Response(200,new byte[]{1},0);});
        for(String bad:new String[]{"../x.jpg","/a/../b.jpg","https://evil.example/a.jpg","/a.gif"})try{t.image(bad,"w342");fail(bad);}catch(IOException expected){}
        try{assertEquals(1,t.image("/abc.jpg","w342").length);}catch(IOException e){fail();}
    }
}
