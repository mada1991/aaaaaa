package com.mediamaster.pushflip;


import android.os.Environment;
import android.text.format.DateFormat;

import com.mediamaster.pushflip.ftp.ZipUtils;
import com.mediamaster.pushflip.ftp.ftppush;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * Created by paladin on 15-7-2.
 */
public final class Log {
        private static final String TAG = "pushflip-loglog";
        public static final int VERBOSE = 2;
        public static final int DEBUG = 3;
        public static final int INFO = 4;
        public static final int WARN = 5;
        public static final int ERROR = 6;
        public static final int ASSERT = 7;
        private static String mLogDir = null;
        private static String mLogPath = null;
        private static String mUid;

        private static boolean start_log_file = false;

//        static {
////            String dir = Environment.getExternalStorageDirectory().getAbsolutePath();
////            File f = new File(dir + "/paladin_no_log_file");
////            if (f.exists()) {
////                GPusherConfig.out2file = true;
////            } else {
////                GPusherConfig.out2file = false;
////            }
////            android.util.Log.i("loglog", " out2file " + GPusherConfig.out2file );
//            genLogPath();
//        }

    private static String getDateStr() {
        SimpleDateFormat formatter = new SimpleDateFormat("MMdd_HHmmss");
        Date curDate = new Date(System.currentTimeMillis());  //获取当前时间
        String str = formatter.format(curDate);
        return str;
    }
    /**
     * 递归删除文件和文件夹
     *
     * @param file
     *            要删除的根目录
     */
    public static  void DeleteFiles(File file) {
        if (file.exists() == false) {

            return;
        } else {
            if (file.isFile()) {
                file.delete();
                return;
            }
            if (file.isDirectory()) {
                File[] childFile = file.listFiles();
                if (childFile == null || childFile.length == 0) {
                    //file.delete();
                    return;
                }
                for (File f : childFile) {
                    DeleteFiles(f);
                }
                //file.delete();
            }
        }
    }

    public static void UploadHistoryLog(final String dir,final boolean end, final String tag) {

        Thread uploadThread = new Thread(new Runnable() {
            private void upload() {
                File directory = new File(dir);
                if (!directory.isDirectory())
                    return;
                if (end) {
                    flushlog();
                    start_log_file = false;
                }

                try {
                    ArrayList<File> resFileList = new ArrayList<File>();
                    File files[] = directory.listFiles();
                    for (File f : files) {
                        if (f.getAbsolutePath().endsWith(".log")) {
                            android.util.Log.i("pushflip-Log", "add " + f.getAbsolutePath());
                            if (end == false) {
                                if (mLogPath != null && mLogPath.equals(f.getAbsolutePath())) {
                                    continue;
                                }
                            }
                            resFileList.add(f);
                        }
                    }


                    if (resFileList.size() > 0) {

                        String zipPath = dir + "/" + getDateStr() + "_" + mUid + "_" + tag +".zip";

                        File zfile = new File(zipPath);
                        ZipUtils.zipFiles(resFileList, zfile);


                        ftppush.uploadSignleFile(zipPath);

                        zfile.delete();
                    }

                    for(File p : resFileList) {
                        p.delete();
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }

                //DeleteFiles(directory);
            }
            @Override
            public void run() {
                upload();
            }
        });
        uploadThread.start();

        try {
            uploadThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    public static void uploadLog() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String path = Log.getLogPath();
                // 上传
                File file = new File(path);
                String zipPath = path  + ".zip";

                try {
                    File zfile = new File(zipPath);
                    ArrayList<File> resFileList = new ArrayList<File>();
                    resFileList.add(file);
                    ZipUtils.zipFiles(resFileList, zfile);

                    ftppush.uploadSignleFile(zipPath);

                    File zfile2 = new File(zipPath);
                    zfile2.delete();

                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public static String getLogPath() {
        return mLogPath;
    }

    public static void setDir(String dir, String uid) {
        mLogDir = dir;
        start_log_file = true;
        String    str    =    getDateStr();
        mUid = uid.replace(" ", "x").replace("\\", "x").replace("/", "x");

        android.util.Log.i(TAG, mLogDir + " " + mUid + " " + mLogPath );
        File f = new File(mLogDir);

        if(!f.exists()) {
            f.mkdir();
        } else if (!f.isDirectory()){
            f.delete();
            f.mkdir();
        }
    }

//    public static void genLogPath() {
//
//        mLogDir = null;
//        mLogPath = null;
//        if (Environment.getExternalStorageDirectory().exists()){
//            mLogDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/mediamaster_log";
//
//        } else if (Environment.getDownloadCacheDirectory().exists()){
//            mLogDir = Environment.getDownloadCacheDirectory().getAbsolutePath() + "/mediamaster_log";
//
//        }
//
//        if (mLogDir != null) {
//            String    str    =    getDateStr();
//            mLogPath = mLogDir + "/rtmppush_" + str +".log";
//            File f = new File(mLogDir);
//            if(!f.exists())
//                f.mkdir();
//            else if (!f.isDirectory()){
//                f.delete();
//                f.mkdir();
//            }
//        }
//
//    }

    private static void flushlog() {
        if (start_log_file == false) {
            logBuffer.delete(0,logBuffer.length()-1);
            return;
        }
        if (mLogDir == null) {
            android.util.Log.w(TAG, "Log dir is null");
            return;
        }
        if (logBuffer == null || logBuffer.length() < 1) {
            android.util.Log.w(TAG, "logBuffer is null or empty");
            return;
        }
        if (logFile == null) {

            String    str    =    getDateStr();
            mLogPath = mLogDir + "/" + mUid + "_"+ str +".log";
            logFile = new File(mLogPath);
            if (!logFile.exists()) {
                try {
                    logFile.createNewFile();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
        if (logFile == null) {
            android.util.Log.w(TAG, "logFile is null");
            return;
        }
//            android.util.Log.v(tag,logBuffer.toString());
        try {
            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
            //android.util.Log.v(tag,logBuffer.toString());
//                buf.append(logBuffer.toString());
            buf.write(logBuffer.toString());
//                buf.newLine();
            buf.close();
            try {
                if (logBuffer.length() > 0)
                    logBuffer.setLength(0);
            } catch (Exception e) {
                android.util.Log.w(TAG, "logBuffer.setLength exception");
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private static StringBuffer logBuffer = new StringBuffer();
    private static File logFile = null;
    private static  int appendLog(String level, String tag, String text)
    {
        if (logBuffer.length() < 4096 || level.equals("E") || level.equals("W")) {
            SimpleDateFormat formatter = new SimpleDateFormat("MM-dd HH:mm:ss:SSS");
            Date curDate = new Date(System.currentTimeMillis());    // 获取当前时间
            String str = formatter.format(curDate);
            logBuffer.append(android.os.Process.myPid() + "\t" + Thread.currentThread().getId() + "\t" + str + " " + level + " " + tag + " : " + text + "\n");
        } else {
            flushlog();
        }
        return 0;
    }

        /**
         * Handy function to get a loggable stack trace from a Throwable
         * @param tr An exception to log
         */
        public static String getStackTraceString(Throwable tr) {
            if (tr == null) {
                return "";
            }

            // This is to reduce the amount of log spew that apps do in the non-error
            // condition of the network being unavailable.
            Throwable t = tr;
            while (t != null) {
                if (t instanceof UnknownHostException) {
                    return "";
                }
                t = t.getCause();
            }

            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw, false);
            tr.printStackTrace(pw);
            pw.flush();
            return sw.toString();
        }

        Log() {
        }

        public static int v(String tag, String msg) {
            if (GPusherConfig.out2file)  return  appendLog("V", tag, msg);
            return android.util.Log.v(tag, msg);
        }

        public static int v(String tag, String msg, Throwable tr) {
            if (GPusherConfig.out2file)  return  appendLog("V", tag,  msg +'\n' + getStackTraceString(tr));
            return android.util.Log.v(tag,msg  +'\n' + getStackTraceString(tr));
        }

        public static int d(String tag, String msg) {
            if (GPusherConfig.out2file)  return  appendLog("D", tag, msg);
            return android.util.Log.d(tag, msg);
        }

        public static int d(String tag, String msg, Throwable tr) {
            if (GPusherConfig.out2file)  return  appendLog("D", tag,  msg +'\n' + getStackTraceString(tr));
            return android.util.Log.d(tag, msg+ '\n' + getStackTraceString(tr));
        }

        public static int i(String tag, String msg) {
            if (GPusherConfig.out2file)  return  appendLog("I", tag, msg);
            return android.util.Log.i(tag, msg);
        }

        public static int i(String tag, String msg, Throwable tr) {
            if (GPusherConfig.out2file)  return  appendLog("I", tag,  msg  +'\n' + getStackTraceString(tr));
            return android.util.Log.i(tag, msg  +'\n' + getStackTraceString(tr));
        }

        public static int w(String tag, String msg) {
            if (GPusherConfig.out2file)  return  appendLog("W", tag,  msg);
            return android.util.Log.w(tag, msg);
        }

        public static int w(String tag, String msg, Throwable tr) {
            if (GPusherConfig.out2file)  return  appendLog("W", tag,  msg  +'\n' + getStackTraceString(tr));
            return android.util.Log.w(tag,msg  +'\n' + getStackTraceString(tr));
        }

        public static boolean isLoggable(String s, int i) {
            return false;
        }

        public static int w(String tag, Throwable tr) {
            return android.util.Log.w(tag, getStackTraceString(tr));
        }

        public static int e(String tag, String msg) {
            if (GPusherConfig.out2file)  return  appendLog("E", tag,  msg);
            return android.util.Log.e(tag, msg);
        }

        public static int e(String tag, String msg, Throwable tr) {
            if (GPusherConfig.out2file)  return  appendLog("E", tag,  msg +'\n' + getStackTraceString(tr));
            return android.util.Log.e(tag, msg  +'\n' + getStackTraceString(tr));
        }

        public static int wtf(String tag, String msg) {
            return android.util.Log.wtf(tag, msg);
        }

        public static int wtf(String tag, Throwable tr) {
            return android.util.Log.w(tag, getStackTraceString(tr));
        }

        public static int wtf(String tag, String msg, Throwable tr) {
            return android.util.Log.wtf(tag, msg  +'\n' + getStackTraceString(tr));
        }

        public static int println(int priority, String tag, String msg) {
            return android.util.Log.println(priority, tag, msg);
        }
}
