package com.codybot.prototype;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Independent schema calibration flow. It deliberately lives outside the
 * accessibility service so the calibration UI can be changed without risking
 * the already-working keyboard calibration.
 */
public class SchemaCalibrationService extends Service {
    public static final String ACTION_START = "com.codybot.SCHEMA_CALIBRATION_START";
    public static final String ACTION_STOP = "com.codybot.SCHEMA_CALIBRATION_STOP";
    public static final String EXTRA_LENGTH = "length";

    private WindowManager wm;
    private View overlay;
    private View continueOverlay;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int length;
    private final List<List<int[]>> pages = new ArrayList<>();
    private List<int[]> currentRows = new ArrayList<>();
    private List<int[]> currentRow = new ArrayList<>();
    private boolean acquiring;

    @Override public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        if (ACTION_STOP.equals(intent.getAction())) {
            stopCalibration();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_START.equals(intent.getAction())) {
            length = intent.getIntExtra(EXTRA_LENGTH, 0);
            if (length >= 3 && length <= 20) startCalibration();
        }
        return START_NOT_STICKY;
    }

    private void startCalibration() {
        pages.clear();
        currentRows.clear();
        currentRow.clear();
        acquiring = false;
        showInstructions();
    }

    private TextView label(String text, float size) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(size);
        t.setPadding(16, 10, 16, 10);
        return t;
    }

    private void showInstructions() {
        removeAllOverlays();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(24, 20, 24, 20);
        box.setBackgroundColor(Color.parseColor("#EE111111"));

        TextView title = label("📐 CALIBRAZIONE SCHEMA", 20);
        title.setGravity(Gravity.CENTER);
        box.addView(title);

        TextView instructions = label(
                "1. Apri CodyCross e apri la domanda da calibrare.\n\n" +
                "2. Porta lo schema all'inizio, con le prime righe visibili.\n\n" +
                "3. Premi AVVIA ACQUISIZIONE.\n\n" +
                "4. Tocca le caselle della prima parola da sinistra verso destra.\n\n" +
                "5. Alla fine della riga premi FINE RIGA. Ripeti per tutte le righe visibili.\n\n" +
                "6. Quando non ci sono più righe utili sullo schermo, premi SCORRI E CONTINUA.\n\n" +
                "7. Scorri CodyCross e premi CONTINUA. Ripeti fino all'ultima parola.\n\n" +
                "💡 Non è necessario che tutte le parole siano visibili contemporaneamente.\n\n" +
                "⚠️ Tocca esclusivamente le caselle dello schema, non la tastiera.", 16);
        box.addView(instructions);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER);
        Button cancel = new Button(this);
        cancel.setText("ANNULLA");
        cancel.setOnClickListener(v -> { stopCalibration(); stopSelf(); });
        Button start = new Button(this);
        start.setText("AVVIA ACQUISIZIONE");
        start.setOnClickListener(v -> {
            removeAllOverlays();
            handler.postDelayed(this::startAcquisition, 350);
        });
        buttons.addView(cancel);
        buttons.addView(start);
        box.addView(buttons);
        addOverlay(box, (int)(getResources().getDisplayMetrics().widthPixels * .92f), -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 110, false);
    }

    private void startAcquisition() {
        currentRows = new ArrayList<>();
        currentRow = new ArrayList<>();
        acquiring = true;
        showAcquisitionOverlay();
    }

    private void showAcquisitionOverlay() {
        removeMainOverlayOnly();
        DisplayMetrics dm = getResources().getDisplayMetrics();
        final int w = dm.widthPixels;
        final int h = dm.heightPixels;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.TRANSPARENT);
        root.setClickable(true);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setBackgroundColor(Color.parseColor("#DD000000"));
        TextView progress = label(progressText(), 17);
        progress.setGravity(Gravity.CENTER);
        bar.addView(progress);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);

        Button rowDone = new Button(this);
        rowDone.setText("FINE RIGA");
        rowDone.setOnClickListener(v -> finishRow(progress));
        actions.addView(rowDone);

        Button scroll = new Button(this);
        scroll.setText("SCORRI E CONTINUA");
        scroll.setOnClickListener(v -> finishPage());
        actions.addView(scroll);

        Button finish = new Button(this);
        finish.setText("FINE SCHEMA");
        finish.setOnClickListener(v -> finishSchema());
        actions.addView(finish);
        bar.addView(actions);

        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(w, -2, Gravity.TOP);
        root.addView(bar, bp);
        root.setOnTouchListener((v, event) -> {
            if (!acquiring || event == null || event.getAction() != MotionEvent.ACTION_UP) return true;
            if (event.getY() < 210) return true;
            if (currentRow.size() >= length) return true;
            currentRow.add(new int[]{Math.round(event.getX()), Math.round(event.getY())});
            progress.setText(progressText());
            return true;
        });

        overlay = root;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                w, h,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        try { wm.addView(root, lp); } catch (Exception e) { overlay = null; }
    }

    private String progressText() {
        int row = currentRows.size() + 1;
        int cell = Math.min(currentRow.size() + 1, length);
        return "📐 RIGA " + row + " — CASELLA " + cell + "/" + length + "\n" +
                "Tocca il centro delle caselle da sinistra verso destra";
    }

    private void finishRow(TextView progress) {
        if (currentRow.size() != length) {
            progress.setText("⚠️ La riga deve avere " + length + " caselle.\nAcquisite: " + currentRow.size());
            return;
        }
        currentRows.add(new ArrayList<>(currentRow));
        currentRow.clear();
        progress.setText(progressText());
    }

    private void finishPage() {
        if (!currentRow.isEmpty()) {
            updateMessage("⚠️ Completa prima la riga corrente con FINE RIGA.");
            return;
        }
        if (currentRows.isEmpty()) {
            updateMessage("⚠️ Acquisisci almeno una riga prima di scorrere.");
            return;
        }
        pages.add(new ArrayList<>(currentRows));
        currentRows = new ArrayList<>();
        removeMainOverlayOnly();
        showContinueOverlay();
    }

    private void showContinueOverlay() {
        LinearLayout box = new LinearLayout(this);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(Color.parseColor("#EE111111"));
        Button continueButton = new Button(this);
        continueButton.setText("CONTINUA CALIBRAZIONE");
        continueButton.setOnClickListener(v -> {
            removeContinueOverlay();
            handler.postDelayed(this::startAcquisition, 250);
        });
        Button finish = new Button(this);
        finish.setText("FINE SCHEMA");
        finish.setOnClickListener(v -> finishSchema());
        box.addView(continueButton);
        box.addView(finish);
        continueOverlay = box;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                -2, -2,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = 70;
        try { wm.addView(continueOverlay, lp); } catch (Exception e) { continueOverlay = null; }
    }

    private void finishSchema() {
        if (!currentRow.isEmpty()) {
            updateMessage("⚠️ Completa la riga corrente prima di terminare.");
            return;
        }
        if (!currentRows.isEmpty()) pages.add(new ArrayList<>(currentRows));
        if (pages.isEmpty()) {
            updateMessage("⚠️ Nessuna riga acquisita.");
            return;
        }
        savePages();
        removeAllOverlays();
        updateStatus("✅ CALIBRAZIONE SCHEMA COMPLETATA\n" + length + " lettere per parola\n" + countRows() + " parole acquisite");
        handler.postDelayed(this::stopSelf, 800);
    }

    private int countRows() {
        int n = 0;
        for (List<int[]> page : pages) n += page.size();
        return n;
    }

    private void savePages() {
        StringBuilder out = new StringBuilder();
        out.append("CODYBOT_SCHEMA_V2|").append(length).append('\n');
        for (int p = 0; p < pages.size(); p++) {
            out.append("PAGE|").append(p + 1).append('\n');
            List<int[]> rows = pages.get(p);
            for (int r = 0; r < rows.size(); r++) {
                out.append("ROW|").append(r + 1).append('|');
                List<int[]> row = rows.get(r);
                for (int c = 0; c < row.size(); c++) {
                    if (c > 0) out.append(';');
                    out.append(row.get(c)[0]).append(',').append(row.get(c)[1]);
                }
                out.append('\n');
            }
        }
        getSharedPreferences("codybot_schema_v2", MODE_PRIVATE).edit()
                .putString("schema_" + length, out.toString())
                .apply();
    }

    private void updateMessage(String text) {
        if (overlay instanceof FrameLayout) {
            // Keep the current acquisition UI intact; status is broadcast below.
        }
        updateStatus(text);
    }

    private void updateStatus(String text) {
        Intent i = new Intent("com.codybot.UPDATE_OVERLAY");
        i.setPackage(getPackageName());
        i.putExtra("message", text);
        sendBroadcast(i);
    }

    private void addOverlay(View v, int width, int height, int gravity, int y, boolean touchable) {
        overlay = v;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                touchable ? WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN : WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = gravity;
        lp.y = y;
        try { wm.addView(v, lp); } catch (Exception e) { overlay = null; }
    }

    private void removeMainOverlayOnly() {
        if (overlay != null) {
            try { wm.removeView(overlay); } catch (Exception ignored) {}
            overlay = null;
        }
    }

    private void removeContinueOverlay() {
        if (continueOverlay != null) {
            try { wm.removeView(continueOverlay); } catch (Exception ignored) {}
            continueOverlay = null;
        }
    }

    private void removeAllOverlays() {
        removeMainOverlayOnly();
        removeContinueOverlay();
    }

    private void stopCalibration() {
        acquiring = false;
        removeAllOverlays();
    }

    @Override public void onDestroy() {
        stopCalibration();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
