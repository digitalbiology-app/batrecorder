#include <jni.h>
#include <android/log.h>

#include <math.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>

#include "analysis.h"

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
#define LOG_TAG    "analysis"
#define LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)
#endif

//#define FREQ_BAND_THRESHOLD     0.5f
#define OVERALL_THRESHOLD       0.6f
//#define KERNEL_SIZE         5
//#define HALF_KERNEL_SIZE    (KERNEL_SIZE/2)
#define BIN_COUNT           256

//typedef struct {
//    int32_t startTime;
//    int32_t stopTime;
//    int16_t minBin;
//    int16_t maxBin;
//} Signal;

//#define SIGNAL_SLOT_ALLOC   20
//
//typedef struct {
//    Signal *array;
//    uint16_t count;
//    uint16_t slots;
//} SignalArray;
//
//SignalArray signals;

void local_maxima(uint8_t* buffer, int count) {

    uint8_t max = 0;
    uint8_t* bp = buffer;
    uint8_t* last = buffer + count;
    while (bp < last) {
        if (*bp > max) max = *bp;
        bp++;
    }
    bp = buffer;
//    max = 60;
    while (bp < last) {
        if (*bp < max || max == 0) *bp = 0;
//        else
//            *bp = 255;
        bp++;
    }

//    float* temp = (uint8_t*) malloc(count * sizeof(float));
//    memset(temp, 0, count * sizeof(float));
//
//    int window = count / 16;
//    int half_window = window / 2;
//
//    float max = 0;
//
//    float* tp = temp + half_window;
//    uint8_t* bp = buffer + half_window;
//    uint8_t* last = buffer + count - half_window;
//    while (bp < last) {
//        uint8_t* p = bp++ - half_window;
//        uint8_t* lp = p + half_window;
//        float sum = 0;
//        while (p < lp) sum += (float) *p++;
//        *tp = sum / window;
//        if (*tp > max) max = *tp;
//        tp++;
//    }
//
////    memset(buffer, 0, count);
//
//    bp = buffer + half_window;
//    tp = temp + half_window;
//    while (bp < last) {
//        if (((*tp > *(tp-1)) && (*tp > *(tp+1)))) {
////        if (*tp < max)
////            *bp = (uint8_t) *tp;
////            *bp = 255;
//        }
//        else
//            *bp = 0;
//        bp++;
//        tp++;
//    }
//    free(temp);
}

uint32_t subtract_background(const char* in_path, const char* out_path, int height) {

    uint32_t rowCount = 0;

    uint32_t* rowbins = (uint32_t*) malloc(height * BIN_COUNT * sizeof(uint32_t));
    memset(rowbins, 0, height * BIN_COUNT * sizeof(uint32_t));

    uint8_t *buffer = (uint8_t*) malloc(height);

    // FIRST PASS - calculate median background for each frequency band...
    size_t bytesRead;
    uint32_t count = 0;
    FILE *in = fopen(in_path, "rb");
    if (in != 0L) {
        while ((bytesRead = fread(buffer, 1, height, in)) > 0) {
            uint8_t *bp = buffer;
            uint8_t *blast = buffer + bytesRead;
            uint32_t* bnp = rowbins;
            while (bp < blast) {
                bnp[*bp++]++;
                bnp += BIN_COUNT;
            }
            count++;
        }
        fclose(in);
    }
    rowCount = count;

    uint8_t *thresholds = (uint8_t*) malloc(height);
    memset(thresholds, 255, height);
    uint32_t threshold = count * OVERALL_THRESHOLD;
    uint32_t running_total;
    for (int jj = 0; jj < height; ++jj) {
        uint32_t* bnp = rowbins + (jj * BIN_COUNT);
        running_total = 0;
        for (uint16_t ii = 0; ii < BIN_COUNT; ++ii) {
            running_total += bnp[ii];
            if (running_total > threshold) {
                thresholds[jj] = (uint8_t) ii;
//                if (thresholds[jj] < 20) thresholds[jj] = 20;
                break;
            }
        }
    }
    free(rowbins);

    // SECOND PASS - subtract background and build histogram, saving results to temporary file.
//    uint32_t *bins = (uint32_t*) malloc(BIN_COUNT * sizeof(uint32_t));
//    memset(bins, 0, BIN_COUNT * sizeof(uint32_t));
    count = 0;
    in = fopen(in_path, "rb");
    if (in != 0L) {
        FILE *out = fopen(out_path, "wb");
        if (out != 0L) {
            while ((bytesRead = fread(buffer, 1, height, in)) > 0) {
                uint8_t *bp = buffer;
                uint8_t *blast = buffer + bytesRead;
                uint8_t *tp = thresholds;
                while (bp < blast) {
                    if (*bp > *tp) {
                        *bp -= *tp;
//                        bins[*bp]++;
//                        count++;
                    }
                    else
                        *bp = 0;
                    bp++;
                    tp++;
                }
//                local_maxima(buffer, height);
                fwrite(buffer, 1, height, out);
            }
            fclose(out);
        }
        fclose(in);
    }

//    for (int ii = 0; ii < BIN_COUNT; ++ii) {
//        LOGI("%d %d", ii, bins[ii]);
//    }

//    threshold = count * 0.5f;
//    running_total = 0;
//    uint8_t cutoff = BIN_COUNT - 1;
//    for (int ii = 0; ii < BIN_COUNT; ++ii) {
//        running_total += bins[ii];
//        if (running_total > threshold) {
//            cutoff = ii;
//            break;
//        }
//    }
//    free(bins);
    free(thresholds);
//
//    // THIRD PASS
//    FILE* out = fopen(out_path, "rb+");
//    if (out != 0L) {
//        uint32_t row = 0;
//        while ((bytesRead = fread(buffer, 1, height, out)) > 0) {
//            uint8_t *bp = buffer;
//            uint8_t *blast = buffer + bytesRead;
//            while (bp < blast) {
//                if (*bp > cutoff)
//                    *bp = 255;
//                else
//                    *bp = 0;
//                bp++;
//            }
////            // Find local maxima
////            bp = buffer;
////            *bp = 0;
////            bp++;
////            blast--;
////            *blast = 0;
////            uint8_t threshold = total / sum;
////            while (bp < blast) {
////                if ((*bp > threshold) && (*bp > *(bp-1)) && (*bp > *(bp+1)))
////                    *bp = 255;
////                else
////                    *bp = 0;
////                bp++;
////            }
//            fseek(out, row * height, SEEK_SET);
//            fwrite(buffer, 1, height, out);
//            row++;
//        }
//        fclose(out);
//    }

    free(buffer);
    return rowCount;
}
/*
void filter(const char* in_path, const char* out_path, int height)
{
    uint8_t* buffer = (uint8_t*) malloc(height);
    uint8_t* kernel_buffer = (uint8_t*) malloc(3 * height);

    int16_t kernel[9] = { -1, 0, 1, -2, 0, 2, -1, 0, 1 };

    size_t bytesRead;
    FILE* out = fopen(out_path, "wb");
    if (out != 0L) {
        FILE* in = fopen(in_path, "rb");
        if (in != 0L) {
            uint32_t row = 0;
            while ((bytesRead = fread(kernel_buffer, 1, 3 * height, in)) > 0) {

                if (bytesRead < 3 * height) break;      // TODO
                if (row == 0)
                    memset(buffer, 0, height);
                else {
                    uint8_t *bp = buffer;
                    *bp++ = 0;
                    uint8_t *col0 = kernel_buffer;
                    uint8_t *col1 = col0 + height;
                    uint8_t *col2 = col1 + height;
                    for (int ii = 0; ii < height - 2; ii++) {
                        int16_t total = kernel[0] * (int16_t) *col0
                                        + kernel[1] * (int16_t) *col1
                                        + kernel[2] * (int16_t) *col2;
                        total += kernel[3] * (int16_t) *(col0 + 1)
                                 + kernel[4] * (int16_t) *(col1 + 1)
                                 + kernel[5] * (int16_t) *(col2 + 1);
                        total += kernel[6] * (int16_t) *(col0 + 2)
                                 + kernel[7] * (int16_t) *(col1 + 2)
                                 + kernel[8] * (int16_t) *(col2 + 2);
                        *bp++ = total;
                        col0++;
                        col1++;
                        col2++;
                    }
                    *bp = 0;
                }
                fwrite(buffer, 1, height, out);
                row++;
                fseek(in, row * height, SEEK_SET);
            }
            fclose(in);
        }
        fclose(out);
    }
    free(kernel_buffer);
    free(buffer);
}

void blur(const char* in_path, const char* out_path, int height)
{
    uint8_t* buffer = (uint8_t*) malloc(height);
    uint8_t* kernel = (uint8_t*) malloc(KERNEL_SIZE * height);

    size_t bytesRead;
    FILE* out = fopen(out_path, "wb");
    if (out != 0L) {
        FILE* in = fopen(in_path, "rb");
        if (in != 0L) {
            uint32_t row = 0;
            while ((bytesRead = fread(kernel, 1, KERNEL_SIZE * height, in)) > 0) {

                for (int ii = 0; ii < HALF_KERNEL_SIZE; ii++) buffer[ii] = buffer[height-ii-1] = 0;

                for (int ii = HALF_KERNEL_SIZE; ii < height-HALF_KERNEL_SIZE; ii++) {
                    uint32_t mu = 0;
                    for (int jj = 0; jj < KERNEL_SIZE; jj++) {
                        for (int kk = -HALF_KERNEL_SIZE; kk <= HALF_KERNEL_SIZE; kk++) {
                            mu += kernel[jj * height + (ii + kk)];
                        }
                    }
                    buffer[ii] = (uint8_t) (mu / (KERNEL_SIZE * KERNEL_SIZE));
                }
                if ((row < HALF_KERNEL_SIZE) || (bytesRead < (KERNEL_SIZE * height))) {
                    memset(buffer, 0, height);
                }
                fwrite(buffer, 1, height, out);
                row++;
                fseek(in, row * height, SEEK_SET);
            }
            fclose(in);
        }
        fclose(out);
    }
    free(kernel);
    free(buffer);
}
*/
//void calculate_boxes(const char* in_path, int height)
//{
//    uint8_t* buffer = (uint8_t*) malloc(height);
//
//    int32_t row = 0;
//    int16_t current_signal = -1;
//    size_t bytesRead;
//    FILE* in = fopen(in_path, "rb");
//    if (in != 0L) {
//        uint8_t cutoff = (log10(signalGain)+1.0) * 0.2 * BIN_COUNT;
//        while ((bytesRead = fread(buffer, 1, height, in)) > 0) {
//            uint8_t found = 0;
//            for (int ii = 0; ii < bytesRead; ii++) {
//                if (buffer[ii] > cutoff) {
//                    found = 1;
//                    if (current_signal < 0) {
//                        if (signals.array == NULL) {
//                            signals.slots += SIGNAL_SLOT_ALLOC;
//                            signals.array = (Signal*) malloc(sizeof(Signal) * signals.slots);
//                        }
//                        else if (signals.slots <= signals.count) {
//                            signals.slots += SIGNAL_SLOT_ALLOC;
//                            signals.array = (Signal*) realloc(signals.array, sizeof(Signal) * signals.slots);
//                        }
//                        current_signal = signals.count;
//                        signals.count++;
//                        signals.array[current_signal].startTime = row;
//                        signals.array[current_signal].minBin = ii;
//                        signals.array[current_signal].maxBin = ii;
//                    }
//                    else {
//                        if (ii < signals.array[current_signal].minBin)
//                            signals.array[current_signal].minBin = ii;
//                        else if (ii > signals.array[current_signal].maxBin)
//                            signals.array[current_signal].maxBin = ii;
//                    }
//                }
//            }
//            if ((found == 0) && (current_signal >= 0)) {
//                signals.array[current_signal].stopTime = row;
//                current_signal = -1;
//            }
//            row++;
//        }
//        fclose(in);
//    }
//    if (current_signal >= 0) {
//        signals.array[current_signal].stopTime = row;
//    }
//    free(buffer);
//}
/*
void quicksort(uint32_t* x, int16_t first, int16_t last)
{
    uint32_t temp;
    int16_t i, j, pivot;

    if (first < last) {
        pivot = first;
        i = first;
        j = last;

        while(i < j) {
            while(x[i] <= x[pivot] && i < last) i++;
            while(x[j] > x[pivot]) j--;
            if(i < j){
                temp = x[i];
                x[i] = x[j];
                x[j] = temp;
            }
        }

        temp = x[pivot];
        x[pivot] = x[j];
        x[j] = temp;

        quicksort(x, first, j-1);
        quicksort(x, j+1, last);
    }
}
*/
//void combine_boxes()
//{
//    if (signals.count > 1) {
//        int16_t count = signals.count - 1;
//        uint32_t* deltas = (uint32_t*) malloc(count * sizeof(uint32_t));
////        float mu = 0;
//        for (int ii = 0; ii < count; ++ii) {
//            deltas[ii] = signals.array[ii+1].startTime - signals.array[ii].stopTime;
////            mu += signals.array[ii+1].startTime - signals.array[ii].stopTime;
////            mu += deltas[ii];
//        }
////        mu /= count;
////        float sd = 0;
////        for (int ii = 0; ii < count; ++ii) {
////            float delta = deltas[ii] - mu;
////            sd += delta * delta;
////        }
//        quicksort(deltas, 0, count-1);
//        int jj = 0;
//        for (int ii = 1; ii < count; ++ii) {
//            if ((deltas[ii]/ deltas[ii-1]) > 10) {
//                jj = ii;
//                break;
//            }
//        }
//
//        uint32_t cutoff = deltas[jj];
//        free(deltas);
////        mu -= sqrt(sd / count);
////        mu = pow(10, mu);
//
//        for (int ii = 0; ii < signals.count-1; ++ii) {
//            if ((signals.array[ii+1].startTime - signals.array[ii].stopTime) < cutoff) {
//                signals.array[ii].stopTime = signals.array[ii+1].stopTime;
//                if (signals.array[ii+1].minBin < signals.array[ii].minBin) signals.array[ii].minBin = signals.array[ii+1].minBin;
//                if (signals.array[ii+1].maxBin > signals.array[ii].maxBin) signals.array[ii].maxBin = signals.array[ii+1].maxBin;
//                if (ii < (signals.count-2)) memmove(&(signals.array[ii+1]), &(signals.array[ii+2]), sizeof(Signal) * (signals.count-(ii+2)));
//                ii--;
//                signals.count--;
//            }
//        }
//    }
//}

//JNIEXPORT jint JNICALL Java_com_digitalbiology_audio_FeatureExtractor_extract(JNIEnv *env, jclass clazz, jint height, jint cutoff)
//{
//    char tmp_path2[256];
//    strcpy(tmp_path2, cachePath);
//    int len = strlen(tmp_path2) - 3;
//    tmp_path2[len++] = 't';
//    tmp_path2[len++] = 'm';
//    tmp_path2[len] = '2';
//
//    char tmp_path[256];
//    strcpy(tmp_path, cachePath);
//    len = strlen(tmp_path) - 3;
//    tmp_path[len++] = 't';
//    tmp_path[len++] = 'm';
//    tmp_path[len] = 'p';
//
//    // Subtract background.
//    subtract_background(cachePath, tmp_path, height);
////    filter(tmp_path2, tmp_path, height);
//
//    // Blur the data.
////    blur(cachePath, tmp_path, height);
//
////    // Subtract background from blurred data.
////    subtract_background(tmp_path, height, thresholds);
////    free(thresholds);
//
//    signals.array = NULL;
//    signals.count = 0;
//    signals.slots = 0;
//
//    calculate_boxes(tmp_path, height);
////    combine_boxes();
//
//    // Debugging
////    remove(cachePath);
////    rename(tmp_path, cachePath);
//    remove(tmp_path);
//
//    return signals.count;
//}
//
//JNIEXPORT void JNICALL Java_com_digitalbiology_audio_FeatureExtractor_getSignals(JNIEnv *env, jclass clazz, jintArray array)
//{
//    if (signals.count > 0) {
//        jint *sa = (*env)->GetIntArrayElements(env, array, NULL);
//        int jj = 0;
//        for (int ii = 0; ii < signals.count; ii++) {
//            sa[jj++] = signals.array[ii].startTime;
//            sa[jj++] = signals.array[ii].stopTime;
//            sa[jj++] = signals.array[ii].minBin;
//            sa[jj++] = signals.array[ii].maxBin;
//        }
//        free(signals.array);
//        (*env)->ReleaseIntArrayElements(env, array, sa, 0);
//    }
//}
