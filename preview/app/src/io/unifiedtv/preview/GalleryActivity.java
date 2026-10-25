package io.unifiedtv.preview;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.*;
import android.view.KeyEvent;
import android.widget.ImageView;
import org.json.*;
import java.io.InputStream;
import java.util.concurrent.*;

/** Foreground-only gallery: no permanent clock, dock or status badges. */
/* OD-11: a setup screen, so the glass sidebar over a still Home (GlassScreen). */
public final class GalleryActivity extends GlassScreen {
    private final Handler timer=new Handler(Looper.getMainLooper());
    private final ExecutorService images=Executors.newSingleThreadExecutor();
    private JSONArray selected=new JSONArray();private ImageView image;private int index;private boolean active;private int generation;private int interval=45;private boolean paused;
    private final Runnable advance=new Runnable(){public void run(){if(!paused)showImage();timer.postDelayed(this,interval*1000L);}};
    @Override public void onCreate(Bundle b){super.onCreate(b);try{selected=new Vault(this).read("gallery").optJSONArray("images");}catch(Exception ignored){}if(selected==null)selected=new JSONArray();interval=getSharedPreferences("unified-tv-preview",0).getInt("gallerySeconds",45);
        if(selected.length()==0||getIntent().getBooleanExtra("edit",false)){manage();}
        else fullScreen();
    }
    private void manage(){
        image=null;generation++;timer.removeCallbacksAndMessages(null);
        page("Your Gallery");text("Display photographs and artwork when you are not watching TV.",23);text("Selected images: "+selected.length()+" · "+interval+" seconds per image",18);
        Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
        if(picker.resolveActivity(getPackageManager())!=null){button("Choose local or USB images",()->EditLock.ask(this,this::pick));text("Available sources depend on the TV file picker. A network source appears only if a compatible document provider is installed.",17);}
        button("Use five example artworks",()->EditLock.ask(this,()->{try{selected=new JSONArray();for(int n=1;n<=5;n++)selected.put("asset:///gallery/example-"+n+".jpg");save();fullScreen();}catch(Exception e){note("Unable to save gallery");}}));
        button("Slideshow timing",()->new android.app.AlertDialog.Builder(this).setTitle("Time per image").setItems(new String[]{"15 seconds","30 seconds","45 seconds","1 minute","2 minutes"},(d,n)->{interval=new int[]{15,30,45,60,120}[n];getSharedPreferences("unified-tv-preview",0).edit().putInt("gallerySeconds",interval).apply();manage();}).show());
        button("Use included coastal landscape",()->EditLock.ask(this,()->{
            try{selected=new JSONArray().put("asset:///unified-preview-coast.png");new Vault(this).save("gallery",new JSONObject().put("images",selected));fullScreen();}
            catch(Exception e){note("Unable to save gallery selection");}
        }));
        if(selected.length()>0){
            button("Remove an image",()->EditLock.ask(this,()->{
                String[] labels=new String[selected.length()];for(int n=0;n<labels.length;n++)labels[n]="Image "+(n+1);
                new android.app.AlertDialog.Builder(this).setTitle("Remove from gallery · originals are kept").setItems(labels,(d,which)->{
                    selected.remove(which);try{new Vault(this).save("gallery",new JSONObject().put("images",selected));}catch(Exception e){note("Unable to save gallery selection");}manage();
                }).show();
            }));
            button("Start gallery",this::fullScreen);
        }
        button("Back",this::finish);
    }
    private void save()throws Exception{new Vault(this).save("gallery",new JSONObject().put("images",selected));}
    private void pick(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);try{startActivityForResult(i,7);}catch(Exception e){note("This TV has no image picker. Gallery import is unavailable on this device.");}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=7||result!=RESULT_OK||data==null)return;
        JSONArray list=new JSONArray();try{
            int count=data.getClipData()==null?1:Math.min(100,data.getClipData().getItemCount());
            for(int n=0;n<count;n++){
                Uri uri=data.getClipData()==null?data.getData():data.getClipData().getItemAt(n).getUri();
                if(uri==null||!"content".equals(uri.getScheme()))continue;
                getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);list.put(uri.toString());
            }
            new Vault(this).save("gallery",new JSONObject().put("images",list));selected=list;fullScreen();
        }catch(Exception e){note("Unable to keep access to those images. Try local files.");}
    }
    private void fullScreen(){image=new ImageView(this);image.setBackgroundColor(0xff000000);image.setScaleType(ImageView.ScaleType.FIT_CENTER);setContentView(image);
        getWindow().getDecorView().setSystemUiVisibility(5894);timer.removeCallbacks(advance);if(active)timer.post(advance);
    }
    private void showImage(){final int ticket=++generation;if(!active||image==null||selected.length()==0)return;String source=selected.optString(index++%selected.length());
        images.execute(()->{Bitmap bitmap=null;try{
            Uri uri=Uri.parse(source);BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;
            try(InputStream in=openImage(uri)){BitmapFactory.decodeStream(in,null,opts);}
            opts.inJustDecodeBounds=false;opts.inSampleSize=1;while(opts.outWidth/opts.inSampleSize>1920||opts.outHeight/opts.inSampleSize>1080)opts.inSampleSize*=2;
            try(InputStream in=openImage(uri)){bitmap=BitmapFactory.decodeStream(in,null,opts);}
        }catch(Exception ignored){}final Bitmap next=bitmap;runOnUiThread(()->{if(active&&image!=null&&ticket==generation&&next!=null){image.setImageBitmap(next);if(!getSharedPreferences("unified-tv-preview",0).getBoolean("reduceMotion",false)){image.setAlpha(.6f);image.animate().alpha(1f).setDuration(400).start();}}});});
    }
    private InputStream openImage(Uri uri)throws java.io.IOException{
        String source=uri.toString();if(source.equals("asset:///unified-preview-coast.png"))return getAssets().open("unified-preview-coast.png");
        if(source.matches("asset:///gallery/example-[1-5]\\.jpg"))return getAssets().open(source.substring(9));
        if(!"content".equals(uri.getScheme()))throw new java.io.IOException("Unsupported image source");
        return getContentResolver().openInputStream(uri);
    }
    @Override protected void onResume(){super.onResume();active=true;if(image!=null)timer.post(advance);}
    @Override protected void onPause(){active=false;generation++;timer.removeCallbacksAndMessages(null);super.onPause();}
    @Override public boolean dispatchKeyEvent(KeyEvent e){if(image!=null){if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0){int k=e.getKeyCode();if(k==KeyEvent.KEYCODE_DPAD_CENTER||k==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)paused=!paused;else if(k==KeyEvent.KEYCODE_DPAD_RIGHT){showImage();}else if(k==KeyEvent.KEYCODE_DPAD_LEFT){index=Math.max(0,index-2);showImage();}else if(k==KeyEvent.KEYCODE_BACK)finish();}return true;}return super.dispatchKeyEvent(e);}
    @Override protected void onDestroy(){timer.removeCallbacksAndMessages(null);images.shutdownNow();super.onDestroy();}
}
