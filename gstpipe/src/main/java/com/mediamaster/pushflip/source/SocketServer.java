package com.mediamaster.pushflip.source;

/**
 * Created by paladin on 16-3-28.
 */

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import com.mediamaster.pushflip.GpusherService;
import com.mediamaster.pushflip.Log;
import com.mediamaster.pushflip.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;


/**
 * Created by paladin on 16-3-28.
 */
public class SocketServer extends Thread {

    // from test4android project
    private static final String TAG = "pushflip-SocketServer";

    public static String SOCKET_ADDRESS = "gamelive.gamelive.screenrecorder.mysocket";//
    private boolean mStopped = false;
    private MyScreenRecord mRecord;

    public static String myscreenrecord_version = "unkonw";

    public SocketServer(MyScreenRecord record) {
        mRecord = record;
    }


    public static int byteArrayToInt(byte[] b, int i) {
        return   b[0 + i] & 0xFF |
                (b[1+ i] & 0xFF) << 8 |
                (b[2+ i] & 0xFF) << 16 |
                (b[3+ i] & 0xFF) << 24;
    }


    public static long byteArrayToLong(byte[] b, int i) {
//        return   b[0 + i] & 0xFF |
//                (b[1+ i] & 0xFF) << 8 |
//                (b[2+ i] & 0xFF) << 16 |
//                (b[3+ i] & 0xFF) << 24 |
//                (b[4+ i] & 0xFF) << 32 |
//                (b[5+ i] & 0xFF) << 40 |
//                (b[6+ i] & 0xFF) << 48 |
//                (b[7+ i] & 0xFF) << 56
//
//                ;
        long r = 0;
        for(int j = 0; j < 8; j++) {
            r <<= 8;
            r |= (b[i+j] &0xFF);
        }
        return r;
    }

//    private static ByteBuffer mybuffer = ByteBuffer.allocate(8);
//    public static long bytesToLong(byte[] bytes, int i) {
//        mybuffer.put(bytes, i, 8);
//        mybuffer.flip();//need flip
//        return mybuffer.getLong();
//    }


//
//#define FrameType_csd0 1
//#define FrameType_csd1 2


//#define FrameType_esds 3
//#define FrameType_video_iframe 8
//#define FrameType_video_frame 9

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String mybytesToHex(byte[] bytes, int len) {
        char[] hexChars = new char[len * 2];
        for ( int j = 0; j < len; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    private boolean checkTag(byte[] b) {
//        int b1 = byteArrayToInt(b, 0);
//        int b2 = byteArrayToInt(b, 4);
        String debug = mybytesToHex(b, 8);
//        if (b[0] == 0x12
//            && b[1] == 0x34
//                && b[2] == 0x56
//                && b[3] == 0x78
//                && b[4] == 0x90
//                && b[5] == 0x09
//                && b[6] == 0x87
//                && b[7] == 0x65              )
        if(debug.equals("1234567890098765"))
            return true;
        Log.w(TAG, "tag " + debug);
        return false;
    }

    private  class MyPacket {
        int body_size;
        long pts;
        int flags;
    }



    private boolean processHead(byte[] buffer, int len, MyPacket p) {
        if (len != 24) {
            return false;
        }
        //Log.i(TAG, "processBuffer " + len + " : " + debug);
        if (!checkTag(buffer)) {
            String debug = utils.bytesToHex(buffer, 32);
            Log.w(TAG, "check tag failed " + debug);
            return false;
        }

        long tag = byteArrayToLong(buffer, 0);
        p.body_size  = byteArrayToInt(buffer, 8);
        p.flags = byteArrayToInt(buffer, 12);
        p.pts = byteArrayToInt(buffer, 16);
        //if (tag != (long)0x1234567890098765)
        if (p.flags == 100) {
            myscreenrecord_version = "20160" + (p.pts);
            Log.i(TAG, "myscreenrecord version " + p.pts);
        }
        p.pts *= 1000;
        return true;
    }

    private void processBody(byte[] buffer,  MyPacket p) {
        if(p.flags == 1) {
            byte[] bytes = new byte[p.body_size];
            System.arraycopy(buffer, 0, bytes, 0, p.body_size);
            // Wrap a byte array into a buffer
            ByteBuffer csd0 = ByteBuffer.wrap(bytes);
            mRecord.setCsd0(csd0);
            Log.i(TAG, "csd0  set ok");
        } else if (p.flags == 2) {
            byte[] bytes = new byte[p.body_size];
            System.arraycopy(buffer, 0, bytes, 0, p.body_size);
            // Wrap a byte array into a buffer
            ByteBuffer csd1 = ByteBuffer.wrap(bytes);
            mRecord.setCsd1(csd1);
            Log.i(TAG, "csd1  set ok");
        } else if ( p.flags == 8 || p.flags == 9) {
            mRecord.queueFrameInfo(p.body_size, p.flags, p.pts, buffer);
        }
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

    @Override
    public void run() {
        Log.i(TAG, "Server socket run . . . start");
        LocalServerSocket server = null;
        final int BUFFER_SIZE = 1024 * 1024 * 2;
        byte[] data = new byte[BUFFER_SIZE];
        int count = 0;
        int body_size = 0;
        MyPacket pack = new MyPacket();
        String tag = "pushflip-msr";

        try {
            server = new LocalServerSocket(SOCKET_ADDRESS);
            while (!mStopped) {
                LocalSocket receiver = server.accept();
                Log.i(TAG, "server.accept ");
                if (receiver != null) {
                    InputStream inputStream = receiver.getInputStream();
                    while(!mStopped) {
                        read_head:
                        // read head
                        count = 0;
                        boolean recv_ok = true;
                        int ret = 0;
                        try {
                            while (!mStopped && count < 24) {
                                if ((24 - count) > data.length || count > 24) {
                                    Log.w(TAG, "wrong count " + count + " data.len " + data.length);
                                    recv_ok = false;
                                    break;
                                }

                                ret = inputStream.read(data, 0, 24 - count);
                                if (ret == -1) {
                                    Log.w(TAG, "eos ret "  + ret + " count " + count);
                                    recv_ok = false;
                                    break;
                                }

                                count += ret;
                                if (count > 24) {
                                    Log.w(TAG, "22 wrong ret "  + ret +"count " + count + " data.len " + data.length);
                                    recv_ok = false;
                                    break;
                                }
                            }
                        }catch (Exception e) {
                            Log.w(TAG, " read head " + e.toString());
                            recv_ok = false;
                        }

                        if (recv_ok == false)
                            break;

                        if (!processHead(data, 24, pack)){
                            Log.w(TAG, "processHead failed");
                            continue;
                        }

                        if (pack.flags == 100) {
                            continue;
                        } else if (pack.flags == 200){
                            count = 0;
                            byte[] data2 = new byte[pack.body_size];
                            while(!mStopped && count < pack.body_size) {
                                count += inputStream.read(data2, 0, pack.body_size-count);
                            }
                            String msg = new String(data2);

                            //Log.i(TAG, "myscreenreccord log " + pack.body_size + " : " + res);

                            switch ((int)pack.pts) {
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

                            continue;
                        }

                        count = 0;
                        while(!mStopped && count < pack.body_size) {
                            try {
                                count += inputStream.read(data, 0, pack.body_size - count);
                            } catch (Exception e) {
                                Log.w(TAG, "locaosocket met Exception " + e.toString() + " count " + count + " " + pack.body_size );
                                GpusherService.setInternalExeptionBroadcast();
                                return;
                            }
                        }
                        processBody(data,  pack);
                    }
                    receiver.close();

                }
            }
        } catch (IOException e) {
            Log.e(getClass().getName(), e.getMessage());
        } finally {
            if (server != null) {
                try {
                    server.close();
                } catch (IOException e) {
                    Log.e(getClass().getName(), e.getMessage());
                }
            }
        }

        Log.i(TAG, "Server socket run . . . end");
    }

    public void stopScreenRecord() {
        Log.i(TAG, "stopScreenRecord");
        mStopped = true;
        try {
            writeSocket("byebye");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeSocket(String message) throws IOException {
        Log.i(TAG, "writeSocket, " + message);
        LocalSocket sender = new LocalSocket();
        sender.connect(new LocalSocketAddress(SOCKET_ADDRESS));
        sender.getOutputStream().write(message.getBytes());
        sender.getOutputStream().close();
        sender.close();
    }



    //    private void proccessData(byte[] buffer, int len) {
//        if (len <= 24)
//            return;
//
//
//        String debug = ScreenRecorder.bytesToHex(buffer, 16);
//        Log.i(TAG, "processBuffer " + len + " : " + debug);
//        if (!checkTag(buffer)) {
//            Log.w(TAG, "check tag failed");
//            return;
//        }
//        long tag = byteArrayToLong(buffer, 0);
//        int size = byteArrayToInt(buffer, 8);
//        int flags = byteArrayToInt(buffer, 12);
//        long pts = byteArrayToInt(buffer, 16);
//        //if (tag != (long)0x1234567890098765)
//        pts *= 1000;
//
//
//        if (len < size + 24 ) {
//            Log.w(TAG, "receive size error size :" + size + " len " + len);
//            return ;
//        }
//
//        Log.i(TAG, "processData size " + size + " flags " + flags +  " pts "+ pts/1000);
//
//        if(flags == 1) {
//            byte[] bytes = new byte[size];
//            System.arraycopy(buffer, 24, bytes, 0, size );
//            // Wrap a byte array into a buffer
//            ByteBuffer csd0 = ByteBuffer.wrap(bytes);
//            mRecord.setCsd0(csd0);
//            Log.i(TAG, "csd0  set ok");
//        } else if (flags == 2) {
//            byte[] bytes = new byte[size];
//            System.arraycopy(buffer, 24, bytes, 0, size );
//            // Wrap a byte array into a buffer
//            ByteBuffer csd1 = ByteBuffer.wrap(bytes);
//            mRecord.setCsd1(csd1);
//            Log.i(TAG, "csd1  set ok");
//        } else if ( flags == 9 || flags == 9) {
//            mRecord.queueFrameInfo(size, flags, pts, buffer);
//        }
//
//        if (len > size + 24 + 24) {
//            int left = len-size-24;
//            byte[] bytes = new byte[left];
//            System.arraycopy(buffer, 24+size, bytes, 0, left );
//            proccessData(bytes, left);
//            bytes = null;
//        }
//    }
}
