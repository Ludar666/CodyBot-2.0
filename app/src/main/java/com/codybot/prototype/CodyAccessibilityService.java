package com.codybot.prototype;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.TextView;

public class CodyAccessibilityService extends AccessibilityService {
    private WindowManager windowManager;
    private View overlayView;
    private TextView statusText;
    private Button toggleButton;

    private final BroadcastReceiver overlayReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && intent.hasExtra("message")) {
                String msg = intent.getStringExtra("message");
                if (statusText != null) {
                    statusText.setText(msg);
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        registerReceiver(overlayReceiver, new IntentFilter("com.codybot.UPDATE_OVERLAY"));
        showOverlay();
    }

    private void showOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        layout.setBackgroundColor(0xAA000000);
        layout.setPadding(16, 16, 16, 16);

        statusText = new TextView(this);
        statusText.setText("CodyBot 3.1 Pronto");
        statusText.setTextColor(0xFFFFFFFF);
        statusText.setPadding(0, 0, 16, 0);

        toggleButton = new Button(this);
        toggleButton.setText("START / STOP");
        toggleButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, ScreenCaptureService.class);
            intent.setAction(ScreenCaptureService.ACTION_TOGGLE);
            startService(intent);
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

        windowManager.addView(overlayView, params);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (overlayReceiver != null) {
            unregisterReceiver(overlayReceiver);
        }
        if (overlayView != null && windowManager != null) {
            windowManager.removeView(overlayView);
        }
    }
}
