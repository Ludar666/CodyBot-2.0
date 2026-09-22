package com.codybot.prototype;

import com.codybot.prototype.AnswerResolver;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.IBinder;
import android.util.Log;

public class ScreenCaptureService extends Service {
    private static final String TAG = "CodyBot";
    private boolean isProcessing = false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Bitmap cropClue(Bitmap source) {
        int w = source.getWidth(), h = source.getHeight();
        int left = Math.max(0, Math.round(w * 0.04f));
        int top = Math.max(0, Math.round(h * 0.625f));
        int right = Math.min(w, Math.round(w * 0.96f));
        int bottom = Math.min(h, Math.round(h * 0.665f));
        if (right <= left || bottom <= top) return source;
        return Bitmap.createBitmap(source, left, top, right - left, bottom - top);
    }

    public void processCapturedText(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) return;

        // Pulizia numeri e icone dei potenziamenti (es. "999+ 209 953 ")
        String cleanClue = rawText.replaceAll("(?i)^[0-9+ \\s]+", "").trim();
        
        if (cleanClue.isEmpty()) return;

        Log.d(TAG, "Indizio pulito: " + cleanClue);

        // Risoluzione risposta con AnswerResolver
        String answer = AnswerResolver.resolve(this, cleanClue, -1);
        Log.d(TAG, "Risposta trovata: " + answer);
    }
}
