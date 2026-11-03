package io.unifiedtv.preview;
import org.junit.Test;
import static org.junit.Assert.*;

/** File and folder names read as people name them: only the clean title and year ever leave the TV. */
public class MediaNameTest {
    private static void film(String raw,String title,String year){MediaName.Parsed p=MediaName.parse(raw);assertEquals(raw,title,p.title);assertEquals(raw,year,p.year);assertFalse(raw,p.episodic());}
    private static void episode(String raw,String show,int season,int episode,String name){
        MediaName.Parsed p=MediaName.parse(raw);assertTrue(raw,p.episodic());assertEquals(raw,show,p.title);assertEquals(raw,season,p.season);assertEquals(raw,episode,p.episode);assertEquals(raw,name,p.episodeTitle);
    }
    @Test public void filmNames(){
        film("Title (2019).mkv","Title","2019");
        film("Title.2019.1080p.BluRay.x264-GRP.mkv","Title","2019");
        film("The.Matrix.1999.mkv","The Matrix","1999");
        film("Blade Runner 2049 (2017).mkv","Blade Runner 2049","2017");
        film("Blade.Runner.2049.2017.2160p.UHD.HDR.mkv","Blade Runner 2049","2017");
        film("1917.mkv","1917","");
        film("2001 A Space Odyssey (1968).mp4","2001 A Space Odyssey","1968");
        film("Mr. Smith Goes to Washington.avi","Mr. Smith Goes to Washington","");
        film("Ocean's_Eleven_2001_720p.mp4","Ocean's Eleven","2001");
        film("Amélie [2001] [1080p].mkv","Amélie","2001");
        film("Up.m4v","Up","");
        film("Dune Part Two 2024 WEB-DL DDP5.1 Atmos.mkv","Dune Part Two","2024");
        film("S.W.A.T.2003.mkv","S.W.A.T.","2003");
        film("Toy Story 4","Toy Story 4","");
    }
    @Test public void episodeNames(){
        episode("Show S01E02 Episode Name.mkv","Show",1,2,"Episode Name");
        episode("Doctor.Who.2005.S03E07.42.720p.HDTV.mkv","Doctor Who",3,7,"42");
        episode("Bluey - s02e10 - Dance Mode.mp4","Bluey",2,10,"Dance Mode");
        episode("Show.1x02.mkv","Show",1,2,"");
        episode("The Office - Season 2 Episode 3 - The Fire.avi","The Office",2,3,"The Fire");
        episode("S01E01.mkv","",1,1,"");
        assertEquals("the year before SxxEyy belongs to the programme","2005",MediaName.parse("Doctor.Who.2005.S03E07.mkv").year);
    }
    @Test public void foldersAndNumbers(){
        assertEquals(1,MediaName.seasonFolder("Season 1"));assertEquals(2,MediaName.seasonFolder("Series 02"));assertEquals(3,MediaName.seasonFolder("S3"));
        assertEquals(0,MediaName.seasonFolder("Specials"));assertEquals(-1,MediaName.seasonFolder("Seasons Greetings"));assertEquals(-1,MediaName.seasonFolder("Video"));
        assertEquals(1,MediaName.episodeNumber("01 - Pilot.mkv"));assertEquals(3,MediaName.episodeNumber("Episode 3.mkv"));assertEquals(4,MediaName.episodeNumber("E04 The Name.mkv"));
        assertEquals(-1,MediaName.episodeNumber("Pilot.mkv"));
        assertTrue(MediaName.generic("movie"));assertFalse(MediaName.generic("Heat"));
    }
    @Test public void normalisedForComparison(){
        assertEquals("matrix",MediaName.normal("The Matrix"));assertEquals("amelie",MediaName.normal("Amélie"));
        assertEquals("fast and furious",MediaName.normal("Fast & Furious"));assertEquals("oceans eleven",MediaName.normal("Ocean's Eleven"));
        assertEquals("",MediaName.normal(null));
    }
}
