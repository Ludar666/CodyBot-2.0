package com.codybot.prototype;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQUEST_CODE_OVERLAY = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        checkOverlayPermission();

        Button btn = new Button(this);
        btn.setText("Avvia CodyBot 3.0");
        btn.setOnClickListener(v -> {
            if (checkOverlayPermission()) {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(intent);
                Toast.makeText(this, "Attiva il servizio CodyBot in Accessibilità", Toast.LENGTH_LONG).show();
            }
        });

        setContentView(btn);
    }

    private boolean checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_CODE_OVERLAY);
                Toast.makeText(this, "Concedi il permesso di visualizzazione sopra altre app", Toast.LENGTH_LONG).show();
                return false;
            }
        }
        return true;
    }
}
