package com.codybot.prototype;

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

public class ScreenCaptureService extends Service {
    public static final String ACTION_CAPTURE_ONCE = "com.codybot.ACTION_CAPTURE_ONCE";
    public static final String ACTION_TOGGLE = "com.codybot.ACTION_TOGGLE";

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isCapturing = false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_TOGGLE.equals(intent.getAction())) {
            toggleCapture();
            return START_NOT_STICKY;
        }

        int resultCode = intent != null ? intent.getIntExtra("resultCode", 0) : 0;
        Intent data = intent != null ? intent.getParcelableExtra("data") : null;

        if (resultCode != 0 && data != null) {
            MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            mediaProjection = projectionManager.getMediaProjection(resultCode, data);
            startCapture();
        } else if (intent != null && ACTION_CAPTURE_ONCE.equals(intent.getAction())) {
            if (mediaProjection != null) {
                startCapture();
            } else {
                updateOverlayText("Errore: MediaProjection non attivo");
            }
        }
        return START_NOT_STICKY;
    }

    private void toggleCapture() {
        if (isCapturing) {
            stopCapture();
            updateOverlayText("Cattura interrotta");
        } else {
            if (mediaProjection != null) {
                startCapture();
            } else {
                updateOverlayText("Errore: Riavviare l'app");
            }
        }
    }

    private void startCapture() {
        isCapturing = true;
        updateOverlayText("SCAN: cattura in corso...");

        WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);

        imageReader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2);
        virtualDisplay = mediaProjection.createVirtualDisplay("CodyBotCapture",
                metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, handler);

        handler.postDelayed(() -> {
            if (isCapturing && virtualDisplay != null) {
                stopCapture();
                updateOverlayText("Errore: Timeout cattura schermo");
            }
        }, 5000);

        imageReader.setOnImageAvailableListener(reader -> {
            Image image = reader.acquireLatestImage();
            if (image != null) {
                Bitmap bitmap = imageToBitmap(image);
                image.close();
                stopCapture();
                if (bitmap != null) {
                    processOCR(bitmap);
                } else {
                    updateOverlayText("Errore: Frame non valido");
                }
            }
        }, handler);
    }

    private Bitmap imageToBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int width = image.getWidth();
        int height = image.getHeight();
        int rowPadding = rowStride - pixelStride * width;

        Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        return Bitmap.createBitmap(bitmap, 0, 0, width, height);
    }

    private void processOCR(Bitmap bitmap) {
        InputImage inputImage = InputImage.fromBitmap(bitmap, 0);
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        recognizer.process(inputImage)
                .addOnSuccessListener(visionText -> {
                    String text = visionText.getText();
                    if (text.isEmpty()) {
                        updateOverlayText("Nessun testo rilevato");
                    } else {
                        updateOverlayText("OCR: " + text.replace("\n", " "));
                    }
                })
                .addOnFailureListener(e -> updateOverlayText("Errore OCR: " + e.getMessage()));
    }

    private void updateOverlayText(String message) {
        Intent intent = new Intent("com.codybot.UPDATE_OVERLAY");
        intent.putExtra("message", message);
        sendBroadcast(intent);
    }

    private void stopCapture() {
        isCapturing = false;
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
    }
}
