/*
 * Copyright 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef SCREENRECORD_SCREENRECORD_H
#define SCREENRECORD_SCREENRECORD_H


typedef void (*onFrameAvaliable_t)(void *frame, int size, int type, int64_t ts);

#define FrameType_spspps 1
//#define FrameType_sps 2
#define FrameType_esds 3
#define FrameType_video_iframe 8
#define FrameType_video_frame 9

#define NO_ERR_STOPED 1
#define NO_ERR_STARTED 0
#define ERR_GET_DISPLAYINFO -1
#define ERR_PREPARE_ENCODER -2
#define ERR_PREPARE_DISPLAY -2
#define ERR_RUN_ENCODER -3

#ifdef _cplusplus
extern "C" 
{
#endif

int initRecorder(int width, int height, onFrameAvaliable_t onFrame) ;
int startRecorder() ;
void stopRecorder() ;

#ifdef _cplusplus
}
#endif

//extern "C" int init_screenrecord(int width, int height, onFrameAvaliable_t onFrame) ;
//extern "C" int start_recordScreen() ;
//extern "C" void stop_recordScreen() ;

#endif /*SCREENRECORD_SCREENRECORD_H*/
