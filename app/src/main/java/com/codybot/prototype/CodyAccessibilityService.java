package com.codybot.app;

import com.codybot.prototype.ScreenCaptureService;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Build;
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
    private volatile float[] calibratedCenters;
    private volatile boolean calibrationInProgress = false;

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
        if (event != null) {
            CharSequence pkg = event.getPackageName();
            if (pkg != null) {
                String name = pkg.toString();
                if (!name.equals(getPackageName()) && !name.equals("android")
                        && !name.equals("com.android.systemui")) {
                    lastTargetPackage = name;
                }
            }
        }
        if (overlayView == null && Settings.canDrawOverlays(this)) {
            handler.post(overlayChecker);
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

        // Prima proviamo a calibrare la tastiera dalla schermata reale.
        // Se il dispositivo non supporta takeScreenshot(), usiamo il fallback.
        if (Build.VERSION.SDK_INT >= 30 && !calibrationInProgress) {
            calibrationInProgress = true;
            takeScreenshot(android.view.Display.DEFAULT_DISPLAY,
                    getMainExecutor(),
                    new android.accessibilityservice.AccessibilityService.TakeScreenshotCallback() {
                        @Override public void onSuccess(android.accessibilityservice.AccessibilityService.ScreenshotResult result) {
                            Bitmap shot = null;
                            try {
                                HardwareBuffer buffer = result.getHardwareBuffer();
                                ColorSpace cs = result.getColorSpace();
                                if (buffer != null) {
                                    shot = Bitmap.wrapHardwareBuffer(buffer, cs);
                                }
                                calibratedCenters = detectKeyboardCenters(shot);
                            } catch (Exception ignored) {
                                calibratedCenters = null;
                            } finally {
                                if (shot != null) {
                                    try { shot.recycle(); } catch (Exception ignored) {}
                                }
                                try { result.getHardwareBuffer().close(); } catch (Exception ignored) {}
                                calibrationInProgress = false;
                                scheduleAnswerTaps(clean);
                            }
                        }
                        @Override public void onFailure(int errorCode) {
                            calibrationInProgress = false;
                            scheduleAnswerTaps(clean);
                        }
                    });
            return;
        }
        scheduleAnswerTaps(clean);
    }

    private void stopCompilation() {
        for (Runnable r : pendingCompilation) handler.removeCallbacks(r);
        pendingCompilation.clear();
        compiling = false;
        if (statusText != null) statusText.setText("🔴 STOP");
    }

    private void scheduleAnswerTaps(final String clean) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        final float w = dm.widthPixels;
        final float h = dm.heightPixels;

        for (int i = 0; i < clean.length(); i++) {
            final char letter = clean.charAt(i);
            final float[] xy = calibratedKeyCenter(letter, w, h);
            if (xy == null) continue;

            final long delay = i * 220L;
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
            calibratedCenters = null;
        };
        pendingCompilation.add(finishRunnable);
        handler.postDelayed(finishRunnable, clean.length() * 220L + 250L);
    }

    private float[] calibratedKeyCenter(char c, float w, float h) {
        final String row1 = "QWERTYUIOP";
        final String row2 = "ASDFGHJKL";
        final String row3 = "ZXCVBNM";
        int idx;
        if (calibratedCenters != null && calibratedCenters.length >= 38) {
            if ((idx = row1.indexOf(c)) >= 0) return new float[]{calibratedCenters[idx * 2], calibratedCenters[idx * 2 + 1]};
            if ((idx = row2.indexOf(c)) >= 0) { int p = 10 + idx; return new float[]{calibratedCenters[p * 2], calibratedCenters[p * 2 + 1]}; }
            if ((idx = row3.indexOf(c)) >= 0) { int p = 19 + idx; return new float[]{calibratedCenters[p * 2], calibratedCenters[p * 2 + 1]}; }
        }
        return keyCenter(c, w, h);
    }

    /**
     * Cerca i 26 centri dei tasti nella parte bassa dello screenshot.
     * Il rilevamento usa la variazione verticale di luminosità: i bordi dei
     * tasti producono picchi regolari e permettono di evitare coordinate fisse.
     */
    private float[] detectKeyboardCenters(Bitmap bmp) {
        if (bmp == null) return null;
        int w = bmp.getWidth(), h = bmp.getHeight();
        if (w < 300 || h < 500) return null;

        int yStart = (int)(h * 0.62f);
        int yEnd = (int)(h * 0.985f);
        int[] rowPeaks = new int[h];
        for (int y = yStart; y < yEnd; y++) {
            int sum = 0;
            int step = Math.max(2, w / 180);
            for (int x = step; x < w - step; x += step) {
                int a = pixelGray(bmp.getPixel(x, y));
                int b = pixelGray(bmp.getPixel(x, Math.min(h - 1, y + 2)));
                sum += Math.abs(a - b);
            }
            rowPeaks[y] = sum;
        }

        int[] rows = findThreeRows(rowPeaks, yStart, yEnd);
        if (rows == null) return null;

        float[] out = new float[52];
        for (int r = 0; r < 3; r++) {
            int cy = rows[r];
            int[] xs = findKeyCentersOnRow(bmp, cy);
            int expected = r == 0 ? 10 : (r == 1 ? 9 : 7);
            if (xs.length != expected) return null;
            for (int i = 0; i < expected; i++) {
                out[(r == 0 ? i : r == 1 ? 10 + i : 19 + i) * 2] = xs[i];
                out[(r == 0 ? i : r == 1 ? 10 + i : 19 + i) * 2 + 1] = cy;
            }
        }
        return out;
    }

    private int[] findThreeRows(int[] score, int start, int end) {
        int[] best = new int[]{-1,-1,-1};
        int minGap = Math.max(35, (end-start)/10);
        for (int a = start + 10; a < end - minGap * 2; a++) {
            for (int b = a + minGap; b < end - minGap; b++) {
                for (int d = b + minGap; d < end; d++) {
                    int v = score[a] + score[b] + score[d];
                    if (best[0] < 0 || v > score[best[0]] + score[best[1]] + score[best[2]]) best = new int[]{a,b,d};
                }
            }
        }
        return best[0] < 0 ? null : best;
    }

    private int[] findKeyCentersOnRow(Bitmap bmp, int cy) {
        int w = bmp.getWidth();
        int y1 = Math.max(0, cy - 24), y2 = Math.min(bmp.getHeight() - 1, cy + 24);
        int[] score = new int[w];
        for (int x = 1; x < w - 1; x++) {
            int s = 0;
            for (int y = y1; y <= y2; y += 4) {
                s += Math.abs(pixelGray(bmp.getPixel(x,y)) - pixelGray(bmp.getPixel(x-1,y)));
            }
            score[x] = s;
        }
        int expected = cy < bmp.getHeight()*0.82f ? 10 : (cy < bmp.getHeight()*0.91f ? 9 : 7);
        ArrayList<Integer> peaks = new ArrayList<>();
        int minDistance = Math.max(25, w / 16);
        for (int i = 1; i < w - 1; i++) {
            if (score[i] > score[i-1] && score[i] >= score[i+1]) {
                if (peaks.isEmpty() || i - peaks.get(peaks.size()-1) >= minDistance) peaks.add(i);
                else if (score[i] > score[peaks.get(peaks.size()-1)]) peaks.set(peaks.size()-1, i);
            }
        }
        // I bordi producono due picchi per tasto: trasformiamo le coppie in centri.
        ArrayList<Integer> centers = new ArrayList<>();
        for (int i=0; i+1<peaks.size(); i++) {
            int gap = peaks.get(i+1)-peaks.get(i);
            if (gap >= w/20 && gap <= w/7) {
                centers.add((peaks.get(i)+peaks.get(i+1))/2);
                i++;
            }
        }
        if (centers.size() != expected) return new int[0];
        int[] out = new int[centers.size()];
        for (int i=0;i<out.length;i++) out[i]=centers.get(i);
        return out;
    }

    private int pixelGray(int color) {
        return (Color.red(color)*299 + Color.green(color)*587 + Color.blue(color)*114) / 1000;
    }

    private float[] keyCenter(char c, float w, float h) {
        final String row1 = "QWERTYUIOP";
        final String row2 = "ASDFGHJKL";
        final String row3 = "ZXCVBNM";
        int idx;
        // Coordinate calibrate per la tastiera CodyCross: usiamo percentuali più
        // conservative e centrate per evitare di colpire il tasto adiacente.
        if ((idx = row1.indexOf(c)) >= 0) return new float[]{w * (0.050f + idx * 0.100f), h * 0.770f};
        if ((idx = row2.indexOf(c)) >= 0) return new float[]{w * (0.100f + idx * 0.100f), h * 0.855f};
        if ((idx = row3.indexOf(c)) >= 0) return new float[]{w * (0.250f + idx * 0.100f), h * 0.940f};
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
