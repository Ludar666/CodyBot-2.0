package com.codybot.prototype;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);

        Button btnToggle = new Button(this);
        btnToggle.setText("START / STOP");
        btnToggle.setTextSize(18);

        btnToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent serviceIntent = new Intent(MainActivity.this, ScreenCaptureService.class);
                serviceIntent.setAction(ScreenCaptureService.ACTION_TOGGLE);
                startService(serviceIntent);
            }
        });

        layout.addView(btnToggle);
        setContentView(layout);
    }
}
