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
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.accessibilityservice.GestureDescription;

import java.util.ArrayList;
import java.util.List;

public class CodyAccessibilityService extends AccessibilityService {
    private WindowManager windowManager;
    private View overlayView;
    private TextView statusText;
    private Button startButton;
    private Button stopButton;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final List<Runnable> pendingCompilation = new ArrayList<>();
    private boolean compiling = false;
    private static volatile String lastTargetPackage = "";

    private final Runnable overlayChecker = new Runnable() {
        @Override public void run() {
            if (overlayView == null) {
                if (Settings.canDrawOverlays(CodyAccessibilityService.this)) showOverlay();
                else handler.postDelayed(this, 1000);
            }
        }
    };

    private final BroadcastReceiver overlayReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null) return;

            if (intent.hasExtra("message") && statusText != null) {
                statusText.setText(intent.getStringExtra("message"));
            }

            if ("com.codybot.FILL_ANSWER".equals(intent.getAction())) {
                String answer = intent.getStringExtra("answer");
                if (answer != null && !answer.trim().isEmpty()
                        && ScreenCaptureService.isServiceRunning()) {
                    fillAnswer(answer);
                }
            }

            if ("com.codybot.STOP_COMPILATION".equals(intent.getAction())) {
                stopCompilation();
            }
        }
    };

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        CharSequence pkg = event.getPackageName();
        if (pkg == null) return;
        String name = pkg.toString();
        if (!name.equals(getPackageName()) && !name.equals("android")
                && !name.equals("com.android.systemui")) {
            lastTargetPackage = name;
        }
    }

    public static String getLastTargetPackage() {
        return lastTargetPackage;
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        try {
            registerReceiver(overlayReceiver, new IntentFilter("com.codybot.UPDATE_OVERLAY"));
            registerReceiver(overlayReceiver, new IntentFilter("com.codybot.FILL_ANSWER"));
            registerReceiver(overlayReceiver, new IntentFilter("com.codybot.STOP_COMPILATION"));
        } catch (Exception e) { e.printStackTrace(); }

        if (!Settings.canDrawOverlays(this)) {
            try {
                Intent settingsIntent = new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(settingsIntent);
            } catch (Exception ignored) {}
        }
        handler.post(overlayChecker);
    }

    private void showOverlay() {
        if (overlayView != null || !Settings.canDrawOverlays(this)) return;

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#CC000000"));
        layout.setPadding(18, 8, 18, 8);

        statusText = new TextView(this);
        statusText.setText("CodyBot 3.1 PRONTO");
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(14);
        statusText.setPadding(0, 0, 12, 0);
        layout.addView(statusText,
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        startButton = new Button(this);
        startButton.setText("START");
        startButton.setTextSize(11);
        startButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, ScreenCaptureService.class);
            intent.setAction(ScreenCaptureService.ACTION_TOGGLE);
            try { startService(intent); }
            catch (Exception e) {
                if (statusText != null) statusText.setText("Errore: " + e.getClass().getSimpleName());
            }
        });

        stopButton = new Button(this);
        stopButton.setText("STOP");
        stopButton.setTextSize(11);
        stopButton.setOnClickListener(v -> {
            stopCompilation();
            Intent intent = new Intent(this, ScreenCaptureService.class);
            intent.setAction(ScreenCaptureService.ACTION_PAUSE_CAPTURE);
            try { startService(intent); }
            catch (Exception e) {
                if (statusText != null) statusText.setText("STOP: " + e.getClass().getSimpleName());
            }
        });

        layout.addView(startButton);
        layout.addView(stopButton);
        overlayView = layout;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = 70;

        try { windowManager.addView(overlayView, params); }
        catch (Exception e) {
            overlayView = null;
            e.printStackTrace();
            handler.postDelayed(overlayChecker, 1000);
        }
    }

    private void fillAnswer(String answer) {
        stopCompilation();

        final String clean = answer.toUpperCase().replaceAll("[^A-Z]", "");
        if (clean.isEmpty()) return;

        compiling = true;
        if (statusText != null) statusText.setText("🟡 COMPILAZIONE: " + clean);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        final float w = dm.widthPixels;
        final float h = dm.heightPixels;

        for (int i = 0; i < clean.length(); i++) {
            final char letter = clean.charAt(i);
            final float[] xy = keyCenter(letter, w, h);
            if (xy == null) continue;

            final long delay = i * 180L;
            final Runnable tapRunnable = () -> {
                if (!compiling || !ScreenCaptureService.isServiceRunning()) return;
                tap(xy[0], xy[1]);
            };
            pendingCompilation.add(tapRunnable);
            handler.postDelayed(tapRunnable, delay);
        }

        final Runnable finishRunnable = () -> {
            if (!compiling) return;
            compiling = false;
            pendingCompilation.clear();
            updateOverlayText("COMPILATA: " + clean);
        };
        pendingCompilation.add(finishRunnable);
        handler.postDelayed(finishRunnable, clean.length() * 180L + 150L);
    }

    private void stopCompilation() {
        for (Runnable r : pendingCompilation) handler.removeCallbacks(r);
        pendingCompilation.clear();
        compiling = false;
        if (statusText != null) statusText.setText("🔴 STOP");
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

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (overlayView == null && Settings.canDrawOverlays(this)) handler.post(overlayChecker);
    }

    @Override public void onInterrupt() {}

    @Override public void onDestroy() {
        stopCompilation();
        handler.removeCallbacks(overlayChecker);
        super.onDestroy();
        try { unregisterReceiver(overlayReceiver); } catch (Exception ignored) {}
        if (overlayView != null && windowManager != null) {
            try { windowManager.removeView(overlayView); } catch (Exception ignored) {}
        }
        overlayView = null;
    }
}
