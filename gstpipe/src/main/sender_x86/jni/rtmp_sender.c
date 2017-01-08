/**
  * This example stream local media files to streaming media 
 * server (Use RTMP as example). 
 * It's the simplest FFmpeg streamer.
 * 
 */

#include <stdio.h>
#include <unistd.h>
#include <pthread.h>


#define __STDC_CONSTANT_MACROS

#ifdef _WIN32
//Windows
extern "C"
{
#include "libavformat/avformat.h"
#include "libavutil/mathematics.h"
#include "libavutil/time.h"
};
#else
//Linux...
#ifdef __cplusplus
extern "C"
{
#endif
#include <libavformat/avformat.h>
#include <libavutil/mathematics.h>
#include <libavutil/time.h>
#ifdef __cplusplus
};
#endif
#endif


#include "avqueue.h"
#include "rtmp_sender.h"

#define DEBUG_LOG 0
#define VERBOSE_LOG 0



 
#define LOCAL_FILE 0

//#if 1
//    char *out_filename = "rtmp://180.153.102.19/swaps/test";
//    //char *out_filename = "/sdcard/movies/rtmp.flv";
//    char *out_format = "flv";
//#else
//    char *out_filename = "/sdcard/movies/rtmp.mp4";
//    char *out_format = "mp4";
//#endif


#include <android/log.h>
extern void native_logCallback(int level, const char *tag, const char *fmt, ...);
//#define ALOGI(x...)    __android_log_print (ANDROID_LOG_INFO, "rtmp_sender", x);
//#define ALOGW(x...)    __android_log_print (ANDROID_LOG_WARN, "rtmp_sender", x);
//#define ALOGE(x...)    __android_log_print (ANDROID_LOG_ERROR, "rtmp_sender", x);
//#define ALOGV(x...)    __android_log_print (ANDROID_LOG_VERBOSE, "rtmp_sender", x);

#define APPLOGD(x...)    native_logCallback (ANDROID_LOG_DEBUG, "rtmp_sender", x);
#define APPLOGV(x...)    native_logCallback (ANDROID_LOG_VERBOSE, "rtmp_sender", x);
#define APPLOGI(x...)    native_logCallback (ANDROID_LOG_INFO, "rtmp_sender", x);
#define APPLOGW(x...)    native_logCallback (ANDROID_LOG_WARN, "rtmp_sender", x);
#define APPLOGE(x...)    native_logCallback (ANDROID_LOG_ERROR, "rtmp_sender", x);



//#define MM_MONITOR 1

#ifdef MM_MONITOR
static g_malloc_p = 0;
#define monitor_malloc(p, x) do{ \
    g_malloc_p ++; \
    APPLOGI("+MemoryMonitor %s:%d : malloc %d   : %p,  %d", __FUNCTION__,__LINE__, x, p, g_malloc_p); \
} while(0);
    
#define monitor_free(p) do{ \
    APPLOGI("-MemoryMonitor %s:%d : %p", __FUNCTION__, __LINE__, p); \
    g_malloc_p--; \
}while(0)
#else
#define monitor_malloc(p, x) 
#define monitor_free(p) 
#endif

typedef struct _Sender {
    AVOutputFormat *ofmt;
    //ÊäÈë¶ÔÓŠÒ»žöAVFormatContext£¬Êä³ö¶ÔÓŠÒ»žöAVFormatContext
    //£šInput AVFormatContext and Output AVFormatContext£©
    AVFormatContext *ofmt_ctx;
    int currentindex;
    int audioindex;
    int videoindex;
    //AVRational in_time_base;
    //AVRational out_time_base;
    
    int frame_index;
    int64_t start_time;

    int64_t first_pts;

    int64_t first_vpts;
    int64_t last_vts; 
    int64_t first_apts;
    int64_t last_ats; 
    
    pthread_mutex_t put_mutex; 
    
    int rtmp_connected;
    pthread_t send_thread;
    pthread_t rtmp_init_thread;
    
    int rtmp_stop;
    
    char * out_filename;
    
    //static int64_t put_apts = 0;
    //static int64_t put_vpts = 0;
    int64_t put_vframe_count;
    int put_vsize;
    int put_asize;
    int64_t send_apts;
    int64_t send_vpts;
    int64_t send_asize;
    int64_t send_vsize;
    int need_drop_vpkt;
    int drop_vpkt_nb;
    int send_apkt;
    int send_vpkt;

    int64_t send_asize_last_mark;
    int64_t send_vsize_last_mark;
    int     send_vpkt_last_mark;

    int64_t long_send_asize_last_mark;
    int64_t long_send_vsize_last_mark;
    int     long_send_vpkt_last_mark;

    uint8_t *spspps;
    int spspps_size;

    int64_t report_time ;
    int64_t long_report_time ;
    int64_t last_send_avsync_print ;

    int64_t last_now_time;
    int type;
}Sender;

PacketQueue audioq;
PacketQueue videoq;

static Sender *mSender = NULL;

static void init_Sender() {
    mSender = (Sender *) calloc(sizeof(Sender), 1);
    mSender->ofmt = NULL;
    mSender->ofmt_ctx = NULL;
    mSender->currentindex=0;
    mSender->audioindex=-1;
    mSender->videoindex=-1;

    //mSender->in_time_base.num = 1;
    //mSender->in_time_base.den = 1000000;

    //mSender->out_time_base.num = 1;
    //mSender->out_time_base.den = 1000;

    
    mSender->frame_index=0;
    mSender->start_time=-1;
    mSender->first_pts =-1;
    mSender->first_vpts = -1; 
    mSender->last_vts = 0; 
    mSender->first_apts = -1; 
    mSender->last_ats = 0; 
    
    //mSender->put_mutex = PTHREAD_MUTEX_INITIALIZER; 
    pthread_mutex_init(&mSender->put_mutex,NULL);
    
    mSender->rtmp_connected = 0;
    mSender->send_thread;
    mSender->rtmp_init_thread;
    
    //mSender->rtmp_stop = 1;
    mSender->rtmp_stop = 0;
    
    mSender->out_filename = NULL;
    
    //static int64_t put_apts = 0;
    //static int64_t put_vpts = 0;
    mSender->put_vframe_count = 0;
    mSender->put_vsize = 0;
    mSender->put_asize = 0;
    mSender->send_apts = 0;
    mSender->send_vpts = 0;
    mSender->send_asize = 0;
    mSender->send_vsize = 0;
    mSender->need_drop_vpkt = 0;
    mSender->drop_vpkt_nb = 0;
    mSender->send_apkt = 0;
    mSender->send_vpkt = 0;
    mSender->spspps = NULL;
    mSender->spspps_size= NULL;

    mSender->report_time = 0;
    mSender->long_report_time  = 0;
    mSender->last_send_avsync_print = 0;
    mSender->last_now_time = 0;
}

static inline int64_t init_system_time(int64_t offset) {
    APPLOGI("init_system_time first_pts %lld ", offset);
    mSender->start_time = av_gettime() ;
    mSender->first_pts = offset;
    if (mSender->start_time %2 == 1) {
        mSender->type = 0;
    } else {
        mSender->type = 1;
    }
    APPLOGI("typetype %d", mSender->type);
}

static inline int64_t get_system_time() {
    int64_t now_time = (int64_t)(av_gettime() - mSender->start_time);

     if ((now_time - mSender->last_now_time) > 60 * 1000000) {
         APPLOGW("get_system_time gap %lld", (now_time- mSender->last_now_time)/1000);
        if ((now_time - mSender->last_now_time) > 3600 * 1000000) {
           APPLOGW("system time change ");
           mSender->start_time = av_gettime() - mSender->last_now_time - 30000;
            now_time = (int64_t)(av_gettime() - mSender->start_time);
        }
     } else if (now_time + 10000 < mSender->last_now_time ) {
           APPLOGW("system time change small ");
           mSender->start_time = av_gettime() - mSender->last_now_time - 30000;
           now_time = (int64_t)(av_gettime() - mSender->start_time);
     }
     mSender->last_now_time = now_time;

     return now_time;
}


static void sanitize(uint8_t *line){
    while(*line){
        if(*line < 0x08 || (*line > 0x0D && *line < 0x20))
            *line='?';
        line++;
    }
}


void nam_av_log_callback(void* ptr, int level, const char* fmt, va_list vl)
{
    static int print_prefix = 1;
    static int count;
    static char prev[1024];
    char line[1024];
    if(mSender->rtmp_stop) {
        APPLOGI("rtmp_stop already");
        return;
    }

    //if (!ptr || !fmt) {
    //    APPLOGI("nam_av ptr %p fmt %p", ptr, fmt);
    //}

    //APPLOGI("nam_av_log_callback");
    //if (level > av_log_get_level())
    //    return;

    av_log_format_line(ptr, level, fmt, vl, line, sizeof(line), &print_prefix);

    if (print_prefix && !strcmp(line, prev)){
        count++;
        return;
    }
    if (count > 0) {
        APPLOGI("Last message repeated %d times\n", count);
        count = 0;
    }
    strcpy(prev, line);
    sanitize((uint8_t *)line);

#if 0
    APPLOGI("%s", line);
#else
#define LOG_BUF_SIZE 2048 
    static char g_msg[LOG_BUF_SIZE];
    static int g_msg_len = 0;

    int saw_lf, check_len;

    do {
        check_len = g_msg_len + strlen(line) + 1;
        if (check_len <= LOG_BUF_SIZE) {
            /* lf: Line feed ('\n') */
            saw_lf = (strchr(line, '\n') != NULL) ? 1 : 0;
            strncpy(g_msg + g_msg_len, line, strlen(line));
            g_msg_len += strlen(line);
            if (!saw_lf) {
               /* skip */
               return;
            } else {
               /* attach the line feed */
               g_msg_len += 1;
               g_msg[g_msg_len] = '\n';
            }
        } else {
            /* trace is fragmented */
            g_msg_len += 1;
            g_msg[g_msg_len] = '\n';
        }
        switch(level) {
        case AV_LOG_TRACE:
        case AV_LOG_VERBOSE:
        case AV_LOG_DEBUG:
            APPLOGV("nam_av %s", g_msg); break;
        case AV_LOG_INFO:
            APPLOGI("nam_av %s", g_msg); break;
        case AV_LOG_WARNING:
            APPLOGW("nam_av %s", g_msg); break;
        case AV_LOG_ERROR:
        case AV_LOG_FATAL:
        case AV_LOG_PANIC:
            APPLOGE("nam_av %s", g_msg); break;
        default :
            APPLOGE("nam_av level %d : %s", level, g_msg); break;
        }
        /* reset g_msg and gmsg_len */
        memset(g_msg, 0, LOG_BUF_SIZE);
        g_msg_len = 0;
     } while (check_len > LOG_BUF_SIZE);
#endif
}


int init() {
    av_log_set_level(AV_LOG_DEBUG);
    av_log_set_callback(nam_av_log_callback);

    av_register_all();
    //Network
    avformat_network_init();

    packet_queue_init(&videoq);
    packet_queue_init(&audioq);
    packet_queue_start(&videoq);
    packet_queue_start(&audioq);
}

static int end_all(int ret)  {
    mSender->rtmp_stop = 1;
    /* close output */
    if (mSender->ofmt_ctx && !(mSender->ofmt->flags & AVFMT_NOFILE))
        avio_close(mSender->ofmt_ctx->pb);
    APPLOGI("%s %d",__FUNCTION__, __LINE__);
    avformat_free_context(mSender->ofmt_ctx);
    if (ret < 0 && ret != AVERROR_EOF) {
        APPLOGE( "Error occurred.\n");
        return -1;
    }
    APPLOGI("%s %d",__FUNCTION__, __LINE__);
}

void setNalHeader_annexb(uint8_t *buf) {
    // annex-b
    buf[0] = buf[1] = buf[2] = 0x0;
    buf[3] = 0x1;
}

void setNalHeader(uint8_t *buf, int size) {
    buf[0] = size>>24 & 0xff;
    buf[1] = size>>16 & 0xff;
    buf[2] = size>>8 & 0xff;
    buf[3] = size & 0xff;
}

#define alloc_and_copy_or_fail(obj, size, pad) \
    if (obj && size > 0) { \
        dest->obj = av_mallocz(size + pad); \
        if (!dest->obj) \
            goto fail; \
        memcpy(dest->obj, obj, size); \
        if (pad) \
            memset(((uint8_t *) dest->obj) + size, 0, pad); \
    }

//static AVStream *out_stream;

int set_acodec(int sample_rate, int channels, uint8_t *extradata, int extradata_size) {
    mSender->ofmt = mSender->ofmt_ctx->oformat;

    AVStream *out_stream = avformat_new_stream(mSender->ofmt_ctx, NULL);
    if (!out_stream) {
        APPLOGE( "Failed allocating output stream\n");
        //ret = AVERROR_UNKNOWN;
        return -1;
    }

    mSender->audioindex =  mSender->currentindex++;
    AVCodecContext *dest = out_stream->codec;
    // audio
    dest->codec_id = AV_CODEC_ID_AAC;
    dest->codec_type = AVMEDIA_TYPE_AUDIO;
    dest->sample_rate = sample_rate;
    dest->channels = channels;

    int i ;
    APPLOGI("%s channels %d sample_rate %d adts %d :",__FUNCTION__, channels, sample_rate, extradata_size);
    for(i = 0; i < extradata_size; i++) {
        APPLOGI("%x ", extradata[i]);
    }
    APPLOGI("\n");

    uint8_t *extra = (uint8_t *)av_mallocz(extradata_size);
    memcpy(extra, extradata, extradata_size);
    dest->extradata = extra;
    dest->extradata_size = extradata_size;
    
    //alloc_and_copy_or_fail(extradata,    extradata_size,
    //                       FF_INPUT_BUFFER_PADDING_SIZE);
    //dest->extradata_size  = extradata_size;

    out_stream->codec->codec_tag = 0;
    if (mSender->ofmt_ctx->oformat->flags & AVFMT_GLOBALHEADER)
        out_stream->codec->flags |= CODEC_FLAG_GLOBAL_HEADER;



}


int set_vcodec(int width, int height, uint8_t *spspps, int spspps_size) {
    mSender->ofmt = mSender->ofmt_ctx->oformat;
    
    APPLOGI("set_vcodec %dX%d %p %d\n", width, height, spspps, spspps_size);

    AVStream *out_stream = avformat_new_stream(mSender->ofmt_ctx, NULL);
    if (!out_stream) {
        APPLOGE( "Failed allocating output stream\n");
        //ret = AVERROR_UNKNOWN;
        return -1;
    }
    mSender->videoindex =  mSender->currentindex++;
    AVCodecContext *dest = out_stream->codec;

    //my_copy_codec(dest, src);

    ///* set values specific to opened codecs back to their default state */
    //dest->slice_offset    = NULL;
    //dest->hwaccel         = NULL;
    //dest->internal        = NULL;
    //dest->coded_frame     = NULL;

    ///* reallocate values that should be allocated separately */
    //dest->extradata       = NULL;
    //dest->intra_matrix    = NULL;
    //dest->inter_matrix    = NULL;
    //dest->rc_override     = NULL;
    //dest->subtitle_header = NULL;


    dest->codec_id = AV_CODEC_ID_H264;
    dest->codec_type = AVMEDIA_TYPE_VIDEO;
    dest->width = width;
    dest->height = height;
    dest->sample_aspect_ratio.den = width;
    dest->sample_aspect_ratio.num = height;
    dest->time_base.num = 1;
    dest->time_base.den = 1000;
    out_stream->time_base.num= 1;
    out_stream->time_base.den = 1000;

    mSender->spspps = malloc(spspps_size);
    memcpy(mSender->spspps, spspps, spspps_size);
    mSender->spspps_size = spspps_size;

    
    //int i ;
    //APPLOGI("%s %d %d spspps %d: ",__FUNCTION__, width, height, spspps_size);
    //for(i = 0; i < spspps_size; i++) {
    //    APPLOGI("%x ", spspps[i]);
    //}
    //APPLOGI("\n");

    // java 1280 X 720 configure sps: 00 00 00 01 67 64 00 29 AC 1B 1A 80 50 05 BA 01 E1 10 8A 70  pps: 00 00 00 01 68 EA 43 CB
    // c : 00 10 67 64 00 29 AC 1B 1A 80 50 05 BA 01 E1 10 8A 70  pps:  00 04 68 EA 43 CB

    // 00 00 00 01 67 42 80 1F DA 05 07 E4  pps: 00 00 00 01 68 CE 06 F2
    //=>  00 08 67 42 80 1F DA 05 07 E4 00 04 68 CE 06 F2

    /*
     1 64 0 d ff e1 0 
     19 
     67 64 0 d ac d9 41 71 fe 5e 10 0 0 3 0 10 0 0 3 3 20 f1 42 99 60 
     1 
     0 6 68 eb e3 cb 22 c0 
     */

    // video
    //out_stream->codec->codec_tag = 0;

#if 1 // libavformat/avc.c::ff_isom_write_avcc
    int extradata_size = spspps_size + 7;
    uint32_t spsSize = spspps[1];
    uint32_t ppsSize = spspps[spsSize + 3];
    uint8_t *sps =  &spspps[2];
    uint8_t *pps = &spspps[2 + spsSize + 2];

    //mSender->spspps = (uint8_t *)av_mallocz(extradata_size);


    uint8_t *extra = (uint8_t *)av_mallocz(extradata_size);
    extra[0] = 1; // version
    extra[1] = sps[1]; // profile
    extra[2] = spspps[2]; // compatibility
    extra[3] = spspps[3]; // level
    extra[4] = 0xFF ;  // reserved (6 bits), NALU length size - 1 (2 bits)
    extra[5] = 0xE1 ;  // reserved (3 bits), num of SPS (5 bits)
    uint8_t *pExtra = extra + 6;
    memcpy(pExtra, spspps, spsSize+2);
    pExtra += spsSize+2;
    *pExtra++ = 1; // num of PPS
    memcpy(pExtra, spspps+2+spsSize, ppsSize+2);


#else
#if 0
    // Calc extradata from nvidia 

    int extradata_size = spspps_size + 7;
    int spsSize = spspps[1];
    int ppsSize = spspps[spsSize + 3];

    //mSender->spspps = (uint8_t *)av_mallocz(extradata_size);


    uint8_t *extra = (uint8_t *)av_mallocz(extradata_size);
    extra[0] = 1; // version
    extra[1] = spspps[3]; // profile
    extra[2] = spspps[4]; // compatibility
    extra[3] = spspps[5]; // level
    extra[4] = 0xFC | 3;  // reserved (6 bits), NALU length size - 1 (2 bits)
    extra[5] = 0xE0 | 1;  // reserved (3 bits), num of SPS (5 bits)
    uint8_t *pExtra = extra + 6;
    memcpy(pExtra, spspps, spsSize+2);
    pExtra += spsSize+2;
    *pExtra++ = 1; // num of PPS
    memcpy(pExtra, spspps+2+spsSize, ppsSize+2);

#else
    int spsSize = spspps[1];
    int ppsSize = spspps[spsSize + 3];
    int extradata_size = 4 + spsSize + 4 + ppsSize;

    uint8_t *sps_start =  &spspps[2];
    uint8_t *pps_start = &spspps[2 + spsSize + 2];

    uint8_t *extra = (uint8_t *)av_mallocz(extradata_size);

    setNalHeader_annexb(extra);
    memcpy(&extra[4], sps_start, spsSize);

    setNalHeader_annexb(&extra[4+spsSize]);
    memcpy(&extra[4+spsSize + 4], pps_start, ppsSize);


#endif
#endif

    dest->extradata = extra;
    dest->extradata_size = extradata_size;

    {
        int ii ;
        char tmp[24];
        char tmp2[1024];
        tmp[0] = '\0';
        tmp2[0] = '\0';
        //tmp2[0] = 0;
        for(ii = 0; ii < extradata_size; ii++) {
            sprintf(tmp, "%02x ", extra[ii]);
            strcat(tmp2, tmp);
        }
        APPLOGI("extradata %s\n", tmp2);
    }


    //alloc_and_copy_or_fail(extradata,    extradata_size,
    //                       FF_INPUT_BUFFER_PADDING_SIZE);

    out_stream->codec->codec_tag = 0;
    if (mSender->ofmt_ctx->oformat->flags & AVFMT_GLOBALHEADER)
        out_stream->codec->flags |= CODEC_FLAG_GLOBAL_HEADER;
fail:
    return -1;
}



static void * open_output2(void *data) {
     const char *filename = (const char *) data;
     int ret = 0;
#if LOCAL_FILE
     filename = "/sdcard/gamelive.flv";
#endif
    APPLOGI("%s entry start connect %s", __FUNCTION__, filename);
    //Êä³ö£šOutput£©
    avformat_alloc_output_context2(&mSender->ofmt_ctx, NULL, "flv", filename); //RTMP

    //avformat_alloc_output_context2(&mSender->ofmt_ctx, NULL, "mpegts", out_filename);//UDP
    if (!mSender->ofmt_ctx) {
        APPLOGE( "Could not create output context\n");
        ret = AVERROR_UNKNOWN;
        ffmpegnative_message_callback(NULL, ERR_CONNECT_FAILED, 0 , 0);
        goto end;
    }
    av_opt_set(mSender->ofmt_ctx, "protocol_whitelist", "rtmp", 0);
    mSender->ofmt_ctx->protocol_whitelist = av_strdup("rtmp");
    //av_opt_set(mSender->ofmt_ctx, "whitelist", "ALL", 0);
    AVDictionary *options = NULL;
    if ((ret = av_dict_set(&options, "protocol_whitelist", "rtmp", 0)) < 0)
        APPLOGE("set protocol_whitelist failed");

    //if (!(mSender->ofmt->flags & AVFMT_NOFILE)) {
        ret = avio_open2(&mSender->ofmt_ctx->pb, filename, AVIO_FLAG_WRITE, &mSender->ofmt_ctx->interrupt_callback, &options);
        if (ret < 0) {
            APPLOGE( "Could not open output URL '%s'", filename);
            ffmpegnative_message_callback(NULL, ERR_CONNECT_FAILED, 0 , 0);
    mSender->rtmp_stop = 1;
    /* close output */
    //if (mSender->ofmt_ctx && !(mSender->ofmt->flags & AVFMT_NOFILE))
    //    avio_close(mSender->ofmt_ctx->pb);
    APPLOGI("%s %d",__FUNCTION__, __LINE__);
    avformat_free_context(mSender->ofmt_ctx);
    return NULL;

//            goto end;
        }
    //}
    APPLOGI("%s entry connect %s finish", __FUNCTION__, filename);
    mSender->rtmp_connected = 1;
    ffmpegnative_message_callback(NULL, INFO_CONNECT_FINISH, 0 , 0);
    return NULL;


end:
    end_all(ret);
    return NULL;
}


int find_start_code(uint8_t *data, int start_find_i, int size) {
    int i = start_find_i ;
    for( ; i + 3 < size ; i++ ) {
        if (data[i] == 0
            && data[i+1] == 0
            && data[i+2] == 0
            && data[i+3] == 1) 
            return i;
    }
    return size;
}


int remove_prevent_code(uint8_t *data, int nal_start_i, int nal_end_i, uint8_t *nal_buf) {
    int i = nal_start_i ;
    int j = 0;

    nal_buf[j++] = data[i++];
    nal_buf[j++] = data[i++];
    nal_buf[j++] = data[i++];
    nal_buf[j++] = data[i++];

    for ( ; i + 2 < nal_end_i; ){
        //if (data[i] == 0 
        //    && data[i+1] == 0 
        //    && data[i+2] == 3 ){
        //    nal_buf[j++] = 0;
        //    nal_buf[j++] = 0;
        //    i+=3;
        //} else 
        {
            nal_buf[j++] = data[i++];
        }
    }
    nal_buf[j++] = data[i++];
    nal_buf[j++] = data[i++];
    setNalHeader(nal_buf, j-4);

    return j;
}

void putBuffer(int isVideo, void *data, int size, int64_t ts, int flags) {
    //APPLOGI("return test");
    //return ;
    //

    if (size <= 0) {
        APPLOGW("putBuffer failed size %d", size);
    }


    //int64_t now_time  = 0;
    //if ( mSender->start_time > 0 ) {
    //    now_time = av_gettime() - mSender->start_time;
    //} else {
    //    now_time = 0;
    //}
    //ts = now_time;

    //APPLOGE("putBuffer %x size %d pts %lld", flags,size, ts);
    if (!mSender->rtmp_connected) {
        APPLOGW("not connect");
        // not connect , just abandon data
        // TO think
        return;
    }

    if (mSender->start_time == -1) {
        init_system_time(ts);
    }

    ts -= mSender->first_pts;
    if (ts < 0) {
        APPLOGW("ts too small %lld", ts);
        ts = 0;
    }


    if (isVideo) {
        if ( mSender->first_vpts == -1) {
            mSender->first_vpts = ts;
            APPLOGI("first vpts %lld, system_time %lld", ts, get_system_time());
        }

        if (ts < mSender->last_vts) {
            APPLOGW("lower ts %lld, last_vts %lld", ts, mSender->last_vts);
            ts = mSender->last_vts;
        }
        //g_put_vpts = ts;
        mSender->put_vframe_count++;
        mSender->put_vsize += size;
        mSender->last_vts = ts;
    } else {
        if ( mSender->first_apts == -1) {
            mSender->first_apts = ts;
            APPLOGI("first apts %lld, system_time %lld", ts, get_system_time());
        } 

        if (ts < mSender->last_ats) {
            APPLOGW("lower ts %lld, last_ats %lld", ts, mSender->last_ats);
            ts = mSender->last_ats;
        }
        //g_put_apts = ts;
        mSender->put_asize += size;
        mSender->last_ats = ts;
    }

    pthread_mutex_lock(&mSender->put_mutex); 
    //APPLOGE("putBuffer 222 %x %d %lld", flags,size, ts);

    AVPacket p2;
    av_init_packet(&p2);
    p2.flags= flags;

    //p2.pts = ts;
    //p2.pts = now_time;
    //float fps = 0.0;
    //int bitrate = 0;
    if ( isVideo )  {
         
        //p2.duration = (int)(ts - mSender->last_vts);
        //p2.pts = ts - mSender->first_vpts ;
        p2.pts = ts;
        p2.stream_index = mSender->videoindex; //pkt.stream_index;
        mSender->last_vts = ts;
        //if (p2.pts/1000 != 0) {
        //    fps = (float)(mSender->put_vframe_count * 1000)/(float)(p2.pts/1000);
        //    bitrate = mSender->put_vsize / (p2.pts/1000);
        //}

    } else {
        //p2.duration = (int)(ts - mSender->last_ats);
        //p2.pts = ts - mSender->first_apts ;
        p2.pts = ts;
        p2.stream_index = mSender->audioindex; //pkt.stream_index;
        mSender->last_ats = ts;
        //if (p2.pts/1000 != 0) {
        //    fps = -1.0;
        //    bitrate = mSender->put_asize / (p2.pts/1000);
        //}
    }


    //APPLOGI("putBuffer %s  %x size %d pts %lld, dur %d, fps %f, bitrate %dK/s  diff : %lld - %lld = %lld ", 
    //    isVideo?"V":"A", flags, size, ts,
    //    p2.duration/1000, 
    //    fps, bitrate,
    //    gput_apts/1000, gput_vpts/1000, (g_put_apts - gput_vpts)/1000);



    p2.dts = p2.pts;
    p2.data = av_mallocz(size );
    monitor_malloc(p2.data, size);
    memcpy(p2.data , data, size );
    p2.size = size ;

    if (!isVideo) {
        packet_queue_put(&audioq, &p2);
    } else {
        int nal_start_i = 0;
        int nal_end_i;
        int nal_size;

        while (nal_start_i + 4 < size) {
            nal_end_i = find_start_code(p2.data, nal_start_i + 4, size);
            setNalHeader(&(p2.data[nal_start_i]), nal_end_i-nal_start_i-4);
            nal_start_i = nal_end_i;
        }

        packet_queue_put(&videoq, &p2);
    }

    //monitor_free(p2.data);
    //av_free(p2.data);


    //APPLOGE( "putBuffer flags %x %x now %lld pts %lld dts %lld duration %d  stream_index %d pos %lld size %d data %p\n",
    //    flags ,p2.flags, now_time, p2.pts, p2.dts, p2.duration, p2.stream_index, p2.pos, p2.size, p2.data);

    //APPLOGE( "after rescale flags %x %x now %lld pts %lld dts %lld duration %d  stream_index %d pos %lld size %d data %p\n",
    //    flags ,p2.flags, now_time, p2.pts, p2.dts, p2.duration, p2.stream_index, p2.pos, p2.size, p2.data);
    //APPLOGE("start put end\n");
    //APPLOGE("put end\n");
    
    pthread_mutex_unlock(&mSender->put_mutex); 
}


static void *thread_send_func(void *data) {
    AVPacket pkt;
    int serial;
    int ret = 0;

    APPLOGI("%s %d now ", __FUNCTION__, __LINE__);
    while (!mSender->rtmp_connected) {
    APPLOGI("%s %d now ", __FUNCTION__, __LINE__);
        if (mSender->rtmp_stop) goto end;
        APPLOGE(" wait rtmp connect ");
        av_usleep(50000);
    }

 
   //Dump Format------------------
    av_dump_format(mSender->ofmt_ctx, 0, mSender->out_filename, 1);

    APPLOGI("%s %d now ", __FUNCTION__, __LINE__);
    //ÐŽÎÄŒþÍ·£šWrite file header£©
    ret = avformat_write_header(mSender->ofmt_ctx, NULL);
    if (ret < 0) {
        APPLOGE( "Error occurred when opening output URL\n");
        goto end;
    }

    APPLOGI("%s %d now ", __FUNCTION__, __LINE__);

     while (1) {
        if (mSender->rtmp_stop) goto end_stop;
        int idle_dur = 0;

         while(1) {
            if (audioq.nb_packets > 100 || videoq.nb_packets > 60) {
                int64_t sys_time = get_system_time();
                int64_t adiff = mSender->send_apts - sys_time/1000;
                int64_t vdiff = mSender->send_vpts - sys_time/1000;
                int64_t avdiff = mSender->send_apts - mSender->send_vpts;
                if (videoq.nb_packets > 30 && vdiff < -2000 && mSender->need_drop_vpkt == 0) {
                    AVPacket *vpkt = packet_queue_peek(&videoq);
                    if (vpkt->pts - sys_time/1000 < -2000) {
                        APPLOGI("need_drop_vpkt  %lld %lld %d-%d", 
                            sys_time/1000, mSender->send_vpts, videoq.nb_packets, audioq.nb_packets);
                        mSender->need_drop_vpkt = 1;
                    } else {
                        APPLOGI("now need need_drop_vpkt  video gap %lld  - %lld  = %lld ", vpkt->pts, mSender->send_vpts,
                            (vpkt->pts - mSender->send_vpts));
                    }
                }

            }

            AVPacket *apkt = packet_queue_peek(&audioq);
            AVPacket *vpkt = packet_queue_peek(&videoq);
            if ( apkt != NULL &&
                (vpkt ==NULL || (apkt->pts <= vpkt->pts)) ){
                if (1 == packet_queue_get(&audioq, &pkt, 0, &serial))
                    break;
                else 
                    APPLOGW("SHOULD NOT HERE got audioq failed ");
            } else if ( vpkt != NULL ){
                if (1 == packet_queue_get(&videoq, &pkt, 0, &serial))
                    break;
                else 
                    APPLOGW("SHOULD NOT HERE got videoq failed ");
            }

            if (apkt != NULL && vpkt != NULL){
                APPLOGW("SHOULD NOT HERE apkt %p  vpkt %p ", apkt, vpkt);
            }

            //if (1 == packet_queue_get(&audioq, &pkt, 0, &serial))
            //    break;
            //if (1 == packet_queue_get(&videoq, &pkt, 0, &serial))
            //    break;

            if (mSender->rtmp_stop) goto end_stop;

            usleep(10000);

            idle_dur += 10;
            if (idle_dur > 1000 && (idle_dur %5000 == 0)) {
                APPLOGI("no data , idle %d", idle_dur);
            }
         }

        int64_t now_time = get_system_time();
    
         //if ( (pkt.stream_index==mSender->videoindex)) {
         //    APPLOGI("vflags %d, %d", pkt.flags, pkt.size);
         //}

         if ( mSender->need_drop_vpkt > 0
             && (pkt.stream_index==mSender->videoindex)) {
                        
            if (pkt.flags == 1 && mSender->need_drop_vpkt > 1) {
                APPLOGI("quit vdrop %d ", pkt.size);
                mSender->need_drop_vpkt = 0;
            } else {
                APPLOGI("dv %lld %d", pkt.pts, pkt.size);
            
                //sprintf(drop_v_str, "%lld-%lld ", now_time/1000, pkt.pts);
                //if (strlen(drop_v_str > 1024) {
                //    APPLOGI("dropv %s", drop_v_str);
                //}

                mSender->drop_vpkt_nb++;
                mSender->need_drop_vpkt++;
                av_free_packet(&pkt);

                if (mSender->need_drop_vpkt > 60 ) {
                    int64_t sys_time = get_system_time();
                    int64_t vdiff = pkt.pts - sys_time/1000;
                    if (vdiff > -500 || videoq.nb_packets < 3 ) {
                        APPLOGW("should not here need_drop_vpkt  %lld-%lld=%lld %d", 
                            pkt.pts, sys_time/1000, vdiff, videoq.nb_packets);
                        mSender->need_drop_vpkt = 0;
                    }
                }
                continue;
            }
         }

         if (pkt.size <= 0) {
             APPLOGI("pkt size %d", pkt.size);
            av_free_packet(&pkt);
            continue;
         }




    if (now_time - mSender->report_time > 1000000) {
        int64_t dur = (now_time - mSender->report_time)/1000;
        int64_t long_dur = (now_time-mSender->long_report_time)/1000;
        dur = dur<=0?1:dur;
        long_dur = long_dur<=0?1:long_dur;
        
        int send_asize = mSender->send_asize - mSender->send_asize_last_mark;
        int send_vsize = mSender->send_vsize - mSender->send_vsize_last_mark;
        int send_vpkt = mSender->send_vpkt - mSender->send_vpkt_last_mark;
        
           APPLOGI("%d (%lld): \td %d \tav %lld\tl: a %d(%d), v %d(%d) \t\tbr  %lld,%lld\tfps %lld",
               __LINE__,
               dur, 
               mSender->drop_vpkt_nb,
               (mSender->send_apts - mSender->send_vpts),
                audioq.size, audioq.nb_packets, videoq.size, videoq.nb_packets,
               (send_asize + send_vsize)/dur,
               (send_vsize)/dur,
               (send_vpkt *1000)/dur);
           if (audioq.nb_packets > 10 && videoq.nb_packets == 0) {
                AVPacket *apkt = packet_queue_peek(&audioq);
                APPLOGW("send %d %lld %lld audio pts %lld",audioq.nb_packets, mSender->send_apts, mSender->send_vpts,  apkt->pts);
           }


        if (long_dur > 30000) {
            int long_send_asize = mSender->send_asize - mSender->long_send_asize_last_mark;
            int long_send_vsize = mSender->send_vsize - mSender->long_send_vsize_last_mark;
            int long_send_vpkt = mSender->send_vpkt - mSender->long_send_vpkt_last_mark;
            APPLOGI(
                "-long--> (%lld-%lld) \tsend:a %lldms(%lldK, %d), v %lldms(%lldK, %d/%d) \t\tbr %lld - %lld \tfps(%d) %lld - %lld ", 
                long_dur, now_time/1000,
                mSender->send_apts, mSender->send_asize/1000, mSender->send_apkt,
                mSender->send_vpts, mSender->send_vsize/1000, mSender->drop_vpkt_nb, mSender->send_vpkt,
                (mSender->send_asize + mSender->send_vsize)/(now_time/1000), (long_send_asize + long_send_vsize)/(long_dur),
                long_send_vpkt, (mSender->send_vpkt * 1000) / (now_time/1000), (long_send_vpkt * 1000)/long_dur
            );


            mSender->long_send_asize_last_mark = mSender->send_asize;
            mSender->long_send_vsize_last_mark = mSender->send_vsize;
            mSender->long_send_vpkt_last_mark = mSender->send_vpkt;
            mSender->long_report_time = now_time;
        }

        mSender->send_asize_last_mark = mSender->send_asize;
        mSender->send_vsize_last_mark = mSender->send_vsize;
        mSender->send_vpkt_last_mark = mSender->send_vpkt;
        mSender->report_time = now_time;

    }


    //if(pkt.stream_index==mSender->videoindex){
    //        AVRational time_base_q={1,AV_TIME_BASE};
    //        int64_t pts_time = av_rescale_q(pkt.dts, mSender->in_time_base, time_base_q);
    //        //int64_t now_time = av_gettime() - mSender->start_time;
    //        //APPLOGE(" pts_time %lld now_time %lld, sleep %lld", pts_time, now_time, pts_time-now_time);
    //        if (pts_time > now_time) {
    //            //if ((pts_time-now_time)/1000 > 200) {
    //            //    APPLOGW("--- pts_time %lld now_time %lld, sleep %lld ms", pts_time, now_time, (pts_time-now_time)/1000);
    //            //} else if ((pts_time-now_time)/1000 > 5) {
    //            //    APPLOGI("pts_time %lld now_time %lld, sleep %lld ms", pts_time, now_time, (pts_time-now_time)/1000);
    //            //}

    //            //av_usleep(pts_time - now_time);

    //            //TODO: need sleep?
    //            if ((pts_time - now_time) > 5000)
    //                av_usleep((pts_time - now_time) - 5000);
    //        }
    //}

    //pkt.pts = av_rescale_q_rnd(pkt.pts, mSender->in_time_base, mSender->out_time_base, (int)(AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX));
    //pkt.dts = av_rescale_q_rnd(pkt.dts, mSender->in_time_base, mSender->out_time_base, (int)(AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX));
    //pkt.duration = av_rescale_q(pkt.duration, mSender->in_time_base, mSender->out_time_base);
    pkt.pos = -1;

    //static int v_mSender->frame_index = 0;
    //pkt.pts = v_mSender->frame_index * 25;
    //pkt.dts = pkt.pts;
    //pkt.duration = 25;
    //v_mSender->frame_index++;

    if((now_time - mSender->last_send_avsync_print)/1000 > 2000) {
        int64_t adiff = mSender->send_apts - now_time/1000;
        int64_t vdiff = mSender->send_vpts - now_time/1000;
        int64_t avdiff = mSender->send_apts - mSender->send_vpts;

        if ( (abs(adiff) > 500 || abs(vdiff) > 500 || abs(avdiff) > 500) )
        {
            APPLOGI("diff s %lld\t as %lld, vs %lld av %lld",
                now_time/1000,  adiff, vdiff, avdiff);
            mSender->last_send_avsync_print = now_time;
        }
    }

    //if (avdiff < -500 && pkt.stream_index == mSender->videoindex) {
    //    mSender->drop_vpkt_nb ++;
    //    APPLOGI("audio too late %lld, drop this video packet %lld", avdiff, mSender->send_vpts);
    //   av_free_packet(&pkt);
    //   continue;
    //}

    //APPLOGI("stream %s flags %x size %d pts %lld dts %lld duration %lld",
    //    pkt.stream_index==mSender->videoindex?"V":"A", pkt.flags, pkt.size, pkt.pts, pkt.dts, pkt.duration);




        if(pkt.stream_index==mSender->videoindex){
            mSender->send_vpts = pkt.pts;
            mSender->send_vsize += pkt.size;
            mSender->send_vpkt++;
#if DEBUG_LOG
            static int tmp_i = 0;
            if ((tmp_i++ %60) == 0) {
                 int i ;
                char tmp[1024];
                char tmp2[12];
                tmp[0] = '\0';
                for(i = 0; i < 32 && i < pkt.size; i++) {
                    sprintf(tmp2, "%02x ", pkt.data[i]);
                    strcat(tmp, tmp2);
                }
                APPLOGV("write frame ts %lld len %d data: %s\n", pkt.pts, pkt.size, tmp);
            }
#endif

            //APPLOGV("send Video %lld  size %d dur %d\n",pkt.pts, pkt.size, pkt.duration);
        } else {
            mSender->send_apts = pkt.pts;
            mSender->send_asize += pkt.size;
            mSender->send_apkt++;
            //APPLOGV("send Audio %lld  size %d dur %d\n",pkt.pts, pkt.size, pkt.duration);
            //APPLOGI("%s %d now ", __FUNCTION__, __LINE__);

        }

        //APPLOGI("send avsync %lld - %lld = %lld ", mSender->send_apts, mSender->send_vpts, mSender->send_apts - mSender->send_vpts);

#if LOCAL_FILE
        if ( pkt.dts > 60000) {
            break;
        }
#endif

#if 0
        if (pkt.flags == 9) {
             int i ;
            char tmp[65*7200*4];
            char tmp2[12];
            tmp[0] = '\0';
            //APPLOGI("i %d %d", i, __LINE__);
            for(i = 0; i < 65*7000 && i < pkt.size; i++) {
                //APPLOGI("i %d %d", i, __LINE__);
                sprintf(tmp2, "%x ", pkt.data[i]);
                //APPLOGI("i %d %d", i, __LINE__);
                //APPLOGI("i %d %d %s, %s", i, __LINE__, tmp, tmp2);
                strcat(tmp, tmp2);
                //APPLOGI("i %d %d", i, __LINE__);
                if (i %32 == 0)
                    strcat(tmp, "\n");
                //APPLOGI("i %d %d", i, __LINE__);
            }
            //APPLOGI("i %d %d", i, __LINE__);
            APPLOGE("keyframe write frame ts %lld len %d data: %s", pkt.pts, pkt.size, tmp);
            //av_free_packet(&pkt);
            //continue;
        }
#endif
       

       //ret = av_write_frame(mSender->ofmt_ctx, &pkt);
       uint8_t *myp = pkt.data;

#ifdef MM_MONITOR
       APPLOGI("11 MemoryMonitor %p", pkt.data);
#endif

       if (mSender->type = 0)
        ret = av_interleaved_write_frame(mSender->ofmt_ctx, &pkt);
       else
        ret = av_write_frame(mSender->ofmt_ctx, &pkt);

#ifdef MM_MONITOR
       APPLOGI("22 MemoryMonitor %p", pkt.data);
#endif

        if (ret < 0) {
            APPLOGW( "Error muxing packet ret = %d %s, avsync %lld - %lld = %lld  \n", ret,
                (pkt.stream_index==mSender->videoindex)?"V":"A",
                mSender->send_apts, mSender->send_vpts, mSender->send_apts - mSender->send_vpts
                );
            ffmpegnative_message_callback(NULL, ERR_SEND_FAILED, 0, 0);
            mSender->rtmp_stop = 1;

            break;
        }
        
#ifdef MM_MONITOR
       monitor_free(myp);
#endif
       av_free(myp);
       //monitor_free(pkt.data);
       //av_free(pkt.data);
       av_free_packet(&pkt);
    }
end_stop:
     APPLOGI("Write file trailer");
    //ÐŽÎÄŒþÎ²£šWrite file trailer£©
    av_write_trailer(mSender->ofmt_ctx);
end:
    end_all(ret);
}



int start_send_data() {
    APPLOGI("%s entry", __FUNCTION__);
    APPLOGI("output_start thread start");
    //Žò¿ªÊä³öURL£šOpen output URL£©
    //if (!(mSender->ofmt->flags & AVFMT_NOFILE)) {
    //    ret = avio_open(&mSender->ofmt_ctx->pb, out_filename, AVIO_FLAG_WRITE);
    //    if (ret < 0) {
    //        APPLOGE( "Could not open output URL '%s'", out_filename);
    //        goto end;
    //    }
    //}
    pthread_create (&mSender->send_thread, NULL, &thread_send_func, NULL);

 
    return 0;

end:
    return -1;
}

int rtmp_sender_init()
{

    APPLOGI("%s entry", __FUNCTION__);

    init_Sender();

    init();

    APPLOGI("%s %d finish",__FUNCTION__, __LINE__);

    return 0;
}

int rtmp_sender_destroy() {
    void*status;
    APPLOGI("%s entry", __FUNCTION__);
    mSender->rtmp_stop = 1;

    if (mSender->rtmp_init_thread)
        pthread_join(mSender->rtmp_init_thread ,&status);
    if (mSender->send_thread)
        pthread_join(mSender->send_thread,&status);
    if ( mSender->out_filename)
        free(mSender->out_filename);

    packet_queue_destroy(&audioq);
    packet_queue_destroy(&videoq);

    //init_gparmeters();
    APPLOGI("%s finish", __FUNCTION__);

    if (mSender != NULL) {
        free(mSender);
        mSender = NULL;
    }
}

int connect_rtmp(const char *filename) {
    APPLOGI("%s entry %s", __FUNCTION__, filename);
    // TODO: maybe timeout while start_send_data
    mSender->out_filename = strdup(filename);
    //pthread_create (&mSender->rtmp_init_thread, NULL, &open_output2, mSender->out_filename);
    open_output2(filename);
}

//void rtmp_sender_stop() {
//    void*status;
//    APPLOGI("%s entry", __FUNCTION__);
//    mSender->rtmp_stop = 1;
//
//    if (mSender->rtmp_init_thread)
//        pthread_join(mSender->rtmp_init_thread ,&status);
//    if (mSender->send_thread)
//        pthread_join(mSender->send_thread,&status);
//    if ( mSender->out_filename)
//        free(mSender->out_filename);
//
//    packet_queue_destroy(&audioq);
//    packet_queue_destroy(&videoq);
//
//    //init_gparmeters();
//    APPLOGI("%s finish", __FUNCTION__);
//}



