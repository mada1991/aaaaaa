package mediamaster.com.gpusher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

public class Login extends Activity {
    private static final String TAG = "pushflip-Login";
    //UI控件
    public TextView UserNameView;
    public TextView PasswordView;
    //静态变量
    public static String rtmp_verify = null;
    public static String room_id = null;
    public static String login_key = null;
    public static String rtmp_url = null;
    public static String msg = null;
    private Toast logToast = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        UserNameView = (TextView) findViewById(R.id.id_login_et_account);    //用户名
        PasswordView = (TextView) findViewById(R.id.id_login_et_password);   //密码

        Button ratio_Button = (Button) findViewById(R.id.id_login_bt_login);
        ratio_Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String username = UserNameView.getText().toString();
                String password = PasswordView.getText().toString();
                Log.i(TAG,"用户名：" + username + " " + "密码：" + password);
                //登入请求接口
                //String loginAPI = "http://old.feiyun.tv/api/login/web?account=" + username + "&pwd=" + password;
                //String loginAPI = "http://api.feiyun.tv/api/login/common?password=feiyun123&mobile=12345678918&pf=android&cv=202";
                //String loginAPI = "http://api.feiyun.tv/api/login/common?password="+ password + "&mobile=" + username + "&pf=android&cv=202";

                //String loginAPI = "http://api.feiyun.tv/api/login/common?password=and123&mobile=12345678902&pf=android&cv=202";
                String loginAPI = "http://api.feiyun.tv/api/login/common?password=feiyun123&mobile=12345678921&pf=android&cv=202";

                //String loginAPI = "http://old.feiyun.tv/api/login/web?account=12345678918&pwd=feiyun123";
                login_key = loginRequest(loginAPI,"user_verify");
                Log.i(TAG, "登入验证：" + login_key);
                if (null != login_key) {
                    //请求推流地址
                    //http://api.feiyun.tv/api/live/push_url?room_id=240430&user_id=240430&user_verify=0e298c2bf7df6445f5ccd928924f83e3&t=1
                    String Rtmp_API = "http://api.feiyun.tv/api/live/push_url?room_id=" + room_id + "&user_id=" + room_id + "&user_verify=" + login_key + "&t=1";
                    rtmp_url = RtmpUrl_Request(Rtmp_API,"push_url");
                    Log.i(TAG,"推流地址为：" + rtmp_url);//得到最终的推流地址url
                    //跳转推流active
                    if (null != rtmp_url)
                    {
                        Intent intent = new Intent(Login.this,Index.class);
                        Login.this.startActivity(intent);
                        Index.Room_id = room_id;
                        Index.Login_key = login_key;
                        room_id = null;
                        login_key = null;
                    }
                }
            }
        });
    }


    //登入请求
    public String loginRequest(String httpUrl, final String key){

        HttpUtils.doGetAsyn(httpUrl, new HttpUtils.CallBack() {
            @Override
            public void onRequestComplete(String result) {
                JSONObject roomJson = null;
                try {
                    roomJson = new JSONObject(result);
                    msg = roomJson.getString("msg");
                    Log.i(TAG, "信息：" + msg);
                    String data = roomJson.getString("data");
                    JSONObject dataJson = new JSONObject(data);
                    room_id = dataJson.getString("user_id");
                    Log.i(TAG, "房间号：" + room_id);
                    rtmp_verify = dataJson.getString(key);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

        long start_time = System.currentTimeMillis();
        while( msg == null ) {
            try {
                Log.i(TAG,"wait msg... ... ");
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            long end_time = System.currentTimeMillis();
            if (5000 <= end_time - start_time) {
                logAndToast("登入状态请求服务器异常，请检查网络或者稍后再试");
                break;
            }
        }

        if (null != msg)
        {
            if (!msg.equals("ok")){
                logAndToast("登入失败，请检查用户名或密码");     // 登入失败
                msg = null;
                return null;
            }
            long sstart_time = System.currentTimeMillis();
            while( rtmp_verify == null || room_id == null) {
                try {
                    Log.i(TAG,"wait rtmp_verify and room_id ");
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                long send_time = System.currentTimeMillis();
                if (5000 <= send_time - sstart_time) {
                    logAndToast("请求服务器异常，请检查网络或者稍后再试");
                    break;
                }
            }
            if (rtmp_verify == null || room_id == null){
                msg = null;
                return null;
            }
        }
        else {
            msg = null;
            return null;
        }

        msg = null;
        return rtmp_verify;
    }

    // 请求推流地址
    String Rtmp_url = null;
    public String RtmpUrl_Request(String httpUrl, final String key){
        Rtmp_url = null;
        HttpUtils.doGetAsyn(httpUrl, new HttpUtils.CallBack() {
            @Override
            public void onRequestComplete(String result) {
                JSONObject roomJson = null;
                try {
                    roomJson = new JSONObject(result);
                    String data = roomJson.getString("data");
                    JSONObject dataJson = new JSONObject(data);
                    String url = dataJson.getString(key);
                    JSONObject dataJson02 = new JSONObject(url);
                    Rtmp_url = dataJson02.getString("rtmp");

                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }
        });

        long start_time = System.currentTimeMillis();
        while( Rtmp_url == null ) {
            try {
                Log.i(TAG,"wait Rtmp Url ... ... ");
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            long end_time = System.currentTimeMillis();
            if (5000 <= end_time - start_time){
                logAndToast("推流地址请求服务器异常，请检查网络或者稍后再试");
                break;
            }
        }
        if ( Rtmp_url == null ){return null;}

        return Rtmp_url;
    }

    //通知信息
    private void logAndToast(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                logAndToast_internal(msg);
            }
        });
    }

    private void logAndToast_internal(String msg) {
        Log.d(TAG, msg);
        if (logToast != null) {
            logToast.cancel();
        }
        logToast = Toast.makeText(this, msg, Toast.LENGTH_LONG);
        logToast.show();
    }

}
