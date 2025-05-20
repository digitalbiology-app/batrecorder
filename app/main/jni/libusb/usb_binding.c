#include <jni.h>
#include <android/log.h>

#include <stdlib.h>
#include <stdio.h>
#include <math.h>
#include <string.h>
#include <assert.h>
#include <pthread.h>
#include "libusb.h"
#include "libusbi.h"
#include "ringbuffer.h"

#if defined(__arm__)
  #if defined(__ARM_ARCH_7A__)
    #if defined(__ARM_NEON__)
      #if defined(__ARM_PCS_VFP)
        #define ABI "armeabi-v7a/NEON (hard-float)"
      #else
        #define ABI "armeabi-v7a/NEON"
      #endif
    #else
      #if defined(__ARM_PCS_VFP)
        #define ABI "armeabi-v7a (hard-float)"
      #else
        #define ABI "armeabi-v7a"
      #endif
    #endif
  #else
   #define ABI "armeabi"
  #endif
#elif defined(__i386__)
   #define ABI "x86"
#elif defined(__x86_64__)
   #define ABI "x86_64"
#elif defined(__mips64)  /* mips64el-* toolchain defines __mips__ too */
   #define ABI "mips64"
#elif defined(__mips__)
   #define ABI "mips"
#elif defined(__aarch64__)
   #define ABI "arm64-v8a"
#else
   #define ABI "unknown"
#endif

#define NUM_TRANSFERS_TO_SUBMIT		5
#define NUM_ISO_PACKETS 			40

int write_log = 0;
char log_buffer[256];

static int do_exit = 0;
static struct libusb_device_handle *devh = NULL;
static struct libusb_transfer* xfr[NUM_TRANSFERS_TO_SUBMIT];
static libusb_context *ctx = NULL;

//volatile int8_t	locked = 0;
FILE* 			recordFile = NULL;
uint8_t* 		transferBuffer[NUM_TRANSFERS_TO_SUBMIT];
ringbufferRec*	audioDataBuffer = NULL;
jshort*         audioTempBuffer = NULL;

#define BULK_TRANSFER_BUFFER_SIZE	51200

uint16_t* 		bulkTransferBuffer = NULL;

#define WRITE_HEADER_SIZE 	44
#define UNCOMPRESSED_PCM	1

#define RIFF_CHUNK_ID	0x46464952
#define WAV_CHUNK_ID	0x45564157
#define FMT_CHUNK_ID	0x20746d66
#define DATA_CHUNK_ID	0x61746164

uint32_t FileSize 		= 0;					// Placeholder
uint32_t Subchunk1Size 	= 16;
uint32_t DataSectionSize = 0;					// Placeholder
uint16_t AudioFormat 	= UNCOMPRESSED_PCM;		// 1 = PCM
uint16_t NumChannels = 1;
uint16_t BitsPerSample = 16;

int little_endian		= 0;
int inputChannels       = 1;
int inputBitResolution	= 16;

pthread_mutex_t         lock_mutex;

int isLittleEndian() {
	unsigned short word=0x0102;
	return (*(char*)&word==2) ? 1 : 0;
}

//! Byte swap unsigned short
//inline uint16_t swap_uint16( uint16_t val )
//{
//	return ((val << 8) | (val >> 8));
//}

//! Byte swap short
//inline int16_t swap_int16( int16_t val )
//{
//	return ((val << 8) | ((val >> 8) & 0xFF));
//}

#define BYTE_COUNT_24               4

int16_t int24_to_int16( uint8_t* bp )
{
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "%02X %02X %02X %02X", bp[0], bp[1], bp[2], bp[3]);
//    return (int16_t) ((int32_t) bp[1] | ((int32_t) bp[2] << 8));
    return (int16_t) ((int32_t) bp[1] | (int32_t) bp[2] << 8);
//	return (int16_t) ((int32_t) bp[1] | (int32_t) bp[0] << 8);
//    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "%d", data);
//    return data;
}

void writeWAVHeader(const char* cpath, int sampleRate, int advance)
{
	FILE* fhandle = fopen(cpath, "wb");
	if (fhandle != 0L) {

 		int16_t BlockAlign = (BitsPerSample * NumChannels) / 8;
		int32_t ByteRate = (sampleRate * BitsPerSample * NumChannels) / 8;

		int32_t	chunkID;

		chunkID = RIFF_CHUNK_ID;
		fwrite(&chunkID, sizeof(uint32_t), 1, fhandle);
		fwrite(&FileSize, sizeof(uint32_t), 1, fhandle);

		chunkID = WAV_CHUNK_ID;
		fwrite(&chunkID, sizeof(uint32_t), 1, fhandle);

		chunkID = FMT_CHUNK_ID;
		fwrite(&chunkID, sizeof(uint32_t), 1, fhandle);
		fwrite(&Subchunk1Size, sizeof(uint32_t), 1, fhandle);
		fwrite(&AudioFormat, sizeof(uint16_t), 1, fhandle);
		fwrite(&NumChannels, sizeof(uint16_t), 1, fhandle);
		fwrite(&sampleRate, sizeof(uint32_t), 1, fhandle);
		fwrite(&ByteRate, sizeof(uint32_t), 1, fhandle);
		fwrite(&BlockAlign, sizeof(uint16_t), 1, fhandle);
		fwrite(&BitsPerSample, sizeof(uint16_t), 1, fhandle);

		chunkID = DATA_CHUNK_ID;
		fwrite(&chunkID, sizeof(uint32_t), 1, fhandle);
		fwrite(&DataSectionSize, sizeof(uint32_t), 1, fhandle);

//		while (locked) usleep(USLEEP_TIME);
//		locked = 1;
        pthread_mutex_lock(&lock_mutex);
		if (advance > 0) {
            pthread_mutex_lock(&(audioDataBuffer->lock_mutex));
			// Write out the advance...
			if (advance <= audioDataBuffer->write_head)
				fwrite(audioDataBuffer->buffer + (audioDataBuffer->write_head - advance), sizeof(uint16_t), advance, fhandle);
			else {
				int remaining = advance - audioDataBuffer->write_head;
				if (audioDataBuffer->fill_size == audioDataBuffer->buffer_size)
					fwrite(audioDataBuffer->buffer + (audioDataBuffer->buffer_size - remaining), sizeof(uint16_t), remaining, fhandle);
				fwrite(audioDataBuffer->buffer, sizeof(uint16_t), audioDataBuffer->write_head, fhandle);
			}
            pthread_mutex_unlock(&(audioDataBuffer->lock_mutex));
		}
		recordFile = fhandle;
//		locked = 0;
        pthread_mutex_unlock(&lock_mutex);
	}
}

uint32_t finalizeWAVFile()
{
//	while (locked) usleep(USLEEP_TIME);
//	locked = 1;
    pthread_mutex_lock(&lock_mutex);
	FILE* fhandle = recordFile;
	recordFile = NULL;
//	locked = 0;
    pthread_mutex_unlock(&lock_mutex);

	fseek(fhandle, 0L, SEEK_END);
	int32_t fileSize = ftell(fhandle);

	fseek(fhandle, sizeof(uint32_t), SEEK_SET);
	fwrite(&fileSize, sizeof(uint32_t), 1, fhandle);

	fseek(fhandle, WRITE_HEADER_SIZE - 4, SEEK_SET);
	uint32_t byteSize = fileSize - WRITE_HEADER_SIZE;
	fwrite(&byteSize, sizeof(uint32_t), 1, fhandle);

	fclose(fhandle);

	return byteSize / sizeof(uint16_t);
}

void doExit(jboolean detached) {

    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "doExit BEGIN.");
	do_exit = 1;

	if (!detached && (bulkTransferBuffer == NULL)) {
		int r;
		for (int ii = 0; ii < NUM_TRANSFERS_TO_SUBMIT; ii++) {
			if (xfr[ii] != NULL) {
				r = libusb_cancel_transfer(xfr[ii]);
				if ((r != LIBUSB_SUCCESS) && (r != LIBUSB_ERROR_NOT_FOUND)) {
                    if (write_log != 0) sprintf(log_buffer, "Error canceling transfer %i: %s.", ii,
                    libusb_error_name(r));
					__android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
										"Error canceling transfer %i: %s.", ii,
										libusb_error_name(r));
				}
			}
		}
	}
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "doExit END.");
}

static void transferCallbackFunc(struct libusb_transfer *xtransfer)
{
	if (xtransfer->status == LIBUSB_TRANSFER_COMPLETED) {
        pthread_mutex_lock(&lock_mutex);
		uint32_t startWriteHead = audioDataBuffer->write_head;
		if (xtransfer->type == LIBUSB_TRANSFER_TYPE_ISOCHRONOUS) {
			for (unsigned int i = 0; i < xtransfer->num_iso_packets; ++i) {
				struct libusb_iso_packet_descriptor *pack = &xtransfer->iso_packet_desc[i];
				if (pack->status == LIBUSB_TRANSFER_COMPLETED) {
					// The data for each packet will be found at an offset into the buffer that can be calculated as if each prior packet completed in full.
					if (pack->actual_length > 0) {
						if (inputBitResolution == 16) {
							if (inputChannels == 2) {
								uint8_t *bp = (uint8_t *) libusb_get_iso_packet_buffer_simple(xtransfer, i);
								uint16_t *p = (uint16_t *) bp;
								uint16_t *p2 = p;
								uint16_t *last = p + (pack->actual_length >> 1);
								while (p < last) {
									*p2++ = *p;
									p += 2;
								}
								writeRingBuffer(audioDataBuffer,
												(uint16_t *) bp,
												(pack->actual_length >> 2));
							} else
								writeRingBytes(audioDataBuffer,
											   (uint8_t *) libusb_get_iso_packet_buffer_simple(xtransfer, i),
											   pack->actual_length);
						} else if (inputBitResolution == 24) {
						    uint8_t* bp =  (uint8_t *) libusb_get_iso_packet_buffer_simple(xtransfer, i);
                            __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "%02X %02X %02X %02X %02X %02X %02X %02X", bp[0], bp[1], bp[2], bp[3],bp[4], bp[5],bp[6], bp[7]);
							writeRingBytes(audioDataBuffer,
										   (uint8_t *) libusb_get_iso_packet_buffer_simple(xtransfer, i),
										   pack->actual_length);
						}
					} else {
						__android_log_print(ANDROID_LOG_WARN, LOG_TAG, "Error: pack %u has 0 length.", i);
					}
				}
				else {
                    if (write_log != 0) sprintf(log_buffer, "Error: pack %u length=%d status %s.", i, pack->actual_length,
                                                libusb_status_name(pack->status));
                    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Error: pack %u length=%d status %s.", i, pack->actual_length,
										libusb_status_name(pack->status));
				}
			}
		}
		else if (xtransfer->type == LIBUSB_TRANSFER_TYPE_BULK) {
			if (xtransfer->actual_length > 0) {
				//	The offset-binary conversion - just invert the most-significant-bit (twos-comp = 0x8000 ^ offset-binary).
				//	Format: 16-bit offset-binary,  LSB-first (little endian)
				//	Big-endian MSB LSB
				// Little-endian LSB MSB
				uint16_t *p = (uint16_t*) xtransfer->buffer;
				uint16_t *last = p + (xtransfer->actual_length >> 1);
				if (little_endian == 1) {
					while (p < last) {
						*p ^= 0x8000;
						p++;
					}
				}
				else {
					while (p < last) {
//						*p = swap_uint16(*p) ^ 0x8000;
						*p = ((*p << 8) | (*p >> 8)) ^ 0x8000;
						p++;
					}
				}
				writeRingBuffer(audioDataBuffer,
								(uint16_t*) xtransfer->buffer,
								(xtransfer->actual_length >> 1));
			}
		}

		if (recordFile != NULL) {
			if (startWriteHead < audioDataBuffer->write_head)
				fwrite(audioDataBuffer->buffer + startWriteHead, sizeof(uint16_t), audioDataBuffer->write_head - startWriteHead, recordFile);
			else if (startWriteHead > audioDataBuffer->write_head) {
				int remaining = audioDataBuffer->buffer_size - startWriteHead;
				fwrite(audioDataBuffer->buffer + startWriteHead, sizeof(uint16_t), remaining, recordFile);
				if (audioDataBuffer->write_head > 0) fwrite(audioDataBuffer->buffer, sizeof(uint16_t), audioDataBuffer->write_head, recordFile);
			}
		}
//		locked = 0;
        pthread_mutex_unlock(&lock_mutex);
	}
	else if (xtransfer->status == LIBUSB_TRANSFER_CANCELLED) {
		libusb_free_transfer(xtransfer);
		__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "Transfer %s.", libusb_status_name(xtransfer->status));
		return;
	}
	else {
        if (write_log != 0) sprintf(log_buffer, "Transfer status %s.", libusb_status_name(xtransfer->status));
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Transfer status %s.", libusb_status_name(xtransfer->status));
	}

	 if (do_exit != 1) {
		 if (libusb_submit_transfer(xtransfer) < 0) {
             if (write_log != 0) sprintf(log_buffer, "Error re-submitting URB.");
			 __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Error re-submitting URB.");
			 doExit(0);
		 }
	 }
 }

void internal_cleanup() {

	if (devh != NULL) {
		libusb_close(devh);
		devh = NULL;
	}
	libusb_exit(ctx);
}

JNIEXPORT void JNICALL Java_com_digitalbiology_usb_UsbConfigurationParser_EnableLogging(JNIEnv *env, jclass clazz, jboolean enable)
{
	if (enable != 0)
	    write_log = 1;
	else
	    write_log = 0;
	log_buffer[0] = 0;
}

JNIEXPORT jstring JNICALL Java_com_digitalbiology_usb_UsbConfigurationParser_GetLogMessage(JNIEnv *env, jclass clazz)
{
	jstring msg = (*env)->NewStringUTF(env, log_buffer);
	log_buffer[0] = 0;
	return msg;
}
/*
JNIEXPORT jstring JNICALL Java_com_digitalbiology_usb_UsbConfigurationParser_GetUSBManufacturerName(JNIEnv *env, jclass clazz, jint VID)
{
	jstring result;
	char str[256];

	libusb_context *context = NULL;
	str[0] = 0;
	int r = libusb_init(&context);
	if (r == 0) {
		struct libusb_device **devs;
		if (libusb_get_device_list(context, &devs) > 0) {
			ssize_t i = 0;
			struct libusb_device *dev;
			while ((dev = devs[i++]) != NULL) {
				struct libusb_device_descriptor desc = {0};
				r = libusb_get_device_descriptor(dev, &desc);
				if (r < 0) {
	                if (write_log != 0) sprintf(log_buffer, "GetUSBManufacturerName libusb_get_device_descriptor error: %s", libusb_error_name(r));
//					__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "libusb_get_device_descriptor error: %s.", libusb_error_name(r));
				}
				else if (desc.idVendor == VID) {
					__android_log_print(ANDROID_LOG_INFO, LOG_TAG, "%d", desc.idVendor);
					struct libusb_device_handle *handle = NULL;
					r = libusb_open(dev, &handle);
					if (r < 0) {
	                    if (write_log != 0) sprintf(log_buffer, "GetUSBManufacturerName libusb_open error: %s", libusb_error_name(r));
//						__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "libusb_open error: %s.", libusb_error_name(r));
						break;
					}
					libusb_get_string_descriptor_ascii(handle, desc.idVendor, (unsigned char*) str, sizeof(str));
					libusb_close(handle);
					break;
				}
			}
			libusb_free_device_list(devs, 1);
		}
        else {
            if (write_log != 0) sprintf(log_buffer, "GetUSBManufacturerName libusb_get_device_list returned %i", r);
        }
		libusb_exit(context);
	}
    else {
        if (write_log != 0) sprintf(log_buffer, "GetUSBManufacturerName libusb_init failed: %s", libusb_error_name(r));
    }
	result = (*env)->NewStringUTF(env, str);
	return result;
}

JNIEXPORT jstring JNICALL Java_com_digitalbiology_usb_UsbConfigurationParser_GetUSBProductName(JNIEnv *env, jclass clazz, jint VID, jint PID)
{
	jstring result;
	char str[256];

	libusb_context *context = NULL;
	str[0] = 0;
	int r = libusb_init(&context);
	if (r == 0) {
		struct libusb_device **devs;
		r = libusb_get_device_list(context, &devs);
		if (r > 0) {
			ssize_t i = 0;
			struct libusb_device *dev;
			while ((dev = devs[i++]) != NULL) {
				struct libusb_device_descriptor desc = {0};
				r = libusb_get_device_descriptor(dev, &desc);
				if (r < 0) {
	                if (write_log != 0) sprintf(log_buffer, "GetUSBProductName libusb_get_device_descriptor error: %s", libusb_error_name(r));
//					__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "libusb_get_device_descriptor error: %s.", libusb_error_name(r));
				}
				else if ((desc.idVendor == VID) && (desc.idProduct == PID)) {
					__android_log_print(ANDROID_LOG_INFO, LOG_TAG, "%d", desc.iProduct);
					struct libusb_device_handle *handle = NULL;
					r = libusb_open(dev, &handle);
					if (r < 0) {
	                    if (write_log != 0) sprintf(log_buffer, "GetUSBProductName libusb_open error: %s", libusb_error_name(r));
//						__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "libusb_open error: %s.", libusb_error_name(r));
						break;
					}
					libusb_get_string_descriptor_ascii(handle, desc.iProduct, (unsigned char*) str, sizeof(str));
					libusb_close(handle);
					break;
				}
			}
			libusb_free_device_list(devs, 1);
		}
		else {
            if (write_log != 0) sprintf(log_buffer, "GetUSBProductName libusb_get_device_list returned %d", r);
		}
		libusb_exit(context);
	}
	else {
	  if (write_log != 0) sprintf(log_buffer, "GetUSBProductName lilibusb_get_device_list returned %d", r);
	}
	result = (*env)->NewStringUTF(env, str);
	return result;
}
*/
JNIEXPORT jint JNICALL Java_com_digitalbiology_audio_UsbMicrophone_InitUSB(JNIEnv *env, jclass clazz, jint VID, jint PID)
{
    ctx = NULL;
    int r = libusb_init(NULL);
	if (r < 0) {
	    if (write_log != 0) sprintf(log_buffer, "Failed to initialize libusb: %s", libusb_error_name(r));
	    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Failed to initialize libusb.");
	    return 0;
	  }

	 devh = libusb_open_device_with_vid_pid(NULL, VID, PID);
	 if (devh == NULL) {
	        if (write_log != 0) sprintf(log_buffer, "Device handle is NULL");
		   __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Device handle is NULL.");
		   internal_cleanup();
		   return 0;
	 }

	little_endian = isLittleEndian();

	return 1;
}

JNIEXPORT jint JNICALL Java_com_digitalbiology_audio_UsbMicrophone_InitUSB2(JNIEnv *env, jclass clazz, jint fd, jstring path)
{
	__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "InitUSB2 BEGIN");
	ctx = malloc(sizeof(libusb_context));
 	memset(ctx, 0, sizeof(libusb_context));
 	int r = libusb_init(&ctx);
	if (r < 0) {
	    if (write_log != 0) sprintf(log_buffer, "Failed to initialize libusb: %s", libusb_error_name(r));
	    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Failed to initialize libusb.");
	    return 0;
	  }

	const char *dpath = (*env)->GetStringUTFChars(env, path, 0);
	libusb_device *device = libusb_get_device2(ctx, dpath);
	(*env)->ReleaseStringUTFChars(env, path, dpath);
	if (device == NULL) {
            if (write_log != 0) sprintf(log_buffer, "libusb_get_device2: Device is NULL.");
		   __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "libusb_get_device2: Device is NULL.");
	    return 0;
	}

	r = libusb_open2(device, &devh, fd);
	if (devh == NULL) {
	        if (write_log != 0) sprintf(log_buffer, "Device handle is NULL");
		   __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Device handle is NULL.");
		   internal_cleanup();
		   return 0;
	 }

	little_endian = isLittleEndian();

	__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "InitUSB2 1 END");
	return 1;
}

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_UsbMicrophone_CleanupUSB(JNIEnv *env, jclass clazz)
{
	__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "Cleanup BEGIN");
	internal_cleanup();
}

void deallocateTransferBuffers() {

	if (bulkTransferBuffer != NULL) {
		free(bulkTransferBuffer);
		bulkTransferBuffer = NULL;
	}
	else {
		for (int ii = 0; ii < NUM_TRANSFERS_TO_SUBMIT; ii++) {
			if (transferBuffer[ii] != NULL) {
				free(transferBuffer[ii]);
				transferBuffer[ii] = NULL;
			}
		}
	}
}

JNIEXPORT jint JNICALL Java_com_digitalbiology_audio_UsbMicrophone_AllocateTransferBuffers(JNIEnv *env, jclass clazz, jint packetSize, jboolean isochronous)
{
	__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "AllocateTransferBuffers BEGIN");
    deallocateTransferBuffers();
	if (isochronous) {
		int buffer_size = packetSize * NUM_ISO_PACKETS;
		for (int ii = 0; ii < NUM_TRANSFERS_TO_SUBMIT; ii++) {
			transferBuffer[ii] = (uint8_t*) malloc(buffer_size);
			if (transferBuffer[ii] == NULL) {
                if (write_log != 0) sprintf(log_buffer, "Unable to allocate transfer buffer %d.", ii);
				__android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
									"Unable to allocate transfer buffer %d.", ii);
	            deallocateTransferBuffers();
				return 0;
			}
		}
	}
	else {
		bulkTransferBuffer = (uint16_t*) malloc(BULK_TRANSFER_BUFFER_SIZE);
		if (bulkTransferBuffer == NULL) {
            if (write_log != 0) sprintf(log_buffer, "Unable to allocate transfer buffer.");
			__android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
								"Unable to allocate transfer bulk buffer");
	        deallocateTransferBuffers();
			return 0;
		}
	}
	__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "AllocateTransferBuffers 1 END");
	return 1;
}

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_UsbMicrophone_EnterUSBLoop(JNIEnv *env, jclass clazz,
                                                                                int interfaceID,
                                                                                int altSetting,
                                                                                int endpointAddress,
                                                                                int sampleRate,
                                                                                int packetSize,
                                                                                int bitResolution,
                                                                                int channels,
                                                                                jboolean isochronous
)
{
    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "EnterUSBLoop BEGIN");
    int r;

    do_exit = 0;

    assert(devh != NULL);

    r = libusb_detach_kernel_driver(devh, interfaceID);
    if (r < 0 && r != LIBUSB_ERROR_NOT_FOUND) {
        if (write_log != 0) sprintf(log_buffer, "Error detaching kernel driver: %s", libusb_error_name(r));
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Error detaching kernel driver: %s.", libusb_error_name(r));
        return;
    }

    r = libusb_claim_interface(devh, interfaceID);
    if(r < 0) {
        if (write_log != 0) sprintf(log_buffer, "Failed to claim interface: %s", libusb_error_name(r));
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Failed to claim interface = %s.", libusb_error_name(r));
        return;
    }

    r = libusb_set_interface_alt_setting(devh, interfaceID, altSetting);
    if(r < 0) {
        if (write_log != 0) sprintf(log_buffer, "Failed to set alternative setting: %s", libusb_error_name(r));
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Failed to set alternative setting = %s.", libusb_error_name(r));
        goto cleanup;
    }

    if (sampleRate > 0) {		// This should only be non-zero if there is more than one sampling rate.
        uint8_t freq_data[3];
        freq_data[0] = sampleRate & 0x000000FF;
        freq_data[1] = (sampleRate & 0x0000FF00) >> 8;
        freq_data[2] = (sampleRate & 0x00FF0000) >> 16;
        if (libusb_control_transfer(devh, 0x22, 0x01, 256, endpointAddress, freq_data, 3, 0) != 3) {
            if (write_log != 0) sprintf(log_buffer, "Setting sampling rate failed.");
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Setting sampling rate failed.");
            goto cleanup;
        }
    }
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Allocating transfer buffers.");

    // Transfers intended for non-isochronous endpoints (e.g. control, bulk, interrupt) should specify an iso_packets count of zero.
    int packetCount = (isochronous) ? NUM_ISO_PACKETS : 0;
    for (int ii = 0; ii < NUM_TRANSFERS_TO_SUBMIT; ii++) {
        xfr[ii] = libusb_alloc_transfer(packetCount);
        if (xfr[ii] == NULL) {
            if (write_log != 0) sprintf(log_buffer, "Unable to allocate transfer %d", ii);
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Unable to allocate transfer %d.", ii);
            goto cleanup;
        }
    }
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Filling transfers.");

    inputChannels = channels;
    inputBitResolution = bitResolution;
    for (int ii = 0; ii < NUM_TRANSFERS_TO_SUBMIT; ii++) {
        if (isochronous) {
            libusb_fill_iso_transfer(xfr[ii], devh, endpointAddress, transferBuffer[ii],
                                     sizeof(transferBuffer[ii]), NUM_ISO_PACKETS,
                                     transferCallbackFunc, NULL, 0);
            libusb_set_iso_packet_lengths(xfr[ii], packetSize);
        }
        else {
            libusb_fill_bulk_transfer(xfr[ii], devh, endpointAddress, transferBuffer[ii],
                                      sizeof(transferBuffer[ii]),
                                      transferCallbackFunc, NULL, 0);
        }
    }
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Submitting transfers.");

    for (int ii = 0; ii < NUM_TRANSFERS_TO_SUBMIT; ii++) {
        r = libusb_submit_transfer(xfr[ii]);
        if (r < 0) {
            if (write_log != 0) sprintf(log_buffer, "Submit transfer %i failed: %s", ii, libusb_error_name(r));
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "submit transfer %d failed = %s.", ii, libusb_error_name(r));
            goto cleanup;
        }
    }
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Entering loop.");

    while (!do_exit) {
        r = libusb_handle_events_completed(ctx, NULL);
        if ((r != LIBUSB_SUCCESS) && (r != LIBUSB_ERROR_NOT_FOUND)) {
            if (write_log != 0) sprintf(log_buffer, "Error handling event: %s.", libusb_error_name(r));
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Error handling event: %s.", libusb_error_name(r));
            doExit(0);
        }
    }
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Exiting loop.");
    if (recordFile != NULL) {
        finalizeWAVFile();
    }

    cleanup:
    libusb_release_interface(devh, interfaceID);
    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "EnterUSBLoop END");
}

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_UsbMicrophone_ExitUSBLoop(JNIEnv *env, jclass clazz, jboolean detached)
{
	__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "ExitUSBLoop BEGIN");
	doExit(detached);
	__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "ExitUSBLoop END");
}

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_UsbMicrophone_EnterBulkUSBLoop(JNIEnv *env, jclass clazz,
                                                                                    int interfaceID,
                                                                                    int altSetting,
                                                                                    int endpointAddress,
                                                                                    int packetSize)
{
    int r;

    do_exit = 0;

    assert(devh != NULL);

    r = libusb_detach_kernel_driver(devh, interfaceID);
    if (r < 0 && r != LIBUSB_ERROR_NOT_FOUND) {
        if (write_log != 0) sprintf(log_buffer, "Error detaching kernel driver: %s", libusb_error_name(r));
//		__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Error detaching kernel driver: %s.", libusb_error_name(r));
        return;
    }

    r = libusb_claim_interface(devh, interfaceID);
    if(r < 0) {
        if (write_log != 0) sprintf(log_buffer, "Failed to claim interface: %s", libusb_error_name(r));
//		__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Failed to claim interface = %s.", libusb_error_name(r));
        return;
    }

    r = libusb_set_interface_alt_setting(devh, interfaceID, altSetting);
    if(r < 0) {
        if (write_log != 0) sprintf(log_buffer, "Failed to set alternative setting: %s", libusb_error_name(r));
//		__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Failed to set alternative setting = %s.", libusb_error_name(r));
        goto cleanup;
    }

    int bytesTransfered;
    while (!do_exit) {
        r = libusb_bulk_transfer(devh, endpointAddress, (uint8_t*) bulkTransferBuffer, BULK_TRANSFER_BUFFER_SIZE, &bytesTransfered, 0);
        if ((r != LIBUSB_SUCCESS) && (r != LIBUSB_ERROR_NOT_FOUND)) {
            if (write_log != 0) sprintf(log_buffer, "Error bulk transfer: %s.", libusb_error_name(r));
//			__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Error bulk transfer: %s.", libusb_error_name(r));
            doExit(0);
        }
        else {
//			The offset-binary conversion - just invert the most-significant-bit (twos-comp = 0x8000 ^ offset-binary).
//			Format: 16-bit offset-binary,  LSB-first (little endian)
//			Big-endian MSB LSB
// 			Little-endian LSB MSB
            if (bytesTransfered > 0) {
                uint16_t *p = bulkTransferBuffer;
                uint16_t *last = p + bytesTransfered / sizeof(uint16_t);
                if (little_endian == 1) {
                    while (p < last) {
                        *p ^= 0x8000;
                        p++;
                    }
                }
                else {
                    while (p < last) {
//						*p = swap_uint16(*p) ^ 0x8000;
                        *p = ((*p << 8) | (*p >> 8)) ^ 0x8000;
                        p++;
                    }
                }
//				while (locked) usleep(USLEEP_TIME);
//				locked = 1;
                pthread_mutex_lock(&lock_mutex);

                uint32_t startWriteHead = audioDataBuffer->write_head;
//				writeRingBuffer(audioDataBuffer, (uint8_t*) bulkTransferBuffer, bytesTransfered);
                writeRingBuffer(audioDataBuffer, bulkTransferBuffer, bytesTransfered / sizeof(uint16_t));
                if (recordFile != NULL) {
                    if (startWriteHead < audioDataBuffer->write_head)
                        fwrite(audioDataBuffer->buffer + startWriteHead, sizeof(uint16_t), audioDataBuffer->write_head - startWriteHead, recordFile);
                    else if (startWriteHead > audioDataBuffer->write_head) {
                        int remaining = audioDataBuffer->buffer_size - startWriteHead;
                        fwrite(audioDataBuffer->buffer + startWriteHead, sizeof(uint16_t), remaining, recordFile);
                        if (audioDataBuffer->write_head > 0) fwrite(audioDataBuffer->buffer, sizeof(uint16_t), audioDataBuffer->write_head, recordFile);
                    }
                }
//				locked = 0;
                pthread_mutex_unlock(&lock_mutex);
            }
        }
    }
    if (recordFile != NULL) {
        finalizeWAVFile();
    }

    cleanup:
    libusb_release_interface(devh, interfaceID);
}

void deallocateAudioBuffer() {
	if (audioDataBuffer != NULL) {
		freeRingBuffer(audioDataBuffer);
		audioDataBuffer = NULL;
	}
	if (audioTempBuffer != NULL) {
	    free(audioTempBuffer);
        audioTempBuffer = NULL;
	}
    pthread_mutex_destroy(&lock_mutex);
}

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_Microphone_AllocateAudioBuffers(JNIEnv *env, jclass clazz, jint sampleBufferSize, jint packetSize, jint bitResolution, jint numChannels)
{
    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "AllocateAudioBuffer BEGIN");
	NumChannels = numChannels;
	BitsPerSample = bitResolution;

	int32_t dataSize;
	if (bitResolution == 24)
		dataSize = BYTE_COUNT_24 * sizeof(uint8_t);
	else
		dataSize = sizeof(uint16_t);
	dataSize *= numChannels;

	int32_t sampleSizeBytes = sampleBufferSize * dataSize;
	int numBytesNeeded = NUM_TRANSFERS_TO_SUBMIT * NUM_ISO_PACKETS * packetSize;
	if (numBytesNeeded < sampleSizeBytes) numBytesNeeded = sampleSizeBytes;

    if ((audioDataBuffer != NULL) && (audioDataBuffer->buffer_size == numBytesNeeded)) {
        resetRingBuffer(audioDataBuffer);
    }
    else {
	    deallocateAudioBuffer();
	    audioDataBuffer = initRingBuffer(numBytesNeeded);
        pthread_mutex_init(&lock_mutex, NULL);
	}

    if (bitResolution == 24 && audioTempBuffer == NULL) {
        audioTempBuffer = (jshort*) malloc(BYTE_COUNT_24 * sizeof(uint8_t) * numChannels * 8192);	// 8192 is the maximum FFT window
    }
    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "AllocateAudioBuffer %d bytes END", numBytesNeeded);
}

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_UsbRecordRunnable_ResetAudioBuffers(JNIEnv *env, jclass clazz)
{
	resetRingBuffer(audioDataBuffer);
}

JNIEXPORT jint JNICALL Java_com_digitalbiology_audio_UsbRecordRunnable_GetOverwriteCount(JNIEnv *env, jclass clazz)
{
	return (audioDataBuffer != NULL) ? audioDataBuffer->overwrites : 0;
}

JNIEXPORT jint JNICALL Java_com_digitalbiology_audio_MainActivity_ReadFrameFromAudioBuffer(JNIEnv *env, jclass clazz, jint bufIndex, jshortArray data, jint len, jint window) {

    uint8_t *buffer = audioDataBuffer->buffer;
    int32_t bufsize = audioDataBuffer->buffer_size;

    pthread_mutex_lock(&(audioDataBuffer->lock_mutex));

    int32_t rhd = audioDataBuffer->read_head[bufIndex];
    int32_t whd = audioDataBuffer->write_head;

    int32_t separation;
    if (whd >= rhd)
        separation = whd - rhd;
    else
        separation = whd + (bufsize - rhd);

    if (BitsPerSample == 16) {      // Assumes a single channel
        int32_t dataByteSize = sizeof(uint16_t);
        int32_t dataLen = len * dataByteSize;
        if (separation < dataLen)
            len = 0;
        else if (dataLen > 0) {
            int32_t incr = (len / window) * dataByteSize;
            if ((rhd + dataLen) < bufsize) {
                (*env)->SetShortArrayRegion(env, data, 0, len, (jshort *) (buffer + rhd));
                audioDataBuffer->read_head[bufIndex] += incr;
            } else {
                int32_t remaining = bufsize - rhd;
                if (remaining > 0)
                    (*env)->SetShortArrayRegion(env, data, 0, remaining / dataByteSize, (jshort *) (buffer + rhd));
                if ((dataLen - remaining) > 0)
                    (*env)->SetShortArrayRegion(env, data, remaining / dataByteSize, (dataLen - remaining) / dataByteSize,
                                                (jshort *) buffer);
                if ((rhd + incr) < bufsize)
                    audioDataBuffer->read_head[bufIndex] += incr;
                else
                    audioDataBuffer->read_head[bufIndex] = (rhd + incr) - bufsize;
            }
        }
    } else if (BitsPerSample == 24) {
        int32_t dataByteSize = BYTE_COUNT_24 * NumChannels;
        int32_t dataLen = len * dataByteSize;
        if (separation < dataLen)
            len = 0;
        else if (dataLen > 0) {
            int32_t incr = (len / window) * dataByteSize;
            if ((rhd + dataLen) < bufsize) {
                uint8_t* bp = buffer + rhd;
				uint8_t* bp_end = bp + dataLen;
                jshort* tp = audioTempBuffer;
                while (bp < bp_end) {
                    *tp = int24_to_int16(bp);
                    tp++;
                    bp += dataByteSize;
                }
                (*env)->SetShortArrayRegion(env, data, 0, len, audioTempBuffer);
                audioDataBuffer->read_head[bufIndex] += incr;
            } else {
                int32_t remaining = bufsize - rhd;
                if (remaining > 0) {
					uint8_t* bp = buffer + rhd;
					uint8_t* bp_end = bp + bufsize;
                    jshort* tp = audioTempBuffer;
                    while (bp < bp_end) {
                        *tp = int24_to_int16(bp);
                        tp++;
                        bp += dataByteSize;
                    }
                    (*env)->SetShortArrayRegion(env, data, 0, remaining / dataByteSize, audioTempBuffer);
                }
                if ((dataLen - remaining) > 0) {
					uint8_t* bp = buffer;
					uint8_t* bp_end = bp + bufsize;
                    jshort* tp = audioTempBuffer;
                    while (bp < bp_end) {
                        *tp = int24_to_int16(bp);
                        tp++;
                        bp += dataByteSize;
                    }
                    (*env)->SetShortArrayRegion(env, data, remaining / dataByteSize, (dataLen - remaining) / dataByteSize, audioTempBuffer);
                }
                if ((rhd + incr) < bufsize)
                    audioDataBuffer->read_head[bufIndex] += incr;
                else
                    audioDataBuffer->read_head[bufIndex] = (rhd + incr) - bufsize;
            }
        }
    }

    pthread_mutex_unlock(&(audioDataBuffer->lock_mutex));
	return len;
}

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_UsbRecordRunnable_WriteDataToAudioBuffer(JNIEnv *env, jclass clazz, jshortArray array, jint len)
{
	jshort *data = (*env)->GetShortArrayElements(env, array, 0);

    pthread_mutex_lock(&lock_mutex);

	writeRingBuffer(audioDataBuffer, (uint16_t*) data, len);
	if (recordFile != NULL) fwrite((uint16_t*) data, sizeof(uint16_t), len, recordFile);

    pthread_mutex_unlock(&lock_mutex);

	(*env)->ReleaseShortArrayElements(env, array, data, 0);
}

JNIEXPORT jint JNICALL Java_com_digitalbiology_audio_AudioReadRunnable_GetAudioBufferFillSize(JNIEnv *env, jclass clazz)
{
	if (audioDataBuffer != NULL) return audioDataBuffer->fill_size / sizeof(uint16_t);
	return 0;
}

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_AudioPlayRunnable_SyncListenHead(JNIEnv *env, jclass clazz)
{
	syncListenHead(audioDataBuffer);
}

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_MainActivity_StartRecord(JNIEnv *env, jclass clazz, jstring path, jint sampleRate, jint advance)
{
	const char *cpath = (*env)->GetStringUTFChars(env, path, 0);
	writeWAVHeader(cpath, sampleRate, advance);
	(*env)->ReleaseStringUTFChars(env, path, cpath);
}

JNIEXPORT jint JNICALL Java_com_digitalbiology_audio_MainActivity_StopRecord(JNIEnv *env, jclass clazz)
{
	if (recordFile != NULL) return finalizeWAVFile();
	return 0;
}

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_MainActivity_AppendMetadata(JNIEnv *env, jclass clazz, jstring path, jint ns, jbyteArray metadata)
{
  	jbyte *cmeta = (*env)->GetByteArrayElements(env, metadata, 0);
	if (cmeta != NULL) {
	    const char *cpath = (*env)->GetStringUTFChars(env, path, 0);
	    FILE* fhandle = fopen(cpath, "rb+");
		if (fhandle != NULL) {
 	        fseek(fhandle, 0L, SEEK_END);
            int32_t chunkID = ns;
            fwrite(&chunkID, sizeof(int32_t), 1, fhandle);

            int32_t chunkSize = (*env)->GetArrayLength(env, metadata);
            fwrite(&chunkSize, sizeof(int32_t), 1, fhandle);
            fwrite(cmeta, 1, chunkSize, fhandle);

            // Need to update overall file size
	        int32_t fileSize = ftell(fhandle);

	        fseek(fhandle, sizeof(int32_t), SEEK_SET);
	        fwrite(&fileSize, sizeof(int32_t), 1, fhandle);

	        fclose(fhandle);
	    }
	    (*env)->ReleaseStringUTFChars(env, path, cpath);
	}
	(*env)->ReleaseByteArrayElements(env, metadata, cmeta, 0);
}

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_MainActivity_createTestToneFile(JNIEnv *env, jclass clazz, jstring path, jint sampleRate, jint frequency)
{
	const char *cpath = (*env)->GetStringUTFChars(env, path, 0);
	writeWAVHeader(cpath, sampleRate, 0);
	(*env)->ReleaseStringUTFChars(env, path, cpath);
	if (recordFile != NULL) {
		double amplitude = (double) SHRT_MAX * 0.2;
		short datum;
		double incr = 0.;
		double samplesPerCycle = (double) sampleRate / (double) frequency;
		double delta = 2.0 * M_PI / samplesPerCycle;
		for (int ii = 0; ii < 2 * sampleRate; ii++) {
			datum = (short) (amplitude * sin(incr));
			fwrite(&datum, 1, sizeof(short), recordFile);
			incr += delta;
		}
		finalizeWAVFile();
	}
}

