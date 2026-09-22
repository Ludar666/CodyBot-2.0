package com.codybot.app;
import com.codybot.prototype.ScreenCaptureService;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.accessibilityservice.GestureDescription;

public class CodyAccessibilityService extends AccessibilityService {
    private WindowManager windowManager;
    private View overlayView;
    private TextView statusText;
    private Button toggleButton;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver overlayReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            if (intent.hasExtra("message") && statusText != null) {
                statusText.setText(intent.getStringExtra("message"));
            }
            if ("com.codybot.FILL_ANSWER".equals(intent.getAction())) {
                String answer = intent.getStringExtra("answer");
                if (answer != null && !answer.trim().isEmpty()) fillAnswer(answer);
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        try {
            registerReceiver(overlayReceiver, new IntentFilter("com.codybot.UPDATE_OVERLAY"));
            IntentFilter fill = new IntentFilter("com.codybot.FILL_ANSWER");
            registerReceiver(overlayReceiver, fill);
        } catch (Exception e) {
            e.printStackTrace();
        }
        showOverlay();
    }

    private void showOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setBackgroundColor(Color.parseColor("#CC000000"));
        layout.setPadding(24, 12, 24, 12);

        statusText = new TextView(this);
        statusText.setText("CodyBot 3.2 Pronto");
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(14);
        statusText.setPadding(0, 0, 16, 0);

        toggleButton = new Button(this);
        toggleButton.setText("START / STOP");
        toggleButton.setTextSize(12);
        toggleButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, ScreenCaptureService.class);
            intent.setAction(ScreenCaptureService.ACTION_TOGGLE);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent);
                else startService(intent);
            } catch (Exception e) {
                if (statusText != null) statusText.setText("Errore avvio cattura: " + e.getClass().getSimpleName());
            }
        });

        layout.addView(statusText);
        layout.addView(toggleButton);
        overlayView = layout;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = 100;
        try { windowManager.addView(overlayView, params); } catch (Exception e) { e.printStackTrace(); }
    }

    /**
     * CodyCross keyboard layout used on the Mi 9T. Coordinates are relative to
     * the full display so they continue to work if the resolution/density changes.
     * Row 1: QWERTYUIOP, row 2: ASDFGHJKL, row 3: ZXCVBNM.
     */
    private void fillAnswer(String answer) {
        final String clean = answer.toUpperCase().replaceAll("[^A-Z]", "");
        if (clean.isEmpty()) return;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        final float w = dm.widthPixels;
        final float h = dm.heightPixels;

        for (int i = 0; i < clean.length(); i++) {
            final char letter = clean.charAt(i);
            final float[] xy = keyCenter(letter, w, h);
            if (xy == null) continue;
            final long delay = i * 180L;
            handler.postDelayed(() -> tap(xy[0], xy[1]), delay);
        }
        handler.postDelayed(() -> updateOverlayText("COMPILATA: " + clean), clean.length() * 180L + 150L);
    }

    private float[] keyCenter(char c, float w, float h) {
        final String row1 = "QWERTYUIOP";
        final String row2 = "ASDFGHJKL";
        final String row3 = "ZXCVBNM";
        int idx;
        if ((idx = row1.indexOf(c)) >= 0) return new float[]{w * (0.048f + idx * 0.101f), h * 0.764f};
        if ((idx = row2.indexOf(c)) >= 0) return new float[]{w * (0.060f + idx * 0.101f), h * 0.850f};
        if ((idx = row3.indexOf(c)) >= 0) return new float[]{w * (0.199f + idx * 0.101f), h * 0.925f};
        return null;
    }

    private void tap(float x, float y) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 60);
        dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(), null, null);
    }

    private void updateOverlayText(String message) {
        Intent intent = new Intent("com.codybot.UPDATE_OVERLAY");
        intent.setPackage(getPackageName());
        intent.putExtra("message", message);
        sendBroadcast(intent);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(overlayReceiver); } catch (Exception ignored) {}
        if (overlayView != null && windowManager != null) {
            try { windowManager.removeView(overlayView); } catch (Exception ignored) {}
        }
    }
}
