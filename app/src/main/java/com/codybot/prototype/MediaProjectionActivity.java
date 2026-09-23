package com.codybot.prototype;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;

public class MediaProjectionActivity extends Activity {
    public static final int REQUEST_CODE = 9001;
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mpm.createScreenCaptureIntent(), REQUEST_CODE);
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Intent service = new Intent(this, ScreenCaptureService.class);
        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            service.setAction(ScreenCaptureService.ACTION_START_CAPTURE);
            service.putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode);
            service.putExtra(ScreenCaptureService.EXTRA_DATA, data);
        } else {
            service.setAction(ScreenCaptureService.ACTION_STOP_CAPTURE);
        }
        startService(service);
        finish();
    }
}
