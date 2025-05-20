#include <jni.h>
#include <android/log.h>
#include <android/bitmap.h>

#include <stdlib.h>
#include <stdio.h>
#include <math.h>
#include <unistd.h>
#include <errno.h>
#include <string.h>
#include <assert.h>

#include "analysis.h"
#include "pffft.h"

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

#ifdef __ANDROID__
#define STRINGIFY(x) #x
#define LOG_TAG    "fft_bind"
#define LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)
#endif

float           INVERSE_SHRT_MAX = 1.0f / (float) SHRT_MAX;

float* 			hannCoef;
jfloat			signalGain;
PFFFT_Setup* 	forwardFFTConfig;
PFFFT_Setup* 	invForwardFFTConfig;
PFFFT_Setup* 	invInverseFFTConfig;
float* 			input;
float* 			output;
float* 			work;

float* 			inv_input;
float* 			inv_output;
float* 			inv_work;
jshort* 		inverseBuffer = NULL;
int 			forwardFFTSize = 0;

volatile uint8_t	waveDataLock = 0;
uint16_t*			waveDataBuffer = 0L;
uint8_t*			waveStateBuffer = 0L;
uint32_t			waveDataLength = 0L;

volatile uint8_t	cacheLock = 0;
uint8_t*			cacheBuffer = 0L;
uint64_t			cacheLength = 0;

#define FILE_CACHE	1

#ifdef FILE_CACHE
FILE*			cacheSpectrumReadFile = 0L;
FILE*			cacheSpectrumWriteFile = 0L;
FILE*			cachePowerReadFile = 0L;
uint8_t*        cacheSpectrumWriteBuffer = 0L;
uint32_t		cacheWriteHead = 0L;
char			cachePath[200];
uint8_t         refreshCache;
#endif

#define TUNING_BIN_COUNT		20

uint16_t 			tuningBins[TUNING_BIN_COUNT];
uint8_t 			tuningIndex;
float 				tuningMax;
//float*				tuningArray;

#define USE_COMPLEX				1

#define MIN_DB					-48.0f
#define MAX_DB					0.0f
#define RANGE_DB				48.0f
#define MIN_DB_DELTA			10.0f
#define DECIBEL_FACTOR			20.0f

#define MAX_BUFFER_PARAM 		0
#define ACTUAL_BUFFER_PARAM 	1
#define WINDOW_FACTOR_PARAM 	2
#define ROW_PARAM 				3
#define STATE_PARAM 			4
#define MIN_FREQ_TRIGGER_PARAM  5
#define MAX_FREQ_TRIGGER_PARAM  6
#define MIN_DB_TRIGGER_PARAM    7
#define MIN_TUNE_BIN_PARAM 		8
#define MAX_TUNE_BIN_PARAM 		9
#define TUNE_BIN_PARAM 			10
#define IGNORE_PARAM 			11

#define WRITE_HEADER_SIZE 		44

#define FREQUENCY_DIVISION		0
#define HETERODYNE				1
#define FREQUENCY_CUTOFF		2
#define TIME_EXPANSION			3

void cleanupFFT()
{
	pffft_aligned_free(input);
	pffft_aligned_free(output);
	pffft_aligned_free(work);
	pffft_destroy_setup(forwardFFTConfig);

	free(hannCoef);
//	free(tuningArray);
}

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_MainActivity_initInverseFFT(JNIEnv *env, jclass clazz, jint len)
{
	inverseBuffer = (jshort*) malloc(sizeof(jshort) * len);

	int Nfloat;
#ifdef USE_COMPLEX
	invForwardFFTConfig = pffft_new_setup(len, PFFFT_COMPLEX);
	invInverseFFTConfig = pffft_new_setup(len, PFFFT_COMPLEX);
	Nfloat = len * 2;
#else
	invForwardFFTConfig = pffft_new_setup(len, PFFFT_REAL);
	invInverseFFTConfig = pffft_new_setup(len, PFFFT_REAL);
	Nfloat = len;
#endif

	int Nbytes = Nfloat * sizeof(float);
	inv_input = pffft_aligned_malloc(Nbytes);
	inv_output = pffft_aligned_malloc(Nbytes);
	inv_work = pffft_aligned_malloc(Nbytes);
}

JNIEXPORT int JNICALL Java_com_digitalbiology_audio_MainActivity_cleanupInverseFFT(JNIEnv *env, jclass clazz)
{
    if (inv_input != NULL)pffft_aligned_free(inv_input);
    if (inv_output != NULL) pffft_aligned_free(inv_output);
    if (inv_work != NULL) pffft_aligned_free(inv_work);
    if (invInverseFFTConfig != NULL) pffft_destroy_setup(invInverseFFTConfig);
    if (invForwardFFTConfig != NULL) pffft_destroy_setup(invForwardFFTConfig);
    if (inverseBuffer != NULL) free(inverseBuffer);

    return 1;
}

JNIEXPORT int JNICALL Java_com_digitalbiology_audio_MainActivity_initFFT(JNIEnv *env, jclass clazz, jint len, jfloat gain)
{
    if (forwardFFTSize > 0) {
		if (len == forwardFFTSize) return 0;
		cleanupFFT();
	}
	forwardFFTSize = len;
	signalGain = gain;

//	tuningArray = (float*) malloc(len * sizeof(float));

	// Pre-calculate Hann windowing coefficients...
	hannCoef = (float*) malloc(len * sizeof(float));
	for (int ii = 0; ii < len; ++ii) {
		hannCoef[ii] = 0.5f * (1.0f - cos(2.0f * M_PI * ((float)(ii)/((float) len - 1.0f)))) * INVERSE_SHRT_MAX;
	}

	int Nfloat;
#ifdef USE_COMPLEX
	forwardFFTConfig = pffft_new_setup(len, PFFFT_COMPLEX);
	Nfloat = len*2;
#else
	forwardFFTConfig = pffft_new_setup(len, PFFFT_REAL);
	Nfloat = len;
#endif

	int Nbytes = Nfloat * sizeof(float);
	input 	= pffft_aligned_malloc(Nbytes);
	output 	= pffft_aligned_malloc(Nbytes);
	work 	= pffft_aligned_malloc(Nbytes);

	return 1;
}

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_MainActivity_cleanupFFT(JNIEnv *env, jclass clazz)
{
	cleanupFFT();
}

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_MainActivity_setGain(JNIEnv *env, jclass clazz, jfloat value)
{
	signalGain = value;
}

JNIEXPORT jfloat JNICALL Java_com_digitalbiology_audio_MainActivity_getGain(JNIEnv *env, jclass clazz)
{
	return (jfloat) signalGain;
}

JNIEXPORT jshort JNICALL Java_com_digitalbiology_audio_MainActivity_analyzeSpectrum(JNIEnv *env, jclass clazz, jshortArray array, jlongArray parray)
{
	int triggered = 0;

	jshort *data = (*env)->GetShortArrayElements(env, array, 0);
	if (data == NULL) {
		LOGE("analyzeSpectrum: Data pointer is null!");
		return 0;
	}
	jlong *params = (*env)->GetLongArrayElements(env, parray, 0);

	long len = params[MAX_BUFFER_PARAM];
	long vlen = params[ACTUAL_BUFFER_PARAM];
	int half_window_size = len / (2 * params[WINDOW_FACTOR_PARAM]);
	int window_center = len >> 1;
	jshort *dp_lower = data + (window_center - half_window_size);
	jshort *dp_upper = data + (window_center + half_window_size);

#ifdef USE_COMPLEX
	vlen 	<<= 1;
	len 	<<= 1;
#endif

	float* hp = hannCoef;
	int minval = INT_MAX;
	int maxval = INT_MIN;
	int val;

	jshort *dp = data;
	jshort *dlast = data + params[ACTUAL_BUFFER_PARAM];

	// Calculate the DC bias in the signal.
	int dc_bias = 0;
	while (dp < dlast) dc_bias += (int) *dp++;
	dc_bias /= params[ACTUAL_BUFFER_PARAM];

	float *xp = input;
	float *last = input + vlen;
	dp = data;
	while (xp < last) {
		val = signalGain * (int) (*dp - dc_bias);
		dp++;
		if ((dp > dp_lower) && (dp <= dp_upper)) {
			if (val < minval)
				minval = val;
			else if (val > maxval)
				maxval = val;
		}
//		*xp++ = (float) val * (*hp++) * INVERSE_SHRT_MAX;
		*xp++ = (float) val * (*hp++);
		*xp++ = 0.f;
	}
	minval = abs(minval);
	maxval = abs(maxval);
	maxval = (minval < maxval) ? maxval : minval;

	uint32_t row = params[ROW_PARAM];
	waveDataBuffer[row] = (maxval > (int) SHRT_MAX) ? SHRT_MAX : maxval;
//	if (maxval >= (int) SHRT_MAX)
//		waveDataBuffer[row] = waveDataHeight;
//	else
//		waveDataBuffer[row] = (waveDataHeight * (int) maxval) / (int) SHRT_MAX;
	waveStateBuffer[row] = params[STATE_PARAM];

	if (vlen < len) memset(last, 0, sizeof(float) * (len - vlen));

	(*env)->ReleaseShortArrayElements(env, array, data, 0);

	pffft_transform_ordered(forwardFFTConfig, input, output, work, PFFFT_FORWARD);

	//	Note: frequency-domain data is stored from dc up to 2pi.
	//	    so cx_out[0] is the dc bin of the FFT - dc stands for direct current, representing 0 Hz
	//		which cannot exist in real life. A sound sample should not have any DC component
	//		but probably will due to inaccuracies in the recording equipment.
	//	    and cx_out[nfft/2] is the Nyquist bin (if exists)

	// Zero out DC bin
//	*rp++ = SHRT_MIN;
//	op++;

	// The power spectral density of your audio file, which is what most people want from the FFT,
	// you'll graph 20*log10( sqrt( re^2 + im^2 ) ), using the first N/2 complex numbers of the FFT output,
	// where N is the number of input samples to the FFT.

	if (params[TUNE_BIN_PARAM] == -1) {
		tuningIndex = 0;
		tuningMax = 0.0f;
		memset(tuningBins, 0, sizeof(uint16_t) * TUNING_BIN_COUNT);
//		memset(tuningArray, 0, sizeof(float) * params[MAX_BUFFER_PARAM]);
		params[TUNE_BIN_PARAM] = params[MIN_TUNE_BIN_PARAM];
	}

	float *op = output;
	float* olast = op + len / 2;
	float ry;
#ifdef USE_COMPLEX
	float iy;
#endif
	float max_db = MIN_DB;
	uint16_t tune_bin = params[MIN_TUNE_BIN_PARAM];

	float decibels;
	uint16_t bin = 0;

	while (cacheLock) usleep(USLEEP_TIME);
	cacheLock = 1;

	uint8_t* bp = cacheBuffer;
#ifndef FILE_CACHE
    bp += row * (params[MAX_BUFFER_PARAM] >> 1);
#endif

    while (op < olast) {
		ry = *op++;
#ifdef USE_COMPLEX
		iy = *op++;
		decibels = 10.0f * log10(ry * ry + iy * iy) - DECIBEL_FACTOR;
#else
		decibels = 10.0f * log10(ry * ry) - DECIBEL_FACTOR;
#endif
		if ((triggered == 0) && (decibels >= params[MIN_DB_TRIGGER_PARAM])
			&& ((bin >= params[MIN_FREQ_TRIGGER_PARAM]) && (bin < params[MAX_FREQ_TRIGGER_PARAM]))
				) {
//			LOGI("bin = %d decibels = %f  db cut=%d freq cut=%d", bin, decibels, params[DB_CUTOFF_PARAM], params[FREQ_CUTOFF_PARAM]);
			triggered = 1;
		}

		if (((bin > params[MIN_TUNE_BIN_PARAM]) && (bin < params[MAX_TUNE_BIN_PARAM])) && (decibels > max_db)) {
			max_db = decibels;
			tune_bin = bin;
		}
		if (decibels <= MIN_DB)
			*bp = 0;
		else if (decibels >= MAX_DB)
			*bp = 0xff;
		else
			*bp = (uint8_t)(((decibels - MIN_DB) / RANGE_DB) * 255.0f);
		bp++;
		bin++;
	}

#ifdef FILE_CACHE
    if ((triggered == 1) || (params[IGNORE_PARAM] == 0)) {
//        uint32_t bytesToWrite = bp - cacheBuffer;
        assert(bin <= cacheLength);
        if (cacheSpectrumWriteFile != 0L) fwrite(cacheBuffer, 1, bin, cacheSpectrumWriteFile);
        cacheWriteHead += bin;
//        fflush(cacheSpectrumWriteFile);       // this can cause hiccups
     }
#endif
	cacheLock = 0;

	if (tune_bin > params[MIN_TUNE_BIN_PARAM]) {
		tuningMax = MIN_DB + 0.9f * (tuningMax - MIN_DB);
//		LOGI("bin=%d tuningMax=%f", tune_bin, tuningMax);
		if (((max_db - MIN_DB) > 20.0f) && (fabsf(max_db - tuningMax) > 10.0f)) {
			tuningMax = max_db;
			tuningBins[tuningIndex] = tune_bin;
			tuningIndex = (tuningIndex + 1) % TUNING_BIN_COUNT;
			if (tuningBins[tuningIndex] > 0) {
				for (int ii = 0; ii < TUNING_BIN_COUNT; ii++)
					params[TUNE_BIN_PARAM] += tuningBins[ii];
				params[TUNE_BIN_PARAM] /= TUNING_BIN_COUNT;
			}
		}
	}

	(*env)->ReleaseLongArrayElements(env, parray, params, 0);

	return (jshort) triggered;
}

void filterFrequency(jshort mode, jfloat factor, jfloat samplesPerCycle, jfloat modAdj, jint len, jint numRead, jbyte* bytes)
{
	jshort *dp = inverseBuffer;
 	jshort val;
	jbyte* bp = bytes;

	if ((mode == TIME_EXPANSION) || (factor == 1.0f)) {
		jshort *last = inverseBuffer + len;
		dp = inverseBuffer;
		while (dp < last) {
			val = signalGain * (*dp++);
			*bp++ = val & 0x00ff;
			*bp++ = (val & 0xff00) >> 8;
		}
	}
	else {
		int fft_bins = len;
#ifdef USE_COMPLEX
		len *= 2;
#endif
		float *xp = inv_input;
		float *xlast = inv_input + len;

		if (mode == HETERODYNE) {
			// Mix with the tuning frequency.
			float mod_time = 0.0f;
			float delta = modAdj * 2.0f * M_PI / samplesPerCycle;
			while (xp < xlast) {
				*xp++ = (float) *dp++ / (float) SHRT_MAX * sin(mod_time);
#ifdef USE_COMPLEX
				*xp++ = 0.f;
#endif
				mod_time += delta;
			}
		}
		else {		// FREQUENCY_DIVISION or FREQUENCY_CUTOFF
			while (xp < xlast) {
				*xp++ = (float) *dp++ / (float) SHRT_MAX;
#ifdef USE_COMPLEX
				*xp++ = 0.f;
#endif
			}
		}
//		for (int ii = 0; ii < len; ii++) {
//			LOGI("IN %d [%f]", ii, inv_input[ii]);
//		}
		// Do frequency division by shifting frequencies downwards...
		pffft_transform_ordered(invForwardFFTConfig, inv_input, inv_output, inv_work, PFFFT_FORWARD);

//		for (int ii = 0; ii < len; ii+= 2) {
//			LOGI("IN %d [%f %f][%f %f]", ii, inv_input[ii], inv_input[ii+1], inv_output[ii], inv_output[ii + 1]);
//		}

		int max_fft_idx = len / 2;		// mirror image
		int max_kk = (int) ((float) max_fft_idx / factor);
		int kk = 0;
		if (mode == FREQUENCY_DIVISION) {
			// Need to divide frequencies by shifting frequency bins down.
			int ii = 0;
			int segment_len = (int) factor;
			while (kk < max_kk) {
				inv_output[kk] = inv_output[ii++];	    // real
#ifdef USE_COMPLEX
				inv_output[kk+1] = inv_output[ii++];	// img
#endif
				for (int jj = 1; jj < segment_len; jj++) {
					inv_output[kk] += inv_output[ii++];
#ifdef USE_COMPLEX
					inv_output[kk+1] += inv_output[ii++];
#endif
				}
#ifdef USE_COMPLEX
				inv_output[len-kk-2] 	= inv_output[kk];
				inv_output[len-kk-1] 	= inv_output[kk+1];
				kk += 2;
#else
				inv_output[len-kk-1] 	= fabsf(inv_output[kk];
				kk++;
#endif
			}
		}
		// Now all modes (except TIME_EXPANSION) need to go through a low pass filter.
		memset(inv_output+max_kk, 0, 2 * sizeof(PFFFT_REAL) * (max_fft_idx - max_kk));

		pffft_transform_ordered(invInverseFFTConfig, inv_output, inv_input, inv_work, PFFFT_BACKWARD);

		float *ip = inv_input;
#ifdef USE_COMPLEX
		float *ilast = inv_input + 2 * numRead;
#else
    	float *ilast = inv_input + numRead;
#endif
#ifdef USE_COMPLEX
		int incr = (int) (2.0f * factor);
#else
		int incr = (int) factor;
#endif
		float scaler = 1.0f / (float) fft_bins;
		while (ip < ilast) {
			val = SHRT_MAX * (signalGain * (*ip) * scaler);
			*bp++ = val & 0x00ff;
			*bp++ = (val & 0xff00) >> 8;
			ip += incr;
		}
	}
}

short* stereo_buffer = 0L;
uint32_t stereo_buffer_len = 0;

JNIEXPORT jint JNICALL Java_com_digitalbiology_audio_AudioPlayRunnable_filterFrequencyFile(JNIEnv *env, jclass clazz,
    jshort mode, jfloat factor, jfloat modulator, jfloat modAdj, jint fftLen, jint offset, jshort channels, jshort which_channel, jstring path, jbyteArray array) {

	int numRead = 0;

	const char *cpath = (*env)->GetStringUTFChars(env, path, 0);
	FILE *file;
	file = fopen(cpath, "rb");
	if (file != NULL) {
	    if (channels == 1) {
            if (fseek(file, offset * sizeof(jshort) + WRITE_HEADER_SIZE, SEEK_SET) == 0) {
                numRead = fread(inverseBuffer, sizeof(jshort), fftLen, file);
            }
        }
        else {
            if (stereo_buffer == 0L) {
               stereo_buffer_len = fftLen * channels;
               stereo_buffer = (short*) malloc(stereo_buffer_len * sizeof(short));
           }
           else if (stereo_buffer_len != fftLen * channels) {
               free(stereo_buffer);
               stereo_buffer_len = fftLen * channels;
               stereo_buffer = (short*) malloc(stereo_buffer_len * sizeof(short));
           }
           if (fseek(file, offset * channels * sizeof(jshort) + WRITE_HEADER_SIZE, SEEK_SET) == 0) {
                 numRead = fread(stereo_buffer, sizeof(jshort), stereo_buffer_len, file);
                 short* dp = inverseBuffer;
                 short* bp = stereo_buffer + (which_channel-1);
                 short* last = stereo_buffer + stereo_buffer_len;
                 while (bp < last) {
                     *dp = *bp;
                     dp++;
                     bp += channels;
                 }
                 numRead = numRead / channels;
           }
         }
		fclose(file);
	}
	(*env)->ReleaseStringUTFChars(env, path, cpath);

	if (numRead == 0) return 0;

	jbyte *bytes = (*env)->GetByteArrayElements(env, array, 0);
	if (numRead < fftLen) {
//	    int _fftLen = fftLen /= 2;
//	    while (numRead < _fftLen) _fftLen /= 2;
//	    if (_fftLen >= 128) filterFrequency(mode, factor, modulator, _fftLen, _fftLen, bytes);
//		memset(inverseBuffer + _fftLen, 0, sizeof(jshort) * (numRead - _fftLen));
		memset(inverseBuffer + numRead, 0, sizeof(jshort) * (fftLen - numRead));
	}
//    else
	    filterFrequency(mode, factor, modulator, modAdj, fftLen, numRead, bytes);
    (*env)->ReleaseByteArrayElements(env, array, bytes, 0);

    return (jint) (numRead * sizeof(jshort));
}

JNIEXPORT jint JNICALL Java_com_digitalbiology_audio_AudioPlayRunnable_filterFrequencyBuffer(JNIEnv *env, jclass clazz, jshort mode, jfloat factor, jfloat modulator, jfloat modAdj, jint fftLen, jshortArray buffer, jbyteArray array)
{
	int numRead = fftLen;

	jshort *data = (*env)->GetShortArrayElements(env, buffer, 0);
	memcpy(inverseBuffer, data, fftLen * sizeof(jshort));
    (*env)->ReleaseShortArrayElements(env, buffer, data, 0);

	jbyte *bytes = (*env)->GetByteArrayElements(env, array, 0);
	filterFrequency(mode, factor, modulator, modAdj, fftLen, numRead, bytes);
//	memcpy(bytes, (uint8_t*) inverseBuffer, fftLen * sizeof(jshort));
    (*env)->ReleaseByteArrayElements(env, array, bytes, 0);

    return (jint) (numRead * sizeof(jshort));
}

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_views_SpectrogramView_openReadCache(JNIEnv *env, jclass clazz, jint offset)
{
#ifdef FILE_CACHE

//	while (cacheLock) usleep(USLEEP_TIME);
//	cacheLock = 1;

	cacheSpectrumReadFile = fopen(cachePath, "rb");
	if (cacheSpectrumReadFile != 0L) {
//		LOGI("openReadCache: cacheSpectrumReadFile fseek = %d", offset);
		if (fseek(cacheSpectrumReadFile, offset, SEEK_SET) != 0) {
	        int errnum = errno;
            LOGE("fseek to %u on cacheSpectrumReadFile failed errno=%d %s", offset, errno, strerror(errnum));
		}
	}
	else {
        int errnum = errno;
		LOGE("Unable to open cacheSpectrumReadFile %s errno=%d %s", cachePath, errno, strerror(errnum));
	}

//	cacheLock = 0;
#endif
}

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_MainActivity_openWriteCache(JNIEnv *env, jclass clazz, jstring path, jint len, jboolean refresh) {
#ifdef FILE_CACHE
	const char *cpath = (*env)->GetStringUTFChars(env, path, 0);

	while (cacheLock) usleep(USLEEP_TIME);
	cacheLock = 1;

	if (cacheBuffer == 0L) {
		cacheLength = len;
		cacheBuffer = (uint8_t*) malloc(cacheLength);
	}
	else if (cacheLength != len) {
		free(cacheBuffer);
		cacheLength = len;
		cacheBuffer = (uint8_t*) malloc(cacheLength);
	}

    if (refresh != 0) {
    	// Open a new empty file
        refreshCache = 1;
        cacheSpectrumWriteFile = fopen(cpath, "wb");
        if (cacheSpectrumWriteFile == 0L) {
            int errnum = errno;
            LOGE("Unable to open cacheSpectrumWriteFile %s errno=%d %s", cpath, errno, strerror(errnum));
        }
	}
	else {
        refreshCache = 0;
	    cacheSpectrumWriteFile = fopen(cpath, "wb+");
//		cacheSpectrumWriteFile = fopen(cpath, "ab");
        if (cacheSpectrumWriteFile != 0L) {
		    if (fseek(cacheSpectrumWriteFile, 0, SEEK_SET) != 0) {
	            int errnum = errno;
                LOGE("fseek to 0 on cacheSpectrumWriteFile %s failed errno=%d %s", cpath, errno, strerror(errnum));
		    }
        }
        else {
            int errnum = errno;
            LOGE("Unable to append to cacheSpectrumWriteFile %s errno=%d %s", cpath, errno, strerror(errnum));
        }
	}

    if (cacheSpectrumWriteFile != 0L) {
        cacheSpectrumWriteBuffer = malloc(cacheLength);
        setvbuf(cacheSpectrumWriteFile, cacheSpectrumWriteBuffer, _IOFBF, cacheLength);
		cacheWriteHead = 0L;
		strcpy(cachePath, cpath);
    }

	cacheLock = 0;

	(*env)->ReleaseStringUTFChars(env, path, cpath);
#endif
}

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_MainActivity_closeWriteCache(JNIEnv *env, jclass clazz)
{
#ifdef FILE_CACHE
	while (cacheLock) usleep(USLEEP_TIME);
	cacheLock = 1;
	if (cacheSpectrumWriteFile != 0L) {
	    fclose(cacheSpectrumWriteFile);
        cacheSpectrumWriteFile = 0L;
	}
	cacheLock = 0;
    free(cacheSpectrumWriteBuffer);
    cacheSpectrumWriteBuffer = 0L;
#endif
}

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_views_SpectrogramView_closeReadCache(JNIEnv *env, jclass clazz)
{
#ifdef FILE_CACHE
//	while (cacheLock) usleep(USLEEP_TIME);
//	cacheLock = 1;
	if (cacheSpectrumReadFile != 0L) {
	    fclose(cacheSpectrumReadFile);
	    cacheSpectrumReadFile = 0L;
	}
//	cacheLock = 0;
#endif
}

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_MainActivity_resetCache(JNIEnv *env, jclass clazz)
{
	while (cacheLock) usleep(USLEEP_TIME);
	cacheLock = 1;

#ifdef FILE_CACHE
//	if (cacheSpectrumReadFile != 0L) {
//		fseek(cacheSpectrumReadFile, 0, SEEK_SET);
//	}
	if (cacheSpectrumWriteFile != 0L) {
	    fflush(cacheSpectrumWriteFile);
        if (refreshCache != 0) ftruncate(fileno(cacheSpectrumWriteFile), 0);
		if (fseek(cacheSpectrumWriteFile, 0, SEEK_SET) != 0) {
	            int errnum = errno;
                LOGE("fseek to 0 on cacheSpectrumWriteFile failed! errno=%d %s", errno, strerror(errnum));
		}
		cacheWriteHead = 0L;
	}
#else
	memset(cacheBuffer, 0, cacheLength);
#endif
	cacheLock = 0;
}

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_views_SpectrogramView_copyCache(JNIEnv *env, jclass clazz, jbyteArray array, jint from, jint len) {

	jbyte *bytes = (*env)->GetByteArrayElements(env, array, 0);

	while (cacheLock) usleep(USLEEP_TIME);
	cacheLock = 1;

#ifdef FILE_CACHE
	if (cacheSpectrumReadFile != 0L) {
		if ((refreshCache != 0) && (from >= cacheWriteHead)) {
			memset(bytes, 0, len);
		} else {
			uint32_t bytesToRead = len;
			if ((refreshCache != 0) && ((cacheWriteHead - from) < len)) bytesToRead = cacheWriteHead - from;
			if (fseek(cacheSpectrumReadFile, from, SEEK_SET) != 0) {
	            int errnum = errno;
                LOGE("fseek to %u on cacheSpectrumReadFile failed! errno=%d %s", from, errno, strerror(errnum));
			}
			uint32_t bytesRead = fread(bytes, 1, bytesToRead, cacheSpectrumReadFile);
			if (bytesRead < len) {
			    memset(bytes + bytesRead, 0, len - bytesRead);
			}
		}
	}
	else
		LOGE("copyCache: Spectrum read cache file is NULL");
#else
	if (len > (cacheLength - from)) len = cacheLength - from;
	memcpy(bytes, cacheBuffer+from, len);
#endif
	cacheLock = 0;
    (*env)->ReleaseByteArrayElements(env, array, bytes, 0);
}

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_views_PowerView_openReadCache(JNIEnv *env, jclass clazz)
{
//	while (cacheLock) usleep(USLEEP_TIME);
//	cacheLock = 1;
#ifdef FILE_CACHE
	cachePowerReadFile = fopen(cachePath, "rb");
	if (cachePowerReadFile == 0L) {
	    int errnum = errno;
        LOGE("Unable to open cachePowerReadFile %s! errno=%d %s", cachePath, errno, strerror(errnum));
    }
//	cacheLock = 0;
#endif
}

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_views_PowerView_closeReadCache(JNIEnv *env, jclass clazz)
{
#ifdef FILE_CACHE
//	while (cacheLock) usleep(USLEEP_TIME);
//	cacheLock = 1;
	if (cachePowerReadFile != 0L) {
	    fclose(cachePowerReadFile);
	    cachePowerReadFile = 0L;
	}
//	cacheLock = 0;
#endif
}

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_views_PowerView_readCache(JNIEnv *env, jclass clazz, jbyteArray array, jfloatArray mxarray, jint from, jint len, jboolean reset) {

	jbyte *bytes = (*env)->GetByteArrayElements(env, array, 0);
	jfloat *mx = (*env)->GetFloatArrayElements(env, mxarray, 0);

//	while (cacheLock) usleep(USLEEP_TIME);
//	cacheLock = 1;

#ifdef FILE_CACHE
	if (cachePowerReadFile != 0L) {
        if (fseek(cachePowerReadFile, from, SEEK_SET) != 0) {
	        int errnum = errno;
            LOGE("fseek to %u on cachePowerReadFile failed! errno=%d %s", from, errno, strerror(errnum));
        }
		uint32_t bytesRead = fread(bytes, 1, len, cachePowerReadFile);
		if (bytesRead < len) memset(bytes + bytesRead, 0, len - bytesRead);
	}
#endif

   if(reset)
        memset((float*) mx, 0, sizeof(float) * cacheLength);
    else {
        float *mp = (float*) mx;
        uint8_t *bp = (uint8_t*) bytes;
        uint8_t *blast = bp + len;
        float val;
        while (bp < blast) {
            val = (float) *bp;
            if (val > *mp)
                *mp = val;
            else
                *mp -= (*mp - val) * 0.002f;
            mp++;
            bp++;
        }
    }

//	cacheLock = 0;

   (*env)->ReleaseFloatArrayElements(env, mxarray, mx, 0);
   (*env)->ReleaseByteArrayElements(env, array, bytes, 0);
}

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_views_PowerView_copyCache(JNIEnv *env, jclass clazz, jbyteArray array, jfloatArray mxarray, jboolean reset)
{
	jbyte *bytes = (*env)->GetByteArrayElements(env, array, 0);
	jfloat *mx = (*env)->GetFloatArrayElements(env, mxarray, 0);

	while (cacheLock) usleep(USLEEP_TIME);
	cacheLock = 1;
	if (cacheBuffer == 0L)
	    memset(bytes, 0, cacheLength);
	else
	    memcpy(bytes, cacheBuffer, cacheLength);

    if(reset)
        memset((float*) mx, 0, sizeof(float) * cacheLength);
    else {
        float *mp = (float*) mx;
        uint8_t *bp = (uint8_t*) bytes;
        uint8_t *blast = bp + cacheLength;
        float val;
        while (bp < blast) {
            val = (float) *bp;
            if (val > *mp)
                *mp = val;
            else
                *mp -= (*mp - val) * 0.002f;
            mp++;
            bp++;
        }
    }
    cacheLock = 0;

   (*env)->ReleaseFloatArrayElements(env, mxarray, mx, 0);
   (*env)->ReleaseByteArrayElements(env, array, bytes, 0);
}

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_views_WaveformView_allocateWaveCaches(JNIEnv *env, jclass clazz, jint len)
{
	while (waveDataLock) usleep(USLEEP_TIME);
	waveDataLock = 1;

	if (waveDataBuffer != 0L) free(waveDataBuffer);
	if (waveStateBuffer != 0L) free(waveStateBuffer);
	waveDataLength = len;
	waveDataBuffer = (uint16_t*) malloc(waveDataLength * sizeof(uint16_t*));
	memset(waveDataBuffer, 0, waveDataLength * sizeof(uint16_t*));
	waveStateBuffer = (uint8_t*) malloc(waveDataLength);
	memset(waveStateBuffer, 0, waveDataLength);

	waveDataLock = 0;
}

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_views_WaveformView_clearWaveCaches(JNIEnv *env, jclass clazz)
{
#ifdef FILE_CACHE
    if (refreshCache == 0) return;
#endif
	while (waveDataLock) usleep(USLEEP_TIME);
	waveDataLock = 1;

	if (waveDataBuffer != 0L) {
		memset(waveDataBuffer, 0, waveDataLength * sizeof(uint16_t*));
	}
	if (waveStateBuffer != 0L) {
		memset(waveStateBuffer, 0, waveDataLength);
	}

	waveDataLock = 0;
}

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_views_WaveformView_copyWaveDataCache(JNIEnv *env, jclass clazz, jshortArray array, jint from, jint len)
{
	jshort *shorts = (*env)->GetShortArrayElements(env, array, 0);

	while (waveDataLock) usleep(USLEEP_TIME);
	waveDataLock = 1;

	if ((waveDataLength == 0L) || (from >= waveDataLength))
		memset(shorts, 0, len * sizeof(uint16_t));
	else {
		uint32_t remaining_len = waveDataLength - from;
		if (len > remaining_len) {
			memcpy(shorts, waveDataBuffer+from, remaining_len * sizeof(uint16_t));
			memset(shorts+remaining_len, 0, (len - remaining_len) * sizeof(uint16_t));
		}
		else
			memcpy(shorts, waveDataBuffer+from, len * sizeof(uint16_t));
	}

	waveDataLock = 0;

    (*env)->ReleaseShortArrayElements(env, array, shorts, 0);
}

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_views_WaveformView_copyWaveStateCache(JNIEnv *env, jclass clazz, jbyteArray array, jint from, jint len)
{
	jbyte *bytes = (*env)->GetByteArrayElements(env, array, 0);

	while (waveDataLock) usleep(USLEEP_TIME);
	waveDataLock = 1;

	if ((waveDataLength == 0L) || (from >= waveDataLength))
		memset(bytes, 0, len);
	else {
		uint32_t remaining_len = waveDataLength - from;
		if (len > remaining_len) {
			memcpy(bytes, waveStateBuffer+from, remaining_len);
			memset(bytes+remaining_len, 0, len - remaining_len);
		}
		else
			memcpy(bytes, waveStateBuffer+from, len);
	}

	waveDataLock = 0;

	(*env)->ReleaseByteArrayElements(env, array, bytes, 0);
}

