#ifndef  J_AV_QUEUE_H__
#define J_AV_QUEUE_H__

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

#define MAX_CACHE_PKT 1024



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


void packet_queue_init(PacketQueue *q);
void packet_queue_start(PacketQueue *q);
int packet_queue_put(PacketQueue *q, AVPacket *pkt);
AVPacket *packet_queue_peek(PacketQueue *q);
int packet_queue_get(PacketQueue *q, AVPacket *pkt, int block, int *serial);
void packet_queue_destroy(PacketQueue *q);
void packet_queue_destroy(PacketQueue *q);

#endif
