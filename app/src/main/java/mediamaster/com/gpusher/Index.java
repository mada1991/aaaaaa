package mediamaster.com.gpusher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Point;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.mediamaster.pushapi.PrivateDataMaker;
import com.mediamaster.pushapi.Pusher;
import com.mediamaster.pushflip.GPusherConfig;
import com.mediamaster.pushflip.PushEvent;
import com.mediamaster.pushflip.PusherEventListener;

public class Index extends Activity {
    private static final String TAG = "pushflip-Index";

    private static final int REQUEST_CODE = 1;
    int density_type = R.id.qx_tv;                              //默认高清分辨率
    int orientation_type = R.id.index_h_recorder_container;  //默认横屏直播
    int width = 1280;
    int height = 720;
    int mBitrate = 2000000;
    private MediaProjectionManager mMediaProjectionManager;
    private MediaProjection mediaProjection = null;
    private Toast logToast = null;
    private static MainActivity mContext;
    public static String Room_id = null;
    public static String Login_key = null;

    private int mScreenDensity;

    TextView mStopButton = null;
    TextView mStartButton = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_index);

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mScreenDensity = metrics.densityDpi;
        Log.i(TAG, "..........." + String.valueOf(mScreenDensity) + ".................");

       //Log.i(TAG, "pushflip version " + GPusherConfig.version + " jni version " + getNativeVersion());
        Log.i(TAG, "get version .........onCreate " + Pusher.getVersion() + " " + Pusher.getNativeVersion());

        //房间号码   房间
        TextView mroom_id = (TextView) findViewById(R.id.index_user_room);
        mroom_id.setText("room:" + Room_id);

        //开启游戏直播
        mStartButton = (TextView) findViewById(R.id.index_start_recorder_btn);
        mStartButton.setEnabled(true);
        mStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startServer(v);
            }
        });

        //停止游戏直播
        mStopButton = (TextView) findViewById(R.id.index_stop_recorder_btn);
        mStopButton.setEnabled(false);
        mStartButton.setEnabled(true);
        mStopButton.setBackgroundResource(R.drawable.shape_corner_item_check_select);
        mStopButton.setTextColor(Color.GRAY);
        mStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Log.i(TAG,"..............停止按钮...............");
                mStopButton.setEnabled(false);
                mStopButton.setBackgroundResource(R.drawable.shape_corner_item_check_select);
                mStopButton.setTextColor(Color.GRAY);

                mStartButton.setEnabled(true);
                mStartButton.setBackgroundResource(R.drawable.shape_corner_item_check);
                mStartButton.setTextColor(Color.WHITE);
                stopServer(v);
            }
        });

        //切换分辨率
        TextView mchange_ratio = (TextView) findViewById(R.id.index_change_ratio_container02);
        mchange_ratio.setVisibility(View.GONE);
        mchange_ratio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                change_ratio(v);
            }
        });

        //切换隐私模式
        TextView mprivate_ratio = (TextView) findViewById(R.id.index_privacy_mode);
        //mprivate_ratio.setVisibility(View.GONE);
        mprivate_ratio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPusher.setPrivateMode(true);
            }
//            Button b = (Button)v;
//            if (mprivate_ratio.getText().toString().equals("private")) {
//                b.setText("unprivate");
//            } else {
//                mPusher.setPrivateMode(false);
//                b.setText("private");
//            }
        });

        //分辨率
//        RadioGroup radioGroup = (RadioGroup) findViewById(R.id.live_density_radio_btn);
//        radioGroup.check(R.id.high_density);
//        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
//            @Override
//            public void onCheckedChanged(RadioGroup group, int checkedId) {
//                density_type = checkedId;
//            }
//        });


        TextView mqxButton = (TextView) findViewById(R.id.qx_tv);
        mqxButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //stopServer(v);
                Log.i(TAG,"清新分辨率被点击");
                density_type = R.id.qx_tv;
            }
        });

        TextView mgqButton = (TextView) findViewById(R.id.gq_tv);
        mgqButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //stopServer(v);
                Log.i(TAG,"高清分辨率被点击");
                density_type = R.id.gq_tv;
            }
        });

        TextView mcqButton = (TextView) findViewById(R.id.cq_tv);
        mcqButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //stopServer(v);
                Log.i(TAG,"超清分辨率被点击");
                density_type = R.id.cq_tv;
            }
        });


        //上传Log
        ImageView mLogButton = (ImageView) findViewById(R.id.bar_right_icon2);
        mLogButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                uploadLog();
                logAndToast("上传Log成功");
            }
        });

        //高清
        TextView gq_view = (TextView) findViewById(R.id.gq_tv);

        //Android 5.0+ 屏幕截取接口
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mMediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            Intent captureIntent = mMediaProjectionManager.createScreenCaptureIntent();

            startActivityForResult(captureIntent, REQUEST_CODE);
        } else {
            if (GPusherConfig.release == 0) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        startServer(null);
                    }
                });
            }
        }


    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i(TAG, "chh  onActivityResult");
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        mediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, data);
        if (mediaProjection == null) {
            Log.e(TAG, "media projection is null");
            return;
        }
        Log.i(TAG, "media projection is " + mediaProjection);

        if (GPusherConfig.release == 0) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    startServer(null);
                }
            });
        }
    }

    private String getReUrl(int i) {
        Log.i(TAG, "getUrl ");
        //String url =  TestPresenter.getReRtmpUrl();
        //Log.i(TAG, "getUrl " + url);
        return null;
    }

    private MyPushListener mListener = new MyPushListener();
    private Context mAppContext = null;
    private Pusher mPusher;
    public void startServer(View v)
    {
        Log.i(TAG, "startServer");
        Log.i(TAG, "maxMemory " + Runtime.getRuntime().maxMemory()) ;
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            logAndToast("Android版本太低，不支持此版本");
            return ;
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {}

        switch (density_type) {
            case R.id.qx_tv:
                height = 360;
                mBitrate = 500000;
                break;

            case R.id.gq_tv:
                height = 480;
                mBitrate = 1000000;
                break;

            case R.id.cq_tv:
                height = 720;
                mBitrate = 2000000;
                break;
        }


        WindowManager windowManager = (WindowManager) getSystemService("window");
        Point point = new Point();
        windowManager.getDefaultDisplay().getRealSize(point);
        float f = ((float) point.y) / ((float) point.x);

        width = (int) (f * ((float) height));
        if (width % 4 != 0) {
            width -= width % 4;
        }

        if (orientation_type != R.id.index_h_recorder_container) {
            int w = width;
            width = height;
            height = w;
        }

        Log.i(TAG, "startServer "+ Login.rtmp_url);
        mAppContext = getApplicationContext();
        mPusher = new Pusher();
        mPusher.regListener(mListener);
        //mPusher.info_log_count();   //统计

        //width = 704;
        //height = 400;
        //rtmp://192.168.3.163/vod/test
        //String urls = "rtmp://192.168.140.131:1935/hls/test";
        Log.i(TAG,"宽度：" + String.valueOf(width) + "高度：" + String.valueOf(height) + "Dpi:" + String.valueOf(mScreenDensity));
        //if (mPusher.prepare(mAppContext, urls,Room_id, PrivateDataMaker.SystemDefaultPrivateData, width, height, mBitrate,mScreenDensity, mediaProjection) == false)
        if (mPusher.prepare(mAppContext, Login.rtmp_url,Room_id, PrivateDataMaker.SystemDefaultPrivateData, width, height, mBitrate,mScreenDensity, mediaProjection) == false)
        {
            logAndToast("链接prepare失败，请检查网络");
            return;
        }
        mPusher.start();
    }

    //停止游戏直播
    public void stopServer(View v) {
        new Thread() {
            public void run() {
                if (mPusher != null) {
                    mPusher.stop();
                    mPusher = null;
                }
            }
        }.start();
    }

    private void logAndToast(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                logAndToast_internal(msg);
            }
        });
    }

    //通知信息
    private void logAndToast_internal(String msg) {
        Log.d(TAG, msg);
        if (logToast != null) {
            logToast.cancel();
        }

        logToast = Toast.makeText(this, msg, Toast.LENGTH_LONG);
        logToast.show();
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

    private void connectTanmu() {
        String ws_url = TestPresenter.getWsUrl();
        TestWebsocket mTestWebsocket = new TestWebsocket(mContext);
        mTestWebsocket.initWebSocket(ws_url);
    }

    private class MyPushListener extends PusherEventListener {

        @Override
        public void OnNofityEvent(final int event) {
            switch(event)
            {
                case PushEvent.info_started:
                    Log.i(TAG, "推流已经开始");
                    //connectTanmu();
                    break;
                case PushEvent.info_reconnecting:
                    Log.i(TAG, "网络或服务器断开，正在重连...");
                    break;
                case PushEvent.info_reconnected:
                    Log.i(TAG, "重连成功， 正在推流 :）");
                    break;
                case PushEvent.error_connect_server:
                    Log.i(TAG, "建立连接失败，请检查网络是否可用后重新开始");
                    break;
                case PushEvent.info_reconnect_failed:
                    Log.i(TAG, "重连失败， 五秒后继续尝试 :）");
                    break;
                case PushEvent.error_send_failed:
                    Log.i(TAG, "数据上传失败，网络或服务器断开，请重连...");
                    break;
                case PushEvent.error_audiorecord_failed:
                    Log.i(TAG, "打开录音设备失败，请检查是否有录音权限");
                    break;
                case PushEvent.error_internal_expection:
                    Log.i(TAG, "内部严重异常错误，请上传日志并重启推流服务");
                    break;
                default:
                    Log.i(TAG, "push event");
                    break;
            }
            if (event == PushEvent.info_started) {

                mStopButton.setEnabled(true);
                mStopButton.setBackgroundResource(R.drawable.shape_corner_item_check);
                mStopButton.setTextColor(Color.WHITE);

                mStartButton.setEnabled(false);
                mStartButton.setBackgroundResource(R.drawable.shape_corner_item_check_select);
                mStartButton.setTextColor(Color.GRAY);

                moveTaskToBack(true);
            } else if (event == PushEvent.error_send_failed) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                while (checkNetworkState() == false) {

                    Log.i(TAG, "wait Network reconnect");
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                connectTanmu();

                String url = getReUrl(0);

                mPusher.reconnect(url);
            }

            logAndToast(PushEvent.tip(event));

        }
    }

    //上传Log
    private void uploadLog() {
        new Thread() {
            public void run() {
                logAndToast("开始上传Log");
                Pusher.uploadLog();
                logAndToast("上传Log成功");
            }
        }.start();
    }

    //切换分辨率
    String rtmp_ratio_url = null;
    public void change_ratio(View v) {
        Log.i(TAG,"切换分辨率按钮被点击");
        rtmp_ratio_url = getRtatioUrl(0);
        Log.i(TAG, "推流地址为：" + rtmp_ratio_url);
        new Thread() {
            public void run() {
                if (mPusher != null) {
                    mPusher.syn_change_ratio( rtmp_ratio_url, 1280, 720, 2000000 );
                }
            }
        }.start();
    }

    //返回推流地址
    private String getRtatioUrl(int i) {
        String url =  TestPresenter.getRaRtmpUrl(Room_id,Login_key);
        return url;
    }

}
