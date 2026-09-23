package com.codybot.prototype;

import com.codybot.app.CodyAccessibilityService;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.app.AlertDialog;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
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

        Button advancedButton = new Button(this);
        advancedButton.setText("3. STRUMENTI AVANZATI");
        advancedButton.setOnClickListener(v -> showAdvancedTools());
        layout.addView(advancedButton);

        Button startButton = new Button(this);
        startButton.setText("START CODYBOT");
        startButton.setOnClickListener(v -> {
            Intent serviceIntent = new Intent(MainActivity.this, ScreenCaptureService.class);
            serviceIntent.setAction(ScreenCaptureService.ACTION_START_SCAN);
            startService(serviceIntent);
        });
        layout.addView(startButton);

        Button stopButton = new Button(this);
        stopButton.setText("STOP CODYBOT");
        stopButton.setOnClickListener(v -> {
            Intent serviceIntent = new Intent(MainActivity.this, ScreenCaptureService.class);
            serviceIntent.setAction(ScreenCaptureService.ACTION_PAUSE_CAPTURE);
            startService(serviceIntent);
        });
        layout.addView(stopButton);

        Button closeButton = new Button(this);
        closeButton.setText("CHIUDI CODYBOT");
        closeButton.setOnClickListener(v -> {
            try {
                Intent serviceIntent = new Intent(MainActivity.this, ScreenCaptureService.class);
                serviceIntent.setAction(ScreenCaptureService.ACTION_PAUSE_CAPTURE);
                startService(serviceIntent);
            } catch (Exception ignored) {}
            finishAffinity();
        });
        layout.addView(closeButton);

        setContentView(layout);
        refreshStatus();
    }

    private void showAdvancedTools() {
        final String[] options = {
                "🔧 CALIBRA TASTIERA",
                "🧪 TEST TASTIERA",
                "📤 ESPORTA CALIBRAZIONE"
        };

        new AlertDialog.Builder(this)
                .setTitle("Strumenti avanzati")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        Intent intent = new Intent("com.codybot.CALIBRATE_KEYBOARD");
                        intent.setPackage(getPackageName());
                        sendBroadcast(intent);
                        status.setText("CALIBRAZIONE AVVIATA\\n\\nTocca A-Z sulla tastiera CodyCross.");
                    } else if (which == 1) {
                        Intent testIntent = new Intent("com.codybot.TEST_KEYBOARD");
                        testIntent.setPackage(getPackageName());
                        sendBroadcast(testIntent);
                        status.setText("TEST TASTIERA AVVIATO\\n\\nCodyBot proverà a digitare A-Z.");
                    } else {
                        exportCalibration();
                    }
                })
                .show();
    }

    private void exportCalibration() {
        String raw = getSharedPreferences("codybot_keyboard", MODE_PRIVATE)
                .getString("centers", null);
        if (raw == null || raw.trim().isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Esporta calibrazione")
                    .setMessage("Nessuna calibrazione salvata. Esegui prima CALIBRA TASTIERA.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        String[] parts = raw.split(",");
        if (parts.length != 52) {
            new AlertDialog.Builder(this)
                    .setTitle("Esporta calibrazione")
                    .setMessage("La calibrazione salvata non è valida.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("CodyBot - Calibrazione tastiera\\n");
        sb.append("Schermo: ")
                .append(getSharedPreferences("codybot_keyboard", MODE_PRIVATE).getInt("width", 0))
                .append(" x ")
                .append(getSharedPreferences("codybot_keyboard", MODE_PRIVATE).getInt("height", 0))
                .append("\\n\\n");

        String letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        for (int i = 0; i < 26; i++) {
            sb.append(letters.charAt(i))
                    .append(" = ")
                    .append(Math.round(Float.parseFloat(parts[i * 2])))
                    .append(", ")
                    .append(Math.round(Float.parseFloat(parts[i * 2 + 1])))
                    .append("\\n");
        }

        final String exportText = sb.toString();

        new AlertDialog.Builder(this)
                .setTitle("Calibrazione salvata")
                .setMessage(exportText)
                .setPositiveButton("COPIA", (d, w) -> {
                    ClipboardManager clipboard =
                            (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newPlainText("CodyBot calibrazione", exportText));
                    status.setText("✅ COORDINATE COPIATE NEGLI APPUNTI");
                })
                .setNegativeButton("CHIUDI", null)
                .show();
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