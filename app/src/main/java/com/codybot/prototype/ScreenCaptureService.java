package com.codybot.prototype;

import android.app.*; import android.content.*; import android.graphics.*; import android.hardware.display.*; import android.media.*; import android.media.projection.*; import android.os.*; import android.util.DisplayMetrics; import java.nio.ByteBuffer;
import com.google.mlkit.vision.common.InputImage; import com.google.mlkit.vision.text.*; import java.util.*;

public class ScreenCaptureService extends Service {
    public static final String ACTION_CAPTURE_ONCE="com.codybot.prototype.CAPTURE_ONCE"; public static final String ACTION_CAPTURE_RESULT="com.codybot.prototype.CAPTURE_RESULT";
    private MediaProjection projection; private VirtualDisplay vd; private ImageReader reader; private boolean running=false;
    @Override public void onCreate(){ super.onCreate(); createChannel(); }
    private void createChannel(){ NotificationManager n=(NotificationManager)getSystemService(NOTIFICATION_SERVICE); if(Build.VERSION.SDK_INT>=26)n.createNotificationChannel(new NotificationChannel("codybot","CodyBot",NotificationManager.IMPORTANCE_LOW)); }
    private void foreground(){ Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"codybot"):new Notification.Builder(this); b.setContentTitle("CodyBot").setContentText("Lettura schermo attiva").setSmallIcon(android.R.drawable.ic_menu_view); startForeground(42,b.build()); }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent!=null && intent.getAction()!=null && ACTION_CAPTURE_ONCE.equals(intent.getAction())) { if(!running) captureOnce(); return START_NOT_STICKY; }
        if(intent!=null && intent.hasExtra("data")) { foreground(); startProjection(intent.getIntExtra("resultCode",Activity.RESULT_CANCELED),(Intent)intent.getParcelableExtra("data")); }
        return START_NOT_STICKY;
    }
    private void captureOnce(){ sendStatus("SCAN: nessuna autorizzazione cattura schermo. Apri CodyBot e premi AUTORIZZA CATTURA SCHERMO.",""); }
    private void startProjection(int result,Intent data){
        if(running)return; MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE); projection=m.getMediaProjection(result,data); if(projection==null)return;
        DisplayMetrics dm=getResources().getDisplayMetrics(); int w=dm.widthPixels,h=dm.heightPixels,d=dm.densityDpi; reader=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2);
        vd=projection.createVirtualDisplay("CodyBot",w,h,d,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,null); running=true; new Handler().postDelayed(this::takeFrame,500);
    }
    private void takeFrame(){
        if(reader==null){sendStatus("Cattura schermo non pronta","");return;}
        Image im=reader.acquireLatestImage(); if(im==null){new Handler().postDelayed(this::takeFrame,250);return;}
        int w=im.getWidth(),h=im.getHeight(); Image.Plane p=im.getPlanes()[0]; ByteBuffer buf=p.getBuffer(); int ps=p.getPixelStride(), rs=p.getRowStride(), pad=rs-ps*w; Bitmap b=Bitmap.createBitmap(w+pad/ps,h,Bitmap.Config.ARGB_8888); b.copyPixelsFromBuffer(buf); im.close(); if(pad>0)b=Bitmap.createBitmap(b,0,0,w,h);
        InputImage ii=InputImage.fromBitmap(b,0); TextRecognizer tr=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        tr.process(ii).addOnSuccessListener(result->{ String clue=pickClue(result,w,h); String raw=result.getText().replace('\n',' ').trim(); String answer=AnswerResolver.resolve(clue); String display=answer.isEmpty()?clue:clue+"\nRISPOSTA: "+answer; sendStatus(display,raw); }).addOnFailureListener(e->sendStatus("OCR fallito: "+e.getMessage(),""));
    }
    private String pickClue(Text result,int w,int h){
        List<Text.Element> els=new ArrayList<>(); for(Text.TextBlock block:result.getTextBlocks()) for(Text.Line line:block.getLines()) { android.graphics.Rect r=line.getBoundingBox(); if(r!=null && r.top>h*.42 && r.top<h*.82 && line.getText().trim().length()>5) els.addAll(line.getElements()); }
        String best=""; for(Text.TextBlock block:result.getTextBlocks()){ String t=block.getText().replace('\n',' ').trim(); android.graphics.Rect r=block.getBoundingBox(); if(r!=null && r.top>h*.42 && r.top<h*.82 && t.length()>best.length()) best=t; }
        return best;
    }
    private void sendStatus(String clue,String raw){ Intent i=new Intent(ACTION_CAPTURE_RESULT); i.setPackage(getPackageName()); i.putExtra("clue",clue); i.putExtra("raw",raw); sendBroadcast(i); }
    @Override public void onDestroy(){ if(vd!=null)vd.release(); if(reader!=null)reader.close(); if(projection!=null)projection.stop(); running=false; super.onDestroy(); }
    @Override public android.os.IBinder onBind(Intent i){return null;}
}
