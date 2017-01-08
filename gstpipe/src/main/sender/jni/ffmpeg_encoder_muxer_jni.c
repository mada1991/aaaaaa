#include <string.h>
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

#define TAG "ffmpeg_encoder_muxer_jni"
#define ALOGI(x...)    __android_log_print (ANDROID_LOG_INFO, TAG , x);
#define ALOGE(x...)    __android_log_print (ANDROID_LOG_ERROR, TAG, x);

typedef struct fields_t {
    jfieldID context;
    jmethodID arrayID;
} fields_t;

static fields_t gFields;

#define CHECK(x) do{if (!(x)) {ALOGI("faile check %s", #x); return -1;}}while(0)


/*
 * Java Bindings
 */

static int native_init (JNIEnv* env, jobject thiz) {
    ALOGI("%s entry now",__FUNCTION__);

    jclass byteBufClass = (*env)->FindClass (env, "java/nio/ByteBuffer");
    CHECK(byteBufClass != NULL);
    gFields.arrayID = (*env)->GetMethodID(env, byteBufClass, "array", "()[B");
    CHECK(gFields.arrayID != NULL);

    //rtmp_sender_init();
    return 0;
}


static int get_bytearray(JNIEnv* env, jbyteArray byteArray,int max_size, void **buf, int *size) {
    jlong dstSize;
    jboolean isCopy;
    void *dst = (*env)->GetByteArrayElements(env, byteArray, &isCopy);
    dstSize = (*env)->GetArrayLength(env, byteArray);

    //ALOGI("get_bytearray size %lld", dstSize);
    if (max_size > 0 && dstSize > max_size) {
        dstSize = max_size;
    }

    void *e = malloc(dstSize);
    memcpy(e, dst, dstSize);
    //ALOGI("get_bytearray memcpy %lld", dstSize);

    *buf = e;
    *size = dstSize;

    if (byteArray != NULL) {
        (*env)->ReleaseByteArrayElements(env, byteArray, (jbyte *)dst, 0);
    }


    return 0;
}

static int native_init_audio (JNIEnv* env, jobject thiz, jint channels, jint sample_rate, jbyteArray byteBuf ) {
    ALOGI("%s entry %d %d ",__FUNCTION__, channels, sample_rate);
    ///void *e = NULL;
    ///int esize = 0;
    ///get_bytearray(env, byteBuf, -1, &e, &esize);
    //set_acodec(sample_rate, channels, e, esize);

    return 0;
}

static int native_init_video (JNIEnv* env, jobject thiz, jint width, jint height, jbyteArray byteBuf) {
    ALOGI("%s entry %d*%d ",__FUNCTION__, width, height);
    //void *e;
    //int esize;
    //get_bytearray(env, byteBuf, -1, &e, &esize);
    //ALOGI("start set_vcodec %d", esize);
    //set_vcodec(width, height, e, esize);
    muxing_init_video(width, height);

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
            ALOGE("java/lang/IllegalArgumentException byteArray is null");
            return -1;
        }

        jboolean isCopy;
        dst = (*env)->GetByteArrayElements(env, byteArray, &isCopy);

        dstSize = (*env)->GetArrayLength(env, byteArray);
    } else {
        dstSize = (*env)->GetDirectBufferCapacity(env, byteBuf);
    }

    if (dstSize < (offset + size)) {
        ALOGE("writeSampleData saw wrong dstSize %lld, size  %d, offset %d",
              dstSize, size, offset);
        if (byteArray != NULL) {
            (*env)->ReleaseByteArrayElements(env, byteArray, (jbyte *)dst, 0);
        }
        ALOGE("java/lang/IllegalArgumentException sample has a wrong size");
        return -1;
    }

    void *e = malloc(size);
    memcpy(e, dst+offset, size);
    //ALOGI("get_bytearray memcpy %lld", dstSize);

    *buf = e;
    *out_size = size;

    if (byteArray != NULL) {
        (*env)->ReleaseByteArrayElements(env, byteArray, (jbyte *)dst, 0);
    }

    return 0;


}

static int native_send_buffer (JNIEnv* env, jobject thiz, jint isVideo, jlong ts, jint flags, jint size, jint offset, jobject byteBuf) {
    //ALOGI("%s entry ts %lld size %d flag %x",__FUNCTION__, ts, size, flags);
    void *data;
    int data_size;

    get_bytebuffer(env, byteBuf, size, offset, &data, &data_size);
    int ret = muxing_putBuffer(isVideo, data, data_size, ts, flags);

    free(data);


    return ret;
}

static int native_start(JNIEnv* env, jobject thiz, jstring url) {
    if (url == NULL) {
        //jniThrowException(env, "java/lang/IllegalArgumentException", NULL);
        return -1;
    }

    const char *tmp = (*env)->GetStringUTFChars(env, url, NULL);
    if (tmp == NULL) {  // Out of memory
        return -1;
    }
    ALOGI("setDataSource: path %s", tmp);
 
    ALOGI("%s entry",__FUNCTION__);
    muxing_start(tmp);

    (*env)->ReleaseStringUTFChars(env, url, tmp);
    tmp = NULL;

   return 0;
}

static void native_stop (JNIEnv* env, jobject thiz) {
    ALOGI("%s entry",__FUNCTION__);
    //rtmp_sender_stop();
    muxing_stop();
    return ;
}


static JNINativeMethod native_methods[] = {
  { "nativeInit", "()I", (void *) native_init},
  { "nativeStart", "(Ljava/lang/String;)I", (void *) native_start},
  { "nativeInitAudio", "(II[B)I", (void *) native_init_audio},
  { "nativeInitVideo", "(II[B)I", (void *) native_init_video},
  { "nativeSendBuffer", "(IJIIILjava/nio/ByteBuffer;)I", (void *) native_send_buffer},
  { "nativeStop", "()V", (void *) native_stop}
};

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
  JNIEnv *env = NULL;

  if ((*vm)->GetEnv(vm, (void**) &env, JNI_VERSION_1_4) != JNI_OK) {
    __android_log_print (ANDROID_LOG_ERROR, TAG, "Could not retrieve JNIEnv");
    return 0;
  }

 // com.mediamaster.graphmaster.NativeFfmpegEncoderMuxer
  jclass klass = (*env)->FindClass (env, "com/mediamaster/ffmpegwrap/NativeEncoderMuxer");
  (*env)->RegisterNatives (env, klass, native_methods,  sizeof(native_methods)/sizeof(native_methods[0]));

  return JNI_VERSION_1_4;
}
