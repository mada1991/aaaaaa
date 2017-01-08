LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_C_INCLUDES += $(NDK_PROJECT_PATH)/librtmp \

#	$(SSL)/include

LOCAL_SRC_FILES:= \
	ffmpegnative.c

LOCAL_LDLIBS := -llog  -lz  
LOCAL_SHARED_LIBRARIES  := rtmp
LOCAL_MODULE := libffmpeg_jni

include $(BUILD_SHARED_LIBRARY)

