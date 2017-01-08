package com.mediamaster.pushflip;

import com.mediamaster.gstpipe.R;

/**
 * Created by paladin on 16-5-8.
 */
public class PushEvent {


    public static final int info_started = 1;
    public static final int info_reconnecting = 2;
    public static final int info_reconnected = 3;
    public static final int info_stoped = 4;
    public static final int info_reconnect_failed = 5;



    public static final int error_connect_server = 19901;
    public static final int error_quit = 19902;
    public static final int error_send_failed = 19903;
    public static final int error_audiorecord_failed = 19904;
    public static final int error_internal_expection = 19905;

    public static String tip(int event) {
    switch(event)

    {
        case PushEvent.info_started:
            return "推流已经开始";
        case PushEvent.info_reconnecting:
            return "网络或服务器断开，正在重连...";
        case PushEvent.info_reconnected:
            return "重连成功， 正在推流 :）";
        case error_connect_server:
            return "建立连接失败，请检查网络是否可用后重新开始";
        case info_reconnect_failed:
            return "重连失败， 五秒后继续尝试 :）";
        case error_send_failed:
            return "数据上传失败，网络或服务器断开，请重连...";
        case error_audiorecord_failed:
            return "打开录音设备失败，请检查是否有录音权限";
        default:
            return "push event";

    }
}



}
