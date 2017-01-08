/*
 * Copyright (c) 2003 Fabrice Bellard
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

/**
 * @file
 * libavformat API example.
 *
 * Output a media file in any supported libavformat format. The default
 * codecs are used.
 * @example muxing.c
 */

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <math.h>
#include <pthread.h>

#include <libavutil/avassert.h>
#include <libavutil/channel_layout.h>
#include <libavutil/opt.h>
#include <libavutil/mathematics.h>
#include <libavutil/timestamp.h>
#include <libavformat/avformat.h>
#include <libswscale/swscale.h>
#include <libswresample/swresample.h>

#define STREAM_FRAME_RATE 25 /* 25 images/s */
#define STREAM_PIX_FMT    AV_PIX_FMT_YUV420P /* default pix_fmt */

#define SCALE_FLAGS SWS_BICUBIC

#if 1
#include <android/log.h>
#define TAG "muxing"
#define printf(x...)    __android_log_print (ANDROID_LOG_INFO, TAG, x);
#define ALOGI(x...)    __android_log_print (ANDROID_LOG_INFO, TAG, x);
#else
#define ALOGI(x...) {printf(x);printf("\n");}
#endif

static void my_av_packet_rescale_ts(AVPacket *pkt, AVRational src_tb, AVRational dst_tb);

// a wrapper around a single output AVStream
typedef struct OutputStream {
    AVStream *st;

    /* pts of the next frame that will be generated */
    int64_t next_pts;
    int samples_count;

    AVFrame *frame;
    AVFrame *tmp_frame;

    float t, tincr, tincr2;

    struct SwsContext *sws_ctx;
    struct SwrContext *swr_ctx;
} OutputStream;


    OutputStream video_st = { 0 }, audio_st = { 0 };
    AVOutputFormat *fmt;
    AVFormatContext *oc;
    AVCodec *audio_codec, *video_codec;
    int ret;
    int have_video = 0, have_audio = 0;
    int encode_video = 0, encode_audio = 0;
    AVDictionary *opt = NULL;
    int eos;
    static pthread_t g_put_thread;
    static pthread_t g_get_thread;
    static int gen_video_fmt = AV_PIX_FMT_RGBA;
    static int g_width, g_height;
    static int g_stop;
    static int g_video_next_pst = 0;
    static int g_thread_put_runing = 0;


/************************************************ PACKET_QUEUE *****************************/

#define SDL_mutex char 
#define SDL_cond  char 

#define SDL_CreateMutex() 0
#define SDL_CreateCond() 1

#define SDL_CondSignal(x) x=1

#define SDL_LockMutex(x) while(1){ if(!x){x=1;break;}else{usleep(1000);}}
#define SDL_UnlockMutex(x) x=0

#define SDL_CondWait(x,y) while(1){ if(x){x=0;break;}else{usleep(1000);}}

#define SDL_DestroyMutex(x)
#define SDL_DestroyCond(x)

static AVPacket flush_pkt;

// 720*440 * 4 = 1.3 M
// 800*800 * 4 = 2.5 M
// 1200*720 * 4 = 3.6 M

#define NEEDWAIT_CACHE_PKT (50*1024*1024)
#define MAX_CACHE_PKT (NEEDWAIT_CACHE_PKT + 10*1024*1024)

typedef struct MyAVPacketList {
    AVPacket pkt;
    struct MyAVPacketList *next;
    int serial;
} MyAVPacketList;

typedef struct PacketQueue {
    MyAVPacketList *first_pkt, *last_pkt;
    int nb_packets;
    int size;
    int abort_request;
    int serial;
    //SDL_mutex *mutex;
    //SDL_cond *cond;
    SDL_mutex mutex;
    SDL_cond cond;
} PacketQueue;

PacketQueue audioq;
PacketQueue videoq;

static int packet_queue_put_private(PacketQueue *q, AVPacket *pkt)
{
    MyAVPacketList *pkt1;

    if (q->abort_request)
       return -1;

    pkt1 = (MyAVPacketList *)av_malloc(sizeof(MyAVPacketList));
    if (!pkt1)
        return -1;
    pkt1->pkt = *pkt;
    pkt1->next = NULL;
    if (pkt == &flush_pkt)
        q->serial++;
    pkt1->serial = q->serial;

    if (!q->last_pkt)
        q->first_pkt = pkt1;
    else
        q->last_pkt->next = pkt1;
    q->last_pkt = pkt1;
    q->nb_packets++;
    q->size += pkt1->pkt.size + sizeof(*pkt1);
    /* XXX: should duplicate packet data in DV case */
    SDL_CondSignal(q->cond);
    return 0;
}



static int packet_queue_put(PacketQueue *q, AVPacket *pkt)
{
    int ret;

    /* duplicate the packet */
    if (pkt != &flush_pkt && av_dup_packet(pkt) < 0)
        return -1;

    SDL_LockMutex(q->mutex);
    ret = packet_queue_put_private(q, pkt);
    SDL_UnlockMutex(q->mutex);

    if (pkt != &flush_pkt && ret < 0)
        av_free_packet(pkt);

    return ret;
}

static int packet_queue_put_nullpacket(PacketQueue *q, int stream_index)
{
    AVPacket pkt1, *pkt = &pkt1;
    av_init_packet(pkt);
    pkt->data = NULL;
    pkt->size = 0;
    pkt->stream_index = stream_index;
    return packet_queue_put(q, pkt);
}

/* packet queue handling */
static void packet_queue_init(PacketQueue *q)
{
    memset(q, 0, sizeof(PacketQueue));
    q->mutex = SDL_CreateMutex();
    q->cond = SDL_CreateCond();
    q->abort_request = 1;
}

static void packet_queue_flush(PacketQueue *q)
{
    MyAVPacketList *pkt, *pkt1;

    SDL_LockMutex(q->mutex);
    for (pkt = q->first_pkt; pkt; pkt = pkt1) {
        pkt1 = pkt->next;
        av_free_packet(&pkt->pkt);
        av_freep(&pkt);
    }
    q->last_pkt = NULL;
    q->first_pkt = NULL;
    q->nb_packets = 0;
    q->size = 0;
    SDL_UnlockMutex(q->mutex);
}

static void packet_queue_destroy(PacketQueue *q)
{
    packet_queue_flush(q);
    SDL_DestroyMutex(q->mutex);
    SDL_DestroyCond(q->cond);
}

static void packet_queue_abort(PacketQueue *q)
{
    SDL_LockMutex(q->mutex);

    q->abort_request = 1;

    SDL_CondSignal(q->cond);

    SDL_UnlockMutex(q->mutex);
}

static void packet_queue_start(PacketQueue *q)
{
    SDL_LockMutex(q->mutex);
    q->abort_request = 0;
    packet_queue_put_private(q, &flush_pkt);
    SDL_UnlockMutex(q->mutex);
}

/* return < 0 if aborted, 0 if no packet and > 0 if packet.  */
static int packet_queue_get(PacketQueue *q, AVPacket *pkt, int block, int *serial)
{
    MyAVPacketList *pkt1;
    int ret;

    SDL_LockMutex(q->mutex);

    for (;;) {
        if (q->abort_request) {
            ret = -1;
            break;
        }

        pkt1 = q->first_pkt;
        if (pkt1) {
            q->first_pkt = pkt1->next;
            if (!q->first_pkt)
                q->last_pkt = NULL;
            q->nb_packets--;
            q->size -= pkt1->pkt.size + sizeof(*pkt1);
            *pkt = pkt1->pkt;
            if (serial)
                *serial = pkt1->serial;
            av_free(pkt1);
            ret = 1;
            break;
        } else if (!block) {
            ret = 0;
            break;
        } else {
            SDL_CondWait(q->cond, q->mutex);
        }
    }
    SDL_UnlockMutex(q->mutex);
    return ret;
}



/************************************************ END OF PACKET_QUEUE *****************************/
static void my_av_packet_rescale_ts(AVPacket *pkt, AVRational src_tb, AVRational dst_tb)
{
    if (pkt->pts != AV_NOPTS_VALUE)
        pkt->pts = av_rescale_q(pkt->pts, src_tb, dst_tb);
    if (pkt->dts != AV_NOPTS_VALUE)
        pkt->dts = av_rescale_q(pkt->dts, src_tb, dst_tb);
    if (pkt->duration > 0)
        pkt->duration = av_rescale_q(pkt->duration, src_tb, dst_tb);
    if (pkt->convergence_duration > 0)
        pkt->convergence_duration = av_rescale_q(pkt->convergence_duration, src_tb, dst_tb);
}

/*************************************************************************************************/

static void log_packet(const AVFormatContext *fmt_ctx, const AVPacket *pkt)
{
    AVRational *time_base = &fmt_ctx->streams[pkt->stream_index]->time_base;

    printf("log_packet : pts:%s pts_time:%s dts:%s dts_time:%s duration:%s duration_time:%s stream_index:%d\n",
           av_ts2str(pkt->pts), av_ts2timestr(pkt->pts, time_base),
           av_ts2str(pkt->dts), av_ts2timestr(pkt->dts, time_base),
           av_ts2str(pkt->duration), av_ts2timestr(pkt->duration, time_base),
           pkt->stream_index);
}

static int write_frame(AVFormatContext *fmt_ctx, const AVRational *time_base, AVStream *st, AVPacket *pkt)
{
    /* rescale output packet timestamp values from codec to stream timebase */
    my_av_packet_rescale_ts(pkt, *time_base, st->time_base);
    pkt->stream_index = st->index;

    /* Write the compressed frame to the media file. */
    log_packet(fmt_ctx, pkt);
    return av_interleaved_write_frame(fmt_ctx, pkt);
}

static AVCodec *find_codec_or_die(const char *name, enum AVMediaType type, int encoder)
{
    const AVCodecDescriptor *desc;
    const char *codec_string = encoder ? "encoder" : "decoder";
    AVCodec *codec;

    ALOGI( "start  find name %s", name);

    codec = encoder ?
        avcodec_find_encoder_by_name(name) :
        avcodec_find_decoder_by_name(name);
    if (!codec) {
        ALOGI( "can't find by name %s", name);
    }

    if (!codec && (desc = avcodec_descriptor_get_by_name(name))) {
        codec = encoder ? avcodec_find_encoder(desc->id) :
                          avcodec_find_decoder(desc->id);
        if (codec)
            av_log(NULL, AV_LOG_VERBOSE, "Matched %s '%s' for codec '%s'.\n",
                   codec_string, codec->name, desc->name);
        else {
            ALOGI( "find failed %d", __LINE__);
        }
    } else {
            ALOGI( "find failed %d", __LINE__);
    }

    if (!codec) {
        ALOGI( "Unknown %s '%s'\n", codec_string, name);
        return NULL;
    }
    if (codec->type != type) {
        ALOGI( "Invalid %s type '%s'\n", codec_string, name);
        return NULL;
    }
            ALOGI( "find OK %d", __LINE__);
    return codec;
}

/* Add an output stream. */
static void add_stream(OutputStream *ost, AVFormatContext *oc,
                       AVCodec **codec,
                       enum AVCodecID codec_id)
{
    AVCodecContext *c;
    int i;

    /* find the encoder */
    if (codec_id == AV_CODEC_ID_H264)
        *codec = find_codec_or_die("libx264", 0, 1);
    else
        *codec = avcodec_find_encoder(codec_id);
    if (!(*codec)) {
        fprintf(stderr, "Could not find encoder for '%s'\n",
                avcodec_get_name(codec_id));
        exit(1);
    }

    ost->st = avformat_new_stream(oc, *codec);
    if (!ost->st) {
        fprintf(stderr, "Could not allocate stream\n");
        exit(1);
    }
    ost->st->id = oc->nb_streams-1;
    c = ost->st->codec;

    switch ((*codec)->type) {
    case AVMEDIA_TYPE_AUDIO:
        c->sample_fmt  = (*codec)->sample_fmts ?
            (*codec)->sample_fmts[0] : AV_SAMPLE_FMT_FLTP;
        c->bit_rate    = 64000;
        c->sample_rate = 44100;
        if ((*codec)->supported_samplerates) {
            c->sample_rate = (*codec)->supported_samplerates[0];
            for (i = 0; (*codec)->supported_samplerates[i]; i++) {
                if ((*codec)->supported_samplerates[i] == 44100)
                    c->sample_rate = 44100;
            }
        }
        c->channels        = av_get_channel_layout_nb_channels(c->channel_layout);
        c->channel_layout = AV_CH_LAYOUT_STEREO;
        if ((*codec)->channel_layouts) {
            c->channel_layout = (*codec)->channel_layouts[0];
            for (i = 0; (*codec)->channel_layouts[i]; i++) {
                if ((*codec)->channel_layouts[i] == AV_CH_LAYOUT_STEREO)
                    c->channel_layout = AV_CH_LAYOUT_STEREO;
            }
        }
        c->channels        = av_get_channel_layout_nb_channels(c->channel_layout);
        ost->st->time_base = (AVRational){ 1, c->sample_rate };
        break;

    case AVMEDIA_TYPE_VIDEO:
        c->codec_id = codec_id;

        c->bit_rate = 2000000;
        /* Resolution must be a multiple of two. */
        c->width    = g_width;
        c->height   = g_height;
        /* timebase: This is the fundamental unit of time (in seconds) in terms
         * of which frame timestamps are represented. For fixed-fps content,
         * timebase should be 1/framerate and timestamp increments should be
         * identical to 1. */
        //ost->st->time_base = (AVRational){ 1, STREAM_FRAME_RATE };
        ost->st->time_base = (AVRational) {1, STREAM_FRAME_RATE};

        c->time_base       = ost->st->time_base;

        c->gop_size      = 12; /* emit one intra frame every twelve frames at most */
        c->pix_fmt       = STREAM_PIX_FMT;
        if (c->codec_id == AV_CODEC_ID_MPEG2VIDEO) {
            /* just for testing, we also add B frames */
            c->max_b_frames = 2;
        }
        if (c->codec_id == AV_CODEC_ID_MPEG1VIDEO) {
            /* Needed to avoid using macroblocks in which some coeffs overflow.
             * This does not happen with normal video, it just happens here as
             * the motion of the chroma plane does not match the luma plane. */
            c->mb_decision = 2;
        }
    break;

    default:
        break;
    }

    /* Some formats want stream headers to be separate. */
    if (oc->oformat->flags & AVFMT_GLOBALHEADER)
        c->flags |= CODEC_FLAG_GLOBAL_HEADER;
}

/**************************************************************/
/* audio output */

static AVFrame *alloc_audio_frame(enum AVSampleFormat sample_fmt,
                                  uint64_t channel_layout,
                                  int sample_rate, int nb_samples)
{
    AVFrame *frame = av_frame_alloc();
    int ret;

    if (!frame) {
        fprintf(stderr, "Error allocating an audio frame\n");
        exit(1);
    }

    frame->format = sample_fmt;
    frame->channel_layout = channel_layout;
    frame->sample_rate = sample_rate;
    frame->nb_samples = nb_samples;

    if (nb_samples) {
        ret = av_frame_get_buffer(frame, 0);
        if (ret < 0) {
            fprintf(stderr, "Error allocating an audio buffer\n");
            exit(1);
        }
    }

    return frame;
}

static void open_audio(AVFormatContext *oc, AVCodec *codec, OutputStream *ost, AVDictionary *opt_arg)
{
    AVCodecContext *c;
    int nb_samples;
    int ret;
    AVDictionary *opt = NULL;

    c = ost->st->codec;

    /* open it */
    av_dict_copy(&opt, opt_arg, 0);
    av_dict_set(&opt, "strict", "experimental", 0);
    ret = avcodec_open2(c, codec, &opt);
    av_dict_free(&opt);
    if (ret < 0) {
        fprintf(stderr, "Could not open audio codec: %s\n", av_err2str(ret));
        exit(1);
    }

    /* init signal generator */
    ost->t     = 0;
    ost->tincr = 2 * M_PI * 110.0 / c->sample_rate;
    /* increment frequency by 110 Hz per second */
    ost->tincr2 = 2 * M_PI * 110.0 / c->sample_rate / c->sample_rate;

    if (c->codec->capabilities & CODEC_CAP_VARIABLE_FRAME_SIZE)
        nb_samples = 10000;
    else
        nb_samples = c->frame_size;

    ost->frame     = alloc_audio_frame(c->sample_fmt, c->channel_layout,
                                       c->sample_rate, nb_samples);
    ost->tmp_frame = alloc_audio_frame(AV_SAMPLE_FMT_S16, c->channel_layout,
                                       c->sample_rate, nb_samples);

    /* create resampler context */
        ost->swr_ctx = swr_alloc();
        if (!ost->swr_ctx) {
            fprintf(stderr, "Could not allocate resampler context\n");
            exit(1);
        }

        /* set options */
        av_opt_set_int       (ost->swr_ctx, "in_channel_count",   c->channels,       0);
        av_opt_set_int       (ost->swr_ctx, "in_sample_rate",     c->sample_rate,    0);
        av_opt_set_sample_fmt(ost->swr_ctx, "in_sample_fmt",      AV_SAMPLE_FMT_S16, 0);
        av_opt_set_int       (ost->swr_ctx, "out_channel_count",  c->channels,       0);
        av_opt_set_int       (ost->swr_ctx, "out_sample_rate",    c->sample_rate,    0);
        av_opt_set_sample_fmt(ost->swr_ctx, "out_sample_fmt",     c->sample_fmt,     0);

        /* initialize the resampling context */
        if ((ret = swr_init(ost->swr_ctx)) < 0) {
            fprintf(stderr, "Failed to initialize the resampling context\n");
            exit(1);
        }
}

/* Prepare a 16 bit dummy audio frame of 'frame_size' samples and
 * 'nb_channels' channels. */
static void gen_audio_frame(OutputStream *ost, uint8_t **ppdata, int *size, int frame_index, int nb_samples, int channels)
{
   int j, i, v;

    *size = nb_samples*channels;
    uint8_t *data = av_malloc(nb_samples*channels);
    *ppdata = data;
    ALOGI("gen_audio_frame %d %d size %d", nb_samples, channels, *size);

    i = frame_index;

    for (j = 0; j < nb_samples; j++) {
        v = (int)(sin(ost->t) * 10000);
        for (i = 0; i < channels; i++)
            *data++ = v;
        ost->t     += ost->tincr;
        ost->tincr += ost->tincr2;
    }
}

static void fill_audio_frame(OutputStream *ost, uint8_t *data) {
    AVFrame *frame = ost->tmp_frame;
    int j, i, v;
    int16_t *q = (int16_t*)frame->data[0];

    for (j = 0; j <frame->nb_samples; j++) {
        for (i = 0; i < ost->st->codec->channels; i++)
            *q++ = *data++;
    }
    

    return frame;
}

static void push_audio_frame(OutputStream *ost) {
    AVCodecContext *c = ost->st->codec;
    AVPacket pkt;

    /* gen data */
    uint8_t *data ; 
    int size;
    gen_audio_frame(ost, &data, &size, ost->next_pts, ost->tmp_frame->nb_samples, ost->st->codec->channels);

    /* fill to packet */
    AVPacket p2;
    av_init_packet(&p2);
    p2.data = av_malloc(size);
    p2.size = size;
    p2.pts = ost->next_pts;
    ost->next_pts  += ost->tmp_frame->nb_samples;

    memcpy(p2.data, data, size);
    packet_queue_put(&audioq, &p2);
}

static AVFrame * pull_audio_frame( OutputStream *ost ) {
    AVPacket pkt;
    int serial;

    while(1) {
        if ( 1 == packet_queue_get(&audioq, &pkt, 0, &serial) && pkt.size != 0 && pkt.data != NULL)
         break;
    }

    // set frame
    fill_audio_frame(ost, pkt.data);
    av_free(pkt.data);
    ost->tmp_frame->pts = pkt.pts;
    return ost->tmp_frame;
    
}

static AVFrame *get_audio_frame(OutputStream *ost)
{
    AVFrame *frame = ost->tmp_frame;
    
    push_audio_frame(ost);

    pull_audio_frame(ost);

    fprintf(stderr, "get_audio_frame %d %d pts %lld\n", frame->nb_samples, ost->st->codec->channels, frame->pts);
    return frame;
}


/*
 * encode one audio frame and send it to the muxer
 * return 1 when encoding is finished, 0 otherwise
 */
static int write_audio_frame(AVFormatContext *oc, OutputStream *ost)
{
    AVCodecContext *c;
    AVPacket pkt = { 0 }; // data and size must be 0;
    AVFrame *frame;
    int ret;
    int got_packet;
    int dst_nb_samples;

    av_init_packet(&pkt);
    c = ost->st->codec;

    frame = get_audio_frame(ost);

    if (frame) {
        /* convert samples from native format to destination codec format, using the resampler */
            /* compute destination number of samples */
            dst_nb_samples = av_rescale_rnd(swr_get_delay(ost->swr_ctx, c->sample_rate) + frame->nb_samples,
                                            c->sample_rate, c->sample_rate, AV_ROUND_UP);
            av_assert0(dst_nb_samples == frame->nb_samples);

        /* when we pass a frame to the encoder, it may keep a reference to it
         * internally;
         * make sure we do not overwrite it here
         */
        ret = av_frame_make_writable(ost->frame);
        if (ret < 0)
            exit(1);

            /* convert to destination format */
            ret = swr_convert(ost->swr_ctx,
                              ost->frame->data, dst_nb_samples,
                              (const uint8_t **)frame->data, frame->nb_samples);
            if (ret < 0) {
                fprintf(stderr, "Error while converting\n");
                exit(1);
            }
            frame = ost->frame;

        frame->pts = av_rescale_q(ost->samples_count, (AVRational){1, c->sample_rate}, c->time_base);
        ost->samples_count += dst_nb_samples;
    }

    ret = avcodec_encode_audio2(c, &pkt, frame, &got_packet);
    if (ret < 0) {
        fprintf(stderr, "Error encoding audio frame: %s\n", av_err2str(ret));
        exit(1);
    }

    if (got_packet) {
        ret = write_frame(oc, &c->time_base, ost->st, &pkt);
        if (ret < 0) {
            fprintf(stderr, "Error while writing audio frame: %s\n",
                    av_err2str(ret));
            exit(1);
        }
    }

    return (frame || got_packet) ? 0 : 1;
}

/**************************************************************/
/* video output */

static AVFrame *alloc_picture(enum AVPixelFormat pix_fmt, int width, int height)
{
    AVFrame *picture;
    int ret;

    picture = av_frame_alloc();
    if (!picture)
        return NULL;

    picture->format = pix_fmt;
    picture->width  = width;
    picture->height = height;

    /* allocate the buffers for the frame data */
    ret = av_frame_get_buffer(picture, 32);
    if (ret < 0) {
        fprintf(stderr, "Could not allocate frame data.\n");
        exit(1);
    }

    return picture;
}

static void open_video(AVFormatContext *oc, AVCodec *codec, OutputStream *ost, AVDictionary *opt_arg)
{
    int ret;
    AVCodecContext *c = ost->st->codec;
    AVDictionary *opt = NULL;

    av_dict_copy(&opt, opt_arg, 0);

    av_dict_set(&opt, "vprofile", "baseline", 0);
    av_dict_set(&opt, "tune", "zerolatency", 0);
    av_dict_set(&opt, "preset","ultrafast",0);
    av_dict_set(&opt, "threads","4",0);

    /* open the codec */
    ret = avcodec_open2(c, codec, &opt);
    av_dict_free(&opt);
    if (ret < 0) {
        fprintf(stderr, "Could not open video codec: %s\n", av_err2str(ret));
        exit(1);
    }

    /* allocate and init a re-usable frame */
    ost->frame = alloc_picture(c->pix_fmt, c->width, c->height);
    if (!ost->frame) {
        fprintf(stderr, "Could not allocate video frame\n");
        exit(1);
    }

    /* If the output format is not YUV420P, then a temporary YUV420P
     * picture is needed too. It is then converted to the required
     * output format. */
    ost->tmp_frame = NULL;
    //if (c->pix_fmt != AV_PIX_FMT_YUV420P) 
    {
        fprintf(stderr, "use tmp_frame\n");
        ost->tmp_frame = alloc_picture(gen_video_fmt, c->width, c->height);
        if (!ost->tmp_frame) {
            fprintf(stderr, "Could not allocate temporary picture\n");
            exit(1);
        }
    }
}



static void fill_yuv_image_2(AVFrame *pict, uint8_t *data, int width, int height)
{
    int x, y, i, ret;


    /* Y */
    for (y = 0; y < height; y++)
        for (x = 0; x < width; x++)
            pict->data[0][y * pict->linesize[0] + x] = data[y*width + x];

    int cb_start = height*width;
    int cr_start = height*width + height*width/4;
    /* Cb and Cr */
    for (y = 0; y < height / 2; y++) {
        for (x = 0; x < width / 2; x++) {
            pict->data[1][y * pict->linesize[1] + x] = data[cb_start + y*width/2 + x];
            pict->data[2][y * pict->linesize[2] + x] = data[ cr_start + y*width/2 + x];
        }
    }
}

static void gen_yuv_image(uint8_t **ppdata, int *size, int frame_index , int width, int height)
{
    int x, y, i, ret;
    uint8_t *data = av_malloc(width*height*1.5);
    *ppdata = data;
    *size = width*height*1.5;
    ALOGI("gen_yuv_image %d %d size %d", width, height, *size);

    i = frame_index;

    /* Y */
    for (y = 0; y < height; y++)
        for (x = 0; x < width; x++)
            data[y * width + x] = x + y + i * 3;

    int cb_start = height*width;
    int cr_start = height*width + height*width/4;
    /* Cb and Cr */
    for (y = 0; y < height / 2; y++) {
        for (x = 0; x < width / 2; x++) {
            data[cb_start + x] = 128 + y + i * 2;
            data[ cr_start + y*width/2 + x] = 64 + x + i * 5;
        }
    }
}

static void fill_rgba_image(AVFrame *pict, uint8_t *data, int width, int height)
{
    int x, y, i, ret;

    /* Y */
    int len = (height*width -1) * 4;
    for (y = 0; y < height; y++)
        for (x = 0; x < width; x++) {
            //int k = y*width + x;
            //(x,y) -> (y,x)
            int k = y*width + (width-x);
            pict->data[0][pict->linesize[0] * y + 4*x  ] = data[len-4*k];
            pict->data[0][pict->linesize[0] * y + 4*x  + 1 ]= data[len-4*k + 1];
            pict->data[0][pict->linesize[0] * y + 4*x  + 2 ] = data[len-4*k + 2];
            pict->data[0][pict->linesize[0] * y + 4*x  + 3 ] = data[len-4*k + 3];
            //pict->data[1][y * pict->linesize[0] + x] = data[4*k + 1];
            //pict->data[2][y * pict->linesize[0] + x] = data[4*k + 2];
            //pict->data[3][y * pict->linesize[0] + x] = data[4*k + 3];
        }
}


static void gen_rgba_image(uint8_t **ppdata, int *size, int frame_index , int width, int height)
{
    int x, y, i, ret;
    *size = width*height*4;
    uint8_t *data = av_malloc(*size);
    *ppdata = data;
    ALOGI("gen_yuv_image %d %d size %d", width, height, *size);

    i = frame_index;

    /* Y */
    for (y = 0; y < height; y++)
        for (x = 0; x < width; x++) {
            int k = y*width + x;
            
            data[k*4] = 0xFF;
            data[k*4 + 1] = k + i ;
            data[k*4 + 2] = k + i * 2;
            data[k*4 + 3] = k + i * 3;
        }


}
static void gen_image(uint8_t **data, int *size, int frame_index , int width, int height) {
    //gen_yuv_image(data, size, frame_index,  width, height);
    gen_rgba_image(data, size, frame_index,  width, height);
}

static void fill_image(AVFrame *pict, uint8_t *data, int width, int height) {
    //fill_yuv_image_2(pict, data, width, height);
    fill_rgba_image(pict, data, width, height);
}

static int push_video_frame_andorid( uint8_t *data, int size, int64_t ts) {
    AVPacket pkt;

    int i = 0;
    if(videoq.size > MAX_CACHE_PKT ) {
        ALOGI("too MUCH CACHE %d, drop it", videoq.size);
        return -1;
    } 
    /* fill to packet */
    AVPacket p2;
    av_init_packet(&p2);
    p2.data = av_malloc(size);
    if ( !p2.data ) {
        ALOGI("can't malloc %d", size);
    }
    //ALOGI("mmonitor : malloc %d : %p", size, p2.data);
    p2.size = size;
    //pkt.pts = ts;
    pkt.pts = g_video_next_pst++;

    memcpy(p2.data, data, size);

    packet_queue_put(&videoq, &p2);

    if(videoq.size > NEEDWAIT_CACHE_PKT) {
        ALOGI("too MUCH CACHE %d, sleep 10 ms", videoq.size);
        usleep(10*1000);
    } 
    return 0;
}

static void push_video_frame(OutputStream *ost) {
    AVCodecContext *c = ost->st->codec;
    AVPacket pkt;

    /* gen data */
    uint8_t *data ; 
    int size;
    gen_image(&data, &size, ost->next_pts,  c->width, c->height);

    /* fill to packet */
    AVPacket p2;
    av_init_packet(&p2);
    p2.data = av_malloc(size);
    p2.size = size;
    pkt.pts = ost->next_pts++;

    memcpy(p2.data, data, size);
    packet_queue_put(&videoq, &p2);
}

static AVFrame * pull_video_frame(AVFrame *frame, int w, int h) {
    AVPacket pkt;
    int serial;

    while(1) {
        if ( 1 == packet_queue_get(&videoq, &pkt, 0, &serial) && pkt.size != 0 && pkt.data != NULL)
         break;
    }

    // set frame
    fill_image(frame, pkt.data, w, h);
    av_free(pkt.data);
    //ALOGI("mmonitor free %p", pkt.data);
    frame->pts = pkt.pts;
    return frame;
    
}

static AVFrame *get_video_frame(OutputStream *ost) {
    AVCodecContext *c;
    c = ost->st->codec;


    //push_video_frame(ost);

    //ost->tmp_frame =  
    pull_video_frame(ost->tmp_frame, c->width, c->height);
#if 1
        /* as we only generate a YUV420P picture, we must convert it
         * to the codec pixel format if needed */
        if (!ost->sws_ctx) {
            ost->sws_ctx = sws_getContext(c->width, c->height,
                                          gen_video_fmt,
                                          c->width, c->height,
                                          c->pix_fmt,
                                          SCALE_FLAGS, NULL, NULL, NULL);
            if (!ost->sws_ctx) {
                fprintf(stderr,
                        "Could not initialize the conversion context\n");
                exit(1);
            }
        }
        sws_scale(ost->sws_ctx,
                  (const uint8_t * const *)ost->tmp_frame->data, ost->tmp_frame->linesize,
                  0, c->height, ost->frame->data, ost->frame->linesize);

#endif

    ost->frame->pts = ost->tmp_frame->pts;
    fprintf(stderr, "get_video_frame %d %d pts %lld\n", ost->frame->width, ost->frame->height, ost->frame->pts);
    return ost->frame;
}

/*
 * encode one video frame and send it to the muxer
 * return 1 when encoding is finished, 0 otherwise
 */
static int write_video_frame(AVFormatContext *oc, OutputStream *ost)
{
    int ret;
    AVCodecContext *c;
    AVFrame *frame;
    int got_packet = 0;

    c = ost->st->codec;

    frame = get_video_frame(ost);

    if (oc->oformat->flags & AVFMT_RAWPICTURE) {
        /* a hack to avoid data copy with some raw video muxers */
        AVPacket pkt;
        av_init_packet(&pkt);

        if (!frame)
            return 1;

        pkt.flags        |= AV_PKT_FLAG_KEY;
        pkt.stream_index  = ost->st->index;
        pkt.data          = (uint8_t *)frame;
        pkt.size          = sizeof(AVPicture);

        pkt.pts = pkt.dts = frame->pts;
        my_av_packet_rescale_ts(&pkt, c->time_base, ost->st->time_base);

        ret = av_interleaved_write_frame(oc, &pkt);
    } else {
        AVPacket pkt = { 0 };
        av_init_packet(&pkt);

        /* encode the image */
        ret = avcodec_encode_video2(c, &pkt, frame, &got_packet);
        if (ret < 0) {
            fprintf(stderr, "Error encoding video frame: %s\n", av_err2str(ret));
            exit(1);
        }

        if (got_packet) {
            ret = write_frame(oc, &c->time_base, ost->st, &pkt);
        } else {
            ret = 0;
        }
    }

    if (ret < 0) {
        fprintf(stderr, "Error while writing video frame: %s\n", av_err2str(ret));
        exit(1);
    }

    return (frame || got_packet) ? 0 : 1;
}

static void close_stream(AVFormatContext *oc, OutputStream *ost)
{
    avcodec_close(ost->st->codec);
    av_frame_free(&ost->frame);
    av_frame_free(&ost->tmp_frame);
    sws_freeContext(ost->sws_ctx);
    swr_free(&ost->swr_ctx);
    ost->frame = NULL;
    ost->tmp_frame = NULL;
    ost->sws_ctx = NULL;
}

/**************************************************************/
/* media file output */

void init_audio() {
}

void muxing_init_video(int width, int height) {
    g_width = width;
    g_height = height;

}

void open_output(void *data)
{
    const char *filename = (const char *) data;

    /* Initialize libavcodec, and register all codecs and formats. */
    av_register_all();

    packet_queue_init(&videoq);
    packet_queue_init(&audioq);
    packet_queue_start(&videoq);
    packet_queue_start(&audioq);

    /* allocate the output media context */
    avformat_alloc_output_context2(&oc, NULL, NULL, filename);
    if (!oc) {
        printf("Could not deduce output format from file extension: using MPEG.\n");
        avformat_alloc_output_context2(&oc, NULL, "mpeg", filename);
    }
    if (!oc)
        return 1;

    fmt = oc->oformat;

    /* Add the audio and video streams using the default format codecs
     * and initialize the codecs. */
    fmt->video_codec = AV_CODEC_ID_H264;
    if (fmt->video_codec != AV_CODEC_ID_NONE) {
        add_stream(&video_st, oc, &video_codec, fmt->video_codec);
        have_video = 1;
        encode_video = 1;
    }
    if (fmt->audio_codec != AV_CODEC_ID_NONE) {
        add_stream(&audio_st, oc, &audio_codec, fmt->audio_codec);
        have_audio = 1;
        encode_audio = 1;
    }

    /* Now that all the parameters are set, we can open the audio and
     * video codecs and allocate the necessary encode buffers. */
    if (have_video)
        open_video(oc, video_codec, &video_st, opt);

    if (have_audio)
        open_audio(oc, audio_codec, &audio_st, opt);

    av_dump_format(oc, 0, filename, 1);

    /* open the output file, if needed */
    if (!(fmt->flags & AVFMT_NOFILE)) {
        ret = avio_open(&oc->pb, filename, AVIO_FLAG_WRITE);
        if (ret < 0) {
            fprintf(stderr, "Could not open '%s': %s\n", filename,
                    av_err2str(ret));
            return 1;
        }
    }

    /* Write the stream header, if any. */
    ret = avformat_write_header(oc, &opt);
    if (ret < 0) {
        fprintf(stderr, "Error occurred when opening output file: %s\n",
                av_err2str(ret));
        return 1;
    }

}


int muxing_putBuffer(int isVideo, uint8_t *data, int size, int64_t ts, int flags) {
    if (isVideo) {
        push_video_frame_andorid(data, size, ts);
#if 0
             int i ;
            char tmp[1024];
            char tmp2[12];
            tmp[0] = '\0';
            for(i = 0; i < 12 && i < size; i++) {
                sprintf(tmp2, "%x ", data[i]);
                strcat(tmp, tmp2);
            }
            printf("%lld : %s", ts, tmp);
#endif

    } else {
    }
}

static void *thread_put_loop(){
    g_thread_put_runing = 1;

    while(!g_stop && (encode_video || encode_audio)) {
        /* select the stream to encode */
        if (encode_video &&
            (!encode_audio || av_compare_ts(video_st.next_pts, video_st.st->codec->time_base,
                                            audio_st.next_pts, audio_st.st->codec->time_base) <= 0)) {
            ALOGI("push_video_frame");
            push_video_frame(&video_st);
        } else {
            push_audio_frame(&audio_st);
        }
    }
    g_thread_put_runing = 0;
}

static void *thread_get_loop() {
    while ((encode_video || encode_audio) && !g_stop) {
        /* select the stream to encode */
        if (encode_video &&
            (!encode_audio || av_compare_ts(video_st.next_pts, video_st.st->codec->time_base,
                                            audio_st.next_pts, audio_st.st->codec->time_base) <= 0)) {
            //ALOGI("write_video_frame");
            encode_video = !write_video_frame(oc, &video_st);
        } else {
            ALOGI("write_audio_frame");
            encode_audio = !write_audio_frame(oc, &audio_st);
        }
    }

    while(g_thread_put_runing) {
        usleep(10000);
        ALOGI("stop wait g_thread_put_runing");
    }

    /* Write the trailer, if any. The trailer must be written before you
     * close the CodecContexts open when you wrote the header; otherwise
     * av_write_trailer() may try to use memory that was freed on
     * av_codec_close(). */
    av_write_trailer(oc);

    /* Close each codec. */
    if (have_video)
        close_stream(oc, &video_st);
    if (have_audio)
        close_stream(oc, &audio_st);

    if (!(fmt->flags & AVFMT_NOFILE))
        /* Close the output file. */
        avio_closep(&oc->pb);

    /* free the stream */
    avformat_free_context(oc);
    eos = 1;

    return 0;
}

int muxing_stop() {
    ALOGI("stop now");
   g_stop = 1; 
    while(!eos) {
        usleep(10000);
        ALOGI("stop wait eos");
    }

    packet_queue_destroy(&videoq);
    packet_queue_destroy(&audioq);
}

#if 1
int muxing_start(const char *g_out_filename) {
    //char * g_out_filename = "/sdcard/a.mp4";
    eos = 0;
    g_stop = 0;

    //init_video(720, 480);
    open_output(g_out_filename);
    //pthread_create (&g_put_thread, NULL, &thread_put_loop, NULL);
    pthread_create (&g_get_thread, NULL, &thread_get_loop, NULL);
    //thread_get_loop();

}

#else
int main(int argc, char **argv) {
    char * g_out_filename = "/sdcard/a.mp4";
    eos = 0;
    g_stop = 0;

    init_video(720, 480);
    open_output(g_out_filename);
    pthread_create (&g_put_thread, NULL, &thread_put_loop, NULL);
    pthread_create (&g_get_thread, NULL, &thread_get_loop, NULL);
    //thread_get_loop();

    int i = 0;
    while(!eos) {
        sleep(1);
        if (i++ == 3 ) {
            stop();
        }
    }

}
#endif

