
#ifndef __AVPACKET_QUEUE_H_
#define __AVPACKET_QUEUE_H_

#define SDL_mutex char 
#define SDL_cond  char 

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


#endif
