package com.mediamaster.pushflip;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.projection.MediaProjection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemClock;

import com.mediamaster.androidtranscoder.MediaTranscoder;
import com.mediamaster.androidtranscoder.format.MediaFormatStrategyPresets;

import java.io.IOException;
import java.util.concurrent.Future;

/**
 * Created by paladin on 16-4-21.
 */
public class GpusherService extends Service {
//    public static RemoteBinder remoteBinder;
    public static String TAG = "pushflip-GpusherService";

    RtmpSender mRtmpSender = null;
    public static MediaProjection mediaProjection;
//    public static Context appContext;
    public static String rtmp_uri;
    public static int width;
    public static int height;
    public static int mBitrate;
    public static int mDpi;


    //    VideoOrientation orientation;
    private static final String ExeptionFilterStr = "com.GpusherService.ExeptionFilterStr";
    private static Context mContext = null;
    public static void setInternalExeptionBroadcast() {
        if (mContext == null)
            return;
        Intent i = new Intent(ExeptionFilterStr);
        mContext.sendBroadcast(i);
    }


    /**
     * 定义广播接收器（内部类）
     *
     * @author lenovo
     *
     */
    private class UpdateUIBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.w(TAG, "cmdReceiver ");
            if (intent.getAction().equals(ExeptionFilterStr)) {
                if (mPushListener == null)
                    return;
                mPushListener.OnNofityEvent(PushEvent.error_internal_expection);
            }
        }

    }

    private UpdateUIBroadcastReceiver cmdReceiver = new UpdateUIBroadcastReceiver();



//
    @Override
    public int onStartCommand( Intent intent, int flags, int startId )
    {
        Log.i(TAG, "onStartCommand");

//        if (intent == null)
//            return START_STICKY;
//
//        if ( intent.getAction() == "INIT") {
//            Log.i(TAG, "INIT");
////            Intent airplane = new Intent(this, MPActivity.class);
////            airplane.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
////            startActivity(airplane);
//        } else if (intent.getAction().equals("START")) {
//            Log.i(TAG, "START");
//            if (mediaProjection == null) {
//                Log.i(TAG, "mediaProjection is null");
//                return START_STICKY;
//            }
////            while(MPActivity.mediaProjection == null) {
////                Log.i(TAG, "mediaProjection is null");
////                try {
////                    Thread.sleep(1000);
////                } catch (InterruptedException e) {
////                    e.printStackTrace();
////                }
//////                if(MPActivity.mediaProjection == null) {
//////                    Log.i(TAG, "mediaProjection is null");
//////                    return false;
//////                }
////
////
////            }
//////            mediaProjection = MPActivity.mediaProjection;
//            startServer();
//
//        } else  if ( intent.getAction() == "STOP") {
//            Log.i(TAG, "STOP");
//            //dispose();
//        }else  if ( intent.getAction() == "DESTROY") {
//            Log.i(TAG, "DESTROY");
//            dispose();
//            return START_NOT_STICKY;
//        }
//
        return START_STICKY;
    }

    private class MyPushListener extends  PusherEventListener {
        GpusherService mService = null;
        public MyPushListener(GpusherService s) {
            mService = s;
        }
        @Override
        public void OnNofityEvent(int event) {
            if (mService != null) {
                Log.i(TAG, "OnNofityEvent " + event);
                mService.replayClient(event);
            }
//            switch(event) {
//
//            }
//            replayClient.
        }
    }

    private static MyPushListener mPushListener ;
    private void startServer() {
        //开启线程
        new Thread() {
            public void run() {

//                String rtmp_uri =  "rtmp://publish.huizu100.com/g_live/songguangyu?key=d2b755c06e892172";
////                String rtmp_uri =  "rtmp://123.59.63.4/g_live/songguangyu?key=d2b755c06e892172";
//
//                WindowManager windowManager = (WindowManager) getSystemService("window");
//                Point point = new Point();
//                windowManager.getDefaultDisplay().getRealSize(point);
//                float f = ((float) point.y) / ((float) point.x);
//                int width, height;
//                int mBitrate = 500000;
//                if (mBitrate == 1000000) {
//                    height = 480;
//                } else if (mBitrate == 2000000) {
//                    height = 720;
//                } else if (mBitrate == 500000) {
//                    height = 360;
//                } else
//                    height = 360;
//
//                width = (int) (f * ((float) height));
//                if (width % 4 != 0) {
//                    width -= width % 4;
//                }
                try {
                    mRtmpSender = new RtmpSender(GpusherService.this);
                    mPushListener = new MyPushListener(GpusherService.this);
                    mRtmpSender.regListener(mPushListener);

                    if (!mRtmpSender.connectServer(rtmp_uri)) {
                        Log.w(TAG, "connectServer failed " + rtmp_uri);
                        mPushListener.OnNofityEvent(PushEvent.error_connect_server);
                        return;
                    }

                    GPusherConfig cfg = new GPusherConfig();

                    cfg.width = width;
                    cfg.height = height;
                    cfg.bitrate = mBitrate;
                    cfg.dpi = mDpi;
                    cfg.mediaProjection = mediaProjection;
                    mRtmpSender.prepare(cfg);
                    mRtmpSender.start();
                    mPushListener.OnNofityEvent(PushEvent.info_started);

                } catch ( Exception e) {
                    Log.w(TAG, "audio encoder met Exception " + e.toString());
                    mPushListener.OnNofityEvent(PushEvent.error_internal_expection);
                    return;
                }
            }
        }.start();
    }
//    private void dispose() {
//        if (mRtmpSender != null) {
//            mRtmpSender.stop();
//        }
//    }


//    /** 向Service发送Message的Messenger对象 */
//    Messenger mService = null;
//
//    /** 判断有没有绑定Service */
//    boolean mBound;
//
//    private ServiceConnection mConnection = new ServiceConnection() {
//        public void onServiceConnected(ComponentName className, IBinder service) {
//            // Activity已经绑定了Service
//            // 通过参数service来创建Messenger对象，这个对象可以向Service发送Message，与Service进行通信
//            mService = new Messenger(service);
//            mBound = true;
//        }
//
//        public void onServiceDisconnected(ComponentName className) {
//            mService = null;
//            mBound = false;
//        }
//    };

//    public void sayHello(View v) {
//        if (!mBound) return;
//        // 向Service发送一个Message
//        Message msg = Message.obtain(null, MessengerService.MSG_SAY_HELLO, 0, 0);
//        try {
//            mService.send(msg);
//        } catch (RemoteException e) {
//            e.printStackTrace();
//        }
//    }
//
//
//    @Override
//    protected void onStart() {
//        super.onStart();
//        // 绑定Service
//        bindService(new Intent(this, MessengerService.class), mConnection,
//                Context.BIND_AUTO_CREATE);
//    }

//    @Override
//    protected void onStop() {
//        super.onStop();
//        // 解绑
//        if (mBound) {
//            unbindService(mConnection);
//            mBound = false;
//        }
//    }



    public static final int MSG_CLIENT_INIT = 100;
    public static final int MSG_CLIENT_START = 101;
    public static final int MSG_CLIENT_STOP = 102;
    public static final int MSG_CLIENT_RECONNECT = 103;
    public static final int MSG_CLIENT_PRIVATEMODE = 104;

    //clientMessenger表示的是客户端的Messenger，可以通过来自于客户端的Message的replyTo属性获得，
    //其内部指向了客户端的ClientHandler实例，可以用clientMessenger向客户端发送消息
    private Messenger clientMessenger = null;

    //serviceMessenger是Service自身的Messenger，其内部指向了ServiceHandler的实例
    //客户端可以通过IBinder构建Service端的Messenger，从而向Service发送消息，
    //并由ServiceHandler接收并处理来自于客户端的消息
    private Messenger serviceMessenger = new Messenger(new ServiceHandler());

    //MyService用ServiceHandler接收并处理来自于客户端的消息
    private class ServiceHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            Bundle data = msg.getData();
            if (data != null) {
                String str = data.getString("msg");
                Log.i(TAG, "GpusherService rec: " + msg.what + " msg " + str);
            } else {
                Log.i(TAG, "GpusherService rec: " + msg.what);
            }

            switch (msg.what) {
                case MSG_CLIENT_INIT:

                    break;

                case MSG_CLIENT_START:
                    clientMessenger = msg.replyTo;
                    if (mediaProjection == null) {
                        Log.i(TAG, "mediaProjection is null");

                    }
                    //            while(MPActivity.mediaProjection == null) {
                    //                Log.i(TAG, "mediaProjection is null");
                    //                try {
                    //                    Thread.sleep(1000);
                    //                } catch (InterruptedException e) {
                    //                    e.printStackTrace();
                    //                }
                    ////                if(MPActivity.mediaProjection == null) {
                    ////                    Log.i(TAG, "mediaProjection is null");
                    ////                    return false;
                    ////                }
                    //
                    //
                    //            }
                    ////            mediaProjection = MPActivity.mediaProjection;
                    //开启线程 进行 编码 推流
                    startServer();
                    break;
                case MSG_CLIENT_RECONNECT:
                    String url = data.getString("url");
                    if (mRtmpSender == null) {
                        Log.w(TAG, "MSG_CLIENT_RECONNECT mRtmpSender is null");
                    }
                    mRtmpSender.reconnect(url);
                    break;
                case MSG_CLIENT_PRIVATEMODE:
                    boolean mode = data.getBoolean("mode");
                    if (mRtmpSender == null) {
                        Log.w(TAG, "MSG_CLIENT_PRIVATEMODE mRtmpSender is null");
                    }
                    mRtmpSender.setPrivateMode(mode);
                    break;
                case MSG_CLIENT_STOP:
//                    if (cmdReceiver != null){
//                        unregisterReceiver(cmdReceiver);
//                    }
                    if (mRtmpSender != null) {
                        mRtmpSender.stop();
                        //mRtmpSender = null;
                    }
                    replayClient(PushEvent.info_stoped);

                    break;

            }
            Log.i(TAG, "GpusherService handleMessage finish : " + msg.what);
        }
    }

    private void replayClient(int what) {
                //通过Message的replyTo获取到客户端自身的Messenger，
                //Service可以通过它向客户端发送消息
                if(clientMessenger != null){
                    Log.i(TAG, "GpusherService to client " + what);
                    Message msgToClient = Message.obtain();
                    msgToClient.what = what;
                    //可以通过Bundle发送跨进程的信息
//                    Bundle bundle = new Bundle();
//                    bundle.putString(TAG, "你好，客户端，我是 GpusherService");
//                    msgToClient.setData(bundle);
                    try{
                        clientMessenger.send(msgToClient);
                    }catch (RemoteException e){
                        e.printStackTrace();
                        Log.e(TAG, "GpusherService to client failed: " + e.getMessage());
                    }
                    Log.i(TAG, "GpusherService to client " + what + " OK ") ;
                }
    }

    @Override
    public void onCreate() {
        Log.i(TAG, "GpusherService -> onCreate");
        super.onCreate();
        mContext = this;
        IntentFilter filter=new IntentFilter(ExeptionFilterStr);
        registerReceiver(cmdReceiver, filter);
        setForeground();

    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "GpusherService -> onBind");
        //获取Service自身Messenger所对应的IBinder，并将其发送共享给所有客户端
        return serviceMessenger.getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "GpusherService -> onUnbind");
        super.onUnbind(intent);

//        if (mRtmpSender != null) {
//            mRtmpSender.stop();
//            mRtmpSender = null;
//        } else {
//            Log.i(TAG, "GpusherService  onUnbind mRtmpSender is NULL !!!");
//        }
//        replayClient(PushEvent.info_stoped);
        return false;
    }
    @Override
    public void onDestroy() {
        Log.i(TAG, "GpusherService -> onDestroy");
        clientMessenger = null;
        super.onDestroy();
    }


    private final int PID = android.os.Process.myPid();
    private AssistServiceConnection mConnection;
//
//
    public void setForeground() {
        Log.i(TAG, "GpusherService -> startForeground");
        this.startForeground(PID, getNotification());

        // sdk < 18 , 直接调用startForeground即可,不会在通知栏创建通知
        if (Build.VERSION.SDK_INT < 18) {
            this.startForeground(PID, getNotification());
            return;
        }

        if (null == mConnection) {
            mConnection = new AssistServiceConnection();
        }

        this.bindService(new Intent(this, AssistService.class), mConnection,
                Service.BIND_AUTO_CREATE);
    }

    private class AssistServiceConnection implements ServiceConnection {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "GpusherService: onServiceDisconnected");
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.d(TAG, "GpusherService: onServiceConnected");

            // sdk >=18
            // 的，会在通知栏显示service正在运行，这里不要让用户感知，所以这里的实现方式是利用2个同进程的service，利用相同的notificationID，
            // 2个service分别startForeground，然后只在1个service里stopForeground，这样即可去掉通知栏的显示
            Service assistService = ((AssistService.LocalBinder) binder)
                    .getService();
            GpusherService.this.startForeground(PID, getNotification());
            assistService.startForeground(PID, getNotification());
            assistService.stopForeground(true);

            GpusherService.this.unbindService(mConnection);
            mConnection = null;
        }
    }

    private Notification getNotification() {
        // 定义一个notification
        Notification notification = new Notification();
        Intent notificationIntent = new Intent(this, GpusherService.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);
        notification.setLatestEventInfo(this, "GpusherTitle", "GpusherContent",
                pendingIntent);
        return notification;
    }
}
