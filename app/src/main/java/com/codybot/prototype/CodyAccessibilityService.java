package com.codybot.app;

import com.codybot.prototype.ScreenCaptureService;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CodyAccessibilityService extends AccessibilityService {
 private WindowManager windowManager; private View overlayView; private volatile boolean overlayHidden=false; private TextView statusText; private View calibrationView; private final Handler handler=new Handler(Looper.getMainLooper());
 private final List<Runnable> pendingCompilation=new ArrayList<>(); private boolean compiling=false; private static volatile String lastTargetPackage=""; private volatile boolean calibrationInProgress=false; private final String calibrationLetters="ABCDEFGHIJKLMNOPQRSTUVWXYZ"; private int calibrationIndex=0; private float[] manualCalibrationCenters; private volatile float[] calibratedCenters; private TextRecognizer recognizer; private volatile boolean schemaCapturePending=false; private volatile String currentSchemaAnswer="";
 private final Runnable overlayChecker=new Runnable(){public void run(){if(overlayHidden)return;if(overlayView==null){if(Settings.canDrawOverlays(CodyAccessibilityService.this))showOverlay();else handler.postDelayed(this,1000);}}};
 private final BroadcastReceiver overlayReceiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){if(i==null)return;if(i.hasExtra("message")&&statusText!=null)statusText.setText(i.getStringExtra("message"));String a=i.getAction();if("com.codybot.FILL_ANSWER".equals(a)){String s=i.getStringExtra("answer");if(s!=null&&!s.trim().isEmpty()&&ScreenCaptureService.isServiceRunning())fillAnswer(s);}else if("com.codybot.STOP_COMPILATION".equals(a))stopCompilation();else if("com.codybot.TEST_KEYBOARD".equals(a)){if(ScreenCaptureService.isServiceRunning())runKeyboardTest();else updateOverlayText("⚠️ TEST: avvia prima START");}else if("com.codybot.CALIBRATE_KEYBOARD".equals(a))startManualKeyboardCalibration();else if("com.codybot.CALIBRATE_SCHEMA".equals(a)){int n=i.getIntExtra("length",0);if(n>0)startSchemaCalibration(n);}else if("com.codybot.TEST_SCHEMA".equals(a)){int n=i.getIntExtra("length",0);if(n>0)testSchemaCalibration(n);}else if("com.codybot.EXPORT_SCHEMA".equals(a))exportSchemaCalibration();else if("com.codybot.HIDE_OVERLAY".equals(a))hideOverlay();}};
 @Override public void onAccessibilityEvent(AccessibilityEvent e){if(e!=null&&e.getPackageName()!=null){String n=e.getPackageName().toString();if(!n.equals(getPackageName())&&!n.equals("android")&&!n.equals("com.android.systemui"))lastTargetPackage=n;}if(!overlayHidden&&overlayView==null&&Settings.canDrawOverlays(this))handler.post(overlayChecker);}
 public static String getLastTargetPackage(){return lastTargetPackage;}
 @Override protected void onServiceConnected(){super.onServiceConnected();recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);loadManualCalibration();try{registerReceiver(overlayReceiver,new IntentFilter("com.codybot.UPDATE_OVERLAY"));registerReceiver(overlayReceiver,new IntentFilter("com.codybot.FILL_ANSWER"));registerReceiver(overlayReceiver,new IntentFilter("com.codybot.STOP_COMPILATION"));registerReceiver(overlayReceiver,new IntentFilter("com.codybot.TEST_KEYBOARD"));registerReceiver(overlayReceiver,new IntentFilter("com.codybot.CALIBRATE_KEYBOARD"));registerReceiver(overlayReceiver,new IntentFilter("com.codybot.CALIBRATE_SCHEMA"));registerReceiver(overlayReceiver,new IntentFilter("com.codybot.TEST_SCHEMA"));registerReceiver(overlayReceiver,new IntentFilter("com.codybot.EXPORT_SCHEMA"));registerReceiver(overlayReceiver,new IntentFilter("com.codybot.HIDE_OVERLAY"));}catch(Exception ignored){}overlayHidden=false;handler.post(overlayChecker);}
 private void showOverlay(){if(overlayHidden||overlayView!=null||!Settings.canDrawOverlays(this))return;windowManager=(WindowManager)getSystemService(WINDOW_SERVICE);LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setGravity(Gravity.CENTER_HORIZONTAL);l.setBackgroundColor(Color.parseColor("#CC000000"));l.setPadding(18,8,18,8);statusText=new TextView(this);statusText.setText("CodyBot 3.1 PRONTO");statusText.setTextColor(Color.WHITE);statusText.setTextSize(14);statusText.setGravity(Gravity.CENTER);statusText.setMaxLines(6);l.addView(statusText,new LinearLayout.LayoutParams(-1,-2));LinearLayout b=new LinearLayout(this);b.setGravity(Gravity.CENTER);Button start=new Button(this);start.setText("START");start.setOnClickListener(v->{Intent i=new Intent(this,ScreenCaptureService.class);i.setAction(ScreenCaptureService.ACTION_START_SCAN);try{startService(i);}catch(Exception e){updateOverlayText("Errore: "+e.getClass().getSimpleName());}});Button stop=new Button(this);stop.setText("STOP");stop.setOnClickListener(v->{stopCompilation();Intent i=new Intent(this,ScreenCaptureService.class);i.setAction(ScreenCaptureService.ACTION_PAUSE_CAPTURE);try{startService(i);}catch(Exception e){updateOverlayText("STOP: "+e.getClass().getSimpleName());}});b.addView(start);b.addView(stop);l.addView(b);overlayView=l;DisplayMetrics dm=getResources().getDisplayMetrics();WindowManager.LayoutParams p=new WindowManager.LayoutParams((int)(dm.widthPixels*.92f),-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;p.y=70;try{windowManager.addView(overlayView,p);}catch(Exception e){overlayView=null;handler.postDelayed(overlayChecker,1000);}}
 private void startManualKeyboardCalibration(){stopCompilation();if(!Settings.canDrawOverlays(this)){updateOverlayText("⚠️ Attiva prima la sovrapposizione");return;}if(calibrationView!=null)removeCalibrationOverlay();LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);p.setGravity(Gravity.CENTER_HORIZONTAL);p.setPadding(28,24,28,24);p.setBackgroundColor(Color.parseColor("#EE111111"));TextView t=new TextView(this);t.setText("🔧 CALIBRAZIONE TASTIERA");t.setTextColor(Color.WHITE);t.setTextSize(20);t.setGravity(Gravity.CENTER);p.addView(t,new LinearLayout.LayoutParams(-1,-2));TextView m=new TextView(this);m.setText("1. Apri CodyCross.\n\n2. Entra in una domanda e lascia visibile tutta la tastiera.\n\n3. Premi AVVIA ACQUISIZIONE.\n\nCodyBot chiederà A, B, C... fino a Z.");m.setTextColor(Color.WHITE);m.setTextSize(16);m.setPadding(0,20,0,20);p.addView(m,new LinearLayout.LayoutParams(-1,-2));LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER);Button c=new Button(this);c.setText("ANNULLA");c.setOnClickListener(v->removeCalibrationOverlay());Button s=new Button(this);s.setText("AVVIA ACQUISIZIONE");s.setOnClickListener(v->{removeCalibrationOverlay();updateOverlayText("🔧 Preparazione calibrazione...");handler.postDelayed(this::showManualCalibrationOverlay,500);});r.addView(c);r.addView(s);p.addView(r);calibrationView=p;DisplayMetrics dm=getResources().getDisplayMetrics();WindowManager.LayoutParams q=new WindowManager.LayoutParams((int)(dm.widthPixels*.90f),-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);q.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;q.y=120;try{windowManager.addView(calibrationView,q);}catch(Exception e){calibrationView=null;updateOverlayText("❌ Errore calibrazione: "+e.getClass().getSimpleName());}}
 private void showManualCalibrationOverlay(){if(!Settings.canDrawOverlays(this)){updateOverlayText("⚠️ Attiva prima la sovrapposizione");return;}DisplayMetrics dm=getResources().getDisplayMetrics();final int sw=dm.widthPixels,sh=dm.heightPixels;final float[] c=new float[52];calibrationIndex=0;calibrationInProgress=true;FrameLayout f=new FrameLayout(this);f.setBackgroundColor(Color.TRANSPARENT);f.setClickable(true);TextView p=new TextView(this);p.setTextColor(Color.WHITE);p.setTextSize(18);p.setGravity(Gravity.CENTER);p.setBackgroundColor(Color.parseColor("#DD000000"));p.setText("🔧 CALIBRAZIONE TASTIERA\n\nTocca la lettera A\n0/26");FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);pp.topMargin=55;f.addView(p,pp);f.setOnTouchListener((v,e)->{if(!calibrationInProgress||e==null||e.getAction()!=android.view.MotionEvent.ACTION_UP)return true;if(e.getY()<220)return true;int i=calibrationIndex;if(i>=26)return true;c[i*2]=e.getX();c[i*2+1]=e.getY();calibrationIndex++;if(calibrationIndex<26){char n=calibrationLetters.charAt(calibrationIndex);p.setText("🔧 CALIBRAZIONE TASTIERA\n\nTocca la lettera "+n+"\n"+calibrationIndex+"/26");}else{manualCalibrationCenters=c.clone();saveManualCalibration(c,sw,sh);calibrationInProgress=false;removeCalibrationOverlay();calibratedCenters=c.clone();updateOverlayText("✅ CALIBRAZIONE COMPLETATA\n26/26 tasti salvati");}return true;});calibrationView=f;WindowManager.LayoutParams q=new WindowManager.LayoutParams(sw,sh,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);q.gravity=Gravity.TOP|Gravity.START;try{windowManager.addView(calibrationView,q);}catch(Exception e){calibrationView=null;calibrationInProgress=false;updateOverlayText("❌ Errore calibrazione: "+e.getClass().getSimpleName());}}
 private void removeCalibrationOverlay(){if(calibrationView!=null&&windowManager!=null)try{windowManager.removeView(calibrationView);}catch(Exception ignored){}calibrationView=null;}
 private void saveManualCalibration(float[] c,int w,int h){StringBuilder s=new StringBuilder();for(int i=0;i<c.length;i++){if(i>0)s.append(',');s.append(c[i]);}getSharedPreferences("codybot_keyboard",MODE_PRIVATE).edit().putString("centers",s.toString()).putInt("width",w).putInt("height",h).apply();}
 private void loadManualCalibration(){String raw=getSharedPreferences("codybot_keyboard",MODE_PRIVATE).getString("centers",null);if(raw==null)return;String[] p=raw.split(",");if(p.length!=52)return;try{float[] c=new float[52];for(int i=0;i<52;i++)c[i]=Float.parseFloat(p[i]);manualCalibrationCenters=c;calibratedCenters=c.clone();}catch(Exception ignored){manualCalibrationCenters=null;}}
 private void runKeyboardTest(){stopCompilation();String clean="ABCDEFGHIJKLMNOPQRSTUVWXYZ";compiling=true;calibratedCenters=manualCalibrationCenters!=null?manualCalibrationCenters.clone():null;updateOverlayText("🧪 TEST TASTIERA\nA-Z in corso...");DisplayMetrics dm=getResources().getDisplayMetrics();for(int i=0;i<clean.length();i++){final char c=clean.charAt(i);final float[] xy=calibratedKeyCenter(c,dm.widthPixels,dm.heightPixels);final int n=i+1;Runnable r=()->{if(compiling&&ScreenCaptureService.isServiceRunning()&&xy!=null){tap(xy[0],xy[1]);updateOverlayText("🧪 TEST TASTIERA\nPremuto: "+c+" ("+n+"/26)");}};pendingCompilation.add(r);handler.postDelayed(r,i*300L);}Runnable f=()->{if(compiling){compiling=false;pendingCompilation.clear();updateOverlayText("🧪 TEST TERMINATO\nControlla quali lettere sono state digitate.");}};pendingCompilation.add(f);handler.postDelayed(f,clean.length()*300L+500L);}
 private void fillAnswer(String answer){stopCompilation();final String clean=answer.toUpperCase().replaceAll("[^A-Z]","");if(clean.isEmpty())return;compiling=true;currentSchemaAnswer=clean;calibratedCenters=manualCalibrationCenters!=null?manualCalibrationCenters.clone():null;updateOverlayText("🟢 COMPILAZIONE\nRISPOSTA: "+clean+"\nControllo schema calibrato...");if(!hasSchemaCalibration(clean.length())){updateOverlayText("⚠️ Nessuna calibrazione per "+clean.length()+" lettere\nCompilo tutte le posizioni.");scheduleAnswerTaps(clean,new java.util.HashSet<Integer>());return;}schemaCapturePending=true;if(!ScreenCaptureService.requestSchemaCapture(this)){schemaCapturePending=false;scheduleAnswerTaps(clean,new java.util.HashSet<Integer>());return;}handler.postDelayed(()->{if(compiling&&schemaCapturePending){schemaCapturePending=false;updateOverlayText("⚠️ Acquisizione schema non disponibile\nCompilo tutte le posizioni.");scheduleAnswerTaps(clean,new java.util.HashSet<Integer>());}},1800L);}
 private void findExistingLettersAndSchedule(Bitmap b,String clean){
  schemaCapturePending=false;
  if(b==null||recognizer==null){
    scheduleAnswerTaps(clean,new java.util.HashSet<Integer>());
    return;
  }

  final Bitmap source=b.copy(Bitmap.Config.ARGB_8888,false);
  try{b.recycle();}catch(Exception ignored){}
  if(source==null){
    scheduleAnswerTaps(clean,new java.util.HashSet<Integer>());
    return;
  }

  final int[] baseCenters=getSchemaCenters(clean.length());
  if(baseCenters==null){
    try{source.recycle();}catch(Exception ignored){}
    scheduleAnswerTaps(clean,new java.util.HashSet<Integer>());
    return;
  }

  // The schema is reflowed when the answer length changes and then moves
  // vertically while the player advances.  Calibration therefore describes
  // the geometry, not the final Y coordinate.  Find the common vertical
  // translation in the current screenshot and apply it to every cell.
  final int[] centers=shiftSchemaCentersVertically(source,baseCenters);
  final int spacing=schemaSpacing(centers);
  final int half=Math.max(22,Math.min(70,(int)(spacing*.42f)));
  final java.util.Set<Integer> found=new java.util.HashSet<Integer>();

  updateOverlayText("🟢 COMPILAZIONE\nRISPOSTA: "+clean+"\nSchema trovato - controllo lettere...");
  processSchemaCell(source,clean,centers,0,half,found,()->{
    // Safety: never blindly type the whole answer if the schema position
    // detection produced no positive match.
    if(found.isEmpty()){
      updateOverlayText("⚠️ Schema non riconosciuto con sufficiente sicurezza\\nNessuna lettera confermata: non digito.");
      compiling=false;
      pendingCompilation.clear();
      calibratedCenters=null;
    }else{
      scheduleAnswerTaps(clean,found);
    }
    try{source.recycle();}catch(Exception ignored){}
  });
 }

 private int[] shiftSchemaCentersVertically(Bitmap b,int[] base){
  int[] out=base.clone();
  if(b==null||base==null||base.length<2)return out;

  int spacing=schemaSpacing(base);
  int half=Math.max(18,Math.min(65,(int)(spacing*.34f)));

  // We expect a vertical scroll, not a horizontal re-layout: X remains
  // calibrated while every cell receives approximately the same Y offset.
  int bestDy=0;
  long bestScore=Long.MIN_VALUE;

  for(int dy=-900;dy<=900;dy+=4){
    long score=0;
    int samples=0;

    for(int i=0;i<base.length/2;i++){
      int cx=base[i*2];
      int cy=base[i*2+1]+dy;
      if(cx-half<2||cx+half>=b.getWidth()||cy-half<2||cy+half>=b.getHeight())continue;

      // Score the four sides of a cell.  A CodyCross box has a much stronger
      // local contrast at its perimeter than in the middle of the cell.
      score+=schemaEdge(b,cx-half,cy-half,cx+half,cy-half,true);
      score+=schemaEdge(b,cx-half,cy+half,cx+half,cy+half,true);
      score+=schemaEdge(b,cx-half,cy-half,cx-half,cy+half,false);
      score+=schemaEdge(b,cx+half,cy-half,cx+half,cy+half,false);
      samples++;
    }

    if(samples>=Math.max(2,base.length/4)){
      // Prefer a shift supported by many calibrated cells, not an isolated
      // strong line elsewhere on the screen.
      score += samples*35L;
      if(score>bestScore){
        bestScore=score;
        bestDy=dy;
      }
    }
  }

  for(int i=0;i<out.length/2;i++)out[i*2+1]+=bestDy;
  if(Math.abs(bestDy)>=4)
    updateOverlayText("🟢 COMPILAZIONE\nSchema spostato: "+bestDy+" px\nControllo lettere...");
  return out;
 }

 private int schemaEdge(Bitmap b,int x1,int y1,int x2,int y2,boolean horizontal){
  int n=horizontal?Math.max(2,(x2-x1)/6):Math.max(2,(y2-y1)/6);
  int sum=0;
  for(int i=0;i<=n;i++){
    float t=(float)i/(float)n;
    int x=horizontal?Math.round(x1+(x2-x1)*t):x1;
    int y=horizontal?y1:Math.round(y1+(y2-y1)*t);
    if(x<2||y<2||x>=b.getWidth()-2||y>=b.getHeight()-2)continue;

    int c=lum(b.getPixel(x,y));
    int a,bv;
    if(horizontal){
      a=lum(b.getPixel(x,Math.max(0,y-3)));
      bv=lum(b.getPixel(x,Math.min(b.getHeight()-1,y+3)));
    }else{
      a=lum(b.getPixel(Math.max(0,x-3),y));
      bv=lum(b.getPixel(Math.min(b.getWidth()-1,x+3),y));
    }
    sum+=Math.abs(c-a)+Math.abs(c-bv);
  }
  return sum;
 }
 private void processSchemaCell(Bitmap b,String clean,int[] centers,int index,int half,java.util.Set<Integer> found,Runnable done){
  if(index>=clean.length()){done.run();return;}
  final int sw=b.getWidth(),sh=b.getHeight(),cx=centers[index*2],cy=centers[index*2+1];
  final int left=Math.max(0,cx-half),top=Math.max(0,cy-half),right=Math.min(sw,cx+half),bottom=Math.min(sh,cy+half);
  if(right<=left||bottom<=top){processSchemaCell(b,clean,centers,index+1,half,found,done);return;}
  final Bitmap cell=Bitmap.createBitmap(b,left,top,right-left,bottom-top);
  InputImage image=InputImage.fromBitmap(cell,0);
  recognizer.process(image).addOnSuccessListener(result->{
    boolean letter=false;
    char expected=clean.charAt(index);
    for(Text.TextBlock block:result.getTextBlocks())
      for(Text.Line line:block.getLines())
        for(Text.Element el:line.getElements()){
          String t=el.getText()==null?"":el.getText().toUpperCase().replaceAll("[^A-Z]","");
          // Only accept OCR that contains the letter expected at this position.
          if(t.indexOf(expected)>=0){letter=true;break;}
        }
    if(letter)found.add(index);
    try{cell.recycle();}catch(Exception ignored){}
    processSchemaCell(b,clean,centers,index+1,half,found,done);
  }).addOnFailureListener(e->{
    try{cell.recycle();}catch(Exception ignored){}
    processSchemaCell(b,clean,centers,index+1,half,found,done);
  });
 }
 private int lum(int color){
  return (299*Color.red(color)+587*Color.green(color)+114*Color.blue(color))/1000;
 }
 private int schemaSpacing(int[] centers){if(centers==null||centers.length<4)return 70;int sum=0,count=0;for(int i=1;i<centers.length/2;i++){int d=Math.abs(centers[i*2]-centers[(i-1)*2]);if(d>5&&d<500){sum+=d;count++;}}return count>0?sum/count:70;}
 private void startSchemaCalibration(int length){stopCompilation();if(length<1||length>20){updateOverlayText("⚠️ Numero lettere non valido");return;}if(!Settings.canDrawOverlays(this)){updateOverlayText("⚠️ Attiva prima la sovrapposizione");return;}removeCalibrationOverlay();final int sw=getResources().getDisplayMetrics().widthPixels,sh=getResources().getDisplayMetrics().heightPixels;final int[] centers=new int[length*2];final int[] idx=new int[]{0};FrameLayout f=new FrameLayout(this);f.setBackgroundColor(Color.TRANSPARENT);f.setClickable(true);TextView p=new TextView(this);p.setTextColor(Color.WHITE);p.setTextSize(18);p.setGravity(Gravity.CENTER);p.setBackgroundColor(Color.parseColor("#DD000000"));p.setText("🔧 CALIBRAZIONE SCHEMA\n\nTocca il centro della posizione 1\n1/"+length+"\n\nEvita di toccare la tastiera.");FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);pp.topMargin=55;f.addView(p,pp);f.setOnTouchListener((v,e)->{if(e==null||e.getAction()!=android.view.MotionEvent.ACTION_UP)return true;if(e.getY()<220)return true;int i=idx[0];if(i>=length)return true;centers[i*2]=Math.round(e.getX());centers[i*2+1]=Math.round(e.getY());idx[0]++;if(idx[0]<length){p.setText("🔧 CALIBRAZIONE SCHEMA\n\nTocca il centro della posizione "+(idx[0]+1)+"\n"+idx[0]+"/"+length+"\n\nEvita di toccare la tastiera.");}else{saveSchemaCalibration(length,centers,sw,sh);removeCalibrationOverlay();updateOverlayText("✅ SCHEMA "+length+" LETTERE CALIBRATO\n"+length+"/"+length+" posizioni salvate");}return true;});calibrationView=f;WindowManager.LayoutParams q=new WindowManager.LayoutParams(sw,sh,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);q.gravity=Gravity.TOP|Gravity.START;try{windowManager.addView(calibrationView,q);}catch(Exception e){calibrationView=null;updateOverlayText("❌ Errore calibrazione schema: "+e.getClass().getSimpleName());}}
 private void saveSchemaCalibration(int length,int[] centers,int w,int h){StringBuilder sb=new StringBuilder();for(int i=0;i<centers.length;i++){if(i>0)sb.append(',');sb.append(centers[i]);}getSharedPreferences("codybot_schema",MODE_PRIVATE).edit().putString("centers_"+length,sb.toString()).putInt("width_"+length,w).putInt("height_"+length,h).apply();}
 private int[] getSchemaCenters(int length){String raw=getSharedPreferences("codybot_schema",MODE_PRIVATE).getString("centers_"+length,null);if(raw==null)return null;String[] p=raw.split(",");if(p.length!=length*2)return null;try{int[] c=new int[p.length];for(int i=0;i<p.length;i++)c[i]=Integer.parseInt(p[i]);return c;}catch(Exception e){return null;}}
 private boolean hasSchemaCalibration(int length){return getSchemaCenters(length)!=null;}
 private void testSchemaCalibration(int length){int[] c=getSchemaCenters(length);if(c==null){updateOverlayText("⚠️ Nessuna calibrazione per "+length+" lettere");return;}compiling=true;updateOverlayText("🧪 TEST SCHEMA "+length+" LETTERE");for(int i=0;i<length;i++){final int n=i+1;final int x=c[i*2],y=c[i*2+1];Runnable r=()->{if(compiling){tap(x,y);updateOverlayText("🧪 TEST SCHEMA\nPosizione "+n+"/"+length);}};pendingCompilation.add(r);handler.postDelayed(r,i*350L);}Runnable f=()->{if(compiling){compiling=false;pendingCompilation.clear();updateOverlayText("🧪 TEST SCHEMA TERMINATO");}};pendingCompilation.add(f);handler.postDelayed(f,length*350L+500L);}
 private void exportSchemaCalibration(){StringBuilder sb=new StringBuilder("CodyBot - Calibrazione schema\n\n");boolean any=false;for(int length=1;length<=20;length++){int[] c=getSchemaCenters(length);if(c==null)continue;any=true;int w=getSharedPreferences("codybot_schema",MODE_PRIVATE).getInt("width_"+length,0),h=getSharedPreferences("codybot_schema",MODE_PRIVATE).getInt("height_"+length,0);sb.append(length).append(" lettere - Schermo ").append(w).append(" x ").append(h).append("\n");for(int i=0;i<length;i++)sb.append(i+1).append(" = ").append(c[i*2]).append(", ").append(c[i*2+1]).append("\n");sb.append("\n");}if(!any){new android.app.AlertDialog.Builder(this).setTitle("Calibrazione schema").setMessage("Nessuna calibrazione salvata.").setPositiveButton("OK",null).show();return;}final String out=sb.toString();new android.app.AlertDialog.Builder(this).setTitle("Calibrazione schema").setMessage(out).setPositiveButton("COPIA",(d,w)->{android.content.ClipboardManager cb=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cb.setPrimaryClip(android.content.ClipData.newPlainText("CodyBot schema",out));updateOverlayText("✅ CALIBRAZIONE SCHEMA COPIATA");}).setNegativeButton("CHIUDI",null).show();}

 public void handleSchemaBitmap(Bitmap bitmap){
  if(bitmap==null){schemaCapturePending=false;return;}
  if(!compiling){try{bitmap.recycle();}catch(Exception ignored){}schemaCapturePending=false;return;}
  findExistingLettersAndSchedule(bitmap,currentSchemaAnswer);
 }
 private void hideOverlay(){overlayHidden=true;stopCompilation();handler.removeCallbacks(overlayChecker);removeCalibrationOverlay();if(overlayView!=null&&windowManager!=null)try{windowManager.removeView(overlayView);}catch(Exception ignored){}overlayView=null;statusText=null;try{stopService(new Intent(this,ScreenCaptureService.class));}catch(Exception ignored){}handler.postDelayed(()->{try{disableSelf();}catch(Exception ignored){}},150);}
 private java.util.Set<Integer> alignDetectedLetters(String clean,java.util.List<Character> detected){java.util.Set<Integer> matched=new java.util.HashSet<Integer>();if(detected==null||detected.isEmpty())return matched;int n=clean.length(),m=detected.size();int[][] dp=new int[n+1][m+1];for(int i=1;i<=n;i++)for(int j=1;j<=m;j++){int best=Math.max(dp[i-1][j],dp[i][j-1]);if(clean.charAt(i-1)==detected.get(j-1))best=Math.max(best,dp[i-1][j-1]+1);dp[i][j]=best;}int i=n,j=m;while(i>0&&j>0){if(clean.charAt(i-1)==detected.get(j-1)&&dp[i][j]==dp[i-1][j-1]+1){matched.add(i-1);i--;j--;}else if(dp[i-1][j]>=dp[i][j-1])i--;else j--;}return matched;}
 private void scheduleAnswerTaps(String clean,java.util.Set<Integer> existing){if(!compiling)return;DisplayMetrics dm=getResources().getDisplayMetrics();float w=dm.widthPixels,h=dm.heightPixels;int index=0,skipped=0;for(int i=0;i<clean.length();i++){if(existing.contains(i)){skipped++;continue;}final char c=clean.charAt(i);final float[] xy=calibratedKeyCenter(c,w,h);if(xy==null){updateOverlayText("⚠️ Nessuna coordinata calibrata per "+c+"\\nEsegui CALIBRA TASTIERA prima di compilare.");compiling=false;pendingCompilation.clear();calibratedCenters=null;return;}final long d=index*240L;index++;Runnable r=()->{if(compiling&&ScreenCaptureService.isServiceRunning())tap(xy[0],xy[1]);};pendingCompilation.add(r);handler.postDelayed(r,d);}final int n=clean.length(),s=skipped;Runnable f=()->{if(!compiling)return;compiling=false;pendingCompilation.clear();calibratedCenters=null;ScreenCaptureService.resetLastClue();updateOverlayText("✅ COMPILATA: "+clean+"\\nGià presenti: "+s+"/"+n);};pendingCompilation.add(f);handler.postDelayed(f,Math.max(300,index*240L+300L));}
 private float[] calibratedKeyCenter(char c,float w,float h){String a="QWERTYUIOP",b="ASDFGHJKL",d="ZXCVBNM";int i;if(manualCalibrationCenters!=null&&manualCalibrationCenters.length==52){int p=calibrationLetters.indexOf(c);if(p>=0)return new float[]{manualCalibrationCenters[p*2],manualCalibrationCenters[p*2+1]};}return null;}
 private float[] keyCenter(char c,float w,float h){String a="QWERTYUIOP",b="ASDFGHJKL",d="ZXCVBNM";int i;if((i=a.indexOf(c))>=0)return new float[]{w*(.05f+i*.10f),h*.77f};if((i=b.indexOf(c))>=0)return new float[]{w*(.10f+i*.10f),h*.855f};if((i=d.indexOf(c))>=0)return new float[]{w*(.25f+i*.10f),h*.94f};return null;}
 private void tap(float x,float y){Path p=new Path();p.moveTo(x,y);GestureDescription.StrokeDescription s=new GestureDescription.StrokeDescription(p,0,60);dispatchGesture(new GestureDescription.Builder().addStroke(s).build(),null,null);}
 private void stopCompilation(){for(Runnable r:pendingCompilation)handler.removeCallbacks(r);pendingCompilation.clear();compiling=false;if(statusText!=null)statusText.setText("🔴 STOP");}
 private void updateOverlayText(String m){Intent i=new Intent("com.codybot.UPDATE_OVERLAY");i.setPackage(getPackageName());i.putExtra("message",m);sendBroadcast(i);}
 @Override public void onInterrupt(){}
 @Override public void onDestroy(){overlayHidden=true;stopCompilation();handler.removeCallbacks(overlayChecker);if(recognizer!=null)recognizer.close();try{unregisterReceiver(overlayReceiver);}catch(Exception ignored){}if(overlayView!=null&&windowManager!=null)try{windowManager.removeView(overlayView);}catch(Exception ignored){}overlayView=null;super.onDestroy();}
}
