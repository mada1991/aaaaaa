//package com.mediamaster.pushflip;
//
//
//import java.nio.ByteBuffer;
//
////import mediamaster.com.gstpush.NativeRawPusher;
//
///**
// * Created by paladin on 16-4-21.
// */
//public class GPusher implements VideoSource.OnFrameAvaiableListener, AudioSource.OnSampleAvaiableListener{
//
//    private static final String TAG = "RtmpSender";
//
//    private VideoSource mVideoSource;
//    private AudioSource mAudioSource;
//    private NativeRawPusher mSender;
//    private boolean mVideoSourceInit = false;
//    private boolean mAudioSourceInit = false;
//
//
//
//
//    GPusherConfig mCfg = null ;
//
//    public interface OnEventListener {
//        public void onPrepared();
//    }
//
//    OnEventListener mListerner = null;
////    private SenderThread mSenderThread;
//
//
//    public GPusher() {
//
//        mSender = new NativeRawPusher();
//
////        mSenderThread = new SenderThread();
//    }
//
//    public void registerEventListener(OnEventListener l)
//    {
//        mListerner = l;
//    }
//
//    public void connectServer(String url) {
//        Log.i(TAG, "connectServer");
//        mSender.connectRtmp(url);
//        try {
//            Thread.sleep(5000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//    }
//
//    public void setConfig(GPusherConfig cfg) {
//        mCfg = cfg;
//    }
//    public void prepare() {
//        Log.i(TAG, "prepare");
//        if (mCfg == null) {
//            Log.i(TAG, "prepare failed, cfg is null");
//            return;
//        }
//        mVideoSource = new ScreenVideoSource(this,  mCfg.width, mCfg.height, mCfg.bitrate, 1, mCfg.mediaProjection);
//        mAudioSource = new MicAudioSource(this);
//
//
//        mVideoSource.start();
//        mAudioSource.start();
//        new Thread(){
//            public void run(){
//                if(mVideoSource != null) {
//                    while(!mVideoSourceInit) {
//                        try {
//                            Log.i(TAG, "wait mVideoSourceInit");
//                            Thread.sleep(20);
//                        } catch (InterruptedException e) {
//                            e.printStackTrace();
//                        }
//                    }
//                }
//                if(mAudioSource != null) {
//                    while(!mAudioSourceInit) {
//                        try {
//                            Log.i(TAG, "wait mAudioSourceInit");
//                            Thread.sleep(20);
//                        } catch (InterruptedException e) {
//                            e.printStackTrace();
//                        }
//                    }
//                }
//
//                if (mListerner != null) {
//                    mListerner.onPrepared();
//                }
//            }
//        }.start();
//    }
//
//    public boolean start() {
//        Log.i(TAG, "start");
//        if ((mVideoSource != null && !mVideoSourceInit)
//            || (mAudioSource != null && !mAudioSourceInit) ) {
//            Log.i(TAG, "Not ready for start, please wait onPrepared event");
//            return false;
//        }
//
//        mSender.start();
//        return true;
//
//    }
//
//    public void pause() {
//        if (mVideoSource != null)
//            mVideoSource.pause();
//        if (mAudioSource != null)
//        mAudioSource.pause();
//
//    }
//
//    public void resume() {
//        if (mVideoSource != null)
//        mVideoSource.resume();
//        if (mAudioSource != null)
//        mAudioSource.resume();
//    }
//
//    public void stop() {
//        if (mVideoSource != null)
//        mVideoSource.stop();
//        if (mAudioSource != null)
//        mAudioSource.stop();
//        mSender.stop();
//    }
//
//    @Override
//    public void onFrameAvalailable(ByteBuffer jData, int jOffset, int jSize, int jFlags, long jPts) {
//        mSender.writeAVPacketFromEncodedData(jData, 1, jOffset, jSize, jFlags, jPts);
//    }
//
//    @Override
//    public void onSampleAvalailable(ByteBuffer jData, int jOffset, int jSize, int jFlags, long jPts) {
//        mSender.writeAVPacketFromEncodedData(jData, 0, jOffset, jSize, jFlags, jPts);
//    }
//
//    @Override
//    public void initVideoCodec(int width, int height, byte []extract_data) {
//        mSender.initVideo(width, height, extract_data);
//        mVideoSourceInit = true;
//        Log.i(TAG, "initVideoCodec");
//    }
//
//    @Override
//    public void initAudioCodec(int channels, int samplerate, byte []extract_data) {
//        mSender.initAudio(channels, samplerate, extract_data);
//        mAudioSourceInit = true;
//        Log.i(TAG, "initVideoCodec");
//    }
//}
