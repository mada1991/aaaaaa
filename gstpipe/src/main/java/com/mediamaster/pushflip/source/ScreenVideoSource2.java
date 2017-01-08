package com.mediamaster.pushflip.source;

import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.projection.MediaProjection;
import android.view.Surface;

import com.mediamaster.pushflip.GPusherConfig;
import com.mediamaster.pushflip.Log;
import com.mediamaster.pushflip.VideoSource;
import com.mediamaster.pushflip.utils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * Created by paladin on 16-4-12.
 */
public class ScreenVideoSource2 extends VideoSource {
    private static final String TAG = "pushflip-ScreenVideoSource2";
    private int mWidth;
    private int mHeight;
    private int mBitRate;
    private int mDpi;
    private MediaProjection mMediaProjection;
    // parameters for the encoder
    private static final String MIME_TYPE = "video/avc"; // H.264 Advanced Video Coding
    private static final int FRAME_RATE = 20; // 30 fps
    private static final int IFRAME_INTERVAL = 2; // 10 seconds between I-frames
    private static final long TIMEOUT_US = 10000;

    private MediaCodec mVideoEncoder;

    public static Surface mSurface = null;

    public static boolean swapbuffer;
    private int mVideoTrackIndex = -1;

    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    private MediaCodec.BufferInfo aBufferInfo = new MediaCodec.BufferInfo();
    private VirtualDisplay mVirtualDisplay;


    // Related to extracting H264 SPS + PPS from MediaCodec
    private ByteBuffer mH264Keyframe;
    private int mH264MetaSize = 0;                   // Size of SPS + PPS data
    private final Object mVideoEncoderReleasedSync = new Object();
    private boolean mVideoEncoderReleased;                   // TODO: Account for both encoders
    long mFirstPts = -1;
    String mRtmpUri;
    private OnFrameAvaiableListener mListener;

    MyScreenRecord mMyScreenRecord;
    ReadyThread ready;
    VideoThread mRecordDisplayThread;

    public class MyDequeueFramewInfo {
        public MyDequeueFramewInfo(ByteBuffer data, int off, int size, int flag, long pts) {
            frameData = data;
            offset = off;
            frameData_size = size;
            presentationTimeUs = pts;
            flags = flag;
        }

        ByteBuffer frameData;
        int frameData_size;
        int flags;
        long presentationTimeUs;
        int offset;

    }

    public class DeSortComparator implements Comparator {
        @Override
        public int compare(Object s, Object e) {

            return (int) (((MyDequeueFramewInfo) s).presentationTimeUs - ((MyDequeueFramewInfo) e).presentationTimeUs);
        }
    }


    private DeSortComparator mDeSortComparator = new DeSortComparator();
    ArrayList<MyDequeueFramewInfo> mMyDequeueFrameInfoQueue = new ArrayList<MyDequeueFramewInfo>();

    public ScreenVideoSource2(OnFrameAvaiableListener l, int width, int height, int bitrate, int dpi, MediaProjection mp) {
        mWidth = width;
        mHeight = height;
        mBitRate = bitrate;
        mDpi = dpi;
        mMediaProjection = mp;
        mListener = l;
        mMyScreenRecord = new MyScreenRecord(GPusherConfig.myscreenrecord_path, width, height, bitrate, dpi);
    }

    public void start() {
        Log.i(TAG, "start");
        ready = new ReadyThread();
        ready.start();
    }

    public void stop() {
        Log.i(TAG, "stop");
        mQuit.set(true);
//        if (ready != null)
//            ready.stop();
//        if (mRecordDisplayThread != null)
//            mRecordDisplayThread.stop();
//
        if (mMyScreenRecord != null)
            mMyScreenRecord.stop();
    }

    private long prevOutputPTSUs = 0;

    protected long getPTSUs() {
        long result = System.nanoTime() / 1000L;
        if (result < prevOutputPTSUs)
            result = (prevOutputPTSUs - result) + result;

        return result;
    }

    class ReadyThread extends Thread {
        @Override
        public void run() {
            mMyScreenRecord.prepareScreenRecorder();
            /****************** init video ************************************/
            mMyScreenRecord.waitForReady();
            int width = mMyScreenRecord.getWidth();
            int height = mMyScreenRecord.getHeight();
            ByteBuffer sps = mMyScreenRecord.getCsd0();
            ByteBuffer pps = mMyScreenRecord.getCsd1();
            byte[] sps_nal = new byte[sps.remaining()];
            sps.get(sps_nal);
            byte[] pps_nal = new byte[pps.remaining()];
            pps.get(pps_nal);

            Log.i(TAG, "handleAddTrack Init Video , " + width + " X " + height + " configure sps: " + utils.bytesToHex(sps_nal) + " pps: " + utils.bytesToHex(pps_nal));


            int j = 0;
            byte[] combined = new byte[sps_nal.length + pps_nal.length - 8 + 4];
            int slen = sps_nal.length - 4;
            int plen = pps_nal.length - 4;
            combined[j++] = (byte) ((slen >> 8) & 0xFF);
            combined[j++] = (byte) ((slen) & 0xFF);
            for (int i = 4; i < sps_nal.length; ++i) {
                combined[j++] = sps_nal[i];
            }
            combined[j++] = (byte) ((plen >> 8) & 0xFF);
            combined[j++] = (byte) ((plen) & 0xFF);
            for (int i = 4; i < pps_nal.length; ++i) {
                combined[j++] = pps_nal[i];
            }
            mListener.initVideoCodec(width, height, combined, sps_nal, pps_nal);
            //mSender.initVideo(width, height ,combined);
            mRecordDisplayThread = new VideoThread();
            mRecordDisplayThread.start();
        }
    }

    class VideoThread extends Thread {
        @Override
        public void run() {
            MyScreenRecord.MyFrameInfo info;
            while (mQuit.get() == false) {
                info = mMyScreenRecord.readFrame();
                if (info == null) {
                    if (mQuit.get() == true)
                        break;
                    else {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        continue;
                    }
                }

                info.presentationTimeUs = getPTSUs();
                if (mFirstPts < 0) {
                    mFirstPts = info.presentationTimeUs;
                }

                //mSender.writeAVPacketFromEncodedData(info.frameData, 1, 0, info.frameData_size, info.flags, info.presentationTimeUs - mFirstPts);
                mListener.onFrameAvalailable(info.frameData, 0, info.frameData_size, info.flags, info.presentationTimeUs - mFirstPts);
                info.frameData = null;

                prevOutputPTSUs = mBufferInfo.presentationTimeUs;
                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    // when EOS come.
                    //mIsCapturing = false;
                    mQuit.set(true);

                }
            }
        }
    }

}
