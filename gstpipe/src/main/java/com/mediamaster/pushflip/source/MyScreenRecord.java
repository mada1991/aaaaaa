package com.mediamaster.pushflip.source;

import android.os.AsyncTask;
import android.os.Environment;

import com.mediamaster.pushflip.GPusherConfig;
import com.mediamaster.pushflip.Log;
import com.mediamaster.pushflip.source.SocketServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Created by paladin on 16-3-27.
 */
public class MyScreenRecord {
    private static final String TAG = "pushflip-MyScreenRecord";
    private int mWidth;
    private int mHeight;
    private int mBitRate;
    private int mDpi;
    String mMyScreenRecordPath;

    private SocketServer mSocketServer = null;


    public class MyFrameInfo {
        int flags;

        ByteBuffer frameData;
        int frameData_size;
        long presentationTimeUs;
    }

    Queue<MyFrameInfo> mMyFrameInfoQueue = new LinkedList<MyFrameInfo>();
    private long total_size = 0;

    public MyScreenRecord(String myscreenrecord_path, int width, int height, int bitrate, int dpi) {
        mWidth = width;
        mHeight = height;
        mBitRate = bitrate;
        mDpi = dpi;

        mMyScreenRecordPath = myscreenrecord_path;
        Log.i(TAG, "mMyScreenRecordPath " + mMyScreenRecordPath);
    }

    public void stop() {
        Log.i(TAG, "stop");
        if (mSocketServer != null)
            mSocketServer.stopScreenRecord();
        if (sh != null) {
            try {
                sh.destroy();
            }catch (Exception e) {
                Log.i(TAG, "sh.destroy " + e.toString());
            }
        }
    }

    public void prepareScreenRecorder() {
        StringBuilder cmd = new StringBuilder(mMyScreenRecordPath);
        final float BPP = 0.16f;
        final int bitrate = (int) (BPP * 24 * mWidth * mHeight);
        if (mBitRate < bitrate * 0.5) {
            Log.i(TAG, "too small " + mBitRate + " => " + bitrate  );
            mBitRate = (int) (bitrate * 0.5);
        }
        String cfps = GPusherConfig.ControlFrameRate?"1":"0";
        cmd.append( " " + mBitRate + " " + mWidth + " " + mHeight + " " + cfps);

        Log.i(TAG, "gogo " + cmd.toString());


        //mSocketClient = new SocketClient();
        mSocketServer = new SocketServer(this);
        mSocketServer.start();

        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        try {
            new SuTask(cmd.toString().getBytes("ASCII")).execute();

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }


    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    private ByteBuffer mCsd0;
    private ByteBuffer mCsd1;

    public final void setCsd0(ByteBuffer data) {
        mCsd0 = data;
    }

    public final void setCsd1(ByteBuffer data) {
        mCsd1 = data;
    }

    public final ByteBuffer getCsd0() {
        return mCsd0;
    }

    public final ByteBuffer getCsd1() {
        return mCsd1;
    }

    public void waitForReady() {
        int i = 0;
        while (mCsd0 == null || mCsd1 == null) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (i++ % 50 == 0) {
                Log.w(TAG, "waitForReady csd0 csd1");
            }
        }
        Log.i(TAG, "waitForReady OK");
    }

    public static final Object lock1 = new Object();

    public void queueFrameInfo(int size, int flags, long pts, byte[] buffer) {
        MyFrameInfo info = new MyFrameInfo();
        info.frameData_size = size;
        info.flags = flags;
        info.presentationTimeUs = pts;
        // Create a byte array
        byte[] bytes = new byte[size];
        System.arraycopy(buffer, 0, bytes, 0, size);
// Wrap a byte array into a buffer
        info.frameData = ByteBuffer.wrap(bytes);
        if (total_size > 60 * 1024 * 1024) {
            return;
        }
        total_size += info.frameData_size;
        synchronized (lock1) {
            mMyFrameInfoQueue.offer(info);
        }

    }

    public MyFrameInfo readFrame() {
        MyFrameInfo info;
        synchronized (lock1) {
            info = mMyFrameInfoQueue.poll();
        }
        if (info != null)
            total_size -= info.frameData_size;
        return info;
    }

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 53312;
    private BufferedReader in = null;
    private PrintWriter out = null;

    private void processBuffer(byte[] buffer, int len) {
//        String debug = ScreenRecorder.bytesToHex(buffer);
//        Log.i(TAG, "processBuffer " + debug);
    }

    private Process sh = null;

    private class SuTask extends AsyncTask<Boolean, Void, Boolean> {
        private final byte[] mCommand;

        public SuTask(byte[] command) {
            super();
            this.mCommand = command;
        }

        @Override
        protected Boolean doInBackground(Boolean... booleans) {
            try {
                sh = Runtime.getRuntime().exec("su", null, null);

                OutputStream outputStream = sh.getOutputStream();
                Log.i(TAG, "excute " + mCommand.toString());
                outputStream.write(mCommand);
                outputStream.flush();
                outputStream.close();

//                long start = System.currentTimeMillis();
                String path = Environment.getExternalStorageDirectory().toString() + "/recording.mp4";
//                    File file = new File(path);
//                     while(true) {
//
//                         long now = System.currentTimeMillis();
//                         if (now - start > 180 * 1000) {
//                            break;
//                         }
//                         Log.i(TAG , "file length " +  file.length());
//                         Thread.sleep(1000);
//
//                     }
                sh.waitFor();
                Log.i(TAG, "finish excute");
                return true;

            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            return false;
        }

        @Override
        protected void onPostExecute(Boolean bool) {
            super.onPostExecute(bool);
        }


    }
}
