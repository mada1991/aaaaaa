package com.mediamaster.pushflip;


import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import com.mediamaster.ffmpegwrap.NativeFfmpegSender;
import com.mediamaster.pushflip.source.MicAudioSource;
import com.mediamaster.pushflip.source.ScreenVideoSource;
import com.mediamaster.pushflip.source.ScreenVideoSource2;
import com.mediamaster.pushflip.source.ScreenVideoSource3;

//import mediamaster.com.gstpush.NativeRawPusher;


/**
 * Created by paladin on 16-4-12.
 */
public class RtmpSender implements VideoSource.OnFrameAvaiableListener, AudioSource.OnSampleAvaiableListener {
    private static final String TAG = "pushflip-RtmpSender";

    private VideoSource mVideoSource = null;
    private AudioSource mAudioSource = null;
    //    private NativeGPusher mSender;
    private NativeFfmpegSender mSender;
    private boolean mVideoSourceInit = false;
    private boolean mAudioSourceInit = false;
//    private SendThread mSendThread = null;
//    private SenderThread mSenderThread;

    private PusherEventListener mListener = null;

    private boolean mIsReconnecting = false;
    private boolean mIsDisconnected = false;
    private boolean mReconnectCleared = false;
    private long mFirstPts = -1;
    private String mUrl;
    public  AtomicBoolean mQuit ;
    private boolean mPrivateMode;
    private boolean privateThreadWantStop;
    private boolean mFirstUnPrivate = false;
    private int mPrivateIndex = 0;
    private int mPrivateCount = 2;
    private Context mContext;

    public RtmpSender(Context c) {
        mContext = c;
        mQuit = new AtomicBoolean(false);
        mIsDisconnected = false;
        MyNativeRtmpListener l = new MyNativeRtmpListener();
        mSender = new NativeFfmpegSender(l);
        mSender.init();
//        mSendThread = new SendThread();

//        mSenderThread = new SenderThread();
    }

    public void regListener(PusherEventListener listener) {
        mListener = listener;

    }

    public boolean connectServer(String url) {
        Log.i(TAG, "connectServer");
        mUrl = url;
        return mSender.connectRtmp(url);
//        try {
//            Thread.sleep(5000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
    }

    //TODO: add error proccess
    public boolean prepare(GPusherConfig cfg) {
        Log.i(TAG, "prepare");
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                mVideoSource = new ScreenVideoSource2(this, cfg.width, cfg.height, cfg.bitrate, 1, cfg.mediaProjection);
            } else {
                //mVideoSource = new ScreenVideoSource(this, cfg.width, cfg.height, cfg.bitrate, 1, cfg.mediaProjection);
                mVideoSource = new ScreenVideoSource3(this, cfg.width, cfg.height, cfg.bitrate, cfg.dpi, cfg.mediaProjection);
            }
            mAudioSource = new MicAudioSource(this);

            if (mVideoSource != null)
                mVideoSource.start();
            if (mAudioSource != null)
                mAudioSource.start();

//        try {
//            loadPrivateData();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        return true;
    }

    public void start() {
        Log.i(TAG, "start");
        if (mVideoSource != null) {
            while (!mVideoSourceInit) {
                if (mQuit.get() == true) {
                    Log.i(TAG, "mQuit at wait mVideoSourceInit");
                    return;
                }
                try {
                    Log.i(TAG, "wait mVideoSourceInit");
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        if (mAudioSource != null) {
            while (!mAudioSourceInit) {
                if (mQuit.get() == true) {
                    Log.i(TAG, "mQuit at wait mAudioSourceInit");
                    return;
                }
                try {
                    Log.i(TAG, "wait mAudioSourceInit");
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        mSender.start();
//        mSendThread.start();

    }

    public void pause() {
        if (mVideoSource != null)
            mVideoSource.pause();
        if (mAudioSource != null)
            mAudioSource.pause();

    }

    public void resume() {
        if (mVideoSource != null)
            mVideoSource.resume();
        if (mAudioSource != null)
            mAudioSource.resume();
    }

    public void stop() {
        Log.i(TAG, "stop");
        if (mQuit.get()) {
            Log.i(TAG, "stop again");
            return;
        }
        if (mVideoSource != null)
            mVideoSource.stop();
        if (mAudioSource != null)
            mAudioSource.stop();
        mQuit.set(true);
        if (mSender != null) {
            mSender.destroy();
            mSender = null;
        }
    }

    private long mLastVpts = 0;
    private long mLastApts = 0;
    private long mLastPrint = 0;
    private long mLastPrintVgap = 0;
    private long mAvUnsyncCount = 0;
    private long mVgapCount = 0;
    private long mVDecCount = 0;
    private void enqueueData(boolean isVideo, ByteBuffer jData, int jOffset, int jSize, int jFlags, long jPts) {

    }

//    public class PrivateData{
//        ByteBuffer cfg_buf;
//        int cfg_size;
//        ByteBuffer buf;
//        int flags;
//        int size;
//    }
//    private PrivateData[] mPrivateData = new PrivateData[2];
//
//    private void loadPrivateData() throws IOException {
//        String path = "/sdcard/movies/1.h264";
//        mPrivateData[0] = loadPrivateDataOne( "/sdcard/movies/1.h264");
//        mPrivateData[1] = loadPrivateDataOne( "/sdcard/movies/2.h264");
//    }
//    private PrivateData loadPrivateDataOne(String path) throws IOException {
//        PrivateData p  = new PrivateData();
//        File f = new File(path);
//        InputStream in = new FileInputStream(f);
//        byte b[] = new byte[1024*512];
//        int len = 0;
//        int temp=0;//所有读取的内容都使用temp接收
//        while((temp=in.read())!=-1){    //当没有读取完时，继续读取
//            b[len]=(byte)temp;
//            len++;
//        }
//        in.close();
//
//        if (b[4] == 0x65) {
//            p.flags = 1;
//        } else {
//            p.flags = 0;
//        }
//        p.size = len;
//        p.buf = ByteBuffer.wrap(b, 0, p.size );
//
////        p.cfg_buf = ByteBuffer.wrap(b, 0, 30 );
////        p.cfg_size = 30;
////
////        p.flags = 1;
////        p.size = len-30;
////        p.buf = ByteBuffer.wrap(b, 30, p.size );
//
//        Log.i(TAG, path + " " + p.flags + " " + p.size);
//        return p;
//
//    }

//    class PrivateThread extends  Thread {
//        @Override
//        public  void run() {
//            long startPts = mLastVpts;
//            long startSysTs = System.currentTimeMillis();
//            mPrivateIndex = 0;
////            mSender.sendBuffer(1, startPts, 1, ByteBuffer.wrap(sps_nal), sps_nal.length, 0);
////            mSender.sendBuffer(1, startPts, 1, ByteBuffer.wrap(pps_nal), pps_nal.length, 0);
//            while(!privateThreadWantStop) {
//                long jPts = startPts + (System.currentTimeMillis() - startSysTs);
//
//
//                PrivateData p = mPrivateData[mPrivateIndex];
//                Log.i(TAG, "private mode onFrameAvalailable video " + jPts + " flags " + p.flags + " size " + p.size + " offset 0");
////                byte[] sps_nal = new byte[p.cfg_buf.remaining()];
////                byte[] pps_nal = new byte[p.buf.remaining()];
////                Log.i(TAG, "privateBufer cfg : " + utils.bytesToHex(sps_nal));
////                Log.i(TAG, "privateBufer data :" + utils.bytesToHex(pps_nal));
////                mSender.sendBuffer(1, jPts, 0, p.cfg_buf, p.cfg_size, 0);
//                mSender.sendBuffer(1, jPts, p.flags, p.buf, p.size, 0);
//                mLastVpts = jPts;
//                if (++mPrivateIndex >= mPrivateCount)
//                    mPrivateIndex = 0;
//                try {
//                    for(int i  = 0 ; i< 30; i++) {
//                        if (privateThreadWantStop)
//                            return;
//                        Thread.sleep(10);
//                    }
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//            }
//
//
//        }
//    }

    class PrivateThread extends  Thread {

        public class TrackResult {

            private TrackResult() {
            }

            public int mVideoTrackIndex;
            public String mVideoTrackMime;
            public MediaFormat mVideoTrackFormat;
            public int mAudioTrackIndex;
            public String mAudioTrackMime;
            public MediaFormat mAudioTrackFormat;
        }

        public TrackResult getFirstVideoAndAudioTrack(MediaExtractor extractor) {
            TrackResult trackResult = new TrackResult();
            trackResult.mVideoTrackIndex = -1;
            trackResult.mAudioTrackIndex = -1;
            int trackCount = extractor.getTrackCount();
            for (int i = 0; i < trackCount; i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (trackResult.mVideoTrackIndex < 0 && mime.startsWith("video/")) {
                    trackResult.mVideoTrackIndex = i;
                    trackResult.mVideoTrackMime = mime;
                    trackResult.mVideoTrackFormat = format;
                } else if (trackResult.mAudioTrackIndex < 0 && mime.startsWith("audio/")) {
                    trackResult.mAudioTrackIndex = i;
                    trackResult.mAudioTrackMime = mime;
                    trackResult.mAudioTrackFormat = format;
                }
                if (trackResult.mVideoTrackIndex >= 0 && trackResult.mAudioTrackIndex >= 0) break;
            }
            if (trackResult.mVideoTrackIndex < 0 || trackResult.mAudioTrackIndex < 0) {
                //throw new IllegalArgumentException("extractor does not contain video and/or audio tracks.");
                Log.w(TAG, "extractor does not contain video and/or audio tracks.");
                return null;
            }
            return trackResult;
        }

        @Override
        public  void run() {
            long startPts = mLastVpts;
            long startSysTs = System.currentTimeMillis();
            mPrivateIndex = 0;
            MediaExtractor mExtractor;
            mExtractor = new MediaExtractor();
            //String path = GPusherConfig.privateDir+ "/private_" +mWidth +"x" + mHeight +".mp4";
            String path = GPusherConfig.privatePath;
            File f = new File(path);
            if (!f.exists()) {
                Log.w(TAG, "not exists " + path );
                return;
            }
            try {
                mExtractor.setDataSource(path);
            } catch (IOException e) {
                e.printStackTrace();
            }
            TrackResult trackResult = getFirstVideoAndAudioTrack(mExtractor);
            if (trackResult == null) {
                return;
            }
            mExtractor.selectTrack(trackResult.mVideoTrackIndex);
            ByteBuffer mDecoderInputBuffers = ByteBuffer.allocate(512*1024);
//            mSender.sendBuffer(1, startPts, 1, ByteBuffer.wrap(sps_nal), sps_nal.length, 0);
//            mSender.sendBuffer(1, startPts, 1, ByteBuffer.wrap(pps_nal), pps_nal.length, 0);

            while(!privateThreadWantStop) {
                long jPts = startPts + (System.currentTimeMillis() - startSysTs);
                int trackIndex = mExtractor.getSampleTrackIndex();
                if (trackIndex < 0) {
                        mExtractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                        Log.i(TAG, "seek begin");
                        trackIndex = mExtractor.getSampleTrackIndex();
                        if (trackIndex < 0) {
                            Log.w(TAG, "Error file, do nothing");
                        }
                }

                int sampleSize = mExtractor.readSampleData(mDecoderInputBuffers, 0);
                boolean isKeyFrame = (mExtractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0;
                mExtractor.advance();
                int flags = isKeyFrame?1:0;
                //Log.i(TAG, "private mode onFrameAvalailable video " + jPts + " flags " + isKeyFrame + " size " + sampleSize + " offset 0");
//                byte[] sps_nal = new byte[p.cfg_buf.remaining()];
//                byte[] pps_nal = new byte[p.buf.remaining()];
//                Log.i(TAG, "privateBufer cfg : " + utils.bytesToHex(sps_nal));
//                Log.i(TAG, "privateBufer data :" + utils.bytesToHex(pps_nal));
//                mSender.sendBuffer(1, jPts, 0, p.cfg_buf, p.cfg_size, 0);

                if (mQuit.get())
                    return;
                if (mSender == null) {
                    return;
                }
                mSender.sendBuffer(1, jPts, flags, mDecoderInputBuffers, sampleSize, 0);
                mLastVpts = jPts;
                if (++mPrivateIndex >= mPrivateCount)
                    mPrivateIndex = 0;
                try {
                    for(int i  = 0 ; i< 4; i++) {
                        if (privateThreadWantStop)
                            return;
                        Thread.sleep(10);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            if (mExtractor != null) {
                mExtractor.release();
                mExtractor = null;
            }

        }
    }
    ByteBuffer mPrivateBuf;
    int mPrivateBufSize;
    int mPrivateBufferOffset;
    @Override
    public void onFrameAvalailable(ByteBuffer jData, int jOffset, int jSize, int jFlags, long jPts) {
        if (mIsDisconnected) {
            return;
        }
        if (mQuit.get())
            return;


        if (jSize <= 0) {
            return;
        }
        jPts = jPts / 1000;

        if (mFirstPts < 0) {
            mFirstPts = jPts;
        }
        jPts = jPts - mFirstPts;
//        if (jPts > 1000)
//            jPts -= 1000;

// DONOT SEND THIS
//        if (mFirstSendVideo) {
//            ByteBuffer d = ByteBuffer.wrap(video_extradata);
//            mSender.sendBuffer(1, 0, jFlags, d, video_extradata.length, 0);
//            mFirstSendVideo =false;
//
//        }

        if (Math.abs(mLastApts - jPts) > 500) {
            mAvUnsyncCount++;
            if ( (System.currentTimeMillis() - mLastPrint) > 1000){
                Log.w(TAG, " avsync " + mAvUnsyncCount  + " v " + jPts + " -  a " + mLastApts + " = " + (jPts - mLastApts));
                mLastPrint = System.currentTimeMillis();
            }
        }
//        //TODO: vd->st->en msk
//        if ( Math.abs(mLastVpts - jPts) < 40 && jFlags == 0) {
//            return ;
//        }
//        if (mLastVpts > jPts) {
//            Log.i(TAG, "\n\n---------------------- smaller pts " + (mLastVpts - jPts));
//        }

        // middle 40
        if ((Math.abs(mLastVpts - jPts) > 60
                        || Math.abs(mLastVpts - jPts) < 20
                        || mLastVpts > jPts)) {
            mVgapCount++;
            if (mLastVpts > jPts)
                mVDecCount++;
            if ((System.currentTimeMillis() - mLastPrintVgap) > 5000) {
                Log.i(TAG, "  vgap  " + mVDecCount +"/" + mVgapCount + " " + jPts + " - last " + mLastVpts + " = " + (jPts - mLastVpts));
                mLastPrintVgap = System.currentTimeMillis();
            }

        }

//        jPts = mLastVpts +  (jPts - mLastVpts)/41*41 + 41;

//        Log.i(TAG, "jPts - mLastVpts " + (jPts-mLastVpts));
        //Log.i(TAG, "onFrameAvalailable video " + jPts + " len " + jSize);
        int ret = 0;
        if (mPrivateMode) {
            if(jFlags == 1) {
                mPrivateBuf = jData;
                mPrivateBufSize = jSize;
                mPrivateBufferOffset = jOffset;
            }
            return;
        } else {
            if (mFirstUnPrivate) {
                mSender.sendBuffer(1, jPts-1, 1, ByteBuffer.wrap(sps_nal), sps_nal.length, 0);
                mSender.sendBuffer(1, jPts-1, 1, ByteBuffer.wrap(pps_nal), pps_nal.length, 0);
                if(mPrivateBufSize != 0) {
                    mSender.sendBuffer(1, jPts - 1, 1, mPrivateBuf, mPrivateBufSize, mPrivateBufferOffset);
                    mPrivateBufSize = 0;
                }
                mFirstUnPrivate = false;
            }
            //Log.i(TAG, "onFrameAvalailable video " + jPts + " flags " + jFlags + " size " + jSize  + " offset "+ jOffset);
            ret = mSender.sendBuffer(1, jPts, jFlags, jData, jSize, jOffset);
        }
        if (ret < 0) {
//            if (!mIsDisconnected) {
//                mIsDisconnected = true;
//                ReconnectThread t = new ReconnectThread();
//                t.start();
//            }

        }
        mLastVpts = jPts;
    }

    @Override
    public void onSampleAvalailable(ByteBuffer jData, int jOffset, int jSize, int jFlags, long jPts) {
        if (mIsDisconnected) {
            return;
        }

        if (mQuit.get())
            return;

        if (jSize <= 0) {
            return;
        }
        jPts = jPts / 1000;
        if (mFirstPts < 0) {
            mFirstPts = jPts;
        }
        jPts = jPts - mFirstPts;

        //Log.i(TAG, "onSampleAvalailable Audio " + jPts + " len " + jSize);
        int ret = mSender.sendBuffer(0, jPts, jFlags, jData, jSize, jOffset);
        if (ret < 0) {
//            if (!mIsDisconnected) {
//                mIsDisconnected = true;
//                ReconnectThread t = new ReconnectThread();
//                t.start();
//            }

        }
        mLastApts = jPts;
    }


    class MyNativeRtmpListener extends NativeRtmpListener {
        //        @Override
//        public void OnReconnecting() {
//            mIsReconnecting = true;
//            if(mListener != null) {
//                mListener.OnNofityEvent(PushEvent.info_reconnecting);
//            }
//        }
//        @Override
//        public void OnReconnected(){
//            mIsReconnecting = false;
//
//            mIsDisconnected = true;
//            mReconnectCleared = false;
//            mListener.OnNofityEvent(PushEvent.info_reconnected);
//        }
        @Override
        public void onSendFailed() {
            Log.i(TAG, "onSendFailed");
            if (!mIsDisconnected) {
                mIsDisconnected = true;
//                if (mSender != null) {
//                    mSender.destroy();
//                    mSender = null;
//                }
//                ReconnectThread t = new ReconnectThread();
//                t.start();
                if (mSender != null) {
                    Log.i(TAG,".... .....onSendFailed mSender ... ... .... ...");
                    mSender.destroy();
                    mSender = null;
                }
                mListener.OnNofityEvent(PushEvent.error_send_failed);
            } else {
                Log.i(TAG, "mIsDisconnected !!!");
            }
        }
    }

    private boolean mFirstSendVideo = true;
    private int mWidth;
    private int mHeight;

    private byte[] video_extradata;
    private byte[] audio_extradata;
    private int mChannels;
    private int mSampleRate;

    private byte[] sps_nal;
    private byte[] pps_nal;

    @Override
    public void initVideoCodec(int width, int height, byte[] extract_data, byte[] sps ,  byte[] pps) {
        Log.i(TAG, "initVideoCodec");
        if (mQuit.get())
            return;
        // TODO check sender status
        mWidth = width;
        mHeight = height;
        video_extradata = new byte[extract_data.length];
        for (int i = 0; i < extract_data.length; i++)
            video_extradata[i] = extract_data[i];

        sps_nal = new byte[sps.length];
        for (int i = 0; i < sps.length; i++)
            sps_nal[i] = sps[i];

        pps_nal = new byte[pps.length];
        for (int i = 0; i < pps.length; i++)
            pps_nal[i] = pps[i];

        mSender.initVideo(width, height, video_extradata);
        mVideoSourceInit = true;

    }

    @Override
    public void initAudioCodec(int channels, int samplerate, byte[] extract_data) {
        if (mQuit.get())
            return;
        // TODO check sender status
        mChannels = channels;
        mSampleRate = samplerate;
        audio_extradata = new byte[extract_data.length];
        for (int i = 0; i < extract_data.length; i++)
            audio_extradata[i] = extract_data[i];
        mSender.initAudio(channels, samplerate, audio_extradata);
        mAudioSourceInit = true;
        Log.i(TAG, "initAudioCodec");
    }

    @Override
    public byte [] sendPcmData( byte []pcm_data )
    {
        return  mSender.sendPcmData( pcm_data );
    }

    @Override
    public void onAudioSourceError(int error_no) {
        mListener.OnNofityEvent(error_no);
    }
    /**
     * 检测网络是否连接
     *
     * @return
     */
    private boolean checkNetworkState() {
        boolean flag = false;
        //得到网络连接信息
        ConnectivityManager manager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        //去进行判断网络是否连接
        if (manager.getActiveNetworkInfo() != null) {
            flag = manager.getActiveNetworkInfo().isAvailable();
        }
        if (!flag) {
            return false;
        } else {
            NetworkInfo.State gprs = manager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE).getState();
            NetworkInfo.State wifi = manager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState();
            if (wifi == NetworkInfo.State.CONNECTED || gprs == NetworkInfo.State.CONNECTED)
                return true;
        }

        return false;
    }

    private boolean reconnecting = false;

    /**
     * 重连
     * @param url
     * @return false 重连失败，正在重连。
     */
    public boolean reconnect(String url) {
        Log.i(TAG, "reconnect " + url);
        if (mQuit.get() == true) {
            Log.i(TAG, "mQuit at try recoonect 111");
            return false;
        }
        if (reconnecting) {
            // TODO:
            return false;
        }
        mUrl = url;
        new Thread() {
            public void run() {
                reconnecting = true;
                if (mSender != null) {
                    mSender.destroy();
                    mSender = null;
                }
                if (mQuit.get() == true) {
                    Log.i(TAG, "mQuit at try recoonect");
                    return ;
                }

                MyNativeRtmpListener l = new MyNativeRtmpListener();
                mSender = new NativeFfmpegSender(l);
                mSender.init();
                if (!mSender.connectRtmp(mUrl)) {
                    Log.w(TAG, "reconnectRtmp failed!!! " + mUrl);
                    if (mListener != null) {
                        mListener.OnNofityEvent(PushEvent.info_reconnect_failed);
                    }
                    reconnecting = false;
                    return;
                }

                mSender.initVideo(mWidth, mHeight, video_extradata);
                mSender.initAudio(mChannels, mSampleRate, audio_extradata);
                mSender.start();
                Log.i(TAG, "reconnectRtmp OK!!!" + mUrl);
                mIsDisconnected = false;
                if (mListener != null) {
                    mListener.OnNofityEvent(PushEvent.info_reconnected);
                }
                reconnecting = false;
            }
        }.start();
        return true;
    }

    PrivateThread privateThread;

    public void setPrivateMode(boolean p) {
        if (mVideoSource == null) {
            Log.w(TAG, "setPrivateMode mVideoSource is null");
            return;
        }
        if (mPrivateMode == p)
            return;

        if (p == true) {
            privateThread = new PrivateThread();
            privateThread.start();
            mPrivateMode = true;
            privateThreadWantStop = false;
        } else {
            mFirstUnPrivate = true;
            privateThreadWantStop = true;
            try {
                privateThread.join(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mPrivateMode = false;

        }
        //mVideoSource.setPrivateMode(p);
    }


}
