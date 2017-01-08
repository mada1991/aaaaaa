LOCAL_PATH := $(call my-dir)

# build/armeabi-v7a/lib/

#include $(CLEAR_VARS)
#LOCAL_MODULE := avcodec-55-prebuilt
#LOCAL_SRC_FILES := prebuilt/libavcodec-55.so
#LOCAL_SRC_FILES := build/armeabi-v7a/lib/libavcodec.a
#include $(PREBUILT_SHARED_LIBRARY)
#
#include $(CLEAR_VARS)
#LOCAL_MODULE := avdevice-55-prebuilt
#LOCAL_SRC_FILES := prebuilt/libavdevice-55.so
#include $(PREBUILT_SHARED_LIBRARY)
#
#include $(CLEAR_VARS)
#LOCAL_MODULE := avfilter-4-prebuilt
#LOCAL_SRC_FILES := prebuilt/libavfilter-4.so
#include $(PREBUILT_SHARED_LIBRARY)
#
#include $(CLEAR_VARS)
#LOCAL_MODULE := avformat-55-prebuilt
#LOCAL_SRC_FILES := prebuilt/libavformat-55.so
#include $(PREBUILT_SHARED_LIBRARY)
#
#include $(CLEAR_VARS)
#LOCAL_MODULE :=  avutil-52-prebuilt
#LOCAL_SRC_FILES := prebuilt/libavutil-52.so
#include $(PREBUILT_SHARED_LIBRARY)
#
#include $(CLEAR_VARS)
#LOCAL_MODULE :=  avswresample-0-prebuilt
#LOCAL_SRC_FILES := prebuilt/libswresample-0.so
#include $(PREBUILT_SHARED_LIBRARY)
#
#include $(CLEAR_VARS)
#LOCAL_MODULE :=  swscale-2-prebuilt
#LOCAL_SRC_FILES := prebuilt/libswscale-2.so
#include $(PREBUILT_SHARED_LIBRARY)
#

#include $(CLEAR_VARS)
#LOCAL_MODULE := avcodec-55-prebuilt
#LOCAL_SRC_FILES := lib/libavcodec-55.so
#include $(PREBUILT_SHARED_LIBRARY)
#
#include $(CLEAR_VARS)
#LOCAL_MODULE := avdevice-55-prebuilt
#LOCAL_SRC_FILES := lib/libavdevice-55.so
#include $(PREBUILT_SHARED_LIBRARY)
#
#include $(CLEAR_VARS)
#LOCAL_MODULE := avfilter-4-prebuilt
#LOCAL_SRC_FILES := lib/libavfilter-4.so
#include $(PREBUILT_SHARED_LIBRARY)
#
#include $(CLEAR_VARS)
#LOCAL_MODULE := avformat-55-prebuilt
#LOCAL_SRC_FILES := lib/libavformat-55.so
#include $(PREBUILT_SHARED_LIBRARY)
#
#include $(CLEAR_VARS)
#LOCAL_MODULE :=  avutil-52-prebuilt
#LOCAL_SRC_FILES := lib/libavutil-52.so
#include $(PREBUILT_SHARED_LIBRARY)
#
#include $(CLEAR_VARS)
#LOCAL_MODULE :=  avswresample-0-prebuilt
#LOCAL_SRC_FILES := lib/libswresample-0.so
#include $(PREBUILT_SHARED_LIBRARY)
#
#include $(CLEAR_VARS)
#LOCAL_MODULE :=  swscale-2-prebuilt
#LOCAL_SRC_FILES := lib/libswscale-2.so
#include $(PREBUILT_SHARED_LIBRARY)

#####################################################################
#include $(CLEAR_VARS)
#LOCAL_MODULE := ffmpeg_jni
#LOCAL_SRC_FILES := ffmpegnative.c rtmp_sender.c
#
#LOCAL_LDLIBS := -llog -ljnigraphics -lz -landroid 
#LOCAL_SHARED_LIBRARIES := avcodec-55-prebuilt avdevice-55-prebuilt avfilter-4-prebuilt avformat-55-prebuilt avutil-52-prebuilt
#
#include $(BUILD_SHARED_LIBRARY)
#####################################################################

#include $(CLEAR_VARS)
#LOCAL_MODULE :=  ffmpeg_all
#
#LOCAL_SRC_FILES := empty.c
#
#LOCAL_LDLIBS := lib_static/libavformat.a
#LOCAL_LDLIBS += lib_static/libavcodec.a
#LOCAL_LDLIBS += lib_static/libavutil.a
#LOCAL_LDLIBS += lib_static/libavfilter.a
#LOCAL_LDLIBS += lib_static/libswscale.a
#LOCAL_LDLIBS += lib_static/libswresample.a
#LOCAL_LDLIBS += lib_static/libx264.a
#
#include $(BUILD_SHARED_LIBRARY)



include $(CLEAR_VARS)
LOCAL_MODULE := ffmpeg_jni
#LOCAL_SRC_FILES := ffmpeg_encoder_muxer_jni.c  muxing.c 
LOCAL_SRC_FILES := ffmpegnative.c  rtmp_sender.c avqueue.c

LOCAL_C_INCLUDES += $(LOCAL_PATH)/av_include
LOCAL_C_INCLUDES += $(LOCAL_PATH)/spe_include


LOCAL_LDLIBS := -llog  -lz -landroid 

LOCAL_LDLIBS += slib/libavformat.a
LOCAL_LDLIBS += slib/libavcodec.a
LOCAL_LDLIBS += slib/libavutil.a
LOCAL_LDLIBS += slib/libavfilter.a
LOCAL_LDLIBS += slib/libswscale.a
LOCAL_LDLIBS += slib/libswresample.a
LOCAL_LDLIBS += slib/libspeex.a
#LOCAL_LDLIBS += slib/libx264.a

#LOCAL_SHARED_LIBRARIES := avcodec-55-prebuilt avdevice-55-prebuilt avfilter-4-prebuilt avformat-55-prebuilt avutil-52-prebuilt swscale-2-prebuilt  avswresample-0-prebuilt

include $(BUILD_SHARED_LIBRARY)
