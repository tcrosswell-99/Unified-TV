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
public final class GalleryActivity extends Screen {
    private final Handler timer=new Handler(Looper.getMainLooper());
    private final ExecutorService images=Executors.newSingleThreadExecutor();
    private JSONArray selected=new JSONArray();private ImageView image;private int index;private boolean active;
    private final Runnable advance=new Runnable(){public void run(){showImage();timer.postDelayed(this,45000);}};
    @Override public void onCreate(Bundle b){super.onCreate(b);try{selected=new Vault(this).read("gallery").optJSONArray("images");}catch(Exception ignored){}if(selected==null)selected=new JSONArray();
        if(selected.length()==0||getIntent().getBooleanExtra("edit",false)){manage();}
        else fullScreen();
    }
    private void manage(){
        page("Gallery");text("Choose your own photographs or artwork. Images stay on this TV. Selected images: "+selected.length(),22);
        button("Choose or replace images",()->EditLock.ask(this,this::pick));
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
    private void fullScreen(){image=new ImageView(this);image.setBackgroundColor(0xff000000);image.setScaleType(ImageView.ScaleType.CENTER_CROP);setContentView(image);
        getWindow().getDecorView().setSystemUiVisibility(5894);timer.removeCallbacks(advance);if(active)timer.post(advance);
    }
    private void showImage(){if(!active||image==null||selected.length()==0)return;String source=selected.optString(index++%selected.length());
        images.execute(()->{Bitmap bitmap=null;try{
            Uri uri=Uri.parse(source);BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;
            try(InputStream in=openImage(uri)){BitmapFactory.decodeStream(in,null,opts);}
            opts.inJustDecodeBounds=false;opts.inSampleSize=1;while(opts.outWidth/opts.inSampleSize>1920||opts.outHeight/opts.inSampleSize>1080)opts.inSampleSize*=2;
            try(InputStream in=openImage(uri)){bitmap=BitmapFactory.decodeStream(in,null,opts);}
        }catch(Exception ignored){}final Bitmap next=bitmap;runOnUiThread(()->{if(active&&next!=null)image.setImageBitmap(next);});});
    }
    private InputStream openImage(Uri uri)throws java.io.IOException{
        if(uri.toString().equals("asset:///unified-preview-coast.png"))return getAssets().open("unified-preview-coast.png");
        if(!"content".equals(uri.getScheme()))throw new java.io.IOException("Unsupported image source");
        return getContentResolver().openInputStream(uri);
    }
    @Override protected void onResume(){super.onResume();active=true;if(image!=null)timer.post(advance);}
    @Override protected void onPause(){active=false;timer.removeCallbacksAndMessages(null);super.onPause();}
    @Override public boolean dispatchKeyEvent(KeyEvent e){if(image!=null){if(e.getAction()==KeyEvent.ACTION_UP)finish();return true;}return super.dispatchKeyEvent(e);}
    @Override protected void onDestroy(){timer.removeCallbacksAndMessages(null);images.shutdownNow();super.onDestroy();}
}
