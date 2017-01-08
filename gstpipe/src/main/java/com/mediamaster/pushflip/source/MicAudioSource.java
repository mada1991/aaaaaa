package com.mediamaster.pushflip.source;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Environment;

import com.mediamaster.pushflip.AudioSource;
import com.mediamaster.pushflip.GpusherService;
import com.mediamaster.pushflip.Log;
import com.mediamaster.pushflip.PushEvent;
import com.mediamaster.pushflip.utils;

import org.apache.commons.net.ftp.parser.MLSxEntryParser;
import com.mediamaster.ffmpegwrap.NativeFfmpegSender;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutionException;


/**
 * Created by paladin on 16-4-12.
 */


public class MicAudioSource extends AudioSource {
    private static final String aMIME_TYPE = "audio/mp4a-latm";
    private static final int SAMPLE_RATE = 44100;    // 44.1[KHz] is only setting guaranteed to be available on all devices.
    private static final int BIT_RATE = 64000;
    private AudioThread mAudioThread = null;

    private static final long TIMEOUT_US = 10000;
    private MediaCodec.BufferInfo aBufferInfo = new MediaCodec.BufferInfo();
    private int mAudioTrackIndex = -1;
    long mFirstPts = -1;
    long last_enqueue_pts = -1;
    private boolean mInit = false;

    private static final String TAG = "pushflip-MicAudioSource";

    private MediaCodec mAudioEncoder;
    private OnSampleAvaiableListener mListener = null;

    //自己添加
    File file_02 = null;
    File file_03 = null;

    //pcm
    private NativeFfmpegSender mSender;

    public MicAudioSource(OnSampleAvaiableListener listner) {
        mListener = listner;
    }

    public class MyDequeueSampleInfo {
        public MyDequeueSampleInfo(ByteBuffer data, int size, long pts) {
            frameData = data;
            frameData_size = size;
            presentationTimeUs = pts;
        }

        ByteBuffer frameData;
        int frameData_size;
        int flags;
        long presentationTimeUs;
    }

    public class MyEnqueueSampleInfo {
        public MyEnqueueSampleInfo(long pts) {
            presentationTimeUs = pts;
        }

        long presentationTimeUs;
    }

    public class DeSortComparator implements Comparator {
        @Override
        public int compare(Object s, Object e) {

            return (int) (((MyDequeueSampleInfo) s).presentationTimeUs - ((MyDequeueSampleInfo) e).presentationTimeUs);
        }
    }

    public class EnSortComparator implements Comparator {
        @Override
        public int compare(Object s, Object e) {

            return (int) (((MyEnqueueSampleInfo) s).presentationTimeUs - ((MyEnqueueSampleInfo) e).presentationTimeUs);
        }
    }

    private DeSortComparator mDeSortComparator = new DeSortComparator();
    private EnSortComparator mEnSortComparator = new EnSortComparator();

    ArrayList<MyDequeueSampleInfo> mMyDequeueFrameInfoQueue = new ArrayList<MyDequeueSampleInfo>();
//    ArrayList<MyEnqueueSampleInfo> mMyEnqueueFrameInfoQueue = new ArrayList<MyEnqueueSampleInfo>();

    private void prepareEncoder() throws IOException {
        final MediaFormat audioFormat = MediaFormat.createAudioFormat(aMIME_TYPE, SAMPLE_RATE, 1);
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_MONO);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);

        mAudioEncoder = MediaCodec.createEncoderByType(aMIME_TYPE);
        mAudioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mAudioEncoder.start();
    }

    @Override
    public void start() {
        Log.i(TAG, "start");
        //write_file_aac();
        //write_file_aac02();
        try {
            prepareEncoder();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (mAudioThread == null) {
            mAudioThread = new AudioThread();
            mAudioThread.start();
        }
    }

    public void pause() {

    }

    public void resume() {

    }

    private void encodeToAudioTrack(MediaCodec localEncoder, int index, int TrackIndex) {
        MediaCodec localMediaCodec = localEncoder;

//        ByteBuffer encodedData = localMediaCodec.getOutputBuffer(index);

        ByteBuffer[] outputBuffers = localMediaCodec.getOutputBuffers();
        ByteBuffer encodedData = outputBuffers[index];

        if ((aBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            //Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
            aBufferInfo.size = 0;
        }
        if (aBufferInfo.size == 0) {
            // Log.d(TAG, "info.size == 0, drop it.");
            encodedData = null;
        } else {
//            Log.d(TAG, "Audio got buffer, info: size=" + aBufferInfo.size
//                    + ", presentationTimeUs=" + aBufferInfo.presentationTimeUs
//                    + ", " + (mBufferInfo.presentationTimeUs - mFirstPts)/1000
//                    + ", offset=" + aBufferInfo.offset);
        }
        //aBufferInfo.presentationTimeUs = getPTSUs() -120000 ;
        if (encodedData != null) {
            encodedData.position(aBufferInfo.offset);
            encodedData.limit(aBufferInfo.offset + aBufferInfo.size);
            //TODO:  音频推流
            if (mListener != null)
                mListener.onSampleAvalailable(encodedData, aBufferInfo.offset, aBufferInfo.size, aBufferInfo.flags, aBufferInfo.presentationTimeUs);
        }
    }

    protected void write_file_02_aac(ByteBuffer aac_data,int s_size) throws IOException {
        FileOutputStream fout = new FileOutputStream(file_02.getPath(),true);
        byte[] bytes = new byte[aac_data.remaining()];
        aac_data.get(bytes);
        fout.write(bytes);
        fout.close();
    }

    protected void write_file_byte_aac(byte aac_data[],int s_size) throws IOException {
        FileOutputStream fout = new FileOutputStream(file_02.getPath(),true);
        fout.write(aac_data);
        fout.close();
    }

    protected void write_file_byte_aac_02(byte aac_data[],int s_size) throws IOException {
        FileOutputStream fout = new FileOutputStream(file_03.getPath(),true);
        fout.write(aac_data);
        fout.close();
    }

    protected  void write_file_aac() {
        //String s_s =  Environment.getExternalStorageDirectory().getPath();
        String s_s =  Environment.getExternalStorageDirectory().getAbsolutePath();

        //创建文件夹及文件
        //file_02 = new File(s_s + "/AAC_Data/", "Acc_Demo.pcm");
        //file_02 = new File(s_s + "/AAC_Data/", "Acc_Demo.flv");
        file_02 = new File(s_s, "Acc_Demo.flv");

        if (!file_02.exists()) {
            try {
                //按照指定的路径创建文件夹
                Log.i(TAG, "00000001:" + file_02.getPath());
                //file_02.mkdirs();    /storage/sdcard/Acc_Demo.aac
                //file_02.createNewFile();
                if (false == file_02.createNewFile())
                {
                    Log.i(TAG, "00000001: 创建失败");
                }
            } catch (Exception e) {
                // TODO: handle exception
            }
        }

//        File sdDir = null;
//        boolean sdCardExist = Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED); //判断sd卡是否存在
//        if (sdCardExist)
//        {
//            //getExternalStorageDirectory
//            //getExternalStorageDirectory
//            sdDir = Environment.getExternalStorageDirectory();//获取跟目录
//            Log.i(TAG, "0000000222222222222221:" + sdDir.getPath());
//        }

    }

    protected  void write_file_aac02() {
        String s_s =  Environment.getExternalStorageDirectory().getPath();
        //创建文件夹及文件
        file_03 = new File(s_s + "/AAC_Data/", "Acc_Demo_02.pcm");
        if (!file_03.exists()) {
            try {
                //按照指定的路径创建文件夹
                Log.i(TAG, "00000001:" + file_03.getPath());
                //file_02.mkdirs();
                file_03.createNewFile();
            } catch (Exception e) {
                // TODO: handle exception
            }
        }
    }

    //音频编码
    protected void encode(byte[] buffer, int length, long presentationTimeUs) {
        int ix = 0, sz;
        int inputBufferIndex = MediaCodec.INFO_TRY_AGAIN_LATER;
        final ByteBuffer[] inputBuffers = mAudioEncoder.getInputBuffers();
        while (!mQuit.get() && ix < length) {
            try {
                inputBufferIndex = mAudioEncoder.dequeueInputBuffer(TIMEOUT_US);

                if (inputBufferIndex >= 0) {
                final ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                inputBuffer.clear();
                sz = inputBuffer.remaining();
                sz = (ix + sz < length) ? sz : length - ix;
                if (sz > 0 && (buffer != null)) {
                    inputBuffer.put(buffer, ix, sz);
                }
                ix += sz;
                if (length <= 0) {
                    mAudioEncoder.queueInputBuffer(inputBufferIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    break;
                } else {
                    mAudioEncoder.queueInputBuffer(inputBufferIndex, 0, sz,
                            presentationTimeUs, 0);
                }
            } else if (inputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {

            }

            int index2 = mAudioEncoder.dequeueOutputBuffer(aBufferInfo, TIMEOUT_US);
            if (index2 == MediaCodec.INFO_TRY_AGAIN_LATER) {

            } else if (index2 >= 0) {
                if (!mInit) {
                    mInit = true;
                    initOutputFormat();
                }
                encodeToAudioTrack(mAudioEncoder, index2, mAudioTrackIndex);
                mAudioEncoder.releaseOutputBuffer(index2, false);
            }

            } catch (Exception e) {
                Log.w(TAG, "audio encoder met Exception " + e.toString() + " stacktrace "+ e.getStackTrace());
                GpusherService.setInternalExeptionBroadcast();
                return;
            }
        }
    }
    
    private int  ERROR_INVALID_OPERATION_num = 0;
    class AudioThread extends Thread {
        @Override
        public void run() {
            final int buf_sz = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            //采样率： 22050  11025  16000 44100
            final AudioRecord audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, buf_sz);

            try {
                final byte[] buf = new byte[buf_sz];
                int readBytes;
                audioRecord.startRecording();
                try {
                    while (!mQuit.get()) {
                        // read audio data from internal mic
                        readBytes = audioRecord.read(buf, 0, buf_sz);

                        if (AudioRecord.ERROR_INVALID_OPERATION == readBytes) {
                            ERROR_INVALID_OPERATION_num++;
                            Log.i(TAG, "ERROR_INVALID_OPERATION " + ERROR_INVALID_OPERATION_num);
                            if (ERROR_INVALID_OPERATION_num < 10) {
                                Thread.sleep(50);
                                continue;
                            } else {
                                mListener.onAudioSourceError(PushEvent.error_audiorecord_failed);
                            }
                        }

                        ERROR_INVALID_OPERATION_num = 0;
                        if (readBytes > 0 && !mQuit.get()) {
                            // set audio data to encoder
                            if (mFirstPts < 0) {
                                mFirstPts = System.nanoTime();
                            }
                            long now_pts = (System.nanoTime() - mFirstPts) / 1000;
                            if (now_pts < last_enqueue_pts) {
                                Log.w(TAG, "enqueue last " + last_enqueue_pts + " now " + now_pts + " gap %d"
                                        + (last_enqueue_pts - now_pts));
                            }
                            last_enqueue_pts = now_pts;

                            //try {
                                //write_file_byte_aac(buf,readBytes);
                            //}catch (Exception e) {}

                            //1.把音频数据传到native层做降噪处理
                            //byte[] denoiser_buf =  mListener.sendPcmData( buf );

                           //Log.i(TAG, "......buf_03 "+Arrays.toString(buf03));
                           // try {
                                //write_file_byte_aac_02(buf03,readBytes);
                            //}catch (Exception e){}

                            //encode(denoiser_buf, readBytes, now_pts);
                            encode(buf, readBytes, now_pts);
                        } else {
                            Log.i(TAG, "readBytes " + readBytes);
                            Thread.sleep(50);
                        }

                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    Log.i(TAG, "audioRecord stop ");
                    audioRecord.stop();
                }
            } finally {
                Log.i(TAG, "audioRecord release ");
                audioRecord.release();
            }
        }
    }

    private void initOutputFormat() {
        /****************** init audio ************************************/
        MediaFormat audioTrackFormat = mAudioEncoder.getOutputFormat();
        int channels = audioTrackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        int samplerate = audioTrackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        ByteBuffer audio_extract_data = audioTrackFormat.getByteBuffer("csd-0");
        byte[] audio_extract_data_nal = new byte[audio_extract_data.remaining()];
        audio_extract_data.get(audio_extract_data_nal);
        Log.i(TAG, "handleAddTrack Init aac , " + channels + " X " + samplerate + " configure audio_extract_data_nal: " + utils.bytesToHex(audio_extract_data_nal));

        mListener.initAudioCodec(channels, samplerate, audio_extract_data_nal);
    }

}


