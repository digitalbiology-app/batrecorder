#include <jni.h>
#include <pthread.h>

typedef struct {
uint8_t*	        buffer;
volatile int32_t	write_head;
volatile int32_t	read_head[2];
int32_t				buffer_size;
int32_t			    fill_size;
int32_t				overwrites;
pthread_mutex_t     lock_mutex;
} ringbufferRec;

ringbufferRec* initRingBuffer(size_t size);
void freeRingBuffer(ringbufferRec* rb);
void resetRingBuffer(ringbufferRec* rb);
void syncListenHead(ringbufferRec* rb);
void writeRingBuffer(ringbufferRec* rb, const uint16_t* data, int32_t len);
void writeRingBytes(ringbufferRec* rb, const uint8_t* data, int32_t len);

//#define USLEEP_TIME		1

#define FILE_READ_HEAD      0
#define LISTEN_READ_HEAD    1

