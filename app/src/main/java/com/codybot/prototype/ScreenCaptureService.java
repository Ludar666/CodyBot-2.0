package com.codybot.prototype;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import android.util.DisplayMetrics;
import androidx.annotation.Nullable;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.nio.ByteBuffer;

public class ScreenCaptureService extends Service {
    public static final String ACTION_TOGGLE="com.codybot.prototype.ACTION_TOGGLE";
    public static final String ACTION_START_CAPTURE="com.codybot.prototype.ACTION_START_CAPTURE";
    public static final String ACTION_STOP_CAPTURE="com.codybot.prototype.ACTION_STOP_CAPTURE";
    public static final String EXTRA_RESULT_CODE="result_code";
    public static final String EXTRA_DATA="projection_data";
    private static boolean running;
    private MediaProjection projection;
    private ImageReader reader;
    private android.hardware.display.VirtualDisplay display;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextRecognizer recognizer;
    private String lastClue="";
    private long lastScan=0;

    @Override public void onCreate(){
        super.onCreate();
        recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        startForeground(11, notification());
    }
    @Override public int onStartCommand(Intent i,int flags,int id){
        if(i==null) return START_STICKY;
        String a=i.getAction();
        if(ACTION_TOGGLE.equals(a)){ if(running) stopCapture(); else requestProjection(); }
        else if(ACTION_START_CAPTURE.equals(a)) startCapture(i);
        else if(ACTION_STOP_CAPTURE.equals(a)) stopCapture();
        updateStatus();
        return START_STICKY;
    }
    private Notification notification(){
        String ch="codybot_capture";
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel c=new NotificationChannel(ch,"CodyBot cattura",NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,ch):new Notification.Builder(this);
        return b.setContentTitle("CodyBot").setContentText("Cattura schermo attiva").setSmallIcon(android.R.drawable.ic_menu_view).build();
    }
    private void requestProjection(){
        Intent x=new Intent(this,MediaProjectionActivity.class); x.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(x);
        broadcast("RICHIESTA CATTURA: premi Consenti","");
    }
    private void startCapture(Intent i){
        try{
            stopCapture();
            int rc=i.getIntExtra(EXTRA_RESULT_CODE,0);
            Intent data=i.getParcelableExtra(EXTRA_DATA);
            MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            projection=m.getMediaProjection(rc,data);
            if(projection==null) throw new IllegalStateException("MediaProjection non disponibile");
            DisplayMetrics dm=getResources().getDisplayMetrics(); int w=dm.widthPixels,h=dm.heightPixels;
            reader=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2);
            reader.setOnImageAvailableListener(r->{
                if(!running)return;
                Image image=null;
                try{
                    image=r.acquireLatestImage();
                    if(image==null)return;
                    long now=System.currentTimeMillis();
                    if(now-lastScan<850)return;
                    lastScan=now;
                    Image.Plane p=image.getPlanes()[0]; ByteBuffer buf=p.getBuffer(); int ps=p.getPixelStride(), rs=p.getRowStride(); int pad=rs-ps*w;
                    Bitmap full=Bitmap.createBitmap(w+pad/ps,h,Bitmap.Config.ARGB_8888); full.copyPixelsFromBuffer(buf);
                    int top=(int)(h*.54f), bottom=(int)(h*.75f); Bitmap crop=Bitmap.createBitmap(full,0,top,w,bottom-top); full.recycle(); runOcr(crop);
                }catch(Exception e){broadcast("ERRORE CATTURA: "+e.getClass().getSimpleName(),"");}
                finally{if(image!=null)image.close();}
            },handler);
            display=projection.createVirtualDisplay("CodyBot",w,h,dm.densityDpi,android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,handler);
            running=true; lastScan=0; lastClue="";
            broadcast("🟢 SCANSIONE ATTIVA\nIn attesa dell'indizio...","");
        }catch(Exception e){broadcast("ERRORE AVVIO: "+e.getMessage(),"");stopCapture();}
    }
    private void runOcr(Bitmap bmp){
        InputImage input=InputImage.fromBitmap(bmp,0);
        recognizer.process(input).addOnSuccessListener(result->{
            String text=result.getText();
            bmp.recycle();
            if(text==null||text.trim().isEmpty())return;
            String clue=text.replaceAll("\\s+"," ").trim();
            if(clue.equalsIgnoreCase(lastClue))return;
            lastClue=clue;
            broadcast("🟢 SCANSIONE ATTIVA\nINDIZIO: "+clue+"\nRicerca risposta...",clue);
            String ans=AnswerResolver.resolve(this,clue,-1);
            if(ans!=null&&!ans.startsWith("Non in archivio")&&!ans.startsWith("Nessun")){
                broadcast("🟢 SCANSIONE ATTIVA\nINDIZIO: "+clue+"\nRISPOSTA: "+ans+"\nCompilazione...",ans);
                Intent x=new Intent("com.codybot.FILL_ANSWER"); x.setPackage(getPackageName()); x.putExtra("answer",ans); sendBroadcast(x);
            } else {
                broadcast("🟢 SCANSIONE ATTIVA\nINDIZIO: "+clue+"\nRISPOSTA: NON TROVATA","");
            }
        }).addOnFailureListener(e->{bmp.recycle(); broadcast("🟢 SCANSIONE ATTIVA\nOCR: ERRORE","");});
    }
    private void broadcast(String msg,String clue){ Intent x=new Intent("com.codybot.UPDATE_OVERLAY"); x.setPackage(getPackageName()); x.putExtra("message",msg); x.putExtra("clue",clue); sendBroadcast(x); }
    private void updateStatus(){ broadcast(running?"🟢 SCANSIONE ATTIVA":"🔴 SCANSIONE FERMA",""); }
    private void stopCapture(){ running=false; lastClue=""; lastScan=0; if(display!=null){display.release();display=null;} if(reader!=null){reader.close();reader=null;} if(projection!=null){projection.stop();projection=null;} broadcast("🔴 SCANSIONE FERMA",""); }
    public static boolean isServiceRunning(){return running;}
    @Nullable @Override public IBinder onBind(Intent i){return null;}
    @Override public void onDestroy(){stopCapture();if(recognizer!=null)recognizer.close();super.onDestroy();}
}
