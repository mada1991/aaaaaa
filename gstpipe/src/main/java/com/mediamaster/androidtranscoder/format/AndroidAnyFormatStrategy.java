/*
 * Copyright (C) 2014 Yuya Tanaka
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mediamaster.androidtranscoder.format;

import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

class AndroidAnyFormatStrategy implements MediaFormatStrategy {
    public static final int AUDIO_BITRATE_AS_IS = -1;
    public static final int AUDIO_CHANNELS_AS_IS = -1;
    private static final String TAG = "720pFormatStrategy";
    private static  int mWidth = 1280;
    private static  int mHeight = 720;
    private static int mFrameRate = 24;
    private static final int DEFAULT_VIDEO_BITRATE = 8000 * 1000; // From Nexus 4 Camera in 720p
    private final int mVideoBitrate;
    private final int mAudioBitrate;
    private final int mAudioChannels;

    public AndroidAnyFormatStrategy(int w, int h) {
        mWidth = w;
        mHeight = h;
        mVideoBitrate = calcBitRate(mFrameRate);
        mAudioBitrate = AUDIO_BITRATE_AS_IS;
        mAudioChannels = AUDIO_CHANNELS_AS_IS;
    }

    private static final float BPP = 0.16f;
    protected int calcBitRate(final int frameRate) {
        final int bitrate = (int) (BPP * frameRate * mWidth * mHeight);
        Log.i(TAG, String.format("try bitrate=%5.2f[Mbps] -> %5.2f[Mbps]", bitrate / 1024f / 1024f, bitrate / 1024f / 1024f * 0.7f));

        return bitrate;
    }
    @Override
    public MediaFormat createVideoOutputFormat(MediaFormat inputFormat) {
        int width = inputFormat.getInteger(MediaFormat.KEY_WIDTH);
        int height = inputFormat.getInteger(MediaFormat.KEY_HEIGHT);
        int longer, shorter, outWidth, outHeight;
        outWidth = mWidth;
        outHeight = mHeight;

        MediaFormat format = MediaFormat.createVideoFormat("video/avc", outWidth, outHeight);
        // From Nexus 4 Camera in 720p
        format.setInteger(MediaFormat.KEY_BIT_RATE, mVideoBitrate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 4);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        return format;
    }

    @Override
    public MediaFormat createAudioOutputFormat(MediaFormat inputFormat) {
        if (mAudioBitrate == AUDIO_BITRATE_AS_IS || mAudioChannels == AUDIO_CHANNELS_AS_IS) return null;

        // Use original sample rate, as resampling is not supported yet.
        final MediaFormat format = MediaFormat.createAudioFormat(MediaFormatExtraConstants.MIMETYPE_AUDIO_AAC,
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE), mAudioChannels);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_BIT_RATE, mAudioBitrate);
        return format;
    }
}
