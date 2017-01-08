package com.mediamaster.pushflip;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;

/**
 * Created by paladin on 16-4-21.
 */
public class MediaProjectionActivity extends Activity {
    private static final String TAG = "pushflip-MediaProjectionActivity";

    private static final int sdk_init = Build.VERSION.SDK_INT;
    private MediaProjectionManager mMediaProjectionManager;
    private MediaProjection mediaProjection;

    private static final int REQUEST_CODE = 1;
    private static MediaProjectionActivity mContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "chh  onCreate sdk_init " + sdk_init);

        mContext = this;
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

    public static void myfinish() {
        mContext.finish();
    }
}
