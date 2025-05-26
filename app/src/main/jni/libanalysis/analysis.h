#ifndef BATRECORDER_ANALYSIS_BINDING_H_H
#define BATRECORDER_ANALYSIS_BINDING_H_H

#define USLEEP_TIME				1

extern volatile uint8_t	    waveDataLock;
extern uint16_t*			waveDataBuffer;
extern uint32_t			    waveDataLength;

extern char			        cachePath[200];

extern float			    signalGain;

uint32_t subtract_background(const char* in_path, const char* out_path, int height);

#endif //BATRECORDER_ANALYSIS_BINDING_H_H
