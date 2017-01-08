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

#include <android/log.h>
#define APPLOGI(x...)    native_logCallback (ANDROID_LOG_INFO, "rtmp_sender", x);

static int packet_queue_put_private(PacketQueue *q, AVPacket *pkt)
{
    MyAVPacketList *pkt1;

    if (q->abort_request)
       return -1;

    pkt1 = (MyAVPacketList *)av_mallocz(sizeof(MyAVPacketList));
    //monitor_malloc(pkt1, sizeof(MyAVPacketList));
    if (!pkt1)
        return -1;
    pkt1->pkt = *pkt;
    pkt1->next = NULL;
    if (pkt == &flush_pkt)
        q->serial++;
    pkt1->serial = q->serial;

    if (!q->last_pkt) {
        q->first_pkt = pkt1;
        q->last_pkt = pkt1;
    } else {
        // sort 
        if (q->last_pkt->pkt.pts <=  pkt->pts) {
            //APPLOGI("sort pkt normal");
            q->last_pkt->next = pkt1;
            q->last_pkt = pkt1;
        } else if (q->first_pkt->pkt.pts >=  pkt->pts) {

            APPLOGI("sort pkt too small put head  %lld %lld",q->first_pkt->pkt.pts ,  pkt->pts );
            pkt1->next = q->first_pkt;
            q->first_pkt = pkt1;

        } else {

            // first < pts and last > pts
            MyAVPacketList *mypkt, *mypkt1;
            for (mypkt = q->first_pkt; mypkt; mypkt = mypkt1) {
                mypkt1 = mypkt->next;

                if (mypkt->next->pkt.pts >= pkt->pts) {
                    APPLOGI("sort pkt put pkt %lld %lld %lld",mypkt->pkt.pts ,  pkt->pts, mypkt->next->pkt.pts );
                    struct MyAVPacketList *next = mypkt->next;
                    mypkt->next = pkt1;
                    pkt1->next = next;
                    break;
                }
            }

        }
    }

    q->nb_packets++;
    q->size += pkt1->pkt.size + sizeof(*pkt1);
    /* XXX: should duplicate packet data in DV case */
    SDL_CondSignal(q->cond);
    return 0;
}



int packet_queue_put(PacketQueue *q, AVPacket *pkt)
{
    int ret;

    /* duplicate the packet */
    //if (pkt != &flush_pkt /*&& av_dup_packet(pkt) < 0*/)
    //    return -1;

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
void packet_queue_init(PacketQueue *q)
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
        //monitor_free(&pkt);
        av_freep(&pkt);
    }
    q->last_pkt = NULL;
    q->first_pkt = NULL;
    q->nb_packets = 0;
    q->size = 0;
    SDL_UnlockMutex(q->mutex);
}

void packet_queue_destroy(PacketQueue *q)
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

void packet_queue_start(PacketQueue *q)
{
    SDL_LockMutex(q->mutex);
    q->abort_request = 0;
    packet_queue_put_private(q, &flush_pkt);
    SDL_UnlockMutex(q->mutex);
}

/* return < 0 if aborted, 0 if no packet and > 0 if packet.  */
AVPacket *packet_queue_peek(PacketQueue *q)
{
    return q->first_pkt;
}

/* return < 0 if aborted, 0 if no packet and > 0 if packet.  */
int packet_queue_get(PacketQueue *q, AVPacket *pkt, int block, int *serial)
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
            //monitor_free(pkt1);
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


