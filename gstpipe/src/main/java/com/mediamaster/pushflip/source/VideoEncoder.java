package com.mediamaster.pushflip.source;
/*
* ScreenRecordingSample
* Sample project to cature and save audio from internal and video from screen as MPEG4 file.
*
* Copyright (c) 2014-2015 saki t_saki@serenegiant.com
*
* File name: MediaEncoder.java
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
*  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing, software
*  distributed under the License is distributed on an "AS IS" BASIS,
*  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*  See the License for the specific language governing permissions and
*  limitations under the License.
*
* All files in the folder are under this Apache License, Version 2.0.
*/

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.view.Surface;

import com.mediamaster.pushflip.GpusherService;
import com.mediamaster.pushflip.Log;
import com.mediamaster.pushflip.VideoSource;
import com.mediamaster.pushflip.utils;

import java.io.IOException;
import java.nio.ByteBuffer;

public abstract class VideoEncoder implements Runnable {
    private static final boolean DEBUG = true;    // TODO set false on release
    private static final String TAG = "pushflip-VideoEncoder";

    protected static final int TIMEOUT_USEC = 10000;    // 10[msec]
    protected static final int MSG_FRAME_AVAILABLE = 1;
    protected static final int MSG_STOP_RECORDING = 9;

//    public interface VideoEncoderListener {
//        public void onPrepared(VideoEncoder encoder);
//        public void onStopped(VideoEncoder encoder);
//    }

    protected final Object mSync = new Object();
    /**
     * Flag that indicate this encoder is capturing now.
     */
    protected volatile boolean mIsCapturing;
    /**
     * Flag that indicate the frame data will be available soon.
     */
    private int mRequestDrain;
    /**
     * Flag to request stop capturing
     */
    protected volatile boolean mRequestStop;
    /**
     * Flag that indicate encoder received EOS(End Of Stream)
     */
    protected boolean mIsEOS;
    /**
     * Flag the indicate the muxer is running
     */

    /**
     * Track Number
     */
    protected int mTrackIndex;
    /**
     * MediaCodec instance for encoding
     */
    protected MediaCodec mMediaCodec;                // API >= 16(Android4.1.2)
    /**
     * Weak refarence of MediaMuxerWarapper instance
     */

    /**
     * BufferInfo instance for dequeuing
     */
    private MediaCodec.BufferInfo mBufferInfo;        // API >= 16(Android4.1.2)

//    protected final VideoEncoderListener mListener;

    protected volatile boolean mRequestPause;
    private long mLastPausedTimeUs;
    private VideoSource.OnFrameAvaiableListener mListener;

    private long mFirstPts = -1;

    public VideoEncoder(
            //final VideoEncoderListener listener,
            VideoSource.OnFrameAvaiableListener l,
            int w, int h) {
        //if (listener == null) throw new NullPointerException("VideoEncoderListener is null");


        //mListener = listener;
        mListener = l;
        mWidth = w;
        mHeight = h;
        synchronized (mSync) {
            // create BufferInfo here for effectiveness(to reduce GC)
            mBufferInfo = new MediaCodec.BufferInfo();
            // wait for starting thread  开启线程
            new Thread(this, getClass().getSimpleName()).start();
            try {
                //线程等待
                mSync.wait();
            } catch (final InterruptedException e) {
            }
        }
    }


    /**
     * the method to indicate frame data is soon available or already available
     *
     * @return return true if encoder is ready to encod.
     */
    public boolean frameAvailableSoon() {
//    	if (DEBUG) Log.v(TAG, "frameAvailableSoon");
        synchronized (mSync) {
            if (!mIsCapturing || mRequestStop) {
                return false;
            }
            mRequestDrain++;
            mSync.notifyAll();
        }
        return true;
    }

    /**
     * encoding loop on private thread
     */
    @Override
    public void run() {
//		android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
        //线程唤醒
        synchronized (mSync) {
            mRequestStop = false;
            mRequestDrain = 0;
            mSync.notify();
        }
        final boolean isRunning = true;
        boolean localRequestStop;
        boolean localRequestDrain;
        while (isRunning) {
            synchronized (mSync) {
                localRequestStop = mRequestStop;
                localRequestDrain = (mRequestDrain > 0);
                if (localRequestDrain)
                    mRequestDrain--;
            }
            if (localRequestStop) {
                drain();
                // request stop recording
                signalEndOfInputStream();
                // process output data again for EOS signale
                drain();
                // release all related objects
                release();
                break;
            }
            if (localRequestDrain) {
                drain();
            } else {
                synchronized (mSync) {
                    try {
                        //线程睡眠
                        mSync.wait();
                    } catch (final InterruptedException e) {
                        break;
                    }
                }
            }
        } // end of while
        if (DEBUG) Log.d(TAG, "Encoder thread exiting");
        synchronized (mSync) {
            mRequestStop = true;
            mIsCapturing = false;
        }
    }

    /*
    * prepareing method for each sub class
    * this method should be implemented in sub class, so set this as abstract method
    * @throws IOException
    */
   /*package*/
   abstract void prepare() throws IOException;

    /*package*/
    void startRecording() {
        if (DEBUG) Log.v(TAG, "startRecording");
        synchronized (mSync) {
            mIsCapturing = true;
            mRequestStop = false;
            mRequestPause = false;
            mSync.notifyAll();   //唤醒所有的线程  进行编码
        }
    }

    /**
     * the method to request stop encoding
     */
    /*package*/
    void stopRecording() {
        if (DEBUG) Log.v(TAG, "stopRecording");
        synchronized (mSync) {
            if (!mIsCapturing || mRequestStop) {
                return;
            }
            mRequestStop = true;    // for rejecting newer frame
            mSync.notifyAll();
            // We can not know when the encoding and writing finish.
            // so we return immediately after request to avoid delay of caller thread
        }
    }

    /*package*/
    void pauseRecording() {
        if (DEBUG) Log.v(TAG, "pauseRecording");
        synchronized (mSync) {
            if (!mIsCapturing || mRequestStop) {
                return;
            }
            mRequestPause = true;
            mLastPausedTimeUs = System.nanoTime() / 1000;
            mSync.notifyAll();
        }
    }

    /*package*/
    void resumeRecording() {
        if (DEBUG) Log.v(TAG, "resumeRecording");
        synchronized (mSync) {
            if (!mIsCapturing || mRequestStop) {
                return;
            }
            offsetPTSUs = System.nanoTime() / 1000 - mLastPausedTimeUs;
            mRequestPause = false;
            mSync.notifyAll();
        }
    }

//********************************************************************************
//********************************************************************************

    /**
     * Release all releated objects
     */
    protected void release() {
        if (DEBUG) Log.d(TAG, "release:");

//        try {
//            mListener.onStopped(this);
//        } catch (final Exception e) {
//            Log.e(TAG, "failed onStopped", e);
//        }

        mIsCapturing = false;
        if (mMediaCodec != null) {
            try {
                mMediaCodec.stop();
                mMediaCodec.release();
                mMediaCodec = null;
            } catch (final Exception e) {
                Log.e(TAG, "failed releasing MediaCodec", e);
            }
        }

        mBufferInfo = null;
    }

//    protected void signalEndOfInputStream() {
//        if (DEBUG) Log.d(TAG, "sending EOS to encoder");
//        // signalEndOfInputStream is only avairable for video encoding with surface
//        // and equivalent sending a empty buffer with BUFFER_FLAG_END_OF_STREAM flag.
////		mMediaCodec.signalEndOfInputStream();	// API >= 18
//        encode(null, 0, getPTSUs());
//    }

    /**
     * Method to set byte array to the MediaCodec encoder
     *
     * @param buffer
     * @param length             　length of byte array, zero means EOS.
     * @param presentationTimeUs
     */
//    protected void encode(final ByteBuffer buffer, final int length, final long presentationTimeUs) {
//        if (!mIsCapturing) return;
//        final ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
//        while (mIsCapturing) {
//            final int inputBufferIndex = mMediaCodec.dequeueInputBuffer(TIMEOUT_USEC);
//            if (inputBufferIndex >= 0) {
//                final ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
//                inputBuffer.clear();
//                if (buffer != null) {
//                    inputBuffer.put(buffer);
//                }
////	            if (DEBUG) Log.v(TAG, "encode:queueInputBuffer");
//                if (length <= 0) {
//                    // send EOS
//                    mIsEOS = true;
//                    if (DEBUG) Log.i(TAG, "send BUFFER_FLAG_END_OF_STREAM");
//                    mMediaCodec.queueInputBuffer(inputBufferIndex, 0, 0,
//                            presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
//                    break;
//                } else {
//                    mMediaCodec.queueInputBuffer(inputBufferIndex, 0, length,
//                            presentationTimeUs, 0);
//                }
//                break;
//            } else if (inputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
//                // wait for MediaCodec encoder is ready to encode
//                // nothing to do here because MediaCodec#dequeueInputBuffer(TIMEOUT_USEC)
//                // will wait for maximum TIMEOUT_USEC(10msec) on each call
//            }
//        }
//    }

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        return bytesToHex(bytes, bytes.length);
    }

    public static String bytesToHex(byte[] bytes, int len) {
        char[] hexChars = new char[len * 3];
        for (int j = 0; j < len; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 3] = hexArray[v >>> 4];
            hexChars[j * 3 + 1] = hexArray[v & 0x0F];
            if (j > 0 && (j + 1) % 32 == 0)
                hexChars[j * 3 + 2] = '\n';
            else
                hexChars[j * 3 + 2] = ' ';
        }
        return new String(hexChars);
    }

    /**
     * drain encoded data and write them to muxer
     */
    protected void drain() {
        if (mMediaCodec == null) return;
        //ByteBuffer[] encoderOutputBuffers = mMediaCodec.getOutputBuffers();
        int encoderStatus;
        int count = 0;
        long last_pts = 0;
        LOOP:
        while (mIsCapturing) {
            try {
                // get encoded data with maximum timeout duration of TIMEOUT_USEC(=10[msec])
                encoderStatus = mMediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);

                if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // wait 5 counts(=TIMEOUT_USEC x 5 = 50msec) until data/EOS come
                    if (!mIsEOS) {
                        ++count;
//                    if (++count > 5)
//                        break LOOP;		// out of while
                    }
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    if (DEBUG) Log.v(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
                    // this shoud not come when encoding
                    //encoderOutputBuffers = mMediaCodec.getOutputBuffers();
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (DEBUG) Log.v(TAG, "INFO_OUTPUT_FORMAT_CHANGED");
                    // this status indicate the output format of codec is changed
                    // this should come only once before actual encoded data
                    // but this status never come on Android4.3 or less
                    // and in that case, you should treat when MediaCodec.BUFFER_FLAG_CODEC_CONFIG come.

                    // get output format from codec and pass them to muxer
                    // getOutputFormat should be called after INFO_OUTPUT_FORMAT_CHANGED otherwise crash.
                    final MediaFormat format = mMediaCodec.getOutputFormat(); // API >= 16
                    resetOutputFormat();
                    Log.i(TAG, "start init videocodec");
                } else if (encoderStatus < 0) {
                    // unexpected status
                    //if (DEBUG)
                        Log.w(TAG, "drain:unexpected result from encoder#dequeueOutputBuffer: " + encoderStatus);
                } else {
                    //final ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];

                    //ByteBuffer[] encoderOutputBuffers = mMediaCodec.getOutputBuffers();
                    final ByteBuffer encodedData = mMediaCodec.getOutputBuffer(encoderStatus);

                    if ( encodedData == null ) {
                        // this never should come...may be a MediaCodec internal error
                        throw new RuntimeException("encoderOutputBuffer " + encoderStatus + " was null");
                    }

                    if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        // You shoud set output format to muxer here when you target Android4.3 or less
                        // but MediaCodec#getOutputFormat can not call here(because INFO_OUTPUT_FORMAT_CHANGED don't come yet)
                        // therefor we should expand and prepare output format from buffer data.
                        // This sample is for API>=18(>=Android 4.3), just ignore this flag here
                        if (DEBUG) Log.d(TAG, "drain:BUFFER_FLAG_CODEC_CONFIG");
                        mBufferInfo.size = 0;
                    }

                    if (mBufferInfo.size != 0) {
                        if (mFirstPts < 0) {
                            mFirstPts = mBufferInfo.presentationTimeUs;
                        }
                        // encoded data is ready, clear waiting counter
                        count = 0;
//                    if (!mMuxerStarted) {
//                        // muxer is not ready...this will prrograming failure.
//                        throw new RuntimeException("drain:muxer hasn't started");
//                    }
                        // write encoded data to muxer(need to adjust presentationTimeUs.
//					if (!mRequestPause) {
//                    mBufferInfo.presentationTimeUs = getPTSUs();

                        //muxer.writeSampleData(mTrackIndex, encodedData, mBufferInfo);

                        encodedData.position(mBufferInfo.offset);
                        encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
                        if (mBufferInfo.presentationTimeUs == 0) {
                            Log.d(TAG, " video presentationTimeUs is zero");
                            mBufferInfo.presentationTimeUs = System.nanoTime() / 1000 - mFirstPts;
                        }
                        //mMuxer.writeSampleData(TrackIndex, encodedData, mBufferInfo);
                        //long ts, int flags, ByteBuffer data, int size, int offset


//                    ByteBuffer muxerInput;
//                    muxerInput = ByteBuffer.allocateDirect(mBufferInfo.size);
//                    muxerInput.put(encodedData);
//                    muxerInput.position(0);
//
//                    if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_SYNC_FRAME) != 0) {
////                        packageH264Keyframe(muxerInput, mBufferInfo);
//                    } else {
//
//                    }
                       //  Log.i(TAG," .....encoderStatus 000001.... ");
                        mListener.onFrameAvalailable(encodedData, mBufferInfo.offset, mBufferInfo.size, mBufferInfo.flags, mBufferInfo.presentationTimeUs - mFirstPts);
//                    byte[] bytes = new byte[10];
//                    encodedData.get(bytes);
//                    Log.d(TAG, "writeSampleData " + (mBufferInfo.presentationTimeUs - prevOutputPTSUs) / 1000 + " " + (mBufferInfo.presentationTimeUs-mFirstPts)/1000 + " "
//                                    + mBufferInfo.size + " : " + bytesToHex(bytes, 10)
//                    );

                        prevOutputPTSUs = mBufferInfo.presentationTimeUs;
//					}
                    }
                    // return buffer to encoder
                    mMediaCodec.releaseOutputBuffer(encoderStatus, false);
                    if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        // when EOS come.
                        mIsCapturing = false;
                        break;      // out of while
                    }
                }
        }catch (Exception e) {
            Log.w(TAG, "video encoder met Exception " + e.toString() + " stacktrace "+ e.getStackTrace());
            GpusherService.setInternalExeptionBroadcast();
            return;
        }
        }
    }

    private void resetOutputFormat() {
        /****************** init video *******************/
        MediaFormat videoTrackFormat = mMediaCodec.getOutputFormat();
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

        mListener.initVideoCodec(width, height, combined, sps_nal ,  pps_nal);
    }


    /**
     * previous presentationTimeUs for writing
     */
    private long prevOutputPTSUs = 0;

    private long offsetPTSUs = 0;

    /**
     * get next encoding presentationTimeUs
     *
     * @return
     */
    protected long getPTSUs() {
        long result;
        synchronized (mSync) {
            result = System.nanoTime() / 1000L - offsetPTSUs;
        }
        // presentationTimeUs should be monotonic
        // otherwise muxer fail to write
        if (result < prevOutputPTSUs)
            result = (prevOutputPTSUs - result) + result;
        return result;
    }

    // parameters for recording
    private static final float BPP = 0.16f;

    protected final int mWidth;
    protected final int mHeight;


    protected Surface prepare_surface_encoder(final String mime, final int frame_rate)
            throws IOException, IllegalArgumentException {

        mTrackIndex = -1;
        mIsEOS = false;

        final MediaCodecInfo videoCodecInfo = selectVideoCodec(mime);
        if (videoCodecInfo == null) {
            throw new IllegalArgumentException("Unable to find an appropriate codec for " + mime);
        }
        if (DEBUG) Log.i(TAG, "selected codec: " + videoCodecInfo.getName());

        final MediaFormat format = MediaFormat.createVideoFormat(mime, mWidth, mHeight);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);    // API >= 18
        format.setInteger(MediaFormat.KEY_BIT_RATE, calcBitRate(frame_rate));
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frame_rate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10);   //I 关键帧
        if (DEBUG) Log.i(TAG, "format: " + format);

        mMediaCodec = MediaCodec.createEncoderByType(mime);
        mMediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        //mMediaCodec.configure(format, mMediaCodec.createInputSurface(), null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        // get Surface for encoder input
        // this method only can call between #configure and #start
        return mMediaCodec.createInputSurface();    // API >= 18
    }

    protected int calcBitRate(final int frameRate) {
        final int bitrate = (int) (BPP * frameRate * mWidth * mHeight);
        Log.i(TAG, String.format("try bitrate=%5.2f[Mbps] -> %5.2f[Mbps]", bitrate / 1024f / 1024f, bitrate / 1024f / 1024f * 0.7f));

        return bitrate;
    }

    /**
     * select the first codec that match a specific MIME type
     *
     * @param mimeType
     * @return null if no codec matched
     */
    @SuppressWarnings("deprecation")
    protected static final MediaCodecInfo selectVideoCodec(final String mimeType) {
        if (DEBUG) Log.v(TAG, "selectVideoCodec:");

        // get the list of available codecs
        final int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            final MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);

            if (!codecInfo.isEncoder()) {    // skipp decoder
                continue;
            }
            // select first codec that match a specific MIME type and color format
            final String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    if (DEBUG) Log.i(TAG, "codec:" + codecInfo.getName() + ",MIME=" + types[j]);
                    final int format = selectColorFormat(codecInfo, mimeType);
                    if (format > 0) {
                        return codecInfo;
                    }
                }
            }
        }
        return null;
    }

    /**
     * select color format available on specific codec and we can use.
     *
     * @return 0 if no colorFormat is matched
     */
    protected static final int selectColorFormat(final MediaCodecInfo codecInfo, final String mimeType) {
        if (DEBUG) Log.i(TAG, "selectColorFormat: ");
        int result = 0;
        final MediaCodecInfo.CodecCapabilities caps;
        try {
            Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
            caps = codecInfo.getCapabilitiesForType(mimeType);
        } finally {
            Thread.currentThread().setPriority(Thread.NORM_PRIORITY);
        }
        int colorFormat;
        for (int i = 0; i < caps.colorFormats.length; i++) {
            colorFormat = caps.colorFormats[i];
            if (isRecognizedViewoFormat(colorFormat)) {
                if (result == 0)
                    result = colorFormat;
                break;
            }
        }
        if (result == 0)
            Log.e(TAG, "couldn't find a good color format for " + codecInfo.getName() + " / " + mimeType);
        return result;
    }

    /**
     * color formats that we can use in this class
     */
    protected static int[] recognizedFormats;

    static {
        recognizedFormats = new int[]{
//        	MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
//        	MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
//        	MediaCodecInfo.CodecCapabilities.COLOR_QCOM_FormatYUV420SemiPlanar,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
        };
    }

    protected static final boolean isRecognizedViewoFormat(final int colorFormat) {
        if (DEBUG) Log.i(TAG, "isRecognizedViewoFormat:colorFormat=" + colorFormat);
        final int n = recognizedFormats != null ? recognizedFormats.length : 0;
        for (int i = 0; i < n; i++) {
            if (recognizedFormats[i] == colorFormat) {
                return true;
            }
        }
        return false;
    }

    protected void signalEndOfInputStream() {
        if (DEBUG) Log.d(TAG, "sending EOS to encoder");
        mMediaCodec.signalEndOfInputStream();     // API >= 18
        mIsEOS = true;
    }
}
