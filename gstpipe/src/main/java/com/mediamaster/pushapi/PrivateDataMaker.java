package com.mediamaster.pushapi;

import android.content.Context;

import com.mediamaster.androidtranscoder.MediaTranscoder;
import com.mediamaster.androidtranscoder.format.MediaFormatStrategyPresets;
import com.mediamaster.pushflip.Log;
import com.mediamaster.pushflip.source.ScreenVideoSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Created by paladin on 16-8-6.
 */
public class PrivateDataMaker {
    private String fileDir = null;
    private String privateOrgDir = null;
    private String privateGenDir = null;
    private Context mContext;
    private static final String TAG = "pushflip-PrivateDataMaker";

    public  PrivateDataMaker(Context appContext) {
        mContext = appContext;
        if ( mContext.getFilesDir() == null) {
            fileDir = mContext.getApplicationContext().getFilesDir().toString();
        } else {
            fileDir = mContext.getFilesDir().getAbsolutePath();

        }
        privateOrgDir = fileDir + "/privateOrgData";
        File f = new File(privateOrgDir);
        if(!f.exists()) {
            f.mkdir();
        } else if (!f.isDirectory()){
            f.delete();
            f.mkdir();
        }


        privateGenDir = fileDir + "/privateGenData";
        f = new File(privateGenDir);
        if(!f.exists()) {
            f.mkdir();
        } else if (!f.isDirectory()){
            f.delete();
            f.mkdir();
        }
    }



    public boolean isExistPrivateName(String name) {
        String privateOrgPath = privateOrgDir + "/privateorg_"+ name + ".mp4";
        File f = new File(privateOrgPath);
        return f.exists();
    }
    public boolean isExistPrivateGenData(String name, int width, int height) {
        String outPath =  privateGenDir + "/private_" + name + "_" +width +"x" + height +".mp4";
        File f = new File(outPath);
        return f.exists();
    }
    public String  getPrivateGenDataPath(String name, int width, int height) {
        return privateGenDir + "/private_" + name + "_" + width + "x" + height + ".mp4";
    }
    public List<String> getPrivateDataList() {
        File dir=new File(privateOrgDir);
        List<String> mlist = new ArrayList<String>();
        File[] subFiles=dir.listFiles();
        for(File f:subFiles) {
            String name = f.getName();
            if (name.startsWith("privateorg_") && name.endsWith(".mp4")) {
                mlist.add(name.substring("privateorg_".length(),name.length()-".mp4".length()));
            }
        }

        return mlist;
    }

    /**
     * 不需要等待生成完成。立刻返回
     * @param name
     * @param width
     * @param height
     */
    public void genPrivateDataAsync(String name, int width, int height) {
        String privateOrgPath = privateOrgDir + "/privateorg_"+ name + ".mp4";
        PrivateGenThread p = new PrivateGenThread(name, privateOrgPath, privateGenDir, width,height );
        p.start();
    }
    /**
     * 等待生成完成。再返回
     * @param name
     * @param width
     * @param height
     */
    public void genPrivateData(String name, int width, int height) {
        String privateOrgPath = privateOrgDir + "/privateorg_"+ name + ".mp4";
        PrivateGenThread p = new PrivateGenThread(name, privateOrgPath, privateGenDir, width,height );
        p.start();
        try {
            p.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public boolean addPrivateData(String name, String path) {
        String outpath = privateOrgDir + "/privateorg_"+ name + ".mp4";
        copyFile(path, outpath);
        return true;
    }

    public void copyFile(String oldPath, String newPath) {
        try {
            int bytesum = 0;
            int byteread = 0;
            File oldfile = new File(oldPath);
            if (oldfile.exists()) { //文件存在时
                InputStream inStream = new FileInputStream(oldPath); //读入原文件
                FileOutputStream fs = new FileOutputStream(newPath);
                byte[] buffer = new byte[1444];
                int length;
                while ( (byteread = inStream.read(buffer)) != -1) {
                    bytesum += byteread; //字节数 文件大小
                    System.out.println(bytesum);
                    fs.write(buffer, 0, byteread);
                }
                inStream.close();
            }
        }
        catch (Exception e) {
            System.out.println("复制单个文件操作出错");
            e.printStackTrace();

        }

    }

    public static final String SystemDefaultPrivateData = "SystemDefaultPrivateData";
    private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;
    private static final int EOF = -1;


    public  boolean copyPrivateBinaryFromAssetsToData() {
        // create files directory under /data/data/package name
        File filesDirectory = new File(fileDir);
        String outpath = privateOrgDir + "/privateorg_"+ SystemDefaultPrivateData + ".mp4";

        InputStream is;
        try {
            File myFile = new File(outpath);
            if (myFile.exists()) {
                return true;
            }
            is = mContext.getAssets().open(SystemDefaultPrivateData);


            // copy ffmpeg file from assets to files dir
            Log.i(TAG, "copyPrivateBinaryFromAssetsToData fileNameFromAssets " + SystemDefaultPrivateData + " => " + filesDirectory + " filename " + SystemDefaultPrivateData);


            final FileOutputStream os = new FileOutputStream(myFile);
            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];

            int n;
            while (EOF != (n = is.read(buffer))) {
                os.write(buffer, 0, n);
            }

            os.close();
            is.close();
            return true;
        } catch (IOException e) {
            Log.e(TAG, "copyPrivateBinaryFromAssetsToData issue in coping binary from assets to data. ", e);
        }
        return false;
    }

    class PrivateGenThread extends  Thread {
        private int width;
        private  int height;
        private String name;
        private String path;
        private String outDir;
        private Future<Void> mFuture;
        public PrivateGenThread(String n,String p, String od, int w, int h) {
            name = n;
            path = p;
            outDir = od;
            width = w;
            height = h;
        }
        @Override
        public void run(){
            final String outPath =  outDir + "/private_" + name + "_" +width +"x" + height +".mp4";
            final String tmp_outpaht = outPath + "_tmp";
            File f = new File(outPath);
            if (f.exists()) {
                Log.w(TAG, "exist return");
                return;
            }

            MediaTranscoder.Listener listener = new MediaTranscoder.Listener() {
                @Override
                public void onTranscodeProgress(double progress) {
                    Log.i(TAG, "PrivateGenThread " + progress);
                }

                @Override
                public void onTranscodeCompleted() {
                    Log.i(TAG, "PrivateGenThread onTranscodeCompleted");
                    copyFile(tmp_outpaht, outPath);
                }

                @Override
                public void onTranscodeCanceled() {
                    Log.i(TAG, "PrivateGenThread Transcoder canceled");
                }

                @Override
                public void onTranscodeFailed(Exception exception) {
                    Log.i(TAG, "PrivateGenThread Transcoder error occurred");
                }
            };


            try {
                mFuture = MediaTranscoder.getInstance().transcodeVideo(path, tmp_outpaht,
                        MediaFormatStrategyPresets.createAndroidAnyStrategy(width, height), listener);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }
}
