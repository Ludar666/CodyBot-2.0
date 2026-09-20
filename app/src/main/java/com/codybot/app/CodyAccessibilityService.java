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
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;

public class CodyAccessibilityService extends AccessibilityService {
    private WindowManager windowManager;
    private View overlayView;
    private TextView statusText;
    private Button toggleButton;

    private final BroadcastReceiver overlayReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            
            if ("com.codybot.UPDATE_OVERLAY".equals(intent.getAction()) && intent.hasExtra("message") && statusText != null) {
                statusText.setText(intent.getStringExtra("message"));
            } else if ("com.codybot.AUTO_TYPE".equals(intent.getAction()) && intent.hasExtra("answer")) {
                String answer = intent.getStringExtra("answer");
                if (answer != null && !answer.isEmpty()) {
                    autoTypeAnswer(answer);
                }
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

    private void autoTypeAnswer(String answer) {
        String cleanAnswer = answer.toUpperCase().replace(" ", "");
        new Thread(() -> {
            for (char letter : cleanAnswer.toCharArray()) {
                boolean clicked = clickKeyOnScreen(String.valueOf(letter));
                try {
                    Thread.sleep(300);
                } catch (InterruptedException ignored) {}
            }
        }).start();
    }

    private boolean clickKeyOnScreen(String letter) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return false;

        List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByText(letter);
        if (nodes != null && !nodes.isEmpty()) {
            for (AccessibilityNodeInfo node : nodes) {
                if (node.isClickable()) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    return true;
                } else {
                    AccessibilityNodeInfo parent = node.getParent();
                    while (parent != null) {
                        if (parent.isClickable()) {
                            parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            return true;
                        }
                        parent = parent.getParent();
                    }
                }
            }
        }
        return false;
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
