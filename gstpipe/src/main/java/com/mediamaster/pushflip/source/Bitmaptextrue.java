package com.mediamaster.pushflip.source;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.view.Surface;

/**
 * Created by paladin on 16-7-31.
 */
public class Bitmaptextrue {
    public int mTexture;
    public SurfaceTexture mSurfaceTexture;
    Surface mSurface;
    public Bitmaptextrue(String path, int w, int h) {
        bindTexture(path, w, h);
    }
    private void bindTexture(String path, int w, int h) {
        Bitmap bitmap;

        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_REPEAT);

        try {
            bitmap = BitmapFactory.decodeFile(path);
            /*  init sprite size */
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
            bitmap.recycle();
        } catch (Exception e) {

        } finally {

        }
        mTexture = textures[0];
        mSurfaceTexture = new SurfaceTexture(textures[0]);
        mSurfaceTexture.setDefaultBufferSize(w, h);
        mSurface = new Surface(mSurfaceTexture);
    }
}
