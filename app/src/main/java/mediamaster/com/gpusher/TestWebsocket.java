package mediamaster.com.gpusher;

        import android.content.Context;
        import android.text.TextUtils;
        import android.util.Log;

        import de.tavendo.autobahn.WebSocketConnection;
        import de.tavendo.autobahn.WebSocketHandler;
        import de.tavendo.autobahn.WebSocketOptions;

/**
 * Created by xky on 16/7/16.
 */
public class TestWebsocket {

    private static final String TAG = "pushflip-testwebsocket";
    private Context mContext;
    private WebSocketConnection mConnection = new WebSocketConnection();
    private MyWebSocketHandler myWebSocketHandler = new MyWebSocketHandler();
    public TestWebsocket(Context context) {
        mContext = context;
    }
    public void initWebSocket(String socketUrl) {
        if (!TextUtils.isEmpty(socketUrl)) {
            try {
                WebSocketOptions options = new WebSocketOptions();
                options.setSocketConnectTimeout(3000);
                mConnection.connect(socketUrl, myWebSocketHandler,options);

                Log.i(TAG, "----------弹幕---------------connect finish");

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    public void destroySocket() {
        if (mConnection != null && mConnection.isConnected()) {
            mConnection.disconnect();
        }
    }

    class MyWebSocketHandler extends WebSocketHandler {
        @Override
        public void onOpen() {
            Log.i(TAG, "-------------弹幕------------- onOpen");
//            super.onOpen();
        }

        @Override
        public void onTextMessage(String payload) {
            Log.i(TAG, "onTextMessage " + payload);
//            super.onTextMessage(payload);
        }

        @Override
        public void onClose(int code, String reason) {
//            super.onClose(code, reason);
            Log.i(TAG, "------------弹幕--------------onClose " + reason);
        }
    }
}