package com.mediamaster.pushflip.source;
/*
 * ScreenRecordingSample
 * Sample project to cature and save audio from internal and video from screen as MPEG4 file.
 *
 * Copyright (c) 2015 saki t_saki@serenegiant.com
 *
 * File name: MediaScreenEncoder.java
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * All files in the folder are under this Apache License, Version 2.0.
*/

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.graphics.SurfaceTexture.OnFrameAvailableListener;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.opengl.EGLContext;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;

import com.mediamaster.pushflip.Log;
import com.mediamaster.pushflip.VideoSource;
import com.mediamaster.pushflip.glutils.EglTask;
import com.mediamaster.pushflip.glutils.FullFrameRect;
import com.mediamaster.pushflip.glutils.Texture2dProgram;
import com.mediamaster.pushflip.glutils.WindowSurface;

import java.io.IOException;

public class ScreenSource
        extends VideoEncoder {
    private static final boolean DEBUG = true;    //TODO set false on release
    private static final String TAG = "pushflip-ScreenSource";

    private static final String MIME_TYPE = "video/avc";
    // parameters for recording
    private static final int FRAME_RATE = 30;

    private MediaProjection mMediaProjection;
    private final int mDensity;

    private final Handler mHandler;

    private Surface mEncoderSurface;
    //private long lastDrawPts = 0;
    protected boolean mPrivateMode = false;


    public ScreenSource(
            //VideoEncoderListener listener,
            VideoSource.OnFrameAvaiableListener l,
            final MediaProjection projection, final int width, final int height, final int density) {

        //有开启线程
        super(l, width, height);

        mMediaProjection = projection;
        mDensity = density;

        //开启线程
        final HandlerThread thread = new HandlerThread(TAG);
        thread.start();
        mHandler = new Handler(thread.getLooper());
    }

    protected void release() {
        mHandler.getLooper().quit();
    }

    //准备开启线程
    public void prepare() throws IOException {
        if (DEBUG) Log.i(TAG, "prepare: ");
        mEncoderSurface = prepare_surface_encoder(MIME_TYPE, FRAME_RATE);
        mMediaCodec.start();
        mIsCapturing = true;
        //开启编码流程线程 问题？？？？？
        new Thread(mScreenCaptureTask, "ScreenCaptureThread").start();
        if (DEBUG) Log.i(TAG, "prepare finishing");
//        if (mListener != null) {
//        	try {
//        		mListener.onPrepared(this);
//        	} catch (final Exception e) {
//        		Log.e(TAG, "prepare:", e);
//        	}
//        }
    }

    public void setPrivateMode(boolean p) {
        mPrivateMode = p;
        mScreenCaptureTask.setPrivateMode(p);

    }

    public void startRecording() {
        if (DEBUG) Log.i(TAG, "startRecording: ");
    }

    public void stopRecording() {
        if (DEBUG) Log.v(TAG, "stopRecording:");
        synchronized (mSync) {
            mIsCapturing = false;
            mSync.notifyAll();
        }
    }

    private boolean requestDraw;
    private final DrawTask mScreenCaptureTask = new DrawTask(null, 0);

    private final class DrawTask extends EglTask {
        private VirtualDisplay display;
        private long intervals;
        private int mSourceTexId;
        private SurfaceTexture mSourceTexture;
        private Surface mSourceSurface;
        private WindowSurface mEncoderWindowSurface;
        private FullFrameRect mDrawer;
        private final float[] mTexMatrix = new float[16];

        //        private FullFrameRect mDrawer1;
//        private FullFrameRect mDrawer2;
//        private SurfaceTexture mSourceTexture1;
//        private Surface mSourceSurface1;
//        private SurfaceTexture mSourceTexture2;
//        private Surface mSourceSurface2;
//        private int mBitmapTexId1;
//        private int mBitmapTexId2;
//        private Bitmaptextrue mBitmaptextrue1;
//        private Bitmaptextrue mBitmaptextrue2;
//        private Bitmap bitmap1;
//        private Bitmap bitmap2;
        public DrawTask(final EGLContext shared_context, final int flags) {
            super(shared_context, flags);
        }

        Texture2dProgram mProgram;

        @Override
        protected void onStart() {
            if (DEBUG) Log.d(TAG, "mScreenCaptureTask#onStart:");
            mProgram = new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT);
            mDrawer = new FullFrameRect(mProgram);

//            mBitmaptextrue1 = new Bitmaptextrue("/sdcard/1.jpg", mWidth, mHeight);
//            mBitmaptextrue2 = new Bitmaptextrue("/sdcard/2.jpg", mWidth, mHeight);
//            mBitmapTexId1 = mBitmaptextrue1.mTexture;
//            mBitmapTexId2 = mBitmaptextrue2.mTexture;
//
////            Bitmap bitmap;
////            mDrawer1 = new FullFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
////            mBitmapTexId1 = mDrawer1.createTextureObject();
//            mSourceTexture1 = new SurfaceTexture(mBitmapTexId1);
//            mSourceTexture1.setDefaultBufferSize(mWidth, mHeight);
//            mSourceSurface1 = new Surface(mSourceTexture1);
////
////            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mBitmapTexId1);
////            try {
////                bitmap = BitmapFactory.decodeFile("/sdcard/1.jpg");
////            /*  init sprite size */
////                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
////                bitmap.recycle();
////            } catch (Exception e) {
////                Log.i(TAG, "e " + e.toString());
////            } finally {
////
////            }
////            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
////
////
////            mDrawer2 = new FullFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
//            mSourceTexture2 = new SurfaceTexture(mBitmapTexId2);
//            mSourceTexture2.setDefaultBufferSize(mWidth, mHeight);
//            mSourceSurface2 = new Surface(mSourceTexture2);
////
////            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mBitmapTexId2);
////            try {
////                bitmap = BitmapFactory.decodeFile("/sdcard/2.jpg");
////            /*  init sprite size */
////                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
////                bitmap.recycle();
////            } catch (Exception e) {
////                Log.i(TAG, "e " + e.toString());
////            } finally {
////
////            }
////
////
////            bitmap1 = BitmapFactory.decodeFile("/sdcard/1.jpg");
////            bitmap2 = BitmapFactory.decodeFile("/sdcard/2.jpg");

            mSourceTexId = mDrawer.createTextureObject();
            mSourceTexture = new SurfaceTexture(mSourceTexId);
            mSourceTexture.setDefaultBufferSize(mWidth, mHeight);
            mSourceSurface = new Surface(mSourceTexture);
            mSourceTexture.setOnFrameAvailableListener(mOnFrameAvailableListener, mHandler);

            mEncoderWindowSurface = new WindowSurface(getEglCore(), mEncoderSurface);

            if (DEBUG) Log.d(TAG, "setup VirtualDisplay");
            intervals = (long) (1000f / FRAME_RATE);
            display = mMediaProjection.createVirtualDisplay(
                    "Capturing Display",
                    mWidth, mHeight, mDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                    mSourceSurface, null, null);    //VIRTUAL_DISPLAY_FLAG_PUBLIC   VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR

            if (DEBUG) Log.v(TAG, "screen capture loop:display=" + display);
            queueEvent(mDrawTask);
        }

        @Override
        protected void onStop() {
            if (DEBUG) Log.d(TAG, "onStop:");
            if (mDrawer != null) {
                mDrawer.release();
                mDrawer = null;
            }
            if (mSourceSurface != null) {
                mSourceSurface.release();
                mSourceSurface = null;
            }
            if (mSourceTexture != null) {
                mSourceTexture.release();
                mSourceTexture = null;
            }
            if (mEncoderWindowSurface != null) {
                mEncoderWindowSurface.release();
                mEncoderWindowSurface = null;
            }
            makeCurrent();
            if (DEBUG) Log.v(TAG, "mScreenCaptureTask#onStop:");
            if (display != null) {
                if (DEBUG) Log.v(TAG, "release VirtualDisplay");
                display.release();
            }

//            if (DEBUG) Log.v(TAG, "tear down MediaProjection");
//            if (mMediaProjection != null) {
//                mMediaProjection.stop();
//                mMediaProjection = null;
//            }
        }

        @Override
        protected boolean onError(final Exception e) {
            if (DEBUG) Log.w(TAG, "mScreenCaptureTask:", e);
            return false;
        }

        @Override
        protected boolean processRequest(final int request, final int arg1, final Object arg2) {
            return false;
        }
        public void setPrivateMode(boolean p) {
//            if (mPrivateMode) {
//                private_start = System.currentTimeMillis();
//                DrawTask.PrivateThread pthread = new DrawTask.PrivateThread();
//                pthread.start();
//            }
        }
        protected final Object mDrawSync = new Object();

        public class PrivateThread extends Thread {
            @Override
            public void run(){
                while(mPrivateMode) {
                    synchronized (mDrawSync) {
//                        Log.i(TAG, "PrivateThread request Draw");
                        requestDraw = true;
                        mDrawSync.notifyAll();
                    }
                    try {
//                        Log.i(TAG, "PrivateThread sleep start");
                        Thread.sleep(500);
//                        Log.i(TAG, "PrivateThread sleep end");
                    } catch (InterruptedException e) {
                        Log.i(TAG, "PrivateThread sleep error");
                        e.printStackTrace();
                    }
                }
            }

        }
        private final OnFrameAvailableListener mOnFrameAvailableListener = new OnFrameAvailableListener() {
            @Override
            public void onFrameAvailable(final SurfaceTexture surfaceTexture) {
//                Log.i(TAG, "onFrameAvailable " + surfaceTexture);
                if (mPrivateMode)
                    return;
                if (mIsCapturing) {
                    synchronized (mDrawSync) {
                        requestDraw = true;
                        mDrawSync.notifyAll();
                    }
                }
//                Log.i(TAG, "onFrameAvailable finish " + surfaceTexture);
            }
        };

        //private long frame_i = 0;
        private long last_late = 0;
        private long segment_start = 0;
        private long segment_frame_i = 0;

        private long private_start = 0;
        private final Runnable mDrawTask = new Runnable() {
            @Override
            public void run() {
                boolean local_request_pause;   //请求暂停
                boolean local_request_draw = false;
                synchronized (mDrawSync) {
                    //Log.i(TAG, "run 11" + requestDraw);
                    if (requestDraw) {
                        local_request_pause = mRequestPause;
                        local_request_draw = requestDraw;
                        requestDraw = false;
                    } else {
                        try {
                            mDrawSync.wait(intervals*4);
                            local_request_pause = mRequestPause;
                            local_request_draw = requestDraw;
                            requestDraw = false;
                        } catch (final InterruptedException e) {
                            e.printStackTrace();
                            return;
                        }
                    }
                }

                if (mIsCapturing) {
//                    Log.i(TAG, "local_request_draw " + local_request_draw + " local_request_pause " + local_request_pause);

//                    if (mPrivateMode) {
//
//                        long dur = System.currentTimeMillis() - private_start;
//                        if ((dur/2000) == 1) {
//                            Log.i(TAG, "texImage2D bitmap1");
////                            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap1, 0);
//                            mSourceTexture1.updateTexImage();
//                            mSourceTexture1.getTransformMatrix(mTexMatrix);
//                            mEncoderWindowSurface.makeCurrent();
//                            mDrawer.drawFrame(mBitmapTexId1, mTexMatrix);
//                        } else {
//                            Log.i(TAG, "texImage2D bitmap2");
////                            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap2, 0);
//                            mSourceTexture2.updateTexImage();
//                            mSourceTexture2.getTransformMatrix(mTexMatrix);
//                            mEncoderWindowSurface.makeCurrent();
//                            mDrawer.drawFrame(mBitmapTexId2, mTexMatrix);
//                         }
//
//
//                        mEncoderWindowSurface.swapBuffers();
//                        makeCurrent();
//                        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
//                        GLES20.glFlush();
//                        frameAvailableSoon();
//                    } else
                    
                    if (local_request_draw) {
                        mSourceTexture.updateTexImage();
                        mSourceTexture.getTransformMatrix(mTexMatrix);
                        //long interv = (System.currentTimeMillis() - lastDrawPts);
                        long frame_late = (System.currentTimeMillis() - segment_start) - (intervals * segment_frame_i);   //帧间隔

                        if (frame_late < (long) (-1 * 0.3 * intervals)) {
//                            Log.v(TAG, "drop " + interv);
                            makeCurrent();
                            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                            GLES20.glFlush();
                        } else {
                            if (frame_late > 2 * intervals) {
                                segment_start = System.currentTimeMillis();
                                segment_frame_i = 0;
                            }

//                            Log.d(TAG, "encode " + segment_frame_i + " / " + frame_i + " " + interv + " late " + frame_late);

                            //lastDrawPts = System.currentTimeMillis();
                            //frame_i++;
                            segment_frame_i++;

                            mEncoderWindowSurface.makeCurrent();

                            mDrawer.drawFrame(mSourceTexId, mTexMatrix);
                            mEncoderWindowSurface.swapBuffers();
                            makeCurrent();
                            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                            GLES20.glFlush();
                            frameAvailableSoon();
//                            Log.i(TAG, "finish draw " + interv);
                        }
                    }
                    //流程继续
                    queueEvent(this);
                } else {
                    releaseSelf();  //销毁egl
                }
            }
        };

    }



}
