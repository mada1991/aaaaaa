package com.mediamaster.pushapi;

import android.app.ActivityManager;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.text.format.Formatter;

import com.mediamaster.pushflip.Log;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * Created by paladin on 16-5-12.
 */
public class Utils {
    private static final String TAG = "pushflip-Utils";

    public static void printSysInfo(Context c) {
        Log.i(TAG,"--------------  printSysInfo ------------");
        try {
            printPhoneInfo(c);
        }catch(Exception e) {
            e.printStackTrace();
        }
        printBuildInfo();
        String [] ci = getCpuInfo();
        for(String i : ci) {
            Log.i(TAG, "cpuinfo " + i);
        }
        Log.i(TAG, "macAdd:" + getMacAddress(c));
        Log.i(TAG, "TotalMemory " + getTotalMemory(c));
        Log.i(TAG, "AvailMemory " + getAvailMemory(c));
        Log.i(TAG,"--------------  printSysInfo ------------");
    }

    private static void printPhoneInfo(Context c) {
        PhoneInfo siminfo = new PhoneInfo(c);
        Log.i(TAG,"--------------  printPhoneInfo ------------");
        Log.i(TAG, "getProvidersName:" + siminfo.getProvidersName()
                + "\ngetNativePhoneNumber:" + siminfo.getNativePhoneNumber()
                + "\ngetPhoneInfo:" + siminfo.getPhoneInfo());

    }

    private static void printBuildInfo() {
        Log.i(TAG,"--------------  printBuildInfo ------------");
        Log.i(TAG, "BOARD:" + Build.BOARD);
        Log.i(TAG, "BOOTLOADER:" + Build.BOOTLOADER);
        Log.i(TAG, "BRAND:" + Build.BRAND);
        Log.i(TAG, "CPU_ABI:" + Build.CPU_ABI);
        Log.i(TAG, "CPU_ABI2:" + Build.CPU_ABI2);
        Log.i(TAG, "DEVICE:" + Build.DEVICE);
        Log.i(TAG, "DISPLAY:" + Build.DISPLAY);
        Log.i(TAG, "FINGERPRINT:" + Build.FINGERPRINT);
        Log.i(TAG, "HARDWARE:" + Build.HARDWARE);
        Log.i(TAG, "HOST:" + Build.HOST);
        Log.i(TAG, "ID:" + Build.ID);
        Log.i(TAG, "MANUFACTURER:" + Build.MANUFACTURER);
        Log.i(TAG, "MODEL:" + Build.MODEL);
        Log.i(TAG, "PRODUCT:" + Build.PRODUCT);
        Log.i(TAG, "RADIO:" + Build.RADIO);
        Log.i(TAG, "TAGS:" + Build.TAGS);
        Log.i(TAG, "TIME:" + Build.TIME);
        Log.i(TAG, "TYPE:" + Build.TYPE);
        Log.i(TAG, "UNKNOWN:" + Build.UNKNOWN);
        Log.i(TAG, "USER:" + Build.USER);
        Log.i(TAG, "VERSION.CODENAME:" + Build.VERSION.CODENAME);
        Log.i(TAG, "VERSION.INCREMENTAL:" + Build.VERSION.INCREMENTAL);
        Log.i(TAG, "VERSION.RELEASE:" + Build.VERSION.RELEASE);
        Log.i(TAG, "VERSION.SDK:" + Build.VERSION.SDK);
        Log.i(TAG, "VERSION.SDK_INT:" + Build.VERSION.SDK_INT);
    }
    private static String[] getCpuInfo() {
        String str1 = "/proc/cpuinfo";
        String str2 = "";
        String[] cpuInfo = {"", ""};  //1-cpu型号  //2-cpu频率
        String[] arrayOfString;
        try {
            FileReader fr = new FileReader(str1);
            BufferedReader localBufferedReader = new BufferedReader(fr, 8192);
            str2 = localBufferedReader.readLine();
            arrayOfString = str2.split("\\s+");
            for (int i = 2; i < arrayOfString.length; i++) {
                cpuInfo[0] = cpuInfo[0] + arrayOfString[i] + " ";
            }
            str2 = localBufferedReader.readLine();
            arrayOfString = str2.split("\\s+");
            cpuInfo[1] += arrayOfString[2];
            localBufferedReader.close();
        } catch (IOException e) {
        }
        //Log.i(TAG, "cpuinfo:" + cpuInfo[0] + " " + cpuInfo[1]);
        return cpuInfo;
    }

    private static String getMacAddress(Context c){
        String result = "";
        WifiManager wifiManager = (WifiManager) c.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        result = wifiInfo.getMacAddress();

        return result;
    }

    private static  String getAvailMemory(Context c) {// 获取android当前可用内存大小

        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        //mi.availMem; 当前系统的可用内存

        return Formatter.formatFileSize(c, mi.availMem);// 将获取的内存大小规格化
    }

    private  static String getTotalMemory(Context c) {
        String str1 = "/proc/meminfo";// 系统内存信息文件
        String str2;
        String[] arrayOfString;
        long initial_memory = 0;

        try {
            FileReader localFileReader = new FileReader(str1);
            BufferedReader localBufferedReader = new BufferedReader(
                    localFileReader, 8192);
            str2 = localBufferedReader.readLine();// 读取meminfo第一行，系统总内存大小

            arrayOfString = str2.split("//s+");
            for (String num : arrayOfString) {
                Log.i(TAG, num + "/t");
            }
            if (arrayOfString.length >= 2) {
                initial_memory = Integer.valueOf(arrayOfString[1]).intValue() * 1024;// 获得系统总内存，单位是KB，乘以1024转换为Byte
            }
            localBufferedReader.close();

        } catch (IOException e) {
        }
        return Formatter.formatFileSize(c, initial_memory);// Byte转换为KB或者MB，内存大小规格化
    }

//获取手机安装的应用信息（排除系统自带）：
//    private String getAllApp() {
//        String result = "";
//        List<PackageInfo> packages = getPackageManager().getInstalledPackages(0);
//        for (PackageInfo i : packages) {
//            if ((i.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
//                result += i.applicationInfo.loadLabel(getPackageManager()).toString() + ",";
//            }
//        }
//        return result.substring(0, result.length() - 1);
//    }

}
