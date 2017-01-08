package com.mediamaster.pushflip.source;

import android.media.projection.MediaProjection;

import com.mediamaster.pushflip.VideoSource;

import java.io.IOException;

/**
 * Created by paladin on 16-5-18.
 */
public class ScreenVideoSource3 extends VideoSource {
    private static final String TAG = "pushflip-ScreenVideoSource";


    ScreenSource mSource;

    public ScreenVideoSource3(OnFrameAvaiableListener l, int width, int height, int bitrate, int dpi, MediaProjection mp) {
//        mWidth = width;
//        mHeight = height;
//        mBitRate = bitrate;
//        mDpi = dpi;
//        mMediaProjection = mp;
//        mListener = l;
        mSource = new ScreenSource(l, mp, width, height, dpi);
    }

    public void start() {
        try {
            mSource.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mSource.startRecording();
    }

//    public void pause() {
//
//    }
//
//    public void resume() {
//
//    }

    public void stop() {
        mSource.stopRecording();
    }


    @Override
    public void setPrivateMode(boolean p) {
        mPrivateMode = p;
        mSource.setPrivateMode(p);
    }
}
