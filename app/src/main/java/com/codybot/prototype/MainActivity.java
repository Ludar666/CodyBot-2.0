package com.codybot.prototype;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);

        TextView title = new TextView(this);
        title.setText("CodyBot 3.1");
        title.setTextSize(26);
        layout.addView(title);

        status = new TextView(this);
        status.setTextSize(16);
        status.setPadding(0, 30, 0, 30);
        layout.addView(status);

        Button overlayButton = new Button(this);
        overlayButton.setText("1. ATTIVA SOVRAPPOSIZIONE");
        overlayButton.setOnClickListener(v -> openOverlaySettings());
        layout.addView(overlayButton);

        Button accessibilityButton = new Button(this);
        accessibilityButton.setText("2. ATTIVA ACCESSIBILITÀ");
        accessibilityButton.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });
        layout.addView(accessibilityButton);

        Button testButton = new Button(this);
        testButton.setText("3. MOSTRA BANNER");
        testButton.setOnClickListener(v -> refreshStatus());
        layout.addView(testButton);

        Button startButton = new Button(this);
        startButton.setText("START / STOP CODYBOT");
        startButton.setOnClickListener(v -> {
            Intent serviceIntent = new Intent(MainActivity.this, ScreenCaptureService.class);
            serviceIntent.setAction(ScreenCaptureService.ACTION_TOGGLE);
            startService(serviceIntent);
        });
        layout.addView(startButton);

        setContentView(layout);
        refreshStatus();
    }

    private void openOverlaySettings() {
        try {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private boolean isAccessibilityEnabled() {
        String enabled = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        String expected = new ComponentName(this,
                CodyAccessibilityService.class).flattenToString();
        return enabled.contains(expected);
    }

    private void refreshStatus() {
        boolean overlay = Settings.canDrawOverlays(this);
        boolean accessibility = isAccessibilityEnabled();
        status.setText("Sovrapposizione: " + (overlay ? "ATTIVA ✓" : "NON ATTIVA ✗")
                + "\nAccessibilità: " + (accessibility ? "ATTIVA ✓" : "NON ATTIVA ✗")
                + "\n\nPer vedere CodyBot sopra CodyCross devono essere attive ENTRAMBE.");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (status != null) refreshStatus();
    }
}
