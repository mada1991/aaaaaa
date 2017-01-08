#include <string.h>
#include <jni.h>
#include <android/log.h>

#include <math.h>
#include "screenrecord.h"


#define ALOGI(x...)    __android_log_print (ANDROID_LOG_INFO, "screenrecord_jni", x);
#define ALOGE(x...)    __android_log_print (ANDROID_LOG_ERROR, "screenrecord_jni", x);

typedef struct fields_t {
    jfieldID context;
} fields_t;

static fields_t gFields;

static int native_init (JNIEnv* env, jobject thiz) {
    ALOGI("%s entry now",__FUNCTION__);

    rtmp_sender_init();
    return 0;
}


static int native_connect_rtmp (JNIEnv* env, jobject thiz, jstring url) {
    if (url == NULL) {
        //jniThrowException(env, "java/lang/IllegalArgumentException", NULL);
        ALOGE("url is NULL");
        return -1;
    }
    
    const char *tmp = (*env)->GetStringUTFChars(env, url, NULL);
    if (tmp == NULL) {  // Out of memory
        ALOGE("get url char is NULL");
        return -1;
    }
    ALOGI("setDataSource: path %s", tmp);
    
    connect_rtmp(tmp);

    (*env)->ReleaseStringUTFChars(env, url, tmp);
    tmp = NULL;
    
    return 0;
}



static int g_init_spspps = 0;

void onFrameAvaliable(void *frame, int size, int type, int64_t ts) {
    int flags = 0;

    switch(type) {
    case FrameType_spspps:
        set_vcodec(540, 960, frame, size);
        g_init_spspps = 1;
        break;
    case FrameType_esds:
        //set_acodec(sample_rate, channels, e, esize);
        break;
    case FrameType_video_iframe:
    case FrameType_video_frame:
        if(type == FrameType_video_iframe) {
            flags = 1;
        }

        putBuffer(1, frame, size, ts, flags);
        break;
    default:
        break;
    }
}

static int native_start(JNIEnv* env, jobject thiz) {
    ALOGI("%s entry",__FUNCTION__);

    initRecorder(540, 960, onFrameAvaliable);
    startRecoder();

    while(g_init_spspps == 0) {
        ALOGI("wait spspps ");
        usleep(100000);
    }

   start_send_data();
   return 0;
}

static void native_stop (JNIEnv* env, jobject thiz) {
    ALOGI("%s entry",__FUNCTION__);
    rtmp_sender_stop();
    stop_recordScreen();
    return ;
}


static JNINativeMethod native_methods[] = {
  { "nativeInit", "()I", (void *) native_init},
  { "nativeConnRtmp", "(Ljava/lang/String;)I", (void *) native_connect_rtmp},
  { "nativeStart", "()I", (void *) native_start},
  { "nativeStop", "()V", (void *) native_stop}
};

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
  JNIEnv *env = NULL;

  if ((*vm)->GetEnv(vm, (void**) &env, JNI_VERSION_1_4) != JNI_OK) {
    __android_log_print (ANDROID_LOG_ERROR, "screenrecord_jni", "Could not retrieve JNIEnv");
    return 0;
  }

  jclass klass = (*env)->FindClass (env, "com/mediamaster/ffmpegwrap/NativeScreenRecord");
  (*env)->RegisterNatives (env, klass, native_methods,  sizeof(native_methods)/sizeof(native_methods[0]));

  return JNI_VERSION_1_4;
}
