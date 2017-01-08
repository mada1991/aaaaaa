package com.mediamaster.pushflip;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by paladin on 16-4-12.
 */
public class AudioSource {
    public static AtomicBoolean mQuit = new AtomicBoolean(false);

    public interface OnSampleAvaiableListener {
        void onSampleAvalailable(ByteBuffer jData, int jOffset, int jSize, int jFlags, long jPts);
        void initAudioCodec(int channels, int samplerate, byte[] extract_data) ;
        void onAudioSourceError(int error_no);
        //pcm data  pcm data
        byte [] sendPcmData( byte []pcm_data );
    }

    public AudioSource(){
        mQuit = new AtomicBoolean(false);
    }

    public void start() {

    }

    public void pause() {

    }

    public void resume() {

    }

    public void stop() {
        mQuit.set(true);
    }
}
