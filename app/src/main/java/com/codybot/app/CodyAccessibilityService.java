package com.codybot.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
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
import java.text.Normalizer;
import java.util.Locale;

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
            if ("com.codybot.UPDATE_OVERLAY".equals(intent.getAction()) && intent.hasExtra("message") && statusText != null) {
                statusText.setText(intent.getStringExtra("message"));
            } else if ("com.codybot.AUTO_TYPE".equals(intent.getAction()) && intent.hasExtra("answer")) {
                String answer = intent.getStringExtra("answer");
                if (answer != null && !answer.isEmpty()) autoTypeAnswer(answer);
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction("com.codybot.UPDATE_OVERLAY");
            filter.addAction("com.codybot.AUTO_TYPE");
            registerReceiver(overlayReceiver, filter);
        } catch (Exception e) { e.printStackTrace(); }
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
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = 100;
        try { windowManager.addView(overlayView, params); } catch (Exception e) { e.printStackTrace(); }
    }

    private void autoTypeAnswer(String answer) {
        String clean = normalizeForKeyboard(answer);
        if (clean.isEmpty()) return;
        handler.post(() -> typeCharacter(clean, 0));
    }

    private void typeCharacter(String answer, int index) {
        if (index >= answer.length()) {
            if (statusText != null) statusText.setText("Completato: " + answer);
            return;
        }
        char c = answer.charAt(index);
        if (!tapKeyboardKey(c)) {
            if (statusText != null) statusText.setText("Tasto non trovato: " + c);
            return;
        }
        handler.postDelayed(() -> typeCharacter(answer, index + 1), 150);
    }

    private String normalizeForKeyboard(String answer) {
        String s = Normalizer.normalize(answer.toUpperCase(Locale.ITALIAN), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return s.replaceAll("[^A-Z]", "");
    }

    private boolean tapKeyboardKey(char key) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false;
        String row1 = "QWERTYUIOP";
        String row2 = "ASDFGHJKL";
        String row3 = "ZXCVBNM";
        int row;
        int index;
        if ((index = row1.indexOf(key)) >= 0) row = 0;
        else if ((index = row2.indexOf(key)) >= 0) row = 1;
        else if ((index = row3.indexOf(key)) >= 0) row = 2;
        else return false;

        DisplayMetrics dm = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(dm);
        float w = dm.widthPixels;
        float h = dm.heightPixels;

        float x;
        int count;
        float offset;
        float span;
        if (row == 0) { count = 10; offset = 0f; span = 1f; }
        else if (row == 1) { count = 9; offset = 0.05f; span = 0.9f; }
        else { count = 7; offset = 0.1f; span = 0.8f; }
        x = (offset + (index + 0.5f) * (span / count)) * w;
        float y = (row == 0 ? 0.823f : row == 1 ? 0.886f : 0.949f) * h;

        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, 30);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
        return dispatchGesture(gesture, null, null);
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
