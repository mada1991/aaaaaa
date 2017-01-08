LOCAL_PATH := $(call my-dir)


include $(CLEAR_VARS)
LOCAL_MODULE := ffmpeg_jni_x86
LOCAL_SRC_FILES := ffmpegnative.c  rtmp_sender.c avqueue.c

LOCAL_C_INCLUDES += $(LOCAL_PATH)/av_include


LOCAL_LDLIBS := -llog  -lz -landroid 

LOCAL_LDLIBS += slib/libavformat.a
LOCAL_LDLIBS += slib/libavcodec.a
LOCAL_LDLIBS += slib/libavutil.a
LOCAL_LDLIBS += slib/libavfilter.a
#LOCAL_LDLIBS += slib/libswscale.a
#LOCAL_LDLIBS += slib/libswresample.a
#LOCAL_LDLIBS += slib/libx264.a

#LOCAL_SHARED_LIBRARIES := avcodec-55-prebuilt avdevice-55-prebuilt avfilter-4-prebuilt avformat-55-prebuilt avutil-52-prebuilt swscale-2-prebuilt  avswresample-0-prebuilt

include $(BUILD_SHARED_LIBRARY)
