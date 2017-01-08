package mediamaster.com.gpusher;
import android.util.Log;

import com.google.gson.Gson;
import com.mediamaster.pushapi.Pusher;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by xky on 16/7/9.
 */
public class TestPresenter {


    class Entry{
        private int code;
        private String msg;
        private UrlData data;

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public String getMsg() {
            return msg;
        }

        public void setMsg(String msg) {
            this.msg = msg;
        }

        public UrlData getData() {
            return data;
        }

        public void setData(UrlData data) {
            this.data = data;
        }
    }

    class UrlData{
        private String publish_url;

        public String getPublish_url() {
            return publish_url;
        }

        public void setPublish_url(String publish_url) {
            this.publish_url = publish_url;
        }
    }

    static  String myurl = null;

    static String rtmp_url = "http://www.feiyun.tv/live/api.php?action=open&user_id=240430&user_verify=0e298c2bf7df6445f5ccd928924f83e3&get_publish=1";

    static String rtmp_ratio_url = "http://www.feiyun.tv/live/api.php?action=open&user_id=240430&user_verify=0e298c2bf7df6445f5ccd928924f83e3&get_publish=1";

    static String re_rtmp_url = "http://www.feiyun.tv/live/api.php?action=open&user_id=240430&user_verify=0e298c2bf7df6445f5ccd928924f83e3&get_publish=1";

    static String ws_url = "http://www.feiyun.tv//api/room/get_wsurl?user_verify=0e298c2bf7df6445f5ccd928924f83e3&user_id=240430&room_user_id=240430";

    public static String getRtmpUrl() {
        return getUrl(rtmp_url, "publish_url");
    }

    //返回切换分辨率
    public static String getRaRtmpUrl(String room_id,String key) {
        rtmp_ratio_url = "http://api.feiyun.tv/api/live/push_url?room_id=" + room_id + "&user_id=" + room_id + "&user_verify=" + key + "&t=1";
        //rtmp_ratio_url = "http://www.feiyun.tv/live/api.php?action=open&user_id="+room_id+"&user_verify="+key+"&get_publish=1";
        Log.i("TestPresenter","地址：" + rtmp_ratio_url);
        //return null;
        return getUrl(rtmp_ratio_url, "push_url");  //push_url   publish_url
    }

    public static String getReRtmpUrl() {
        return getUrl(re_rtmp_url, "publish_url");
    }

    public static String getWsUrl() {
        return getUrl(ws_url, "wsurl");
    }

    public static String getUrl(final  String url, final String key) {

        myurl = null;
        HttpUtils.doGetAsyn(url, new HttpUtils.CallBack() {
            @Override
            public void onRequestComplete(String result) {

                JSONObject roomJson = null;
                try {
//                    roomJson = new JSONObject(result);
//                    String data = roomJson.getString("data");
//                    JSONObject dataJson = new JSONObject(data);
//                    myurl = dataJson.getString(key);

                    roomJson = new JSONObject(result);
                    String data = roomJson.getString("data");
                    JSONObject dataJson = new JSONObject(data);
                    String url = dataJson.getString(key);
                    JSONObject dataJson02 = new JSONObject(url);
                    myurl = dataJson02.getString("rtmp");

                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }
        });

        while( myurl == null ) {
            try {
                Log.i("pushflip-TestPresenter","wait url ");
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        return myurl;
    }






}
