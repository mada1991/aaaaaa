#include <string.h>
#include <pthread.h>

#include <jni.h>
#include <android/log.h>

#include <math.h>
#include <log.h>

#define LOGD(x...)    __android_log_print (ANDROID_LOG_DEBUG, "ffmpegnative", x);
#define LOGI(x...)    __android_log_print (ANDROID_LOG_INFO, "ffmpegnative", x);
#define LOGE(x...)    __android_log_print (ANDROID_LOG_ERROR, "ffmpegnative", x);

static void logCallback(int level, const char *tag, const char *format, ...);

#define APPLOGD(x...)    logCallback (ANDROID_LOG_DEBUG, "ffmpegnative", x);
#define APPLOGI(x...)    logCallback (ANDROID_LOG_INFO, "ffmpegnative", x);
#define APPLOGW(x...)    logCallback (ANDROID_LOG_WARN, "ffmpegnative", x);
#define APPLOGE(x...)    logCallback (ANDROID_LOG_ERROR, "ffmpegnative", x);

#define INFO_RECONNECTTING 3
#define INFO_RECONNECTTED 4

typedef struct fields_t {
    jobject app;                /* Application instance, used to call its methods. A global reference is kept. */
    jfieldID context;
    jmethodID arrayID;
    char *url;
    int reconnect_total;
    int reconnect_success_total;
} fields_t;

static jmethodID onMessageCb;
static jmethodID onLogCb;

static fields_t gFields;
static void *rtmp_handler;
static jclass klass_rtmp;


#define CHECK(x) do{if (!(x)) {APPLOGI("faile check %s", #x); return -1;}}while(0)


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

    //APPLOGD("Attaching thread %p", pthread_self());
    args.version = JNI_VERSION_1_4;
    args.name = NULL;
    args.group = NULL;

    if ((*java_vm)->AttachCurrentThread(java_vm, &env, &args) < 0) {
        LOGE("Failed to attach current thread");
        LOGE( "Could not retrieve JNIEnv");
        return NULL;
    }

    return env;
}
/* Unregister this thread from the VM */
static void detach_current_thread(
    void *env)
{
    LOGD("Detaching thread %p", pthread_self());
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
        LOGI("Failed to call Java method onMessageCb");
        (*env)->ExceptionClear(env);
    }
}

static jboolean native_class_init(
    JNIEnv * env,
    jclass klass)
{
    onMessageCb = (*env)->GetMethodID(env, klass, "onNativeMessage", "(III)V");
    onLogCb = (*env)->GetStaticMethodID(env, klass, "onNativeLog", "(ILjava/lang/String;Ljava/lang/String;)V");
    klass_rtmp = (*env)->FindClass (env, "com/mediamaster/pushflip/NativeRtmp");

    if ( !onMessageCb) {
        /* We emit this message through the Android log instead of the GStreamer log because the later
         * has not been initialized yet.
         */
        APPLOGE( "The calling class does not implement all necessary interface methods");
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

static int native_init (JNIEnv* env, jobject thiz) {
    APPLOGI("%s entry now",__FUNCTION__);


    jclass byteBufClass = (*env)->FindClass (env, "java/nio/ByteBuffer");
    CHECK(byteBufClass != NULL);
    gFields.arrayID = (*env)->GetMethodID(env, byteBufClass, "array", "()[B");
    CHECK(gFields.arrayID != NULL);

    gFields.app = (*env)->NewGlobalRef(env, thiz);
    gFields.reconnect_total = 0;
    gFields.reconnect_success_total = 0;

   (*env)->DeleteLocalRef (env, byteBufClass);

        //rtmp_sender_init();
    return 0;
}




#define MAX_PRINT_LEN	2048

//static void logCallback(int level, char *str) {
static void logCallback(int level, const char *tag, const char *fmt, ...) {
    JNIEnv *env = get_jni_env();
     va_list ap;
	char str[MAX_PRINT_LEN]="";
     
     va_start(ap, fmt);
	 vsnprintf(str, MAX_PRINT_LEN-1, fmt, ap);
     va_end(ap);

    //jclass klass = (*env)->FindClass (env, "com/mediamaster/pushflip/NativeRtmp");
    //if ((*env)->ExceptionCheck(env)) {

    //    LOGE("Failed to call Java method onMessageCb");
    //    (*env)->ExceptionClear(env);
    //    return ;
    //}
    jchar *jtag = (*env)->NewStringUTF(env, tag);
    jchar *jstr = (*env)->NewStringUTF(env, str);

    (*env)->CallStaticVoidMethod(env,klass_rtmp, onLogCb,level, jtag, jstr);
    if ((*env)->ExceptionCheck(env)) {
        LOGE("Failed to call Java method onMessageCb");
        (*env)->ExceptionClear(env);
    }
    (*env)->DeleteLocalRef (env, jtag);
    (*env)->DeleteLocalRef (env, jstr);
    //(*env)->DeleteLocalRef (env, klass);


}

static const char *levels[] = {
  "rtmp_CRIT", "rtmp_ERROR", "rtmp_WARNING", "rtmp_INFO",
  "rtmp_DEBUG", "rtmp_DEBUG2"
};
static void rtmp_log_default(int level, const char *format, va_list vl)
{
	char str[MAX_PRINT_LEN]="";

	vsnprintf(str, MAX_PRINT_LEN-1, format, vl);

	/* Filter out 'no-name' */
	if ( RTMP_debuglevel<RTMP_LOGALL && strstr(str, "no-name" ) != NULL )
		return;

	if ( level <= RTMP_debuglevel ) {
		//if (neednl) {
		//	//putc('\n', fmsg);
		//	neednl = 0;
		//}
		//APPLOGI("%s: %s\n", levels[level], str);

	}

    int l ;
    if (level == RTMP_LOGINFO) {
        l = ANDROID_LOG_INFO;
    } else if (level == RTMP_LOGERROR) {
        l = ANDROID_LOG_ERROR;
    } else if (level == RTMP_LOGWARNING) {
        l = ANDROID_LOG_WARN;
    } else if (level >= RTMP_LOGDEBUG) {
        l = ANDROID_LOG_VERBOSE;
    } else {
        l = ANDROID_LOG_INFO;
    }
    logCallback(l, levels[level], str);
}

void
sigIntHandler(int sig)
{
  //RTMP_ctrlC = TRUE;
  APPLOGE(" rtmp_ERROR Caught signal: %d, cleaning up, just a second...\n", sig);
  // ignore all these signals now and let the connection close
//  signal(SIGINT, SIG_IGN);
//  signal(SIGTERM, SIG_IGN);
////#ifndef WIN32
//  signal(SIGHUP, SIG_IGN);
//  signal(SIGPIPE, SIG_IGN);
//  signal(SIGQUIT, SIG_IGN);
////#endif
}
static int native_connect_rtmp (JNIEnv* env, jobject thiz, jstring url) {
    int ret = 0;
    if (url == NULL) {
        //jniThrowException(env, "java/lang/IllegalArgumentException", NULL);
        return -1;
    }
    
    const char *tmp = (*env)->GetStringUTFChars(env, url, NULL);
    if (tmp == NULL) {  // Out of memory
        return -1;
    }
    APPLOGI("setDataSource: path %s", tmp);
    
    //ret = connect_rtmp(tmp);
    
  signal(SIGINT, sigIntHandler);
  signal(SIGTERM, sigIntHandler);
  signal(SIGHUP, sigIntHandler);
  signal(SIGPIPE, sigIntHandler);
  signal(SIGQUIT, sigIntHandler);

  char *u = malloc(strlen(tmp) + 1);
  strcpy(u, tmp);
  gFields.url = u;


    RTMP_LogSetCallback(rtmp_log_default); 
    RTMP_LogSetLevel(RTMP_LOGALL);
    rtmp_handler = rtmp_sender_alloc(tmp); //return handle
    if (!rtmp_handler) {
        APPLOGW("rtmp_sender_alloc failed");
        return -1;
    }
    ret = rtmp_sender_start_publish(rtmp_handler, 0, 0);
    if (ret) {
        rtmp_sender_free(rtmp_handler);
        return -2;
    }
    APPLOGI("connect OK : path %s", tmp);
    

    (*env)->ReleaseStringUTFChars(env, url, tmp);
    tmp = NULL;
    
    return ret;
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

    *buf = e;
    *out_size = size;

    if (byteArray != NULL) {
        (*env)->ReleaseByteArrayElements(env, byteArray, (jbyte *)dst, 0);
    }

    return 0;


}

#define AAC_ADTS_HEADER_SIZE 7
uint8_t *get_adts(uint32_t *len, uint8_t **offset, uint8_t *start, uint32_t total)
{
    uint8_t *p  =  *offset;
    uint32_t frame_len_1;
    uint32_t frame_len_2;
    uint32_t frame_len_3;
    uint32_t frame_length;
   
    if (total < AAC_ADTS_HEADER_SIZE) {
        return NULL;
    }
    if ((p - start) >= total) {
        return NULL;
    }
    
    if (p[0] != 0xff) {
        return NULL;
    }
    if ((p[1] & 0xf0) != 0xf0) {
        return NULL;
    }
    frame_len_1 = p[3] & 0x03;
    frame_len_2 = p[4];
    frame_len_3 = (p[5] & 0xe0) >> 5;
    frame_length = (frame_len_1 << 11) | (frame_len_2 << 3) | frame_len_3;
    *offset = p + frame_length;
    *len = frame_length;
    return p;
}

static int native_send_buffer (JNIEnv* env, jobject thiz, jint isVideo, jlong ts, jint flags, jint size, jint offset, jobject byteBuf) {
    //APPLOGI("%s entry %d ts %lld size %d flag %x",__FUNCTION__, isVideo, ts, size, flags);
    void *data;
    int data_size;
    int ret = 0;

    get_bytebuffer(env, byteBuf, size, offset, &data, &data_size);
    //putBuffer(isVideo, data, data_size, ts, flags);
    if (isVideo) {
        //if (data_size > 8) {
        //    char *idata = (char *)data;
        //    APPLOGI("send video %d %lld : %x %x %x %x %x %x %x %x", data_size, ts,
        //        idata[0],
        //        idata[1],
        //        idata[2],
        //        idata[3],
        //        idata[4],
        //        idata[5],
        //        idata[6],
        //        idata[7]
        //        );
        //}
        ret = rtmp_sender_write_video_frame(rtmp_handler, data, data_size, ts, 0, 0);
    } else {
        //uint8_t *audio_buf = (uint8_t *)data;
        //uint8_t *audio_buf_offset = audio_buf;
        uint32_t audio_ts = ts;
        //uint8_t *p_audio;
        //uint32_t audio_total = data_size;
        //uint32_t audio_len;
        //if (data_size > 8) {
        //char *idata = (char *)data;
        //APPLOGI("send audio %d %lld : %x %x %x %x %x %x %x %x", data_size, ts,
        //    idata[0],
        //    idata[1],
        //    idata[2],
        //    idata[3],
        //    idata[4],
        //    idata[5],
        //    idata[6],
        //    idata[7]
        //    );
        //}   
    //while(1)
    //    {
    //        p_audio = get_adts(&audio_len, &audio_buf_offset, audio_buf, audio_total);
    //        if (p_audio == NULL){
    //            //audio_buf_offset = audio_buf;
    //            //APPLOGI("p_audio null continue.\n");
    //            //continue;
    //            if (audio_buf_offset - audio_buf != data_size) {
    //                APPLOGI("p_audio left %d.\n", data_size - (audio_buf_offset - audio_buf));
    //            }
    //            break;
    //        }
    //        rtmp_sender_write_audio_frame(rtmp_handler, p_audio, audio_len, &audio_ts,  0);
    //        //audio_ts += 25;
    //    }
    //    //rtmp_sender_write_audio_frame(rtmp_handler, data, data_size, &ts,  0);
        ret = rtmp_sender_write_audio_frame(rtmp_handler, data, data_size, &audio_ts,  0);
    }
    
    free(data);

    if (ret < 0) {
        int try_i = 0;
        // reconnect
        APPLOGW("reconnect ");
        rtmp_sender_stop_publish(rtmp_handler);
        rtmp_sender_free(rtmp_handler);
        rtmp_handler = NULL;

        ffmpegnative_message_callback(rtmp_handler, INFO_RECONNECTTING, 0, 0);
        
        for(try_i = 1 ; try_i < 100; try_i++) {
            if (try_i <= 3)
                sleep(try_i);
            else if(try_i < 10)
                sleep(5);
            else
                return -1;

            APPLOGW("reconnect try_i %d total %d", try_i, gFields.reconnect_total++);
            rtmp_handler = rtmp_sender_alloc(gFields.url); //return handle
            if (!rtmp_handler) {
                APPLOGW("reconnect %d rtmp_sender_alloc failed", __LINE__);
                continue;
            }
            ret = rtmp_sender_start_publish(rtmp_handler, 0, 0);
            if (!ret) {
                APPLOGW("reconnect %d rtmp_sender_start_publish failed", __LINE__);
                rtmp_sender_free(rtmp_handler);
                continue;
            }
            ffmpegnative_message_callback(rtmp_handler, INFO_RECONNECTTED, 0, 0);
            break;
        }
        APPLOGW("reconnect success %d", gFields.reconnect_success_total++);

    }
    return 0;
}

static int native_start(JNIEnv* env, jobject thiz) {
    APPLOGI("%s entry",__FUNCTION__);
   //start_send_data();
   return 0;
}

static void native_stop (JNIEnv* env, jobject thiz) {
    APPLOGI("%s entry",__FUNCTION__);
    //rtmp_sender_stop();
    return ;
}

// IILjava/nio/ByteBuffer
static JNINativeMethod native_methods[] = {
    {"nativeClassInit", "()Z", (void *) native_class_init},
  { "nativeInit", "()I", (void *) native_init},
  { "nativeConnRtmp", "(Ljava/lang/String;)I", (void *) native_connect_rtmp},
  { "nativeStart", "()I", (void *) native_start},
  { "nativeSendBuffer", "(IJIIILjava/nio/ByteBuffer;)I", (void *) native_send_buffer},
  { "nativeStop", "()V", (void *) native_stop}
};

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
  JNIEnv *env = NULL;

    java_vm = vm;

  if ((*vm)->GetEnv(vm, (void**) &env, JNI_VERSION_1_4) != JNI_OK) {
    __android_log_print (ANDROID_LOG_ERROR, "tutorial-1", "Could not retrieve JNIEnv");
    return 0;
  }
  //com.android.testtool
  //jclass klass = (*env)->FindClass (env, "com/android/testtool/NativeFfmpegSender");
  //io.kickflip.sdk.av FFmpegRtmpWrapper
  // com.android.grafika
  //jclass klass = (*env)->FindClass (env, "com/android/grafika/NativeFfmpegSender");
  // io.kickflip.sdk.av;
  jclass klass = (*env)->FindClass (env, "com/mediamaster/pushflip/NativeRtmp");
  (*env)->RegisterNatives (env, klass, native_methods,  sizeof(native_methods)/sizeof(native_methods[0]));

    pthread_key_create(&current_jni_env, detach_current_thread);
  (*env)->DeleteLocalRef (env, klass);

  return JNI_VERSION_1_4;
}
