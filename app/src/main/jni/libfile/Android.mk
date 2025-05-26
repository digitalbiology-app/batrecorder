LOCAL_PATH := $(call my-dir)

#################################################

include $(CLEAR_VARS)
LOCAL_MODULE := file
LOCAL_SRC_FILES := file.c
LOCAL_C_INCLUDES := $(LOCAL_PATH)
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)
LOCAL_LDLIBS    := -lm -llog -ljnigraphics
LOCAL_STATIC_LIBRARIES := cpufeatures
include $(BUILD_SHARED_LIBRARY)
$(call import-module, android/cpufeatures)
