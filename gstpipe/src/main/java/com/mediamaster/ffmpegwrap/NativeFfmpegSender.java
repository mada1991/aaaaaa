package com.mediamaster.ffmpegwrap;


import com.mediamaster.pushflip.Log;
import com.mediamaster.pushflip.NativeRtmpListener;

import java.nio.ByteBuffer;


/**
 * Created by paladin on 15-6-13.
 */
public class NativeFfmpegSender {
    private static final String TAG = "pushflip-NativeFfmpegSender";

    //public  static final String BROADCAST_SEND_FAILED = "NativeFfmpegSender::ERR_SEND_FAILED";
    public  static final String BROADCAST_ACTION = "NativeFfmpegSender::BROADCAST_ACTION";

    public  static final int BROADCAST_SEND_FAILED = -1;


    NativeRtmpListener mListener = null;

    static {


        System.loadLibrary("ffmpeg_jni");

        nativeClassInit();
    }

        private static final int ANDROID_LOG_UNKNOWN = 0;
    private static final int ANDROID_LOG_DEFAULT = 1;   /* only for SetMinPriority() */
    private static final int ANDROID_LOG_VERBOSE = 2;
    private static final int ANDROID_LOG_DEBUG = 3;
    private static final int ANDROID_LOG_INFO = 4;
    private static final int ANDROID_LOG_WARN = 5;
    private static final int ANDROID_LOG_ERROR = 6;
    private static final int ANDROID_LOG_FATAL = 7;
    private static final int ANDROID_LOG_SILENT = 8;  /* only for SetMinPriority(); must be last */

    //AVOptions mAvOptions;
    private static void onNativeLog(int level, String tag, String msg) {

        switch (level) {
            case ANDROID_LOG_VERBOSE:
                Log.v(tag, msg);
                break;
            case ANDROID_LOG_DEBUG:
                Log.d(tag, msg);
                break;
            case ANDROID_LOG_INFO:
                Log.i(tag, msg);
                break;
            case ANDROID_LOG_WARN:
                Log.w(tag, msg);
                break;
            case ANDROID_LOG_ERROR:
                Log.e(tag, msg);
                break;
            case ANDROID_LOG_FATAL:
                Log.e(tag, msg);
                break;
            default:
                Log.i(tag, msg);
                break;


        }
    }
    
    private static native boolean nativeClassInit(); // Initialize native class: cache Method IDs for callbacks
    public native int nativeInit();
    public static native int nativeGetVersion();
    public native int nativeDestroy();
    private native int nativeConnRtmp(String url);
    private native int nativeStart();
    private native void nativeStop();
    private native int nativeInitAudio(int channels, int sameple_rate,  byte []extract_data);
    private native int nativeInitVideo(int width, int height,  byte []extract_data);
    private native int nativeSendBuffer(int isVideo, long ts, int flags, int size, int offset, ByteBuffer extract_data);
    //send native pcm data
    private native byte [] nativeSendPcmData( byte []pcm_data );

    private final int ERR_SEND_FAILED = -1;
    private final int ERR_CONNECT_FAILED = -2;

    private final int INFO_CONNECT_FINISH = 2;
    public enum send_state{
        Init,
        Connecting ,
        Connectted,
        Disconnected,

    };

    public send_state getState() {
        return state;
    }

    private send_state state;

    //AVOptions mAvOptions;
    private void onNativeMessage(int type , int arg1, int arg2) {
        switch(type) {
            case ERR_SEND_FAILED:
                state = send_state.Disconnected;
                Log.i(TAG, "ERR_SEND_FAILED server disconnet need reconnect");
                mListener.onSendFailed();

//                Intent intent=new Intent();
//                intent.setAction(BROADCAST_ACTION);
//                intent.putExtra("cmd",BROADCAST_SEND_FAILED);
//                MainActivity.mContext.sendBroadcast(intent);

                break;
            case ERR_CONNECT_FAILED:
                state = send_state.Disconnected;
                Log.i(TAG, "connect to rtmp server failed ");
                break;
            case INFO_CONNECT_FINISH:
                state = send_state.Connectted;
                Log.i(TAG, "connect to rtmp server OK");
                break;
        }

    }

    public void init(){
        nativeInit();
        state = send_state.Init;
    }
//    public void deinit(){
//        nativeDeinit();
//    }

    public static int getVersion() {
        return nativeGetVersion();
    }

   public void destroy(){
        nativeDestroy();
    }

    public boolean connectRtmp(String url) {
        state = send_state.Connecting;
        int ret =  nativeConnRtmp(url);
        if (ret != 0) {
            Log.w(TAG, "nativeConnRtmp " + ret);
            return false;
        }
        return true;
    }
    public int initAudio(int channels, int samplerate, byte []extract_data) {
        return nativeInitAudio(channels, samplerate, extract_data);
    }


    public int initVideo(int width, int height, byte []extract_data) {
        return nativeInitVideo(width, height, extract_data);
    }


    public  int sendBuffer(int isVideo, long ts, int flags, ByteBuffer data, int size, int offset) {
        return nativeSendBuffer(isVideo, ts, flags, size, offset, data);
    }


    //send native pcm data
    public  byte [] sendPcmData( byte []pcm_data ) {
        return nativeSendPcmData( pcm_data );
    }


    public void start() {
        nativeStart();
    }

//    public void stop() {
//        nativeStop();
//    }


    public NativeFfmpegSender(NativeRtmpListener listenr) {
//        init();
        mListener = listenr;
    }

    /*
    public void setAVOptions(AVOptions jOpts) {
        mAvOptions = jOpts;
        //initAudio(jOpts.numAudioChannels, jOpts.audioSampleRate, 0);
        //initVideo( jOpts.extract_data);
    }
    */
    /*
    public void prepareAVFormatContext(String jOutputPath) {
        //start();
    }
    */
//
//    public void writeAVPacketFromEncodedData(ByteBuffer jData, int jIsVideo, int jOffset, int jSize, int jFlags, long jPts) {
//       // if(jIsVideo != 0)
//            sendBuffer(jIsVideo, jPts, jFlags, jData, jSize, jOffset);
//    }

    /*
    public void finalizeAVFormatContext() {
        stop();
    }
    */

    /**
     * Used to configure the muxer's options.
     * Note the name of this class's fields
     * have to be hardcoded in the native method
     * for retrieval.
     * @author davidbrodsky
     *
     */
    /*
    static public class AVOptions{
        //byte []extract_data;
        public int videoWidth = 720;
        public int videoHeight = 480;

        public int audioSampleRate = 44100;
        public int numAudioChannels = 1;

        // Format specific options
        public int hlsSegmentDurationSec = 10;

        public String outputFormatName = "hls";
        // TODO: Provide a Map for format-specific options
    }
    */
}
