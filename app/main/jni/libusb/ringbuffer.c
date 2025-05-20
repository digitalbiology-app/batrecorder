#include <android/log.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include "ringbuffer.h"

#define LOG_TAG	"RingBuffer"

ringbufferRec* initRingBuffer(size_t size)
{
	ringbufferRec* rb = (ringbufferRec*) malloc(sizeof(ringbufferRec));
	rb->buffer_size = size;
	rb->fill_size = 0;
	rb->buffer = (uint8_t*) malloc(rb->buffer_size);
	rb->write_head = 0;
	rb->read_head[FILE_READ_HEAD] = 0;
	rb->read_head[LISTEN_READ_HEAD] = 0;
	rb->overwrites = 0;
    pthread_mutex_init(&(rb->lock_mutex), NULL);
	return rb;
}

void freeRingBuffer(ringbufferRec* rb)
{
	if (rb->buffer != NULL) free(rb->buffer);
    pthread_mutex_destroy(&(rb->lock_mutex));
	free(rb);
}

void syncListenHead(ringbufferRec* rb)
{
    pthread_mutex_lock(&(rb->lock_mutex));
	rb->read_head[LISTEN_READ_HEAD] = rb->read_head[FILE_READ_HEAD];
    pthread_mutex_unlock(&(rb->lock_mutex));
}

void resetRingBuffer(ringbufferRec* rb)
{
	rb->write_head = 0;
	rb->read_head[FILE_READ_HEAD] = 0;
	rb->read_head[LISTEN_READ_HEAD] = 0;
	rb->overwrites = 0;
	rb->fill_size = 0;
}

void writeRingBuffer(ringbufferRec* rb, const uint16_t* data, int32_t len) {
	writeRingBytes(rb, (uint8_t*) data, len * sizeof(uint16_t));
}

void writeRingBytes(ringbufferRec* rb, const uint8_t* data, int32_t len) {

	pthread_mutex_lock(&(rb->lock_mutex));
	if ((rb->write_head + len) < rb->buffer_size) {

		if ((rb->read_head[FILE_READ_HEAD] > rb->write_head) && (rb->read_head[FILE_READ_HEAD] < (rb->write_head + len))) {
			rb->overwrites += (rb->write_head + len) - rb->read_head[FILE_READ_HEAD];
			__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "overwrite=%d", (rb->write_head + len) - rb->read_head[FILE_READ_HEAD]);
		}

		memcpy(rb->buffer + rb->write_head, data, len);
		rb->write_head += len;
		if (rb->fill_size < rb->buffer_size) rb->fill_size = rb->write_head;
	}
	else {
		if ((rb->read_head[FILE_READ_HEAD] > rb->write_head) || (rb->read_head[0] < (len - (rb->buffer_size - rb->write_head)))) {
			rb->overwrites += (len - (rb->buffer_size - rb->write_head)) - rb->read_head[FILE_READ_HEAD];
			__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "overwrite=%d", (len - (rb->buffer_size - rb->write_head)) - rb->read_head[FILE_READ_HEAD]);
		}

		int32_t remaining = rb->buffer_size - rb->write_head;
		memcpy(rb->buffer + rb->write_head, data, remaining);
		rb->write_head = len - remaining;
		memcpy(rb->buffer, data + remaining, rb->write_head);
		rb->fill_size = rb->buffer_size;
	}
    pthread_mutex_unlock(&(rb->lock_mutex));
}

