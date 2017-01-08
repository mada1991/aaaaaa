package mediamaster.com.gpusher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.mediamaster.pushapi.PrivateDataMaker;
import com.mediamaster.pushapi.Pusher;
import com.mediamaster.pushflip.GPusherConfig;
import com.mediamaster.pushflip.PushEvent;
import com.mediamaster.pushflip.PusherEventListener;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

//import com.mediamaster.pushflip.GPusher;
//import com.mediamaster.pushflip.GPusherConfig;

//public class MainActivity extends ActionBarActivity {
public class MainActivity extends Activity {

    private static final String TAG = "pushflip-MainActivity";

    private static final int REQUEST_CODE = 1;
    private MediaProjectionManager mMediaProjectionManager;
    int mBitrate = 2000000;
    int width = 1280;
    int height = 720;
    int screen_width;
    int screen_height;
    int orientation_type = R.id.horizontal_btn;
    int density_type = R.id.high_density;

    private Toast logToast = null;

    private static String rtmp_url;

    private MediaProjection mediaProjection = null;

    //private static final int sdk_init =  Build.VERSION_CODES.KITKAT;
    private static MainActivity mContext;
    public static final String finish_Intent = "gamelive.gamelive.screenrecorder.finishIntent";
    int last_url_i = 0;

    //返回推流地址
    private String getUrl(int i) {
        String url =  TestPresenter.getRtmpUrl();
        return url;
        //return "rtmp://qnpublish.uimg.cn/g_live/room_3811690?nonce=1470387220&token=ipAo2IWLl2GCsrrlDGIjMvslMpU=";
    }

    private String getReUrl(int i) {
        Log.i(TAG, "getUrl ");
        String url =  TestPresenter.getReRtmpUrl();
        Log.i(TAG, "getUrl " + url);
        return url;
        //return "rtmp://qnpublish.uimg.cn/g_live/room_3811690?nonce=1470387220&token=ipAo2IWLl2GCsrrlDGIjMvslMpU=";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.online_setting_layout);

        Log.i(TAG, "onCreate " + Pusher.getVersion() + " " + Pusher.getNativeVersion());
        mContext = this;
        screen_width = getWindowManager().getDefaultDisplay().getWidth();    //1280
        screen_height = getWindowManager().getDefaultDisplay().getHeight();  //720

        if (GPusherConfig.release == 0) {
            LinearLayout ml = (LinearLayout) findViewById(R.id.main_layout);
            ml.setVisibility(View.INVISIBLE);
        }

        Button mStartButton = (Button) findViewById(R.id.online_live_control_btn);
        mStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startServer(v);
            }
        });

        Button mStopButton = (Button) findViewById(R.id.stop_btn);
        mStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopServer(v);
            }
        });

        Button ratio_Button = (Button) findViewById(R.id.radio_sss);
        ratio_Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                change_ratio(v);
            }
        });

        Button mUploadButton = (Button) findViewById(R.id.upload_log_btn);
        mUploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                uploadLog();
            }

        });

        TextView mUrlTextView = (TextView) findViewById(R.id.live_uri_edit);
        mUrlTextView.setText(getUrl(R.id.url1_btn));

        RadioGroup urlradioGroup = (RadioGroup) findViewById(R.id.url_radio_btn);
        urlradioGroup.check(R.id.url1_btn);
        urlradioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                TextView mUrlTextView = (TextView) findViewById(R.id.live_uri_edit);
                mUrlTextView.setText(getUrl(checkedId));
            }
        });

        RadioGroup radioGroup = (RadioGroup) findViewById(R.id.live_density_radio_btn);
        radioGroup.check(R.id.high_density);
        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                density_type = checkedId;
            }
        });
        RadioGroup orientRadioGroup = (RadioGroup) findViewById(R.id.orientation_radio_btn);

        orientRadioGroup.check(orientation_type);
        orientRadioGroup.check(R.id.horizontal_btn);
        orientRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                orientation_type = checkedId;
            }
        });

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
        Log.e(TAG, "media projection is " + mediaProjection);

        if (GPusherConfig.release == 0) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    startServer(null);
                }
            });
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void StopPush(View v) {

    }

    public void setPrivate(View v) {
        Button b = (Button)v;
        if (b.getText().toString().equals("private")) {
            mPusher.setPrivateMode(true);
            b.setText("unprivate");
        } else {
            mPusher.setPrivateMode(false);
            b.setText("private");
        }
    }

    // Log |msg| and Toast about it.
    private void logAndToast_internal(String msg) {
        Log.d(TAG, msg);
        if (logToast != null) {
            logToast.cancel();
        }

        logToast = Toast.makeText(this, msg, Toast.LENGTH_LONG);
        logToast.show();
    }

    private void logAndToast(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                logAndToast_internal(msg);
            }
        });
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
                    connectTanmu();
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
                moveTaskToBack(false);
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

    private MyPushListener mListener = new MyPushListener();
    private Pusher mPusher;
    private Context mAppContext = null;

    public void startServer(View v) {

        //String rtmp_uri =  "rtmp://publish.huizu100.com/g_live/songguangyu?key=d2b755c06e892172";
//                String rtmp_uri =  "rtmp://123.59.63.4/g_live/songguangyu?key=d2b755c06e892172";
        Log.i(TAG, "startServer");
        Log.i(TAG, "maxMemory " + Runtime.getRuntime().maxMemory()) ;
        //connectTanmu();

        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            logAndToast("Android版本太低，不支持此版本");
            return ;
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            //copyBinaryFromAssetsToData();
            // TODO: check root permission
        }

        switch (density_type) {
            case R.id.normal_density:
                height = 360;
                mBitrate = 500000;
                break;

            case R.id.high_density:
                height = 480;
                mBitrate = 1000000;
                break;

            case R.id.super_density:
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

        if (orientation_type != R.id.horizontal_btn) {
            int w = width;
            width = height;
            height = w;
        }

        TextView urlTextView = (TextView) findViewById(R.id.live_uri_edit);
        String rtmp_uri = urlTextView.getText().toString();

        Log.i(TAG, "startServer "+ rtmp_uri);
        mAppContext = getApplicationContext();
        mPusher = new Pusher();
        mPusher.regListener(mListener);
        if (mPusher.prepare(mAppContext, rtmp_uri,"000001", PrivateDataMaker.SystemDefaultPrivateData, width, height, mBitrate,1, mediaProjection) == false) {
            logAndToast("链接prepare失败，请检查网络");
            return;
        }
        mPusher.start();
    }

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

    //切换分辨率
    public void change_ratio(View v) {
        Log.i(TAG,".............切换按钮被点击.............");
        rtmp_url = getUrl(0);
        Log.i(TAG, "推流地址为：" + rtmp_url);
        new Thread() {
            public void run() {
                if (mPusher != null) {
                    mPusher.stop();
                    mPusher.syn_change_ratio( rtmp_url, 1280, 720, 2000000 );
                    //mPusher = null;
                }
            }
        }.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");
        stopServer(null);
    }

    //    public void startServer(View v) {
//        new Thread() {
//            public void run() {
//
//                        String rtmp_uri =  "rtmp://publish.huizu100.com/g_live/songguangyu?key=d2b755c06e892172";
////                String rtmp_uri =  "rtmp://123.59.63.4/g_live/songguangyu?key=d2b755c06e892172";
//
//                        WindowManager windowManager = (WindowManager) getSystemService("window");
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
//                RtmpSender mRtmpSender = new RtmpSender();
//                mRtmpSender.connectServer(rtmp_uri);
//
//                GPusherConfig cfg = new GPusherConfig();
//
//                cfg.width = width;
//                cfg.height = height;
//                cfg.bitrate = mBitrate;
//                cfg.mediaProjection = mediaProjection;
//
//                mRtmpSender.prepare(cfg);
//                mRtmpSender.start();
//            }
//        }.start();
//    }

    private void uploadLog() {
        new Thread() {
            public void run() {
                logAndToast("开始上传Log");
                Pusher.uploadLog();
                logAndToast("上传Log成功");
            }
        }.start();
    }



}
