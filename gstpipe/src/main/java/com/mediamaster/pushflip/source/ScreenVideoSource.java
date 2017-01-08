package com.mediamaster.pushflip.source;

import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.view.Surface;

import com.mediamaster.pushflip.Log;
import com.mediamaster.pushflip.VideoSource;
import com.mediamaster.pushflip.utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Created by paladin on 16-4-12.
 */
public class ScreenVideoSource extends VideoSource {
    private static final String TAG = "pushflip-ScreenVideoSource";
    private int mWidth;
    private int mHeight;
    private int mBitRate;
    private int mDpi;
    private MediaProjection mMediaProjection;
    // parameters for the encoder
    private static final String MIME_TYPE = "video/avc"; // H.264 Advanced Video Coding
    private static final int FRAME_RATE = 24; // 30 fps
    private static final int IFRAME_INTERVAL = 2; // 10 seconds between I-frames
    private static final long TIMEOUT_US = 5000;

    private MediaCodec mVideoEncoder;

    public static Surface mSurface = null;

    public static boolean swapbuffer;
    private int mVideoTrackIndex = -1;

    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    private MediaCodec.BufferInfo aBufferInfo = new MediaCodec.BufferInfo();
    private VirtualDisplay mVirtualDisplay;
    private RecordDisplayThread mRecordDisplayThread = null;

    // Related to extracting H264 SPS + PPS from MediaCodec
    private ByteBuffer mH264Keyframe;
    private int mH264MetaSize = 0;                   // Size of SPS + PPS data
    private final Object mVideoEncoderReleasedSync = new Object();
    private boolean mVideoEncoderReleased;                   // TODO: Account for both encoders
    long mFirstPts = -1;
    String mRtmpUri;
    private OnFrameAvaiableListener mListener;

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

    public ScreenVideoSource(OnFrameAvaiableListener l, int width, int height, int bitrate, int dpi, MediaProjection mp) {
        mWidth = width;
        mHeight = height;
        mBitRate = bitrate;
        mDpi = dpi;
        mMediaProjection = mp;
        mListener = l;
    }

    public void start() {
        try {
            prepareEncoder();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.i(TAG, "new  mMediaProjection " + mMediaProjection);
        //mVirtualDisplay_f5042y = mMediaProjection_f5033p.createVirtualDisplay("kascend_display", mWidth, mHeight, mDpi, 1, mSurface_f5041x, null, null);
        mVirtualDisplay = mMediaProjection.createVirtualDisplay("ScreenVideoSource" + "-display",
                mWidth, mHeight, mDpi, 1,     //DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mSurface, null, null);
        Log.d(TAG, "created virtual display: " + mVirtualDisplay);

        mRecordDisplayThread = new RecordDisplayThread();
        mRecordDisplayThread.start();

    }

    private void prepareEncoder() throws IOException {
// Kascend
//        MediaCodecInfo codecInfoAt;
//        int codecCount = MediaCodecList.getCodecCount();
//        MediaCodecInfo mediaCodecInfo = null;
//        for (int i = 0; i < codecCount && mediaCodecInfo == null; i++) {
//            codecInfoAt = MediaCodecList.getCodecInfoAt(i);
//            if (codecInfoAt.isEncoder()) {
//                String[] supportedTypes = codecInfoAt.getSupportedTypes();
//                int i3 = 0;
//                for (int i2 = 0; i2 < supportedTypes.length && i3 == 0; i2++) {
//                    if (supportedTypes[i2].equals("video/avc")) {
//                        i3 = 1;
//                    }
//                }
//                if (i3 != 0) {
//                    mediaCodecInfo = codecInfoAt;
//                }
//            }
//        }
//        codecInfoAt = mediaCodecInfo;
//        for (int i4 = 0; i4 < MediaCodecList.getCodecCount(); i4++) {
//            MediaCodecInfo codecInfoAt2 = MediaCodecList.getCodecInfoAt(i4);
//            if (codecInfoAt2.isEncoder()) {
//                String[] supportedTypes2 = codecInfoAt2.getSupportedTypes();
//                MediaCodecInfo mediaCodecInfo2 = codecInfoAt;
//                for (int i5 = 0; i5 < supportedTypes2.length; i5++) {
//                    if (supportedTypes2[i5].equals("video/avc")) {
//                        MediaCodecInfo.CodecCapabilities capabilitiesForType = codecInfoAt2.getCapabilitiesForType(supportedTypes2[i5]);
//                        MediaCodecInfo mediaCodecInfo3 = mediaCodecInfo2;
//                        for (MediaCodecInfo.CodecProfileLevel codecProfileLevel : capabilitiesForType.profileLevels) {
//                            if (codecProfileLevel.profile == 8) {
//                                mediaCodecInfo3 = codecInfoAt2;
//                            }
//                        }
//                        mediaCodecInfo2 = mediaCodecInfo3;
//                    }
//                }
//                codecInfoAt = mediaCodecInfo2;
//            }
//        }
//        Log.d("ScreenRecorder", "Found " + codecInfoAt.getName() + " supporting " + "video/avc");
//        mVideoEncoder = MediaCodec.createByCodecName(codecInfoAt.getName());
//        MediaFormat createVideoFormat = MediaFormat.createVideoFormat("video/avc", mWidth, mHeight);
//        createVideoFormat.setInteger("color-format", MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
//        createVideoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mBitRate);
//        createVideoFormat.setInteger("frame-rate", 24);
//        createVideoFormat.setInteger("max-input-size", 0);
//        createVideoFormat.setInteger("i-frame-interval", 2);
//        mVideoEncoder.configure(createVideoFormat, null, null, 1);
//        Log.d(TAG, " mEncoder format == " + mVideoEncoder.getOutputFormat());
//        mSurface = mVideoEncoder.createInputSurface();
//        mVideoEncoder.start();


        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        Log.d(TAG, "created video format: " + format);
        mVideoEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
        mVideoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        mSurface = mVideoEncoder.createInputSurface();
        Log.d(TAG, "created input surface: " + mSurface);
        mVideoEncoder.start();
    }

    class RecordDisplayThread extends Thread {
        @Override
        public void run() {
            long last_pts = System.currentTimeMillis();

            while (!mQuit.get()) {
                long now = System.currentTimeMillis();
                if (now - last_pts < 30) {
                    try {
//                        Log.i(TAG, "too fast sleep " + (now-last_pts));
                        Thread.sleep(now - last_pts);
                        if ((System.currentTimeMillis() - last_pts < 30))
                            continue;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                last_pts = System.currentTimeMillis();

                int index = mVideoEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_US);


                if (index == -1) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                    }
                } else if (index == -2) {
                    resetOutputFormat();
                } else if (index >= 0) {

                    encodeToVideoTrack(mVideoEncoder, index, mVideoTrackIndex);
                    mVideoEncoder.releaseOutputBuffer(index, false);
                }
            /*
            if( index2 == -1){

        		 if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {


                 } else if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                     try {
                         // wait 10ms
                         Thread.sleep(10);
                     } catch (InterruptedException e) {
                     }



                 } else if (index >= 0) {
                     encodeToVideoTrack(mVideoEncoder,index, mVideoTrackIndex);
                     mVideoEncoder.releaseOutputBuffer(index, false);
                 }
        	}*/
            }
        }
    }

    /**
     * Should only be called once, when the encoder produces
     * an output buffer with the BUFFER_FLAG_CODEC_CONFIG flag.
     * For H264 output, this indicates the Sequence Parameter Set
     * and Picture Parameter Set are contained in the buffer.
     * These NAL units are required before every keyframe to ensure
     * playback is possible in a segmented stream.
     *
     * @param bufferInfo
     */
    private void captureH264MetaData(MediaCodec.BufferInfo bufferInfo, ByteBuffer encodedData22222) {
        mH264MetaSize = bufferInfo.size;
        ByteBuffer encodedData = ByteBuffer.allocateDirect(encodedData22222.capacity());
        encodedData.put(encodedData22222);
        encodedData.position(0);

        mH264Keyframe = ByteBuffer.allocateDirect(encodedData.capacity());

        byte[] videoConfig = new byte[bufferInfo.size];
        encodedData.get(videoConfig, bufferInfo.offset, bufferInfo.size);
        encodedData.position(bufferInfo.offset);
        encodedData.put(videoConfig, 0, bufferInfo.size);
        encodedData.position(bufferInfo.offset);
        mH264Keyframe.put(videoConfig, 0, bufferInfo.size);
    }

    /**
     * Adds the SPS + PPS data to the ByteBuffer containing a h264 keyframe
     *
     * @param encodedData
     * @param bufferInfo
     */
    private void packageH264Keyframe(ByteBuffer encodedData, MediaCodec.BufferInfo bufferInfo) {
        mH264Keyframe.position(mH264MetaSize);
        mH264Keyframe.put(encodedData); // BufferOverflow
    }


    private void encodeToVideoTrack(MediaCodec localEncoder, int index, int TrackIndex) {
        MediaCodec localMediaCodec = localEncoder;
        ByteBuffer encodedData = localMediaCodec.getOutputBuffer(index);
        //mBufferInfo.presentationTimeUs = getPTSUs();
        if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");

            captureH264MetaData(mBufferInfo, encodedData);
            mBufferInfo.size = 0;
        }
        if (mBufferInfo.size == 0 || mH264MetaSize == 0) {
            Log.d(TAG, "info.size == " + mBufferInfo.size + " metasize " + mH264MetaSize + ", drop it.");
            encodedData = null;
        } else {
//            Log.d(TAG, "got buffer, info: size=" + mBufferInfo.size
//                    + ", presentationTimeUs=" + mBufferInfo.presentationTimeUs
//                    + ", " + (mBufferInfo.presentationTimeUs - mFirstPts)/1000
//                    + ", offset=" + mBufferInfo.offset
//                    + " metasize=" + mH264MetaSize
//            + " flags " + mBufferInfo.flags);
        }
        if (encodedData != null) {
            if (mFirstPts < 0) {
                mFirstPts = mBufferInfo.presentationTimeUs;
            }
            encodedData.position(mBufferInfo.offset);
            encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
            if (mBufferInfo.presentationTimeUs == 0) {
                Log.d(TAG, " video presentationTimeUs is zero");
                mBufferInfo.presentationTimeUs = System.nanoTime() / 1000 - mFirstPts;
            }
            //mMuxer.writeSampleData(TrackIndex, encodedData, mBufferInfo);
            //long ts, int flags, ByteBuffer data, int size, int offset

//            //mSender.sendBuffer(1, mBufferInfo.presentationTimeUs, mBufferInfo.flags, encodedData, mBufferInfo.size - mBufferInfo.offset, mBufferInfo.offset);
            ByteBuffer muxerInput;
            muxerInput = ByteBuffer.allocateDirect(mBufferInfo.size);
            muxerInput.put(encodedData);
            muxerInput.position(0);

            MyDequeueFramewInfo sampleinfo;
            if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_SYNC_FRAME) != 0) {
                packageH264Keyframe(muxerInput, mBufferInfo);
                //mListener.onFrameAvalailable(mH264Keyframe, mBufferInfo.offset, mBufferInfo.size + mH264MetaSize, mBufferInfo.flags, mBufferInfo.presentationTimeUs - mFirstPts);
                sampleinfo = new MyDequeueFramewInfo(mH264Keyframe, mBufferInfo.offset, mBufferInfo.size + mH264MetaSize, mBufferInfo.flags, mBufferInfo.presentationTimeUs - mFirstPts);
            } else {
                //mListener.onFrameAvalailable(muxerInput, mBufferInfo.offset, mBufferInfo.size, mBufferInfo.flags, mBufferInfo.presentationTimeUs - mFirstPts);
                sampleinfo = new MyDequeueFramewInfo(muxerInput, mBufferInfo.offset, mBufferInfo.size, mBufferInfo.flags, mBufferInfo.presentationTimeUs - mFirstPts);
            }


            mMyDequeueFrameInfoQueue.add(sampleinfo);
            Collections.sort(mMyDequeueFrameInfoQueue, mDeSortComparator);
            if (mMyDequeueFrameInfoQueue.size() < 5) {
                return;
            }
            MyDequeueFramewInfo de = mMyDequeueFrameInfoQueue.get(0);
//            mListener.onFrameAvalailable(muxerInput, mBufferInfo.offset, mBufferInfo.size, mBufferInfo.flags, mBufferInfo.presentationTimeUs - mFirstPts);
            mListener.onFrameAvalailable(de.frameData, de.offset, de.frameData_size, de.flags, de.presentationTimeUs);
            mMyDequeueFrameInfoQueue.remove(de);

            if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                // when EOS come.
                //TODO

            }
        }
    }


    private void resetOutputFormat() {

        /****************** init video ************************************/
        MediaFormat videoTrackFormat = mVideoEncoder.getOutputFormat();
        int width = videoTrackFormat.getInteger(MediaFormat.KEY_WIDTH);
        int height = videoTrackFormat.getInteger(MediaFormat.KEY_HEIGHT);
        ByteBuffer sps = videoTrackFormat.getByteBuffer("csd-0");
        ByteBuffer pps = videoTrackFormat.getByteBuffer("csd-1");
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

//        byte[] combined = new byte[sps_nal.length + pps_nal.length ];
//        int j  = 0;
//        for (int i = 0; i < sps_nal.length; ++i) {
//            combined[j++] = sps_nal[i];
//        }
//        for (int i = 0; i < pps_nal.length; ++i) {
//            combined[j++] = sps_nal[i];
//        }

        mListener.initVideoCodec(width, height, combined,pps_nal, sps_nal);
    }
}
