package com.mediamaster.pushflip;

/**
 * Created by paladin on 16-4-12.
 */
public class utils {
    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        return  bytesToHex(bytes, bytes.length);
    }

    public static String bytesToHex(byte[] bytes, int len) {
        char[] hexChars = new char[len * 3];
        for ( int j = 0; j < len; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 3] = hexArray[v >>> 4];
            hexChars[j * 3 + 1] = hexArray[v & 0x0F];
            if (j > 0  && (j + 1)%32 == 0)
                hexChars[j*3+2] = '\n';
            else
                hexChars[j*3+2] = ' ';


        }
        return new String(hexChars);
    }


}
