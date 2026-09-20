package com.codybot.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScreenCaptureService extends Service {
    public static final String ACTION_TOGGLE = "com.codybot.ACTION_TOGGLE";
    private static final String CHANNEL_ID = "CodyBotCaptureChannel";
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService resolverExecutor = Executors.newSingleThreadExecutor();
    private boolean isCapturing = false;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(101, createNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(101, createNotification());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "CodyBot Capture", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return builder.setContentTitle("CodyBot 3.3")
                .setContentText("Servizio cattura attivo")
                .setSmallIcon(android.R.drawable.ic_menu_camera).build();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;
        int resultCode = intent.getIntExtra("resultCode", 0);
        Intent data = intent.getParcelableExtra("data");
        if (resultCode != 0 && data != null) {
            MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            if (projectionManager != null) {
                mediaProjection = projectionManager.getMediaProjection(resultCode, data);
                updateOverlayText("MediaProjection attivo");
            }
            return START_STICKY;
        }
        if (ACTION_TOGGLE.equals(intent.getAction())) toggleCapture();
        return START_STICKY;
    }

    private void toggleCapture() {
        if (isCapturing) {
            stopCapture();
            updateOverlayText("Cattura interrotta");
            return;
        }
        if (mediaProjection == null) {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            updateOverlayText("Autorizza la cattura nell'app");
            return;
        }
        startCapture();
    }

    private void startCapture() {
        if (mediaProjection == null || isCapturing) return;
        isCapturing = true;
        updateOverlayText("SCAN: cattura in corso...");
        WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        imageReader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2);
        virtualDisplay = mediaProjection.createVirtualDisplay("CodyBotCapture", metrics.widthPixels, metrics.heightPixels,
                metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.getSurface(), null, handler);

        imageReader.setOnImageAvailableListener(reader -> {
            Image image = reader.acquireLatestImage();
            if (image == null) return;
            Bitmap bitmap = null;
            try { bitmap = imageToBitmap(image); } finally { image.close(); }
            stopCapture();
            if (bitmap != null) {
                int expectedLength = detectAnswerLength(bitmap);
                Bitmap clueBitmap = cropClue(bitmap);
                if (clueBitmap != bitmap) bitmap.recycle();
                processOCR(clueBitmap, expectedLength);
            }
        }, handler);

        handler.postDelayed(() -> {
            if (isCapturing) {
                stopCapture();
                updateOverlayText("Timeout cattura: nessun frame ricevuto");
            }
        }, 5000);
    }

    private int detectAnswerLength(Bitmap source) {
        int w = source.getWidth(), h = source.getHeight();
        int bestY = -1, bestYellow = 0;
        for (int y = Math.round(h * 0.15f); y < Math.round(h * 0.65f); y += 4) {
            int yellow = 0;
            for (int x = Math.round(w * 0.02f); x < Math.round(w * 0.98f); x += 4) {
                int p = source.getPixel(x, y);
                int r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
                if (r > 190 && g > 120 && g < 220 && b < 120 && r > g + 35) yellow++;
            }
            if (yellow > bestYellow) { bestYellow = yellow; bestY = y; }
        }
        if (bestY < 0) return 0;
        int runs = 0; boolean inCell = false; int start = 0;
        int minRun = Math.max(20, w / 30);
        for (int x = 0; x < w; x += 2) {
            int p = source.getPixel(x, bestY);
            int r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
            boolean lightCell = r > 170 && g > 145 && b > 125;
            if (lightCell && !inCell) { inCell = true; start = x; }
            if (!lightCell && inCell) { if (x - start >= minRun) runs++; inCell = false; }
        }
        if (inCell && w - start >= minRun) runs++;
        return runs >= 2 && runs <= 15 ? runs : 0;
    }

    private Bitmap cropClue(Bitmap source) {
        int w = source.getWidth(), h = source.getHeight();
        int left = Math.max(0, Math.round(w * 0.05f));
        int top = Math.max(0, Math.round(h * 0.62f));
        int right = Math.min(w, Math.round(w * 0.95f));
        int bottom = Math.min(h, Math.round(h * 0.73f));
        if (right <= left || bottom <= top) return source;
        return Bitmap.createBitmap(source, left, top, right - left, bottom - top);
    }

    private Bitmap imageToBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride(), rowStride = planes[0].getRowStride();
        int width = image.getWidth(), height = image.getHeight();
        int rowPadding = rowStride - pixelStride * width;
        Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        Bitmap cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height);
        if (cropped != bitmap) bitmap.recycle();
        return cropped;
    }

    private void processOCR(Bitmap clueBitmap, int expectedLength) {
        InputImage inputImage = InputImage.fromBitmap(clueBitmap, 0);
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        recognizer.process(inputImage)
                .addOnSuccessListener(visionText -> {
                    String clue = visionText.getText().replace("\n", " ").trim();
                    if (clue.isEmpty()) { updateOverlayText("OCR: nessun indizio rilevato"); return; }
                    updateOverlayText("INDIZIO (" + (expectedLength > 0 ? expectedLength : "?") + "): " + clue);
                    resolverExecutor.execute(() -> {
                        String answer = AnswerResolver.resolve(clue, expectedLength);
                        handler.post(() -> {
                            if (answer == null || answer.trim().isEmpty()) {
                                updateOverlayText("INDIZIO: " + clue + "\nRISPOSTA: non trovata");
                            } else {
                                updateOverlayText("INDIZIO: " + clue + "\nRISPOSTA: " + answer);
                                Intent fill = new Intent("com.codybot.FILL_ANSWER");
                                fill.setPackage(getPackageName());
                                fill.putExtra("answer", answer);
                                sendBroadcast(fill);
                            }
                        });
                    });
                })
                .addOnFailureListener(e -> updateOverlayText("Errore OCR: " + e.getMessage()))
                .addOnCompleteListener(task -> { recognizer.close(); if (!clueBitmap.isRecycled()) clueBitmap.recycle(); });
    }

    private void updateOverlayText(String message) {
        Intent intent = new Intent("com.codybot.UPDATE_OVERLAY");
        intent.setPackage(getPackageName());
        intent.putExtra("message", message);
        sendBroadcast(intent);
    }

    private void stopCapture() {
        isCapturing = false;
        if (virtualDisplay != null) { virtualDisplay.release(); virtualDisplay = null; }
        if (imageReader != null) { imageReader.close(); imageReader = null; }
    }

    @Override public void onDestroy() {
        stopCapture();
        resolverExecutor.shutdownNow();
        if (mediaProjection != null) { mediaProjection.stop(); mediaProjection = null; }
        super.onDestroy();
    }
}
