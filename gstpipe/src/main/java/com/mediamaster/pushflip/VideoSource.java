package com.mediamaster.pushflip;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by paladin on 16-4-12.
 */
public  class VideoSource {
    public static AtomicBoolean mQuit = new AtomicBoolean(false);
    protected boolean mPrivateMode = false;
    public interface OnFrameAvaiableListener {
        void onFrameAvalailable(ByteBuffer jData, int jOffset, int jSize, int jFlags, long jPts);
        void initVideoCodec(int width, int height, byte[] extract_data, byte[] sps_nal ,  byte[] pps_nal) ;
    }

    public VideoSource() {
        mQuit = new AtomicBoolean(false);
    }

    public  void start() {


    }

    public void pause() {

    }

    public void resume() {

    }

    public void stop() {
        mQuit.set(true);
    }

    public void setPrivateMode(boolean p) {
        mPrivateMode = p;
    }
}
