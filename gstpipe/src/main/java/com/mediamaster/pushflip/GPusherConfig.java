package com.mediamaster.pushflip;

import android.media.projection.MediaProjection;
import android.os.Build;

/**
 * Created by paladin on 16-4-21.
 */
public class GPusherConfig {
    public static final String version = "2017"+"0103"+"1757";
    public static final boolean out2file = true;
    // 0 myself
    // 1 feiyun
    // 2 qiniu
    public static final int release = 1;

    public static String myscreenrecord_path = null;
    public static String privatePath = null;
    public int width;
    public int height;
    public int bitrate;
    public int dpi;
    public static boolean ControlFrameRate = true;
    public MediaProjection mediaProjection;

//    public static final int sdk_init = Build.VERSION.SDK_INT;

}
