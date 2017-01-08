package com.mediamaster.pushflip;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

/**
 * Created by paladin on 16-5-14.
 */
public class AssistService extends Service {
    private static final String TAG = "wxx";

    public class LocalBinder extends Binder {
        public AssistService getService() {
            return AssistService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "AssistService: onBind()");
        return new LocalBinder();
    }

    @Override
    public void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
        Log.d(TAG, "AssistService: onDestroy()");
    }

}