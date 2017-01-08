/*
droid vnc server - Android VNC server
Copyright (C) 2011 Jose Pereira <onaips@gmail.com>

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 3 of the License, or (at your option) any later version.

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
*/

#include <dlfcn.h>

#include <string.h>
#include <jni.h>
#include <android/log.h>

#include <math.h>

#include "screenrecord.h"

#define ALOGI(x...)    __android_log_print (ANDROID_LOG_INFO, "screenrecord_jni", x);
#define ALOGE(x...)    __android_log_print (ANDROID_LOG_ERROR, "screenrecord_jni", x);

void *screenrecorder_lib = NULL;

typedef int  (*init_screenrecord_t  ) (int width, int height, onFrameAvaliable_t onFrame) ;
typedef int  (*start_recordScreen_t ) () ;
typedef void (*stop_recordScreen_t  ) () ;

init_screenrecord_t  init_screenrecord = NULL;
start_recordScreen_t start_recordScreen = NULL;
stop_recordScreen_t stop_recordScreen = NULL;

int loadLib(void)
{
  ALOGI("--Loading screenrecorder native lib--\n");
  int i,len;
  char lib_name[64];

    sprintf(lib_name, "libscreenrecord_sdk.so");

    if (screenrecorder_lib != NULL)
      dlclose(screenrecorder_lib);
    ALOGI("Loading lib: %s\n",lib_name);
    screenrecorder_lib = dlopen(lib_name, RTLD_NOW);
    if (screenrecorder_lib == NULL){
      ALOGE("Couldnt load screenrecorder library %s! Error string: %s\n",lib_name,dlerror());
      return -1;
    }
  
    init_screenrecord = dlsym(screenrecorder_lib,"init_screenrecord");
    if(init_screenrecord == NULL) {
      ALOGE("Couldn't load init_screenrecord! Error string: %s\n",dlerror());
      return -1;
    }
  
    stop_recordScreen = dlsym(screenrecorder_lib,"stop_recordScreen");
    if(stop_recordScreen == NULL) {
      ALOGE("Couldn't load stop_recordScreen! Error string: %s\n",dlerror());
      return -1;
    }

    start_recordScreen = dlsym(screenrecorder_lib,"start_recordScreen");
    if(start_recordScreen == NULL) {
      ALOGE("Couldn't load start_recordScreen! Error string: %s\n",dlerror());
      return -1;
    }

    ALOGI("AKI1 loadLib OK\n");
    
    return 0;
}



int initRecorder(int width, int height, onFrameAvaliable_t onFrame) 
{
  loadLib();
  if (init_screenrecord) {
   ALOGI("init_screenrecord %d %d %p", width, height, onFrame);
    init_screenrecord(width, height, onFrame);
  }
}

void startRecoder(void)
{
  if (start_recordScreen) {
      ALOGI("start_recordScreen");
    start_recordScreen();
  }
}

void stopRecoder(void)
{
  if (stop_recordScreen) {
      ALOGI("stop_recordScreen");
    stop_recordScreen();
  }

  if (screenrecorder_lib)
    dlclose(screenrecorder_lib);
}
