package com.codybot.prototype;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.*;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.view.accessibility.AccessibilityEvent;
import java.util.*;

public class CodyAccessibilityService extends AccessibilityService {
    public static final String ACTION_SCAN = "com.codybot.prototype.SCAN";
    public static final String ACTION_CAPTURE_RESULT = "com.codybot.prototype.CAPTURE_RESULT";
    private WindowManager wm; private View overlay; private TextView status;
    private final BroadcastReceiver receiver = new BroadcastReceiver(){ @Override public void onReceive(Context c, Intent i){
        if(ACTION_CAPTURE_RESULT.equals(i.getAction())) { String clue=i.getStringExtra("clue"); String raw=i.getStringExtra("raw"); updateStatus(clue,raw); }
    }};

    @Override public void onServiceConnected(){
        super.onServiceConnected();
        wm=(WindowManager)getSystemService(WINDOW_SERVICE); showOverlay();
        IntentFilter f=new IntentFilter(ACTION_CAPTURE_RESULT);
        if(Build.VERSION.SDK_INT>=33) registerReceiver(receiver,f,Context.RECEIVER_NOT_EXPORTED); else registerReceiver(receiver,f);
    }
    private void showOverlay(){
        if(overlay!=null)return;
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(12,8,12,8); box.setBackgroundColor(0xDD18212B);
        TextView head=new TextView(this); head.setText("🤖 CodyBot 3.0"); head.setTextColor(0xFFFFFFFF); head.setTextSize(14); box.addView(head);
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        Button scan=new Button(this); scan.setText("SCAN"); scan.setOnClickListener(v->requestScan()); row.addView(scan,new LinearLayout.LayoutParams(0,55,1));
        Button auto=new Button(this); auto.setText("AUTO"); auto.setOnClickListener(v->updateStatus("AUTO: in preparazione","Prima testiamo SCAN")); row.addView(auto,new LinearLayout.LayoutParams(0,55,1));
        Button stop=new Button(this); stop.setText("STOP"); stop.setOnClickListener(v->updateStatus("STOP","")); row.addView(stop,new LinearLayout.LayoutParams(0,55,1));
        box.addView(row);
        status=new TextView(this); status.setText("Pronto. Premi SCAN."); status.setTextColor(0xFFFFFFFF); status.setTextSize(12); box.addView(status);
        overlay=box;
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL; p.y=80; wm.addView(overlay,p);
    }
    private void requestScan(){
        Intent i=new Intent(this,ScreenCaptureService.class); i.setAction(ScreenCaptureService.ACTION_CAPTURE_ONCE); startService(i);
        updateStatus("SCAN: cattura in corso…","");
    }
    private void updateStatus(String clue,String raw){ if(status==null)return; String s=clue==null?"Nessun testo riconosciuto":clue; if(raw!=null && !raw.isEmpty() && !raw.equals(clue)) s += "\nOCR: "+raw; status.setText(s); }
    public void tap(float x,float y){ Path path=new Path(); path.moveTo(x,y); GestureDescription.StrokeDescription st=new GestureDescription.StrokeDescription(path,0,60); dispatchGesture(new GestureDescription.Builder().addStroke(st).build(),null,null); }
    @Override public void onAccessibilityEvent(AccessibilityEvent e){}
    @Override public void onInterrupt(){}
    @Override public void onDestroy(){ try{unregisterReceiver(receiver);}catch(Exception ignored){} if(overlay!=null)try{wm.removeView(overlay);}catch(Exception ignored){} super.onDestroy(); }
}
