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

public class SchemaCalibrationService extends Service {
    public static final String ACTION_START="com.codybot.SCHEMA_CALIBRATION_START";
    public static final String ACTION_STOP="com.codybot.SCHEMA_CALIBRATION_STOP";
    public static final String EXTRA_LENGTH="length";
    private static final float DEFAULT_ADVANCE_X=1016f, DEFAULT_ADVANCE_Y=1559f;
    private WindowManager wm; private View overlay,continueOverlay; private final Handler handler=new Handler(Looper.getMainLooper());
    private final Map<Integer,List<int[]>> rows=new LinkedHashMap<>(); private List<int[]> currentRow=new ArrayList<>(); private int length,activeRow=-1; private boolean acquiring,paused;
    @Override public void onCreate(){super.onCreate();wm=(WindowManager)getSystemService(WINDOW_SERVICE);}
    @Override public int onStartCommand(Intent intent,int flags,int startId){if(intent==null)return START_NOT_STICKY;if(ACTION_STOP.equals(intent.getAction())){stopCalibration();stopSelf();return START_NOT_STICKY;}if(ACTION_START.equals(intent.getAction())){length=intent.getIntExtra(EXTRA_LENGTH,0);if(length>=3&&length<=20)startCalibration();}return START_NOT_STICKY;}
    private void startCalibration(){rows.clear();currentRow.clear();activeRow=-1;acquiring=false;paused=false;showInstructions();}
    private TextView label(String s,float z){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(z);t.setPadding(16,10,16,10);return t;}
    private void showInstructions(){removeAllOverlays();LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.setPadding(24,20,24,20);b.setBackgroundColor(Color.parseColor("#EE111111"));TextView t=label("📐 CALIBRAZIONE SCHEMA",20);t.setGravity(Gravity.CENTER);b.addView(t);b.addView(label("1. Apri CodyCross e porta lo schema all'inizio.\n\n2. Premi AVVIA ACQUISIZIONE.\n\n3. Seleziona la riga da acquisire e tocca le caselle da sinistra a destra.\n\n4. Quando raggiungi il numero di lettere scelto, la riga viene salvata automaticamente.\n\n5. CodyBot premerà automaticamente il pulsante di avanzamento alla coordinata calibrata (1016, 1559) e preparerà la riga successiva.\n\n⚠️ Se qualcosa non va, usa SOSPENDI e seleziona manualmente la riga da riprendere.",16));LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER);Button c=new Button(this);c.setText("ANNULLA");c.setOnClickListener(v->{stopCalibration();stopSelf();});Button s=new Button(this);s.setText("AVVIA ACQUISIZIONE");s.setOnClickListener(v->{removeAllOverlays();handler.postDelayed(this::showRowSelector,250);});r.addView(c);r.addView(s);b.addView(r);addOverlay(b,(int)(getResources().getDisplayMetrics().widthPixels*.92f),-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL,110,false);}
    private void showRowSelector(){removeAllOverlays();LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.setPadding(20,14,20,14);b.setBackgroundColor(Color.parseColor("#EE111111"));TextView t=label("📐 SELEZIONA LA RIGA DA ACQUISIRE",18);t.setGravity(Gravity.CENTER);b.addView(t);b.addView(label("Puoi riprendere una riga già acquisita: la nuova acquisizione la sostituirà.",14));LinearLayout grid=new LinearLayout(this);grid.setOrientation(LinearLayout.VERTICAL);for(int base=1;base<=30;base+=5){LinearLayout line=new LinearLayout(this);for(int n=base;n<base+5&&n<=30;n++){final int row=n;Button x=new Button(this);x.setText(rows.containsKey(n)?"✓ "+n:String.valueOf(n));x.setOnClickListener(v->{activeRow=row;currentRow=new ArrayList<>();removeAllOverlays();handler.postDelayed(this::showAcquisitionOverlay,150);});line.addView(x,new LinearLayout.LayoutParams(0,-2,1));}grid.addView(line);}b.addView(grid);LinearLayout a=new LinearLayout(this);Button p=new Button(this);p.setText("SOSPENDI");p.setOnClickListener(v->{paused=true;showContinueOverlay();});Button f=new Button(this);f.setText("FINE SCHEMA");f.setOnClickListener(v->finishSchema());a.addView(p);a.addView(f);b.addView(a);addOverlay(b,(int)(getResources().getDisplayMetrics().widthPixels*.94f),-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL,90,false);}
    private void showAcquisitionOverlay(){removeAllOverlays();acquiring=true;paused=false;DisplayMetrics dm=getResources().getDisplayMetrics();int w=dm.widthPixels,h=dm.heightPixels;FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.TRANSPARENT);root.setClickable(true);LinearLayout bar=new LinearLayout(this);bar.setOrientation(LinearLayout.VERTICAL);bar.setBackgroundColor(Color.parseColor("#DD000000"));TextView progress=label(progressText(),17);progress.setGravity(Gravity.CENTER);bar.addView(progress);Button pause=new Button(this);pause.setText("SOSPENDI ACQUISIZIONE");pause.setOnClickListener(v->{acquiring=false;paused=true;removeAllOverlays();showContinueOverlay();});bar.addView(pause);root.addView(bar,new FrameLayout.LayoutParams(w,-2,Gravity.TOP));root.setOnTouchListener((v,e)->{if(!acquiring||paused||e==null||e.getAction()!=MotionEvent.ACTION_UP||e.getY()<220)return true;if(currentRow.size()>=length)return true;currentRow.add(new int[]{Math.round(e.getX()),Math.round(e.getY())});progress.setText(progressText());if(currentRow.size()==length)handler.postDelayed(this::finishRow,120);return true;});overlay=root;WindowManager.LayoutParams lp=new WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);lp.gravity=Gravity.TOP|Gravity.START;try{wm.addView(root,lp);}catch(Exception e){overlay=null;}}
    private String progressText(){return "📐 RIGA "+activeRow+" — CASELLA "+Math.min(currentRow.size()+1,length)+"/"+length+"\nTocca il centro delle caselle";}
    private void finishRow(){if(currentRow.size()!=length)return;int completed=activeRow;rows.put(completed,new ArrayList<>(currentRow));currentRow.clear();acquiring=false;paused=false;removeAllOverlays();updateStatus("✅ RIGA "+completed+" ACQUISITA\nPassaggio automatico alla riga "+(completed+1)+"...");final int next=completed+1;handler.postDelayed(()->{sendAdvanceTap();if(next<=30){activeRow=next;updateStatus("📐 ACQUISISCI RIGA "+next);handler.postDelayed(this::showAcquisitionOverlay,500);}else showRowSelector();},250);}
    private void sendAdvanceTap(){android.content.SharedPreferences p=getSharedPreferences("codybot_advance",MODE_PRIVATE);float x=p.getFloat("x",DEFAULT_ADVANCE_X),y=p.getFloat("y",DEFAULT_ADVANCE_Y);Intent i=new Intent("com.codybot.ADVANCE_SCHEMA_ROW");i.setPackage(getPackageName());i.putExtra("x",x);i.putExtra("y",y);sendBroadcast(i);}
    private void showContinueOverlay(){removeAllOverlays();LinearLayout b=new LinearLayout(this);b.setGravity(Gravity.CENTER);b.setPadding(12,8,12,8);b.setBackgroundColor(Color.parseColor("#EE111111"));Button c=new Button(this);c.setText("CONTINUA ACQUISIZIONE");c.setOnClickListener(v->{removeAllOverlays();handler.postDelayed(this::showRowSelector,150);});Button f=new Button(this);f.setText("FINE SCHEMA");f.setOnClickListener(v->finishSchema());b.addView(c);b.addView(f);continueOverlay=b;WindowManager.LayoutParams lp=new WindowManager.LayoutParams(-2,-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);lp.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;lp.y=60;try{wm.addView(continueOverlay,lp);}catch(Exception e){continueOverlay=null;}}
    private void finishSchema(){if(currentRow.size()>0){updateStatus("⚠️ Completa o sospendi la riga "+activeRow+" prima di terminare.");return;}if(rows.isEmpty()){updateStatus("⚠️ Nessuna riga acquisita.");return;}saveRows();removeAllOverlays();updateStatus("✅ CALIBRAZIONE SCHEMA COMPLETATA\n"+length+" lettere per parola\n"+rows.size()+" righe acquisite");handler.postDelayed(this::stopSelf,800);}
    private void saveRows(){StringBuilder out=new StringBuilder("CODYBOT_SCHEMA_V3|").append(length).append('\n');for(Map.Entry<Integer,List<int[]>> e:rows.entrySet()){out.append("ROW|").append(e.getKey()).append('|');List<int[]> row=e.getValue();for(int i=0;i<row.size();i++){if(i>0)out.append(';');out.append(row.get(i)[0]).append(',').append(row.get(i)[1]);}out.append('\n');}getSharedPreferences("codybot_schema_v3",MODE_PRIVATE).edit().putString("schema_"+length,out.toString()).apply();}
    private void updateStatus(String text){Intent i=new Intent("com.codybot.UPDATE_OVERLAY");i.setPackage(getPackageName());i.putExtra("message",text);sendBroadcast(i);}
    private void addOverlay(View v,int width,int height,int gravity,int y,boolean touchable){overlay=v;WindowManager.LayoutParams lp=new WindowManager.LayoutParams(width,height,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,touchable?WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN:WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);lp.gravity=gravity;lp.y=y;try{wm.addView(v,lp);}catch(Exception e){overlay=null;}}
    private void removeMainOverlay(){if(overlay!=null){try{wm.removeView(overlay);}catch(Exception ignored){}overlay=null;}}
    private void removeContinueOverlay(){if(continueOverlay!=null){try{wm.removeView(continueOverlay);}catch(Exception ignored){}continueOverlay=null;}}
    private void removeAllOverlays(){removeMainOverlay();removeContinueOverlay();}
    private void stopCalibration(){acquiring=false;paused=false;removeAllOverlays();}
    @Override public void onDestroy(){stopCalibration();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}