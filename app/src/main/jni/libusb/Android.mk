LOCAL_PATH := $(call my-dir)  

include $(CLEAR_VARS)  

LOCAL_MODULE := usb
#LOCAL_WHOLE_STATIC_LIBRARIES := libwav
LOCAL_SRC_FILES:= \
	 core.c \
	 descriptor.c \
	 io.c \
	 sync.c \
	 os/linux_usbfs.c \
	 os/threads_posix.c \
	 ringbuffer.c \
	 usb_binding.c
LOCAL_CFLAGS := -std=c11
LOCAL_LDLIBS := -lm -llog
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)

include $(BUILD_SHARED_LIBRARY)
