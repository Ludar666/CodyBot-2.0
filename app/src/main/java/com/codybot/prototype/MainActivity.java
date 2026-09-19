package com.codybot.prototype;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int REQ_CAPTURE = 1001;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(32,32,32,32);
        TextView title = new TextView(this); title.setText("CodyBot 3.0\n\n1) Attiva Accessibilità per CodyBot.\n2) Concedi cattura schermo.\n3) Apri CodyCross e usa il pannello SCAN."); title.setTextSize(18); l.addView(title);
        Button a = new Button(this); a.setText("ATTIVA ACCESSIBILITÀ"); a.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))); l.addView(a);
        Button c = new Button(this); c.setText("AUTORIZZA CATTURA SCHERMO"); c.setOnClickListener(v -> requestCapture()); l.addView(c);
        setContentView(l);
    }
    private void requestCapture() {
        MediaProjectionManager m = (MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(m.createScreenCaptureIntent(), REQ_CAPTURE);
    }
    @Override protected void onActivityResult(int r,int result,Intent data){ super.onActivityResult(r,result,data); if(r==REQ_CAPTURE && data!=null){
        Intent i=new Intent(this,ScreenCaptureService.class); i.putExtra("resultCode",result); i.putExtra("data",data); startService(i);
    }}
}
