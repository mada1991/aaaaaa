package com.mediamaster.pushflip.ftp;

import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

/**
 * Created by paladin on 16-5-9.
 */
public class ftppush {

    private static final String TAG = "pushflip-ftppush";

    public static final String FTP_CONNECT_SUCCESSS = "ftp连接成功";
    public static final String FTP_CONNECT_FAIL = "ftp连接失败";
    public static final String FTP_DISCONNECT_SUCCESS = "ftp断开连接";
    public static final String FTP_FILE_NOTEXISTS = "ftp上文件不存在";

    public static final String FTP_UPLOAD_SUCCESS = "ftp文件上传成功";
    public static final String FTP_UPLOAD_FAIL = "ftp文件上传失败";
    public static final String FTP_UPLOAD_LOADING = "ftp文件正在上传";

    public static final String FTP_DOWN_LOADING = "ftp文件正在下载";
    public static final String FTP_DOWN_SUCCESS = "ftp文件下载成功";
    public static final String FTP_DOWN_FAIL = "ftp文件下载失败";

    public static final String FTP_DELETEFILE_SUCCESS = "ftp文件删除成功";
    public static final String FTP_DELETEFILE_FAIL = "ftp文件删除失败";


    public static void uploadLogFile(final String path) {

    }


    public static void uploadSignleFile(final String upath) {
//        new Thread(new Runnable() {
//            @Override
//            public void run() {

                // 上传
                try {
                    File ufile = new File(upath);
                    if (!ufile.exists()) {
                        Log.w(TAG,"no exists " + upath);
                        return ;
                    }
                    Log.d(TAG, "-----sc--- start 0%");
                    SimpleDateFormat formatter = new SimpleDateFormat("MMdd");
                    Date curDate = new Date(System.currentTimeMillis());//获取当前时间
                    String day = formatter.format(curDate);
                    //单文件上传
                    new FTP().uploadSingleFile(ufile, "/"+day,new FTP.UploadProgressListener(){

                        @Override
                        public void onUploadProgress(String currentStep,long uploadSize,File file) {
                            // TODO Auto-generated method stub
//                            Log.d(TAG, currentStep);
                            if(currentStep.equals(FTP_UPLOAD_SUCCESS)){
                                Log.d(TAG, "-----sc--successful");
                            } else if(currentStep.equals(FTP_UPLOAD_LOADING)){
                                long fize = file.length();
                                float num = (float)uploadSize / (float)fize;
                                int result = (int)(num * 100);
                                Log.d(TAG, "-----sc---"+result + "%");
                            }
                        }
                    });
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
//
//            }
//        }).start();
    }


}
