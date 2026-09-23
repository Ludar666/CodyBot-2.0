package com.codybot.prototype;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Button btnToggle = new Button(this);
        btnToggle.setText("START / STOP");
        btnToggle.setTextSize(20);

        btnToggle.setOnClickListener(v -> {
            Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
            serviceIntent.setAction(ScreenCaptureService.ACTION_TOGGLE);
            startService(serviceIntent);
        });

        setContentView(btnToggle);
    }
}
