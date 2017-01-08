//package com.mediamaster.pushflip;
//
//import android.content.Intent;
//
//import java.nio.ByteBuffer;
//
///**
// * Created by paladin on 16-4-25.
// */
//public class NativeRtmp {
//    private static final String TAG = "pushflip-NativeFfmpegSender";
//
//    //public  static final String BROADCAST_SEND_FAILED = "NativeFfmpegSender::ERR_SEND_FAILED";
//    public static final String BROADCAST_ACTION = "NativeFfmpegSender::BROADCAST_ACTION";
//
//    public static final int BROADCAST_SEND_FAILED = -1;
//
//
//    static {
//
////        System.loadLibrary("avutil-52");
////        System.loadLibrary("avcodec-55");
////        System.loadLibrary("avformat-55");
////
////        System.loadLibrary("swresample-0");
////        System.loadLibrary("swscale-2");
////        System.loadLibrary("avfilter-4");
////        System.loadLibrary("avdevice-55");
//
//        System.loadLibrary("rtmp");
//        System.loadLibrary("ffmpeg_jni");
//
//        nativeClassInit();
//    }
//
//    private static native boolean nativeClassInit(); // Initialize native class: cache Method IDs for callbacks
//
//    public native int nativeInit();
//
//    private native int nativeConnRtmp(String url);
//
//    private native int nativeStart();
//
//    private native void nativeStop();
//
//    //    private native int nativeInitAudio(int channels, int sameple_rate,  byte []extract_data);
////    private native int nativeInitVideo(int width, int height,  byte []extract_data);
//    private native int nativeSendBuffer(int isVideo, long ts, int flags, int size, int offset, ByteBuffer extract_data);
//
//    private final int ERR_SEND_FAILED = -1;
//    private final int ERR_CONNECT_FAILED = -2;
//
//    private final int INFO_CONNECT_FINISH = 2;
//    private final int INFO_RECONNECTTING = 3;
//    private final int INFO_RECONNECTTED = 4;
//
//    public enum send_state {
//        Init,
//        Connecting,
//        Connectted,
//        Disconnected,
//
//    }
//
//    ;
//
//    public send_state getState() {
//        return state;
//    }
//
//    private send_state state;
//
//    //AVOptions mAvOptions;
//    private void onNativeMessage(int type, int arg1, int arg2) {
//        switch (type) {
//            case ERR_SEND_FAILED:
//                state = send_state.Disconnected;
//                Log.i(TAG, "ERR_SEND_FAILED server disconnet need reconnect");
//                Intent intent = new Intent();
//                intent.setAction(BROADCAST_ACTION);
//                intent.putExtra("cmd", BROADCAST_SEND_FAILED);
////                MainActivity.mContext.sendBroadcast(intent);
//
//                break;
//            case ERR_CONNECT_FAILED:
//                state = send_state.Disconnected;
//                Log.i(TAG, "connect to rtmp server failed ");
//                break;
//            case INFO_CONNECT_FINISH:
//                state = send_state.Connectted;
//                Log.i(TAG, "connect to rtmp server OK");
//                break;
//
//            case INFO_RECONNECTTING:
//                Log.i(TAG, "reconnecting to rtmp server");
//                mListener.OnReconnecting();
//                break;
//            case INFO_RECONNECTTED:
//                Log.i(TAG, "reconnected to rtmp server");
//                mListener.OnReconnected();
//                break;
//
//        }
//
//    }
//
//    private static final int ANDROID_LOG_UNKNOWN = 0;
//    private static final int ANDROID_LOG_DEFAULT = 1;   /* only for SetMinPriority() */
//    private static final int ANDROID_LOG_VERBOSE = 2;
//    private static final int ANDROID_LOG_DEBUG = 3;
//    private static final int ANDROID_LOG_INFO = 4;
//    private static final int ANDROID_LOG_WARN = 5;
//    private static final int ANDROID_LOG_ERROR = 6;
//    private static final int ANDROID_LOG_FATAL = 7;
//    private static final int ANDROID_LOG_SILENT = 8;  /* only for SetMinPriority(); must be last */
//
//    //AVOptions mAvOptions;
//    private static void onNativeLog(int level, String tag, String msg) {
//
//        switch (level) {
//            case ANDROID_LOG_VERBOSE:
//                Log.v(tag, msg);
//                break;
//            case ANDROID_LOG_DEBUG:
//                Log.d(tag, msg);
//                break;
//            case ANDROID_LOG_INFO:
//                Log.i(tag, msg);
//                break;
//            case ANDROID_LOG_WARN:
//                Log.w(tag, msg);
//                break;
//            case ANDROID_LOG_ERROR:
//                Log.e(tag, msg);
//                break;
//            case ANDROID_LOG_FATAL:
//                Log.e(tag, msg);
//                break;
//            default:
//                Log.i(tag, msg);
//                break;
//
//
//        }
//    }
//
//    public void init() {
//        nativeInit();
//
//    }
//
//    public boolean connectRtmp(String url) {
//        state = send_state.Connecting;
//        int ret = nativeConnRtmp(url);
//        if (ret != 0) {
//            Log.w(TAG, "nativeConnRtmp return " + ret);
//            return false;
//        }
//        return true;
//    }
//
////    public void initAudio(int channels, int samplerate, byte []extract_data) {
////        nativeInitAudio(channels, samplerate, extract_data);
////    }
////
////
////    public void initVideo(int width, int height, byte []extract_data) {
////        nativeInitVideo(width, height, extract_data);
////    }
//
//
//    public void sendBuffer(int isVideo, long ts, int flags, ByteBuffer data, int size, int offset) {
//        nativeSendBuffer(isVideo, ts, flags, size, offset, data);
//    }
//
//    public void start() {
//        nativeStart();
//    }
//
//    public void stop() {
//        nativeStop();
//    }
//
//    NativeRtmpListener mListener = null;
//
//    public NativeRtmp(NativeRtmpListener listenr) {
//        init();
//        state = send_state.Init;
//        mListener = listenr;
//    }
//
//
//}
