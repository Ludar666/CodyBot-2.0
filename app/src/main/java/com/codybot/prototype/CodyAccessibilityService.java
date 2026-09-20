package com.codybot.app;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class CodyAccessibilityService extends AccessibilityService {
    private WindowManager windowManager;
    private View overlayView;
    private TextView statusText;
    private Button toggleButton;

    private final BroadcastReceiver overlayReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && intent.hasExtra("message") && statusText != null) {
                statusText.setText(intent.getStringExtra("message"));
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        try {
            registerReceiver(overlayReceiver, new IntentFilter("com.codybot.UPDATE_OVERLAY"));
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
        statusText.setText("CodyBot 3.1 Pronto");
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent);
                } else {
                    startService(intent);
                }
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

        try {
            windowManager.addView(overlayView, params);
        } catch (Exception e) {
            e.printStackTrace();
        }
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
