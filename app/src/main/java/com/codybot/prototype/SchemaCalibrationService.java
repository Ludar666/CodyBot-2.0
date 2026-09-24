package com.codybot.prototype;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Schema calibration: each row is recorded while it is actually visible. */
public class SchemaCalibrationService extends Service {
    public static final String ACTION_START = "com.codybot.SCHEMA_CALIBRATION_START";
    public static final String ACTION_STOP = "com.codybot.SCHEMA_CALIBRATION_STOP";
    public static final String EXTRA_LENGTH = "length";
    private static final float ADVANCE_X = 1016f;
    private static final float ADVANCE_Y = 1559f;

    private WindowManager wm;
    private View overlay;
    private View continueOverlay;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Integer,List<int[]>> rows = new LinkedHashMap<>();
    private List<int[]> currentRow = new ArrayList<>();
    private int length;
    private int activeRow = -1;
    private boolean acquiring;
    private boolean paused;

    @Override public void onCreate(){super.onCreate();wm=(WindowManager)getSystemService(WINDOW_SERVICE);}
    @Override public int onStartCommand(Intent intent,int flags,int startId){if(intent==null)return START_NOT_STICKY;if(ACTION_STOP.equals(intent.getAction())){stopCalibration();stopSelf();return START_NOT_STICKY;}if(ACTION_START.equals(intent.getAction())){length=intent.getIntExtra(EXTRA_LENGTH,0);if(length>=3&&length<=20)startCalibration();}return START_NOT_STICKY;}
    private void startCalibration(){rows.clear();currentRow.clear();activeRow=-1;acquiring=false;paused=false;showInstructions();}
    private TextView label(String text,float size){TextView t=new TextView(this);t.setText(text);t.setTextColor(Color.WHITE);t.setTextSize(size);t.setPadding(16,10,16,10);return t;}
    private void showInstructions(){removeAllOverlays();LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(24,20,24,20);box.setBackgroundColor(Color.parseColor("#EE111111"));TextView title=label("📐 CALIBRAZIONE SCHEMA",20);title.setGravity(Gravity.CENTER);box.addView(title);TextView instructions=label("1. Apri CodyCross e apri la domanda da calibrare.\n\n2. Porta lo schema all'inizio.\n\n3. Premi AVVIA ACQUISIZIONE.\n\n4. Prima di ogni riga seleziona il numero della riga che stai per acquisire.\n\n5. Tocca le caselle da sinistra verso destra. La posizione Y viene salvata esattamente come appare in quel momento.\n\n6. Dopo l'ultima casella CodyBot proverà automaticamente a premere il pulsante per passare alla riga successiva.\n\n⚠️ Se il passaggio automatico non funziona, usa SOSPENDI e SELEZIONA RIGA.\n\n⚠️ Tocca esclusivamente le caselle dello schema, non la tastiera.",16);box.addView(instructions);LinearLayout buttons=new LinearLayout(this);buttons.setGravity(Gravity.CENTER);Button cancel=new Button(this);cancel.setText("ANNULLA");cancel.setOnClickListener(v->{stopCalibration();stopSelf();});Button start=new Button(this);start.setText("AVVIA ACQUISIZIONE");start.setOnClickListener(v->{removeAllOverlays();handler.postDelayed(this::showRowSelector,300);});buttons.addView(cancel);buttons.addView(start);box.addView(buttons);addOverlay(box,(int)(getResources().getDisplayMetrics().widthPixels*.92f),-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL,110,false);}
    private void showRowSelector(){removeAllOverlays();LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(20,14,20,14);box.setBackgroundColor(Color.parseColor("#EE111111"));TextView title=label("📐 SELEZIONA LA RIGA DA ACQUISIRE",18);title.setGravity(Gravity.CENTER);box.addView(title);TextView info=label("La riga scelta verrà associata alla posizione Y attuale.\nSe vuoi riprendere una riga già acquisita, selezionala di nuovo: la nuova acquisizione la sostituirà.",14);info.setGravity(Gravity.CENTER);box.addView(info);LinearLayout grid=new LinearLayout(this);grid.setOrientation(LinearLayout.VERTICAL);for(int base=1;base<=30;base+=5){LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER);for(int n=base;n<base+5&&n<=30;n++){final int row=n;Button b=new Button(this);b.setText(rows.containsKey(n)?"✓ "+n:String.valueOf(n));b.setOnClickListener(v->{activeRow=row;currentRow=new ArrayList<>();removeAllOverlays();handler.postDelayed(this::showAcquisitionOverlay,180);});line.addView(b,new LinearLayout.LayoutParams(0,-2,1));}grid.addView(line);}box.addView(grid);LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER);Button pause=new Button(this);pause.setText("SOSPENDI");pause.setOnClickListener(v->{paused=true;showContinueOverlay();});Button finish=new Button(this);finish.setText("FINE SCHEMA");finish.setOnClickListener(v->finishSchema());actions.addView(pause);actions.addView(finish);box.addView(actions);addOverlay(box,(int)(getResources().getDisplayMetrics().widthPixels*.94f),-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL,90,false);}
    private void showAcquisitionOverlay(){removeAllOverlays();acquiring=true;paused=false;DisplayMetrics dm=getResources().getDisplayMetrics();final int w=dm.widthPixels, h=dm.heightPixels;FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.TRANSPARENT);root.setClickable(true);LinearLayout bar=new LinearLayout(this);bar.setOrientation(LinearLayout.VERTICAL);bar.setBackgroundColor(Color.parseColor("#DD000000"));TextView progress=label(progressText(),17);progress.setGravity(Gravity.CENTER);bar.addView(progress);LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER);Button pause=new Button(this);pause.setText("SOSPENDI ACQUISIZIONE");pause.setOnClickListener(v->{acquiring=false;paused=true;removeAllOverlays();showContinueOverlay();});Button done=new Button(this);done.setText("FINE RIGA");done.setOnClickListener(v->finishRow());actions.addView(pause);actions.addView(done);bar.addView(actions);root.addView(bar,new FrameLayout.LayoutParams(w,-2,Gravity.TOP));root.setOnTouchListener((v,e)->{if(!acquiring||paused||e==null||e.getAction()!=MotionEvent.ACTION_UP)return true;if(e.getY()<220)return true;if(currentRow.size()>=length)return true;currentRow.add(new int[]{Math.round(e.getX()),Math.round(e.getY())});progress.setText(progressText());if(currentRow.size()==length){handler.postDelayed(this::finishRow,120);}return true;});overlay=root;WindowManager.LayoutParams lp=new WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);lp.gravity=Gravity.TOP|Gravity.START;try{wm.addView(root,lp);}catch(Exception e){overlay=null;}}
    private String progressText(){int cell=Math.min(currentRow.size()+1,length);return "📐 RIGA "+activeRow+" — CASELLA "+cell+"/"+length+"\nTocca il centro delle caselle da sinistra verso destra";}
    private void finishRow(){if(currentRow.size()!=length)return;rows.put(activeRow,new ArrayList<>(currentRow));currentRow.clear();acquiring=false;paused=false;removeAllOverlays();updateStatus("✅ RIGA "+activeRow+" ACQUISITA\nPosizione Y salvata. Passaggio automatico alla riga successiva...");final int nextRow=activeRow+1;handler.postDelayed(()->{Intent i=new Intent("com.codybot.ADVANCE_SCHEMA_ROW");i.setPackage(getPackageName());sendBroadcast(i);if(nextRow<=30){activeRow=nextRow;updateStatus("📐 Acquisisci RIGA "+nextRow);handler.postDelayed(this::showAcquisitionOverlay,450);}else{showRowSelector();}},300);}
    private void showContinueOverlay(){removeAllOverlays();LinearLayout box=new LinearLayout(this);box.setGravity(Gravity.CENTER);box.setPadding(12,8,12,8);box.setBackgroundColor(Color.parseColor("#EE111111"));Button cont=new Button(this);cont.setText("CONTINUA ACQUISIZIONE");cont.setOnClickListener(v->{removeAllOverlays();handler.postDelayed(this::showRowSelector,180);});Button finish=new Button(this);finish.setText("FINE SCHEMA");finish.setOnClickListener(v->finishSchema());box.addView(cont);box.addView(finish);continueOverlay=box;WindowManager.LayoutParams lp=new WindowManager.LayoutParams(-2,-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);lp.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;lp.y=60;try{wm.addView(continueOverlay,lp);}catch(Exception e){continueOverlay=null;}}
    private void finishSchema(){if(currentRow.size()>0){updateStatus("⚠️ Completa o sospendi la riga "+activeRow+" prima di terminare.");return;}if(rows.isEmpty()){updateStatus("⚠️ Nessuna riga acquisita.");return;}saveRows();removeAllOverlays();updateStatus("✅ CALIBRAZIONE SCHEMA COMPLETATA\n"+length+" lettere per parola\n"+rows.size()+" righe acquisite");handler.postDelayed(this::stopSelf,800);}
    private void saveRows(){StringBuilder out=new StringBuilder();out.append("CODYBOT_SCHEMA_V3|").append(length).append('\n');for(Map.Entry<Integer,List<int[]>> e:rows.entrySet()){out.append("ROW|").append(e.getKey()).append('|');List<int[]> row=e.getValue();for(int i=0;i<row.size();i++){if(i>0)out.append(';');out.append(row.get(i)[0]).append(',').append(row.get(i)[1]);}out.append('\n');}getSharedPreferences("codybot_schema_v3",MODE_PRIVATE).edit().putString("schema_"+length,out.toString()).apply();}
    private void updateStatus(String text){Intent i=new Intent("com.codybot.UPDATE_OVERLAY");i.setPackage(getPackageName());i.putExtra("message",text);sendBroadcast(i);}
    private void addOverlay(View v,int width,int height,int gravity,int y,boolean touchable){overlay=v;WindowManager.LayoutParams lp=new WindowManager.LayoutParams(width,height,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,touchable?WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN:WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);lp.gravity=gravity;lp.y=y;try{wm.addView(v,lp);}catch(Exception e){overlay=null;}}
    private void removeMainOverlay(){if(overlay!=null){try{wm.removeView(overlay);}catch(Exception ignored){}overlay=null;}}
    private void removeContinueOverlay(){if(continueOverlay!=null){try{wm.removeView(continueOverlay);}catch(Exception ignored){}continueOverlay=null;}
    private void removeAllOverlays(){removeMainOverlay();removeContinueOverlay();}
    private void stopCalibration(){acquiring=false;paused=false;removeAllOverlays();}
    @Override public void onDestroy(){stopCalibration();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}