#include <string.h>
#include <pthread.h>

#include <jni.h>
#include <android/log.h>

#include <math.h>
#include <libavutil/opt.h>
#include <libavcodec/avcodec.h>
#include <libavutil/channel_layout.h>
#include <libavutil/common.h>
#include <libavutil/imgutils.h>
#include <libavutil/mathematics.h>
#include <libavutil/samplefmt.h>


#define ALOGD(x...)    __android_log_print (ANDROID_LOG_DEBUG, "ffmpegnative", x);
#define ALOGI(x...)    __android_log_print (ANDROID_LOG_INFO, "ffmpegnative", x);
#define ALOGE(x...)    __android_log_print (ANDROID_LOG_ERROR, "ffmpegnative", x);

//static void native_logCallback(int level, const char *tag, const char *format, ...);

#define APPLOGD(x...)    native_logCallback (ANDROID_LOG_DEBUG, "ffmpegnative", x);
#define APPLOGI(x...)    native_logCallback (ANDROID_LOG_INFO, "ffmpegnative", x);
#define APPLOGW(x...)    native_logCallback (ANDROID_LOG_WARN, "ffmpegnative", x);
#define APPLOGE(x...)    native_logCallback (ANDROID_LOG_ERROR, "ffmpegnative", x);

#define v_year 7
#define v_date  0103
#define v_ms 1556

static int ffmpeg_jni_version =  v_year*100000000 + (v_date) * 10000 + v_ms;

typedef struct fields_t {
    jobject app;                /* Application instance, used to call its methods. A global reference is kept. */
    jfieldID context;
    jmethodID arrayID;
    jclass klass_rtmp;
} fields_t;

static jmethodID onMessageCb;
static jmethodID onLogCb;

static fields_t gFields;

#define CHECK(x) do{if (!(x)) {APPLOGI("faile check %s", #x); return -1;}}while(0)
static JNIEnv *get_jni_env( void);

#define MAX_PRINT_LEN	2048

//static void native_logCallback(int level, char *str) {
void native_logCallback(int level, const char *tag, const char *fmt, ...) {
    JNIEnv *env = get_jni_env();
     va_list ap;
	char str[MAX_PRINT_LEN]="";
     
     va_start(ap, fmt);
	 vsnprintf(str, MAX_PRINT_LEN-1, fmt, ap);
     va_end(ap);

    //jclass klass = (*env)->FindClass (env, "com/mediamaster/pushflip/NativeRtmp");
    //if ((*env)->ExceptionCheck(env)) {

    //    APPLOGE("Failed to call Java method onMessageCb");
    //    (*env)->ExceptionClear(env);
    //    return ;
    //}
    jchar *jtag = (*env)->NewStringUTF(env, tag);
    jchar *jstr = (*env)->NewStringUTF(env, str);

    (*env)->CallStaticVoidMethod(env,gFields.klass_rtmp, onLogCb,level, jtag, jstr);
    if ((*env)->ExceptionCheck(env)) {
        APPLOGE("Failed to call Java method onMessageCb");
        (*env)->ExceptionClear(env);
    }
    (*env)->DeleteLocalRef (env, jtag);
    (*env)->DeleteLocalRef (env, jstr);
    //(*env)->DeleteLocalRef (env, klass);
}



/*
 * Java Bindings
 */

static pthread_key_t current_jni_env;
static JavaVM *java_vm;

static int SERVER_DISCONENT = -1;

/* Register this thread with the VM */
static JNIEnv *attach_current_thread(
    void)
{
    JNIEnv *env;
    JavaVMAttachArgs args;

    //ALOGD("Attaching thread %p", pthread_self());
    args.version = JNI_VERSION_1_4;
    args.name = NULL;
    args.group = NULL;

    if ((*java_vm)->AttachCurrentThread(java_vm, &env, &args) < 0) {
        ALOGE("Failed to attach current thread");
        ALOGE( "Could not retrieve JNIEnv");
        return NULL;
    }

    return env;
}
/* Unregister this thread from the VM */
static void detach_current_thread(
    void *env)
{
    //ALOGD("Detaching thread %p", pthread_self());
    (*java_vm)->DetachCurrentThread(java_vm);
}



/* Retrieve the JNI environment for this thread */
static JNIEnv *get_jni_env(
    void)
{
    JNIEnv *env;

    if ((env = pthread_getspecific(current_jni_env)) == NULL) {
        env = attach_current_thread();
        pthread_setspecific(current_jni_env, env);
    }

    return env;
}
void ffmpegnative_message_callback(
    void *userdata,
    int type,
    int arg1,
    int arg2) {

    JNIEnv *env = get_jni_env();

    (*env)->CallVoidMethod(env, gFields.app, onMessageCb, (jint) type, arg1, arg2);
    if ((*env)->ExceptionCheck(env)) {
        APPLOGI( "Failed to call Java method onMessageCb");
        (*env)->ExceptionClear(env);
    }
}

static jboolean native_class_init(
    JNIEnv * env,
    jclass klass)
{
    onMessageCb = (*env)->GetMethodID(env, klass, "onNativeMessage", "(III)V");
    onLogCb = (*env)->GetStaticMethodID(env, klass, "onNativeLog", "(ILjava/lang/String;Ljava/lang/String;)V");
    jclass rtmp = (*env)->FindClass (env, "com/mediamaster/ffmpegwrap/NativeFfmpegSender");
    gFields.klass_rtmp = (*env)->NewGlobalRef(env, rtmp);


    if ( !onMessageCb) {
        /* We emit this message through the Android log instead of the GStreamer log because the later
         * has not been initialized yet.
         */
        APPLOGE( "The calling class does not implement all necessary interface methods");
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

static int native_get_version(JNIEnv* env, jclass class) {
    return ffmpeg_jni_version;
}

static int native_init (JNIEnv* env, jobject thiz) {
    APPLOGI("%s entry now libffmpeg_jni version %d ",__FUNCTION__, ffmpeg_jni_version);

    jclass byteBufClass = (*env)->FindClass (env, "java/nio/ByteBuffer");
    CHECK(byteBufClass != NULL);
    gFields.arrayID = (*env)->GetMethodID(env, byteBufClass, "array", "()[B");
    CHECK(gFields.arrayID != NULL);

    gFields.app = (*env)->NewGlobalRef(env, thiz);

   (*env)->DeleteLocalRef (env, byteBufClass);

    //static int init = 0;
    //if (init) {
    //    APPLOGI("%s entry again WRONG!!",__FUNCTION__);
    //    return -1;
    //}
    //init =1;
    rtmp_sender_init();
    return 0;
}
static int native_destroy(JNIEnv* env, jobject thiz) {
    APPLOGI("%s entry now",__FUNCTION__);
    rtmp_sender_destroy();
    if (gFields.app == NULL) {
        APPLOGW(" destory again");
        return -1;
    }
    (*env)->DeleteGlobalRef(env, gFields.app);
    gFields.app = NULL;

    return 0;
}

//static int native_deinit (JNIEnv* env, jobject thiz) {
//    APPLOGI("%s entry now",__FUNCTION__);
//    (*env)->DeleteGlobalRef(env, gFields.app);
//    rtmp_sender_deinit();
//
//    return 0;
//}

static int native_connect_rtmp (JNIEnv* env, jobject thiz, jstring url) {
    int ret = 0;
    if (url == NULL) {
        //jniThrowException(env, "java/lang/IllegalArgumentException", NULL);
        APPLOGW("setDataSource: uri is NULL");
        return -1;
    }

    const char *tmp = (*env)->GetStringUTFChars(env, url, NULL);
    if (tmp == NULL) {  // Out of memory
        APPLOGW("setDataSource:  GetStringUTFChars NULL");
        return -1;
    }
    APPLOGI("setDataSource: path %s", tmp);
    
    ret = connect_rtmp(tmp);

    (*env)->ReleaseStringUTFChars(env, url, tmp);
    tmp = NULL;
    
    return ret;
}

static int get_bytearray(JNIEnv* env, jbyteArray byteArray,int max_size, void **buf, int *size) {
    jlong dstSize;
    jboolean isCopy;
    void *dst = (*env)->GetByteArrayElements(env, byteArray, &isCopy);
    dstSize = (*env)->GetArrayLength(env, byteArray);

    //APPLOGI("get_bytearray size %lld", dstSize);
    if (max_size > 0 && dstSize > max_size) {
        dstSize = max_size;
    }

    void *e = malloc(dstSize);
    memcpy(e, dst, dstSize);
    //APPLOGI("get_bytearray memcpy %lld", dstSize);

    *buf = e;
    *size = dstSize;

    if (byteArray != NULL) {
        (*env)->ReleaseByteArrayElements(env, byteArray, (jbyte *)dst, 0);
    }

    return 0;
}


//static int native_send_pcm_data ( JNIEnv* env, jobject thiz, jint pcm_size, jbyteArray byteBuf,jbyteArray byteBuf_02 ) {
static jbyteArray native_send_pcm_data ( JNIEnv* env, jobject thiz,jbyteArray byteBuf ) {

    jsize len = (*env)->GetArrayLength(env, byteBuf);
    //在java中申请一块内存以用来将C的数组传输给java程序
    jbyteArray ret = (*env)->NewByteArray(env,len);
    uint8_t *body = (*env)->GetByteArrayElements( env, byteBuf, 0 );
    //降噪
    putPcmData( body );

    //将C的数组拷贝给java中的数组
    (*env)->SetByteArrayRegion( env,ret,0,len,body );
    return ret;

}

static int native_init_audio (JNIEnv* env, jobject thiz, jint channels, jint sample_rate, jbyteArray byteBuf ) {
    APPLOGI("%s entry %d %d ",__FUNCTION__, channels, sample_rate);
    void *e = NULL;
    int esize = 0;
    get_bytearray(env, byteBuf, -1, &e, &esize);
    set_acodec(sample_rate, channels, e, esize);
    free(e);

    return 0;
}

static int native_init_video (JNIEnv* env, jobject thiz, jint width, jint height, jbyteArray byteBuf) {
    APPLOGI("%s entry %d*%d ",__FUNCTION__, width, height);
    void *e;
    int esize;
    get_bytearray(env, byteBuf, -1, &e, &esize);
    APPLOGI("start set_vcodec %d %d %d",width, height, esize);
    set_vcodec(width, height, e, esize);
    free(e);

    return 0;
}

static int get_bytebuffer(JNIEnv* env, jobject byteBuf,int size, int offset,  void **buf, int *out_size)
{
    // Try to convert the incoming byteBuffer into ABuffer
    void *dst = (*env)->GetDirectBufferAddress(env, byteBuf);

    jlong dstSize;
    jbyteArray byteArray = NULL;

    if (dst == NULL) {

        byteArray =
            (jbyteArray)(*env)->CallObjectMethod(env, byteBuf, gFields.arrayID);

        if (byteArray == NULL) {
            APPLOGE("java/lang/IllegalArgumentException byteArray is null");
            return -1;
        }

        jboolean isCopy;
        dst = (*env)->GetByteArrayElements(env, byteArray, &isCopy);

        dstSize = (*env)->GetArrayLength(env, byteArray);
    } else {
        dstSize = (*env)->GetDirectBufferCapacity(env, byteBuf);
    }

    if (dstSize < (offset + size)) {
        APPLOGE("writeSampleData saw wrong dstSize %lld, size  %d, offset %d",
              dstSize, size, offset);
        if (byteArray != NULL) {
            (*env)->ReleaseByteArrayElements(env, byteArray, (jbyte *)dst, 0);
        }
        APPLOGE("java/lang/IllegalArgumentException sample has a wrong size");
        return -1;
    }

    void *e = malloc(size);
    memcpy(e, dst+offset, size);
    //APPLOGI("get_bytearray memcpy %lld", dstSize);

    *buf = e;
    *out_size = size;

    if (byteArray != NULL) {
        (*env)->ReleaseByteArrayElements(env, byteArray, (jbyte *)dst, 0);
    }

    return 0;
}

static int native_send_buffer (JNIEnv* env, jobject thiz, jint isVideo, jlong ts, jint flags, jint size, jint offset, jobject byteBuf) {
    //APPLOGI("%s entry ts %lld size %d flag %x",__FUNCTION__, ts, size, flags);
    void *data;
    int data_size;

    get_bytebuffer(env, byteBuf, size, offset, &data, &data_size);
    putBuffer(isVideo, data, data_size, ts, flags);
    free(data);

    return 0;
}

static int native_start(JNIEnv* env, jobject thiz) {
    APPLOGI("%s entry",__FUNCTION__);
   start_send_data();
   return 0;
}

//static void native_stop (JNIEnv* env, jobject thiz) {
//    APPLOGI("%s entry",__FUNCTION__);
//    rtmp_sender_stop();
//    return ;
//}

// IILjava/nio/ByteBuffer
static JNINativeMethod native_methods[] = {
  {"nativeClassInit", "()Z", (void *) native_class_init},
  { "nativeInit", "()I", (void *) native_init},
  { "nativeDestroy", "()I", (void *) native_destroy},
  //{ "nativeDeinit", "()I", (void *) native_deinit},
  { "nativeConnRtmp", "(Ljava/lang/String;)I", (void *) native_connect_rtmp},
  { "nativeStart", "()I", (void *) native_start},
  { "nativeInitAudio", "(II[B)I", (void *) native_init_audio},
  { "nativeInitVideo", "(II[B)I", (void *) native_init_video},
  { "nativeSendBuffer", "(IJIIILjava/nio/ByteBuffer;)I", (void *) native_send_buffer},
   //send native pcm data
  { "nativeSendPcmData", "([B)[B", (void *) native_send_pcm_data},
  { "nativeGetVersion", "()I", (void *) native_get_version},
  //{ "nativeStop", "()V", (void *) native_stop}
};


jint JNI_OnLoad(JavaVM *vm, void *reserved) {
  JNIEnv *env = NULL;

    java_vm = vm;

    __android_log_print (ANDROID_LOG_INFO, "ffmpegnative", "JNI_OnLoad");

  if ((*vm)->GetEnv(vm, (void**) &env, JNI_VERSION_1_4) != JNI_OK) {
    __android_log_print (ANDROID_LOG_ERROR, "ffmpegnative", "Could not retrieve JNIEnv");
    return 0;
  }
  //com.android.testtool
  //jclass klass = (*env)->FindClass (env, "com/android/testtool/NativeFfmpegSender");
  //io.kickflip.sdk.av FFmpegRtmpWrapper
  // com.android.grafika
  //jclass klass = (*env)->FindClass (env, "com/android/grafika/NativeFfmpegSender");
  // io.kickflip.sdk.av;
  jclass klass = (*env)->FindClass (env, "com/mediamaster/ffmpegwrap/NativeFfmpegSender");
  (*env)->RegisterNatives (env, klass, native_methods,  sizeof(native_methods)/sizeof(native_methods[0]));

    pthread_key_create(&current_jni_env, detach_current_thread);

  return JNI_VERSION_1_4;
}
