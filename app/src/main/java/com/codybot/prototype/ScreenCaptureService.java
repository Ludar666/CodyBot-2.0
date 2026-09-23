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
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.nio.ByteBuffer;
import java.util.ArrayList;

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
    private int lastAnswerLength=-1;

    @Override public void onCreate(){
        super.onCreate();
        recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        startForeground(11, notification());
    }

    @Override public int onStartCommand(Intent i,int flags,int id){
        if(i==null) return START_STICKY;
        String a=i.getAction();
        if(ACTION_TOGGLE.equals(a)){
            if(running) {
                // STOP = pausa: manteniamo la stessa sessione MediaProjection.
                // Cosi' START successivo non richiede di nuovo il consenso.
                stopScanning();
            } else if(projection != null && reader != null && display != null) {
                startScanning();
            } else {
                requestProjection();
            }
        }
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
        Intent x=new Intent(this,MediaProjectionActivity.class);
        x.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(x);
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

            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() {
                    handler.post(() -> {
                        stopScanning();
                        projection = null;
                    });
                }
            }, handler);

            startScanning();
        }catch(Exception e){
            broadcast("ERRORE AVVIO: "+e.getMessage(),"");
            stopCapture();
        }
    }

    private void startScanning(){
        if(projection==null || running) return;
        try{
            DisplayMetrics dm=getResources().getDisplayMetrics();
            int w=dm.widthPixels,h=dm.heightPixels;

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

                    Image.Plane p=image.getPlanes()[0];
                    ByteBuffer buf=p.getBuffer();
                    int ps=p.getPixelStride(), rs=p.getRowStride();
                    int pad=rs-ps*w;

                    Bitmap full=Bitmap.createBitmap(w+pad/ps,h,Bitmap.Config.ARGB_8888);
                    full.copyPixelsFromBuffer(buf);

                    int top=(int)(h*.38f);
                    int bottom=(int)(h*.76f);
                    Bitmap crop=Bitmap.createBitmap(full,0,top,w,bottom-top);
                    int answerLength=detectAnswerLength(full,w,h);
                    lastAnswerLength=answerLength;
                    full.recycle();
                    runOcr(crop,answerLength);
                }catch(Exception e){
                    if(running) broadcast("ERRORE CATTURA: "+e.getClass().getSimpleName(),"");
                }finally{
                    if(image!=null)image.close();
                }
            },handler);

            display=projection.createVirtualDisplay(
                    "CodyBot",w,h,dm.densityDpi,
                    android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader.getSurface(),null,handler);

            running=true;
            lastScan=0;
            lastClue="";
            broadcast("🟢 SCANSIONE ATTIVA\nIn attesa dell'indizio...","");
        }catch(Exception e){
            broadcast("ERRORE AVVIO: "+e.getMessage(),"");
            stopScanning();
        }
    }

    private void runOcr(Bitmap bmp,int detectedLength){
        if(!running){
            bmp.recycle();
            return;
        }

        InputImage input=InputImage.fromBitmap(bmp,0);
        recognizer.process(input).addOnSuccessListener(result->{
            bmp.recycle();

            // Se l'utente ha premuto STOP mentre l'OCR era in corso,
            // ignoriamo completamente il risultato arrivato in ritardo.
            if(!running)return;

            String clue=extractClue(result);
            if(clue.isEmpty())return;

            String normalized=normalizeClue(clue);
            if(normalized.isEmpty() || normalized.equals(normalizeClue(lastClue)))return;

            lastClue=clue;
            broadcast("🟢 SCANSIONE ATTIVA\\nINDIZIO: "+clue+"\\nCASELLE: "+(detectedLength>0?detectedLength:"?")+"\\nRicerca risposta...",clue);

            String ans=AnswerResolver.resolve(this,clue,detectedLength);

            // Non compilare mai una risposta arrivata dopo STOP.
            if(!running)return;

            if(ans!=null && !ans.startsWith("NON TROVATA") && !ans.startsWith("Nessun")
                    && (detectedLength <= 0 || answerLetterCount(ans) == detectedLength)){
                broadcast("🟢 SCANSIONE ATTIVA\\nINDIZIO: "+clue+"\\nRISPOSTA: "+ans+"\\nCompilazione...",ans);
                Intent x=new Intent("com.codybot.FILL_ANSWER");
                x.setPackage(getPackageName());
                x.putExtra("answer",ans);
                sendBroadcast(x);
            }else{
                broadcast("🟢 SCANSIONE ATTIVA\\nINDIZIO: "+clue+"\\nRISPOSTA: NON TROVATA","");
            }
        }).addOnFailureListener(e->{
            bmp.recycle();
            if(running)broadcast("🟢 SCANSIONE ATTIVA\\nOCR: ERRORE","");
        });
    }

    /**
     * Estrae il testo utile dai blocchi OCR invece di usare ciecamente
     * result.getText(). Questo evita che elementi secondari della schermata
     * finiscano dentro la domanda.
     */
    private int answerLetterCount(String answer){
        if(answer==null)return 0;
        return answer.replaceAll("[^A-ZÀÈÉÌÒÙ]","").length();
    }

    /**
     * Cerca la fila delle caselle della risposta usando i bordi verticali.
     * Non compila se il pattern non è sufficientemente regolare: la sicurezza
     * viene prima della velocità.
     */
    private int detectAnswerLength(Bitmap bmp,int w,int h){
        try{
            int y0=(int)(h*.40f), y1=(int)(h*.62f);
            int[] score=new int[w];
            for(int x=1;x<w-1;x++){
                int hits=0;
                for(int y=y0;y<y1;y+=2){
                    int a=lum(bmp.getPixel(x-1,y));
                    int b=lum(bmp.getPixel(x,y));
                    int c=lum(bmp.getPixel(x+1,y));
                    if(Math.abs(b-a)>38 || Math.abs(c-b)>38) hits++;
                }
                score[x]=hits;
            }
            ArrayList<Integer> peaks=new ArrayList<>();
            int threshold=Math.max(5,(y1-y0)/10);
            boolean in=false; int start=0;
            for(int x=1;x<w-1;x++){
                if(score[x]>=threshold){ if(!in){in=true;start=x;} }
                else if(in){
                    int end=x-1;
                    int best=start;
                    for(int k=start;k<=end;k++) if(score[k]>score[best]) best=k;
                    peaks.add(best); in=false;
                }
            }
            if(in)peaks.add(start);
            if(peaks.size()<3)return -1;

            int bestCount=-1;
            for(int i=0;i<peaks.size();i++){
                for(int j=i+2;j<peaks.size();j++){
                    int span=peaks.get(j)-peaks.get(i);
                    int n=j-i;
                    if(span<90 || span>Math.min(1800,w*.92))continue;
                    double pitch=(double)span/n;
                    if(pitch<18 || pitch>180)continue;
                    int count=0;
                    for(int k=i;k<=j;k++){
                        double expected=peaks.get(i)+(k-i)*pitch;
                        if(Math.abs(peaks.get(k)-expected)<=pitch*.28)count++;
                    }
                    if(count==n+1 && n>=2 && n<=15){
                        int slots=n;
                        if(slots>bestCount)bestCount=slots;
                    }
                }
            }
            return (bestCount>=2 && bestCount<=15)?bestCount:-1;
        }catch(Exception ignored){ return -1; }
    }

    private int lum(int color){
        return (int)(0.299f*((color>>16)&255)+0.587f*((color>>8)&255)+0.114f*(color&255));
    }

    private String extractClue(Text result){
        StringBuilder out=new StringBuilder();

        for(Text.TextBlock block: result.getTextBlocks()){
            String value=block.getText();
            if(value==null)continue;

            value=value.replaceAll("\\s+"," ").trim();
            if(value.isEmpty())continue;

            // Etichette che non fanno parte dell'indizio.
            value=value.replaceAll("(?i)\\bCodyCross\\b"," ");
            value=value.replaceAll("(?i)\\bORIZZONTALE\\b"," ");
            value=value.replaceAll("(?i)\\bVERTICALE\\b"," ");
            value=value.replaceAll("(?i)\\bINDIZIO\\b\\s*: ?"," ");

            // Il banner di CodyCross contiene spesso contatori numerici (es. 999+ 209 956)
            // che ML Kit può leggere come parte dell'indizio.
            value=value.replaceAll("(?<![A-Za-zÀ-ÖØ-öø-ÿ])(?:\\d+[+%]?\\s*){2,}", " ");
            value=value.replaceAll("(?<![A-Za-zÀ-ÖØ-öø-ÿ])\\d{2,4}(?=\\s|$)", " ");

            // Correzione OCR comune: 0 letto al posto della O dentro una parola.
            value=value.replaceAll("(?i)(?<=[A-Za-zÀ-ÖØ-öø-ÿ])0(?=[A-Za-zÀ-ÖØ-öø-ÿ])","o");
            value=value.replaceAll("\\s+"," ").trim();

            String letters=value.replaceAll("[^A-Za-zÀ-ÖØ-öø-ÿ0-9]","");
            if(letters.length()<2)continue;

            if(out.length()>0)out.append(" ");
            out.append(value);
        }

        String clue=out.toString()
                .replaceAll("[|_]+"," ")
                .replaceAll("\\s+"," ")
                .trim();

        if(clue.length()>140)clue=clue.substring(0,140).trim();
        return clue;
    }

    private String normalizeClue(String s){
        if(s==null)return "";
        return s.toLowerCase()
                .replaceAll("[^a-zàèéìòùáéíóú0-9 ]","")
                .replaceAll("\\s+"," ")
                .trim();
    }

    private void broadcast(String msg,String clue){
        Intent x=new Intent("com.codybot.UPDATE_OVERLAY");
        x.setPackage(getPackageName());
        x.putExtra("message",msg);
        x.putExtra("clue",clue);
        sendBroadcast(x);
    }

    private void updateStatus(){
        broadcast(running?"🟢 SCANSIONE ATTIVA":"🔴 SCANSIONE FERMA","");
    }

    private void stopScanning(){
        running=false;
        lastClue="";
        lastScan=0;
        // Non rilasciamo MediaProjection/VirtualDisplay: STOP e' una pausa.
        // Rilasciare il display renderebbe necessario un nuovo consenso Android
        // per la sessione successiva su Android 14+.
        broadcast("🔴 SCANSIONE IN PAUSA","");
    }

    private void stopCapture(){
        stopScanning();
        if(projection!=null){projection.stop();projection=null;}
    }

    public static boolean isServiceRunning(){return running;}

    @Nullable @Override public IBinder onBind(Intent i){return null;}

    @Override public void onDestroy(){
        stopCapture();
        if(recognizer!=null)recognizer.close();
        super.onDestroy();
    }
}
