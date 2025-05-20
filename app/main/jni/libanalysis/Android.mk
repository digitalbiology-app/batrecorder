LOCAL_PATH := $(call my-dir)

#################################################
# Non NEON

#ifeq ($(TARGET_ARCH_ABI),armeabi)
include $(CLEAR_VARS)
LOCAL_MODULE := analysis
LOCAL_CFLAGS += -DPFFFT_SIMD_DISABLE
LOCAL_SRC_FILES := analysis.c fft_bind.c pffft.c quickblob.c blobhooks.c
LOCAL_C_INCLUDES := $(LOCAL_PATH)
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)
LOCAL_LDLIBS    := -lm -llog -ljnigraphics
include $(BUILD_SHARED_LIBRARY)
#endif

#################################################
# NEON
ifneq ($(TARGET_ARCH_ABI),armeabi)
include $(CLEAR_VARS)
LOCAL_MODULE := analysisNEON
LOCAL_SRC_FILES := analysis.c fft_bind.c pffft.c quickblob.c blobhooks.c
LOCAL_ARM_NEON := true
LOCAL_C_INCLUDES := $(LOCAL_PATH)
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)
LOCAL_LDLIBS    := -lm -llog -ljnigraphics
include $(BUILD_SHARED_LIBRARY)
endif