/*
  * A H264/AAC TO FLV implementation
  *
  * Copyright (C) 2014 rainfly123 <xiechc@gmail.com>
  *
  * This program is free software; you can redistribute it and/or modify
  * it under the terms of the GNU General Public License as published by
  * the Free Software Foundation; either version 2 of the License, or
  * (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU General Public License for more details.
  *
  * You should have received a copy of the GNU General Public License
  * along with this program; if not, write to the Free Software
  * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
  *
  */
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <fcntl.h>

static uint32_t find_start_code(uint8_t *buf, uint32_t i)   
{   
  uint32_t info;   
  //uint32_t i;   
   
  if ( buf[0] == 0
      && buf[1] == 0
      && buf[2] == 0
      && buf[3] == 1)
      return 1;
  else
      return 0;

  //info = 1;   
  //  info = (buf[zeros_in_startcode] == 1)? 1: 0;
  //if (info == 0)   
  //    return 0;   
  //     
  //for (i = 0; i < zeros_in_startcode; i++)   {
  //  if (buf[i] != 0)   
  //  { 
  //      info = 0;
  //      break;
  //  }   
  //}

  //return info;   
}   

uint8_t * get_nal(uint32_t *len, uint8_t **offset, uint8_t *start, uint32_t total)
{
    uint32_t info;
    uint8_t *q = NULL;
    uint8_t *p  =  *offset;
    *len = 0;

    while(1) {
        info =  find_start_code(p, 3);
        if (info == 1)
            break;
        p++;
        if ((p - start) >= total)
            return NULL;
    }
    q = p + 4;
    p = p + 6;
    while(1) {
        info =  find_start_code(p, 3);
        if (info == 1)
            break;
        p++;
        if ((p - start) >= total)
            return NULL;
    }
    
    *len = (p - q);
    *offset = p;
    return q;
}

#define AAC_ADTS_HEADER_SIZE 7
#define FLV_TAG_HEAD_LEN 11
#define FLV_PRE_TAG_LEN 4

typedef struct {
    uint8_t audio_object_type;
    uint8_t sample_frequency_index;
    uint8_t channel_configuration;
}AudioSpecificConfig;

AudioSpecificConfig gen_config(uint8_t *frame)
{
    AudioSpecificConfig config = {0, 0, 0};

    if (frame == NULL) {
        return config;
    }
    config.audio_object_type = (frame[2] & 0xc0) >> 6;
    config.sample_frequency_index =  (frame[2] & 0x3c) >> 2;
    config.channel_configuration = (frame[3] & 0xc0) >> 6;
    return config;
}

uint8_t gen_audio_tag_header(AudioSpecificConfig config)
{
     uint8_t soundType = config.channel_configuration - 1; //0 mono, 1 stero
     uint8_t soundRate = 0;
     uint8_t val = 0;


     //printf("-------------- sample_frequency_index %d, soundType %d \n", config.sample_frequency_index, soundType);
     switch (config.sample_frequency_index) {
         case 10: { //11.025k
             soundRate = 1;
             break;
         }
         case 7: { //22k
             soundRate = 2;
             break;
         }
         case 4: { //44k
             soundRate = 3;
             break;
         }

         default:
         { 
             return val;
         }
    }
    val = 0xA0 | (soundRate << 2) | 0x02 | soundType;
    return val;
}

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

char *url = "rtmp://publish.huizu100.com/g_live/songguangyu?key=d2b755c06e892172";
char *video_path = "test.264";
char *audio_path = "test.aac";

int video_size = 18051213;
int audio_size = 1570667; 


int main(int argc, char **argv)
{
    int fd = open(video_path, O_RDONLY);
    uint8_t * buf = malloc(video_size);
    uint32_t total;
    total = read(fd, buf, video_size);
    close(fd);
    uint8_t *buf_offset = buf;

    #if 0
    uint8_t *nall;
    uint32_t len;
    while (1) {
        nall = get_nal(&len, &buf_offset, buf, total);
        if (nall == NULL) {
            printf("not nall \n");
            break;
            //continue;
        }
        printf("%d %x %x %x %x\n", len, nall[0], nall[1], nall[2], nall[3]);
   }
    #endif

    int aacfd = open(audio_path, O_RDONLY); 
    uint8_t * audio_buf = malloc(audio_size);
    uint32_t audio_total;
    audio_total = read(aacfd, audio_buf, audio_size );
    close(aacfd);
    uint8_t *audio_buf_offset = audio_buf;
    uint32_t adts_len;
   #if 0
    while (1) {
        char *audio_t = get_adts(&adts_len, &audio_buf_offset, audio_buf, audio_total);
        if (audio_t) {
            printf("%d %x\n", adts_len, (uint8_t)audio_t[0]);
            AudioSpecificConfig config = gen_config(audio_t);
            printf("profile:%d sample:%d channel:%d\n", config.audio_object_type, \
                  config.sample_frequency_index, config.channel_configuration);
        }
        else break;
    }
   #endif

    int flv_file = open("a.flv", O_WRONLY|O_CREAT, 0666);
    uint8_t flv_header[13] = {0x46, 0x4c, 0x56, 0x01, 0x05, 0x00, 0x00, 0x00, 0x09, \
                             0x00, 0x00,0x00,0x00};
    write(flv_file, flv_header, sizeof(flv_header));
    //uint32_t start_time = RTMP_GetTime();
    uint32_t start_time = 0;

    uint32_t nal_len;
    uint32_t nal_len_n;
    uint8_t *nal; 
    uint8_t *nal_n;
    uint8_t *output ; 
    uint32_t offset = 0;
    uint32_t body_len;
    uint32_t ts = 0;
    uint32_t audio_ts = 0;
    uint32_t output_len;
    uint8_t *audio_frame;
    uint8_t audio_seq_set = 0;
    static AudioSpecificConfig config;
while (1) {
    //ts = RTMP_GetTime() - start_time;
    //Audio OUTPUT
#if 1
    offset = 0;
    audio_frame = get_adts(&adts_len, &audio_buf_offset, audio_buf, audio_total);
    if (audio_frame == NULL) break;
    if (audio_seq_set == 0) {
        config = gen_config(audio_frame);
        body_len = 2 + 2; //AudioTagHeader + AudioSpecificConfig
        output_len = body_len + FLV_TAG_HEAD_LEN + FLV_PRE_TAG_LEN;
        output = malloc(output_len);
        // flv tag header
        output[offset++] = 0x08; //tagtype video
        output[offset++] = (uint8_t)(body_len >> 16); //data len
        output[offset++] = (uint8_t)(body_len >> 8); //data len
        output[offset++] = (uint8_t)(body_len); //data len
        output[offset++] = (uint8_t)(audio_ts >> 16); //time stamp
        output[offset++] = (uint8_t)(audio_ts >> 8); //time stamp
        output[offset++] = (uint8_t)(audio_ts); //time stamp
        output[offset++] = (uint8_t)(audio_ts >> 24); //time stamp
        output[offset++] = 0x00; //stream id 0
        output[offset++] = 0x00; //stream id 0
        output[offset++] = 0x00; //stream id 0

        //flv AudioTagHeader
        output[offset++] = gen_audio_tag_header(config); // sound format aac
        output[offset++] = 0x00; //aac sequence header

        //flv VideoTagBody --AudioSpecificConfig
        uint8_t audio_object_type = config.audio_object_type + 1;
        output[offset++] = (audio_object_type << 3) | (config.sample_frequency_index >> 1); 
        output[offset++] = ((config.sample_frequency_index & 0x01) << 7) \
                           | (config.channel_configuration << 3) ;
        uint32_t fff = body_len + FLV_TAG_HEAD_LEN;
        output[offset++] = (uint8_t)(fff >> 24); //data len
        output[offset++] = (uint8_t)(fff >> 16); //data len
        output[offset++] = (uint8_t)(fff >> 8); //data len
        output[offset++] = (uint8_t)(fff); //data len

        write(flv_file, output, output_len);
        free(output);

        audio_seq_set = 1;
    }else
    {
        body_len = 2 + adts_len - AAC_ADTS_HEADER_SIZE; // remove adts header + AudioTagHeader
        output_len = body_len + FLV_TAG_HEAD_LEN + FLV_PRE_TAG_LEN;
        output = malloc(output_len);
        // flv tag header
        output[offset++] = 0x08; //tagtype video
        output[offset++] = (uint8_t)(body_len >> 16); //data len
        output[offset++] = (uint8_t)(body_len >> 8); //data len
        output[offset++] = (uint8_t)(body_len); //data len
        output[offset++] = (uint8_t)(audio_ts >> 16); //time stamp
        output[offset++] = (uint8_t)(audio_ts >> 8); //time stamp
        output[offset++] = (uint8_t)(audio_ts); //time stamp
        output[offset++] = (uint8_t)(audio_ts >> 24); //time stamp
        output[offset++] = 0x00; //stream id 0
        output[offset++] = 0x00; //stream id 0
        output[offset++] = 0x00; //stream id 0

        //flv AudioTagHeader
        output[offset++] = gen_audio_tag_header(config); // sound format aac
        //output[offset++] = 0x01; //aac raw data 
        output[offset++] = 10; //aac raw data 

        //flv VideoTagBody --raw aac data
        memcpy(output + offset, audio_frame + AAC_ADTS_HEADER_SIZE,\
                 (adts_len - AAC_ADTS_HEADER_SIZE)); //H264 sequence parameter set
        //previous tag size 
        uint32_t fff = body_len + FLV_TAG_HEAD_LEN;
        offset += (adts_len - AAC_ADTS_HEADER_SIZE);
        output[offset++] = (uint8_t)(fff >> 24); //data len
        output[offset++] = (uint8_t)(fff >> 16); //data len
        output[offset++] = (uint8_t)(fff >> 8); //data len
        output[offset++] = (uint8_t)(fff); //data len
        write(flv_file, output, output_len);
        free(output);
        audio_ts += 25;

    }
#endif


#if 1

    //Video OUTPUT 
    offset = 0;
    nal = get_nal(&nal_len, &buf_offset, buf, total);
    if (nal == NULL) { 
        printf("can not get nal\n");
        break;
    }
    printf("%d get nal  %x %x %x %x %x %x %x , nal_len %d\n", nal-buf, 
        nal[0], nal[1], nal[2], nal[3], nal[4],
        nal[5],
        nal[6],
        nal[7],
        nal_len);

    if(nal[0] == 0x9) {
        nal_len = nal_len - 6;
        nal = &nal[6];

        printf("%d get nal  %x %x %x %x %x %x , nal_len %d\n", nal-buf, 
            nal[0], nal[1], nal[2], nal[3], nal[4],
            nal[5],
            nal_len);
    }

    int nal_type = nal[0] & 0x1f;
    printf("nal_type %d\n", nal_type);
    if (nal_type == 7)  {
        nal_n  = get_nal(&nal_len_n, &buf_offset, buf, total); //get pps
        printf("nal_type %x\n", nal_n[0]);
        body_len = nal_len + nal_len_n + 16;
        output_len = body_len + FLV_TAG_HEAD_LEN + FLV_PRE_TAG_LEN;
        output = malloc(output_len);
        // flv tag header
        output[offset++] = 0x09; //tagtype video
        output[offset++] = (uint8_t)(body_len >> 16); //data len
        output[offset++] = (uint8_t)(body_len >> 8); //data len
        output[offset++] = (uint8_t)(body_len); //data len
        output[offset++] = (uint8_t)(ts >> 16); //time stamp
        output[offset++] = (uint8_t)(ts >> 8); //time stamp
        output[offset++] = (uint8_t)(ts); //time stamp
        output[offset++] = (uint8_t)(ts >> 24); //time stamp
        output[offset++] = 0x00; //stream id 0
        output[offset++] = 0x00; //stream id 0
        output[offset++] = 0x00; //stream id 0

        //flv VideoTagHeader
        output[offset++] = 0x17; //key frame, AVC
        output[offset++] = 0x00; //avc sequence header
        output[offset++] = 0x00; //composit time ??????????
        output[offset++] = 0x00; // composit time
        output[offset++] = 0x00; //composit time

        //flv VideoTagBody --AVCDecoderCOnfigurationRecord
        output[offset++] = 0x01; //configurationversion
        output[offset++] = nal[1]; //avcprofileindication
        output[offset++] = nal[2]; //profilecompatibilty
        output[offset++] = nal[3]; //avclevelindication
        output[offset++] = 0xff; //reserved + lengthsizeminusone
        output[offset++] = 0xe1; //numofsequenceset
        output[offset++] = (uint8_t)(nal_len >> 8); //sequence parameter set length high 8 bits
        output[offset++] = (uint8_t)(nal_len); //sequence parameter set  length low 8 bits
        memcpy(output + offset, nal, nal_len); //H264 sequence parameter set
        offset += nal_len;
        output[offset++] = 0x01; //numofpictureset
        output[offset++] = (uint8_t)(nal_len_n >> 8); //picture parameter set length high 8 bits
        output[offset++] = (uint8_t)(nal_len_n); //picture parameter set length low 8 bits
        memcpy(output + offset, nal_n, nal_len_n); //H264 picture parameter set

        //no need set pre_tag_size ,RTMP NO NEED
        // flv test 
        offset += nal_len_n;
        uint32_t fff = body_len + FLV_TAG_HEAD_LEN;
        output[offset++] = (uint8_t)(fff >> 24); //data len
        output[offset++] = (uint8_t)(fff >> 16); //data len
        output[offset++] = (uint8_t)(fff >> 8); //data len
        output[offset++] = (uint8_t)(fff); //data len
        write(flv_file, output, output_len);
       //RTMP Send out
        free(output);
        continue;
    }
    if (nal[0] == 0x06)
    {    //do nothin
    }

    if (nal_type  == 5)
    {
        ts += 50;
        body_len = nal_len + 5 + 4; //flv VideoTagHeader +  NALU length
        output_len = body_len + FLV_TAG_HEAD_LEN + FLV_PRE_TAG_LEN;
        output = malloc(output_len);
        // flv tag header
        output[offset++] = 0x09; //tagtype video
        output[offset++] = (uint8_t)(body_len >> 16); //data len
        output[offset++] = (uint8_t)(body_len >> 8); //data len
        output[offset++] = (uint8_t)(body_len); //data len
        output[offset++] = (uint8_t)(ts >> 16); //time stamp
        output[offset++] = (uint8_t)(ts >> 8); //time stamp
        output[offset++] = (uint8_t)(ts); //time stamp
        output[offset++] = (uint8_t)(ts >> 24); //time stamp
        output[offset++] = 0x00; //stream id 0
        output[offset++] = 0x00; //stream id 0
        output[offset++] = 0x00; //stream id 0

        //flv VideoTagHeader
        output[offset++] = 0x17; //key frame, AVC
        output[offset++] = 0x01; //avc NALU unit
        output[offset++] = 0x00; //composit time ??????????
        output[offset++] = 0x00; // composit time
        output[offset++] = 0x00; //composit time

        output[offset++] = (uint8_t)(nal_len >> 24); //nal length 
        output[offset++] = (uint8_t)(nal_len >> 16); //nal length 
        output[offset++] = (uint8_t)(nal_len >> 8); //nal length 
        output[offset++] = (uint8_t)(nal_len); //nal length 
        memcpy(output + offset, nal, nal_len);

        //no need set pre_tag_size ,RTMP NO NEED
        offset += nal_len;
        uint32_t fff = body_len + FLV_TAG_HEAD_LEN;
        output[offset++] = (uint8_t)(fff >> 24); //data len
        output[offset++] = (uint8_t)(fff >> 16); //data len
        output[offset++] = (uint8_t)(fff >> 8); //data len
        output[offset++] = (uint8_t)(fff); //data len
        write(flv_file, output, output_len);
       //RTMP Send out
        free(output);
        continue;
     }

    if (nal_type == 1 )
    {
        ts += 50;
        body_len = nal_len + 5 + 4; //flv VideoTagHeader +  NALU length
        output_len = body_len + FLV_TAG_HEAD_LEN + FLV_PRE_TAG_LEN;
        output = malloc(output_len);
        // flv tag header
        output[offset++] = 0x09; //tagtype video
        output[offset++] = (uint8_t)(body_len >> 16); //data len
        output[offset++] = (uint8_t)(body_len >> 8); //data len
        output[offset++] = (uint8_t)(body_len); //data len
        output[offset++] = (uint8_t)(ts >> 16); //time stamp
        output[offset++] = (uint8_t)(ts >> 8); //time stamp
        output[offset++] = (uint8_t)(ts); //time stamp
        output[offset++] = (uint8_t)(ts >> 24); //time stamp
        output[offset++] = 0x00; //stream id 0
        output[offset++] = 0x00; //stream id 0
        output[offset++] = 0x00; //stream id 0

        //flv VideoTagHeader
        output[offset++] = 0x27; //key frame, AVC
        output[offset++] = 0x01; //avc NALU unit
        output[offset++] = 0x00; //composit time ??????????
        output[offset++] = 0x00; // composit time
        output[offset++] = 0x00; //composit time

        output[offset++] = (uint8_t)(nal_len >> 24); //nal length 
        output[offset++] = (uint8_t)(nal_len >> 16); //nal length 
        output[offset++] = (uint8_t)(nal_len >> 8); //nal length 
        output[offset++] = (uint8_t)(nal_len); //nal length 
        memcpy(output + offset, nal, nal_len);

        //no need set pre_tag_size ,RTMP NO NEED
        offset += nal_len;
        uint32_t fff = body_len + FLV_TAG_HEAD_LEN;
        output[offset++] = (uint8_t)(fff >> 24); //data len
        output[offset++] = (uint8_t)(fff >> 16); //data len
        output[offset++] = (uint8_t)(fff >> 8); //data len
        output[offset++] = (uint8_t)(fff); //data len
        write(flv_file, output, output_len);

       //RTMP Send out
        free(output);
        continue;
     }
#endif

   }


}
