package com.mediamaster.pushflip;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;

/**
 * Created by paladin on 16-5-7.
 */
public class MPActivity extends Activity {
    private static final String TAG = "pushflip-MPActivity";
    private static final int REQUEST_CODE = 1;
    private MediaProjectionManager mMediaProjectionManager;


    public static MediaProjection mediaProjection = null;
    private static final int sdk_init = Build.VERSION.SDK_INT;
    //private static final int sdk_init =  Build.VERSION_CODES.KITKAT;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "chh  onCreate");
        super.onCreate(savedInstanceState);
        mMediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        Intent captureIntent = mMediaProjectionManager.createScreenCaptureIntent();

        startActivityForResult(captureIntent, REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i(TAG, "chh  onActivityResult");
        if (sdk_init < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        mediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, data);
        if (mediaProjection == null) {
            Log.e(TAG, "media projection is null");
            return;
        }
    }

}
