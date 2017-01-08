package com.mediamaster.pushapi;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.net.ConnectivityManager;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import com.mediamaster.androidtranscoder.MediaTranscoder;
import com.mediamaster.androidtranscoder.format.MediaFormatStrategyPresets;
import com.mediamaster.ffmpegwrap.NativeFfmpegSender;
import com.mediamaster.pushflip.GPusherConfig;
import com.mediamaster.pushflip.GpusherService;
import com.mediamaster.pushflip.Log;
import com.mediamaster.pushflip.PushEvent;
import com.mediamaster.pushflip.PusherEventListener;
import com.mediamaster.pushflip.ftp.ZipUtils;
import com.mediamaster.pushflip.ftp.ftppush;
import com.mediamaster.pushflip.source.SocketServer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.Permission;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.Future;

import android.os.Process;

/**
 * Created by paladin on 16-5-7.
 */
public class Pusher {

    private static final String TAG = "pushflip-Pusher";

    private Context mContext;
    private PusherEventListener mListener;

    private boolean mInit = false;
    private String logDir = null;
    private String fileDir = null;
    private String privateDir = null;
    private final int state_init = 0;
    private final int state_preparing = 1;
    private final int state_prepared = 2;
    private final int state_starting = 3;
    private final int state_started = 4;
    private final int state_stoping = 5;
    private final int state_stoped = 6;
    private int mystate ;
    
    private static String stat_rtmp_url;
    private static Context stat_Context;
    private static String room_id;

    static {
//        Intent intent = new Intent(mContext, GpusherService.class);
////        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//        intent.setAction("INIT");
//        mContext.startService(intent);
    }

    public enum VideoOrientation {
        horizontal,
        portrait
    }
    public Pusher() {
        mystate = state_init;
    }

    public static String getVersion() {
        return GPusherConfig.version;
    }

    public static String getNativeVersion() {
        int v = NativeFfmpegSender.getVersion();
        return "201" + v;
    }

    public static String getMyscreenrecordVersion() {
        return SocketServer.myscreenrecord_version;
    }

    public void regListener(PusherEventListener listener) {
        mListener = listener;
    }

//    public boolean prepare(Context appContext, String url,  int width, int height, int bitrate, MediaProjection mediaProjection) {
//        return prepare(appContext,url,null,width,height,bitrate,mediaProjection);
//    }

    /***
     * 帧率控制开关，默认不需要调用，但是在某些特殊的4.4机器上，需要关闭帧率控制才能够正常直播
     * 需要在start之前调用
     * @param ControlFrameRate 是否开启帧率控制
     */
    public void setControlFrameRate(boolean ControlFrameRate) {
        GPusherConfig.ControlFrameRate = ControlFrameRate;
    }
    public boolean prepare(Context appContext, String url, String roomId, String privateName,int width, int height, int bitrate, int dpi,MediaProjection mediaProjection) {
        if (mystate != state_init ) {
            Log.i(TAG, "illeage state");
            return false;
        }
        mystate = state_preparing;

        mContext = appContext;
        stat_Context = appContext;
        GpusherService.rtmp_uri = url;
        GpusherService.width = width;
        GpusherService.height = height;
        GpusherService.mBitrate = bitrate;
        GpusherService.mediaProjection = mediaProjection;
        GpusherService.mDpi = dpi;
        if ( mContext.getFilesDir() == null) {
            fileDir = mContext.getApplicationContext().getFilesDir().toString();
        } else {
            fileDir = mContext.getFilesDir().getAbsolutePath();
        }
        logDir = fileDir + "/pushflip";
        privateDir = fileDir + "/privateModeData";
        File f = new File(privateDir);
        if(!f.exists()) {
            f.mkdir();
        } else if (!f.isDirectory()){
            f.delete();
            f.mkdir();
        }

        Log.i(TAG, "prepare logdir :  " + logDir);
        /*******************************************/
        String phonenum = "";
        String deivceId = "";
        try {
            PhoneInfo siminfo = new PhoneInfo(mContext);

            phonenum = siminfo.getNativePhoneNumber();
            if (siminfo.getNativePhoneNumber() != null && siminfo.getNativePhoneNumber().length() > 5) {
                phonenum = phonenum.substring(3, siminfo.getNativePhoneNumber().length() - 1);
            } else {
                phonenum = "_";
            }
            deivceId = siminfo.getDeviceId();
        } catch (Exception e) {
            e.printStackTrace();
        }

        String uid = roomId;
        room_id = roomId;
//        uid += deivceId;
//        uid += "_" +phonenum;
        uid +=  "_" + Build.VERSION.SDK_INT;
        uid +=  "_" + Build.BRAND;

        //uid +=  "_" + "ceshi"; //自己加
        uid +=  "_"; //自己加

        if (Build.HARDWARE.equals("qcom")) {
            uid += "_" + Build.BOARD;
        } else {
            uid +=  "_" + Build.HARDWARE;
        }

        Log.setDir(logDir, uid);
        Log.UploadHistoryLog(logDir, false, "start2");

        Log.i(TAG, "pushflip version " + GPusherConfig.version + " jni version " + getNativeVersion());
        try {
            Utils.printSysInfo(mContext);
        }catch (Exception e) {
            e.printStackTrace();
        }
        Log.i(TAG, "prepare getFilesDir " + fileDir);

        Log.i(TAG, "init " + width + "X" + height + " " + bitrate  + " "+ url);

        /*******************************************/
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            Log.i(TAG, "Android版本太低，不支持此版本");
            return false;
        } else  if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
//            try {
//                writeSocket();
//            }catch(Exception e) {
//                Log.i(TAG, "writeSocket " + e.toString());
//            }

            SimpleDateFormat formatter = new SimpleDateFormat("MMdd_HHmmss_SSS");
            Date curDate = new Date(System.currentTimeMillis());   //获取当前时间
            String str = formatter.format(curDate);

            String out_screenFileName = screenFileName + "_" + str;

            if (copyBinaryFromAssetsToData(out_screenFileName) == false) {
                Log.w(TAG, "copyBinaryFromAssetsToData failed");
                return false;
            }
            // TODO: check root permission

            GPusherConfig.myscreenrecord_path = fileDir +"/"+out_screenFileName;
            Log.i(TAG, "copyBinaryFromAssetsToData ok path " + GPusherConfig.myscreenrecord_path);
        } else if (mediaProjection == null) {
            Log.w(TAG, "mediaproject is null");
            return false;
        }

        PrivateDataMaker pm = new PrivateDataMaker(mContext);
        if (privateName == null) {
            privateName = PrivateDataMaker.SystemDefaultPrivateData;
        } else {
           if (!pm.isExistPrivateName(privateName)) {
               privateName = PrivateDataMaker.SystemDefaultPrivateData;
           }
        }


        if (!pm.isExistPrivateName(privateName)) {
            pm.copyPrivateBinaryFromAssetsToData();
        }

        if (!pm.isExistPrivateGenData(privateName,width, height)) {
            pm.genPrivateDataAsync(PrivateDataMaker.SystemDefaultPrivateData,width, height);
        }
        GPusherConfig.privatePath = pm.getPrivateGenDataPath(privateName,width, height);

//        Intent intent = new Intent(mContext, GpusherService.class);
//        intent.setAction("INIT");
//        mContext.startService(intent);

        mInit = true;
        mystate = state_prepared;
        return true;
    }


    /**
     * 检测网络是否连接
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
            if(wifi == NetworkInfo.State.CONNECTED || gprs == NetworkInfo.State.CONNECTED )
                return true;
        }

        return false;
    }


    private boolean checkAudioRecord() {
        try {
            final int buf_sz = AudioRecord.getMinBufferSize(
                    44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            final AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, buf_sz);
            boolean hasPermission = mContext.checkPermission(
                    android.Manifest.permission.RECORD_AUDIO,
                    Process.myPid(),
                    Process.myUid()) == PackageManager.PERMISSION_GRANTED;
            if (!hasPermission) {
                Log.i(TAG, "checkAudioRecord  : not android.Manifest.permission.RECORD_AUDIO");
                return false;
            }

            audioRecord.startRecording();
            final byte[] buf = new byte[buf_sz];
            int readBytes;
            readBytes = audioRecord.read(buf, 0, buf_sz);
            if (AudioRecord.ERROR_INVALID_OPERATION == readBytes) {
                //录音可能被禁用了，做出适当的提示
                Log.i(TAG, "checkAudioRecord ERROR_INVALID_OPERATION : ");
                audioRecord.stop();
                return false;
            }
            Log.i(TAG, "checkAudiooRead ok " + readBytes);
            audioRecord.stop();
            return true;

        }catch (Exception e) {
            Log.i(TAG, "checkAudioRecord failed : " + e.toString());
            return false;
        }
    }


    private boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) mContext.getSystemService(mContext.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            Log.i(TAG, "running " + service.service.getClassName());
//            if ("com.example.MyService".equals(service.service.getClassName())) {
//                return true;
//            }
        }
        return false;
    }

    private ServiceConnection conn = null;

    public boolean start() {
        Log.i(TAG, "start");
        if (mystate != state_prepared ) {
            Log.i(TAG, "illeage state");
            return false;
        }
        mystate = state_starting;
////        GpusherService.mediaProjection = MPActivity.mediaProjection;
//        Intent intent = new Intent(mContext, GpusherService.class);
////        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//        intent.setAction("START");
//
//
//        mContext.startService(intent);
        if (!mInit) {
            Log.w(TAG, "not init");
            return false;
        }try {
        if (!checkNetworkState()) {
            Log.w(TAG, "checkNetworkState failed");
        } else {
            Log.i(TAG, "checkNetworkState ok");
        }
    } catch (Exception e) {
        Log.w(TAG, "checkNetworkState failed Exception " + e.toString());
    }
        if (!checkAudioRecord()) {
            Log.w(TAG, "checkAudioRecord failed");
            mListener.OnNofityEvent(PushEvent.error_audiorecord_failed);
            return false;
        } else {
            Log.i(TAG, "checkAudioRecord ok");
        }
//        isServiceRunning();
        //单击了bindService按钮
        if(!isBound){
            Intent intent = new Intent();
            intent.setAction(SERVICE_ACTION);
            intent.addCategory(Intent.CATEGORY_DEFAULT);

            PackageManager pm = mContext.getPackageManager();
            //我们先通过一个隐式的Intent获取可能会被启动的Service的信息
            ResolveInfo info = pm.resolveService(intent, 0);

            if(info != null) {
                //如果ResolveInfo不为空，说明我们能通过上面隐式的Intent找到对应的Service
                //我们可以获取将要启动的Service的package信息以及类型
                String packageName = info.serviceInfo.packageName;
                String serviceNmae = info.serviceInfo.name;
                //然后我们需要将根据得到的Service的包名和类名，构建一个ComponentName
                //从而设置intent要启动的具体的组件信息，这样intent就从隐式变成了一个显式的intent
                //之所以大费周折将其从隐式转换为显式intent，是因为从Android 5.0 Lollipop开始，
                //Android不再支持通过通过隐式的intent启动Service，只能通过显式intent的方式启动Service
                //在Android 5.0 Lollipop之前的版本倒是可以通过隐式intent启动Service

//                String packageName = "mediamaster.com.gpusher";
//                String serviceNmae = "com.mediamaster.pushflip.GpusherService";

                Log.i(TAG, " packageName " + packageName + " serviceNmae " + serviceNmae);
                ComponentName componentName = new ComponentName(packageName, serviceNmae);
                intent.setComponent(componentName);
                conn = new MyServiceConnection();
                try{
                    Log.i(TAG, "client call bindService " + componentName);
                    intent = new Intent(mContext, com.mediamaster.pushflip.GpusherService.class);
                    mContext.bindService(intent, conn, Context.BIND_AUTO_CREATE);
                } catch(Exception e){
                    e.printStackTrace();
                    Log.e(TAG, e.getMessage());
                }
            }
        }

        mystate = state_started;
        return true;
    }

    private boolean wait_stoping = false;
    public boolean stop() {
        Log.i(TAG, "user call stop");
        if (mystate != state_started ) {
            Log.i(TAG, "illeage state");
            if (mystate >= state_prepared) {
                Log.UploadHistoryLog(logDir, true, "end");
            }
            return false;
        }

        mystate = state_stoping;

        if (wait_stoping) {
            Log.w(TAG, "wait_stoping stop again");
            return false;
        }
        wait_stoping = true;

//        Intent intent = new Intent();
//        intent.setAction(SERVICE_ACTION);
//        intent.addCategory(Intent.CATEGORY_DEFAULT);
//        String packageName = "mediamaster.com.gpusher";
//        String serviceNmae = "com.mediamaster.pushflip.GpusherService";
//        //然后我们需要将根据得到的Service的包名和类名，构建一个ComponentName
//        //从而设置intent要启动的具体的组件信息，这样intent就从隐式变成了一个显式的intent
//        //之所以大费周折将其从隐式转换为显式intent，是因为从Android 5.0 Lollipop开始，
//        //Android不再支持通过通过隐式的intent启动Service，只能通过显式intent的方式启动Service
//        //在Android 5.0 Lollipop之前的版本倒是可以通过隐式intent启动Service
//        ComponentName componentName = new ComponentName(packageName, serviceNmae);
//        intent.setComponent(componentName);
        if (conn != null) {
            try {
                wait_stoping = true;
                Message msg = Message.obtain();
                msg.what = GpusherService.MSG_CLIENT_STOP;

                Bundle data = new Bundle();
                data.putString("msg", "Bye，sendService");
                msg.setData(data);
                sendService(msg);

                while(wait_stoping) {
                    Log.i(TAG, "wait stop");
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                }
                if (isBound && mContext != null && conn != null)
                    mContext.unbindService(conn);
                else {
                    Log.i(TAG, "can not unbindService " + isBound + " " + mContext + " " + conn);
                }
            } catch (Exception e) {
                Log.i(TAG, "unbindService error " + e.toString());
            }
        }
        Log.i(TAG, "stop ok");
        Log.UploadHistoryLog(logDir, true, "end");

//        sendService(GpusherService.MSG_CLIENT_STOP);
//        while(wait_stoping) {
//            try {
//                Thread.sleep(20);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//            Log.i(TAG, "wait_stoping");
//        }

        mystate = state_stoped;

        return true;
    }

    //同步
    public boolean syn_change_ratio(final String s_url, final int width, final int heigth, final int bitrate) {
        Log.i(TAG, "Enter change ratio......" + s_url);
        if (false == thread_change_ratio(s_url,width,heigth,bitrate)){
            return false;
        }
        return true;
    }

    //统计测试
    public void info_log_count() {
        Log.i(TAG, "Enter change ratio");
        new Thread() {
            public void run() {
                try {
                    Thread.sleep(30000); // 线程暂停10秒，单位毫秒
                    Utils.printSysInfo(mContext);
                }catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    //异步
    public int flag = 0;   //状态值
    public int asny_change_ratio(final String s_url, final int width, final int heigth, final int bitrate) {
        Log.i(TAG, "Enter change ratio");
        new Thread() {
            public void run() {
                if (false == thread_change_ratio(s_url,width,heigth,bitrate))
                    flag = -1;
            }
        }.start();
        return flag;
    }

    public boolean thread_change_ratio(String url, int width, int heigth, int bitrate) {
        Log.i(TAG, "Enter change ratio");
        if(!this.stop()) {
            Log.i(TAG, "Server Stop failed not change ratio");
            return false;
        } else {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            this.mystate = 0;
            this.isBound = false;
            if(!this.conStart_Server(url, width, heigth, bitrate)) {
                Log.i(TAG, "Server Link failed not change rate ratio");
                return false;
            } else {
                return true;
            }
        }
    }

    public boolean conStart_Server(String rtmp_url, int width, int heigth, int bitrate) {
        Log.i(TAG, "enter conStart_Server");
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            Log.i(TAG,"Android版本太低，不支持此版本");
            return false;
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {}

        if(!this.prepare(stat_Context, rtmp_url, room_id, "SystemDefaultPrivateData", width, heigth, bitrate,GpusherService.mDpi,GpusherService.mediaProjection)) {
                Log.i("pushflip-Pusher", "链接prepare失败，请检查网络");
                return false;
        } else {
                this.start();
                return true;
            }
    }

    public void reconnect(String url) {
        if (url == null) {
            Log.w(TAG, "reconnect url is null");
            return;
        }
        Log.i(TAG, "reconnect " + url);
        Message msg = Message.obtain();
        msg.what = GpusherService.MSG_CLIENT_RECONNECT;

        Bundle data = new Bundle();
        data.putString("url", url);
        msg.setData(data);
        sendService(msg);
    }

//    public void genPrivateModeData(String name, String path, int width, int height) {
//        PrivateGenThread p = new PrivateGenThread(name, path, privateDir, width,height );
//        p.start();
//    }

    //设置隐私模式
    public void setPrivateMode(boolean p) {
        Log.i(TAG, "setPrivateMode " + p);
        Message msg = Message.obtain();
        msg.what = GpusherService.MSG_CLIENT_PRIVATEMODE;

        Bundle data = new Bundle();
        data.putBoolean("mode", p);
        msg.setData(data);
        sendService(msg);
    }
    
    private void sendService(int what) {
        Message msg = Message.obtain();
        msg.what = what;
        Bundle data = new Bundle();
        data.putString("msg", "Hello，sendService");
        msg.setData(data);
        sendService(msg);
    }

    private void sendService(Message msg) {
        if (serviceMessenger == null) {
            Log.w(TAG, "sendService serviceMessenger is null");
            return;
        }
        try {
            Log.i(TAG, "client send to server " + serviceMessenger + " msg : " + msg.what);
            serviceMessenger.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
            Log.i(TAG, "client send to service failed: " + e.getMessage());
        }
    }

    private boolean isBound = false;

    //用于启动MyService的Intent对应的action
    private final String SERVICE_ACTION = "com.mediamaster.pushflip.action.GPUSHSERSERVICE";

    //serviceMessenger表示的是Service端的Messenger，其内部指向了MyService的ServiceHandler实例
    //可以用serviceMessenger向MyService发送消息
    private Messenger serviceMessenger = null;

    //clientMessenger是客户端自身的Messenger，内部指向了ClientHandler的实例
    //MyService可以通过Message的replyTo得到clientMessenger，从而MyService可以向客户端发送消息，
    //并由ClientHandler接收并处理来自于Service的消息
    private Messenger clientMessenger = new Messenger(new ClientHandler());

    //客户端用ClientHandler接收并处理来自于Service的消息
    private class ClientHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            Log.i(TAG, "client receive " + msg.what);
            switch(msg.what) {
                case PushEvent.info_started:
                    break;
                case PushEvent.info_reconnecting:
                    break;
                case PushEvent.info_reconnected:
                    break;
                case PushEvent.info_stoped:
                    wait_stoping =false;
                    break;
            }
            if(mListener != null) {
                mListener.OnNofityEvent(msg.what);
            }
//            if(msg.what == ){
//                Bundle data = msg.getData();
//                if(data != null){
//                    String str = data.getString("msg");
//                    Log.i(TAG, "客户端收到Service的消息: " + str);
//                }
//            }
        }
    }


    public class MyServiceConnection implements   ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            //客户端与Service建立连接
            Log.i(TAG, "client onServiceConnected");

            //我们可以通过从Service的onBind方法中返回的IBinder初始化一个指向Service端的Messenger
            serviceMessenger = new Messenger(binder);
            isBound = true;

            Message msg = Message.obtain();
            msg.what = GpusherService.MSG_CLIENT_START;

            //此处跨进程Message通信不能将msg.obj设置为non-Parcelable的对象，应该使用Bundle
            //msg.obj = "你好，MyService，我是客户端";
            Bundle data = new Bundle();
            data.putString("msg", "Hello，MyService，I am Client");
            msg.setData(data);

            //需要将Message的replyTo设置为客户端的clientMessenger，
            //以便Service可以通过它向客户端发送消息
            msg.replyTo = clientMessenger;
            Log.i(TAG, "start sendService");
            sendService(msg);
//            try {
//                Log.i(TAG, "Cliend send to service");
//                serviceMessenger.send(msg);
//            } catch (RemoteException e) {
//                e.printStackTrace();
//                Log.i(TAG, "Client send to service failed: " + e.getMessage());
//            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            //客户端与Service失去连接
            serviceMessenger = null;
            isBound = false;
            Log.i(TAG, "Client onServiceDisconnected");

        }
    };

    public static void uploadLog() {
        Log.uploadLog();
    }

//    private void writeSocket() throws IOException {
//        String message = "byebye";
//        Log.i(TAG, "writeSocket, " + message);
//        LocalSocket sender = new LocalSocket();
//        sender.connect(new LocalSocketAddress("gamelive.gamelive.screenrecorder.mysocket"));
//        sender.getOutputStream().write(message.getBytes());
//        sender.getOutputStream().close();
//        sender.close();
//    }

    //public static final String screenFileName = "msr";
    public static final String screenFileName = "myscreenrecord";

    private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;
    private static final int EOF = -1;

    public  boolean copyBinaryFromAssetsToData(String out_screenFileName) {

        // create files directory under /data/data/package name
        File filesDirectory = new File(fileDir);

        InputStream is;
        try {
            is = mContext.getAssets().open(screenFileName);
            // copy ffmpeg file from assets to files dir
            Log.i(TAG, "copyBinaryFromAssetsToData fileNameFromAssets " + screenFileName + " => " + filesDirectory + " filename " + out_screenFileName);
            File myFile = new File(filesDirectory, out_screenFileName);
//            if (myFile.exists()) {
//                return true;
//            }

            final FileOutputStream os = new FileOutputStream(myFile);
            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];

            int n;
            while(EOF != (n = is.read(buffer))) {
                os.write(buffer, 0, n);
            }

            os.close();
            is.close();

            File ffmpegFile = new File(filesDirectory, out_screenFileName);

            if(!ffmpegFile.canExecute()) {
                Log.i(TAG, "FFmpeg File is not executable, trying to make it executable ...");
                if (ffmpegFile.setExecutable(true)) {
                    return true;
                } else {
                    Log.w(TAG, "make excute failed");
                }
            } else {
                Log.i(TAG, "FFmpeg file is executable");
                return true;
            }
        } catch (IOException e) {
            Log.e(TAG, "issue in coping binary from assets to data. ", e);
        }
        return false;
    }


}
