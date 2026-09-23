package com.codybot.prototype;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

public class ScreenCaptureService extends Service {
    private static final String TAG = "CodyBot";
    public static final String ACTION_TOGGLE = "com.codybot.prototype.ACTION_TOGGLE";
    
    private static boolean isRunning = false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_TOGGLE.equals(intent.getAction())) {
            toggleService();
        }
        return START_STICKY;
    }

    public static boolean isServiceRunning() {
        return isRunning;
    }

    private void toggleService() {
        isRunning = !isRunning;
        String statusMessage = isRunning ? "CodyBot avviato" : "CodyBot fermato";
        Toast.makeText(this, statusMessage, Toast.LENGTH_SHORT).show();
        Log.d(TAG, statusMessage);
    }

    private Bitmap cropClue(Bitmap source) {
        int w = source.getWidth(), h = source.getHeight();
        int left = Math.max(0, Math.round(w * 0.05f));
        int top = Math.max(0, Math.round(h * 0.625f));
        int right = Math.min(w, Math.round(w * 0.95f));
        int bottom = Math.min(h, Math.round(h * 0.665f));
        if (right <= left || bottom <= top) return source;
        return Bitmap.createBitmap(source, left, top, right - left, bottom - top);
    }

    public void processCapturedText(String rawText) {
        if (!isRunning || rawText == null || rawText.trim().isEmpty()) return;

        String cleanClue = rawText.replaceAll("(?i)^[0-9+ \\s]+", "").trim();
        if (cleanClue.isEmpty()) return;

        Log.d(TAG, "Indizio pulito: " + cleanClue);
        String answer = AnswerResolver.resolve(this, cleanClue, -1);
        Log.d(TAG, "Risposta trovata: " + answer);
    }
}
