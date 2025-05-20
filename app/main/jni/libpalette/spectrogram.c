//
// Created by bill on 4/22/2024.
//
#include <jni.h>
#include <android/log.h>
#include <android/bitmap.h>
#include <stdlib.h>
#include <math.h>
#include <string.h>
#include <assert.h>

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
#define LOG_TAG    __FILE__ ":" STRINGIFY(__MyNative__)
#define LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)
#endif

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_views_SpectrogramView_linearScale(JNIEnv *env, jclass clazz, jobject bitmap, jintArray carray, jbyteArray marray)
{
    jint *colors = (*env)->GetIntArrayElements(env, carray, NULL);
    jbyte *map = (*env)->GetByteArrayElements(env, marray, NULL);

    AndroidBitmapInfo bitmapInfo;
    AndroidBitmap_getInfo(env, bitmap, &bitmapInfo);

    jint* buf;
    if (AndroidBitmap_lockPixels(env, bitmap, &buf) == 0) {

        jint stride = bitmapInfo.stride / sizeof(jint);
        jint width = bitmapInfo.width;
        jint numPixels = bitmapInfo.height * stride;
        jbyte* mapStart = map;
        for (jint rowStart = 0; rowStart < numPixels; rowStart += stride) {
            jint* lineStart = buf + rowStart;
            jint* lineStart0 = lineStart + width;
            while (lineStart < lineStart0) {
                *lineStart++ = colors[(uint8_t) *mapStart];
                mapStart++;
            }
        }
        AndroidBitmap_unlockPixels(env, bitmap);
    }
    (*env)->ReleaseIntArrayElements(env, carray, colors, 0);
    (*env)->ReleaseByteArrayElements(env, marray, map, 0);
}

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_views_SpectrogramView_logScale(JNIEnv *env, jclass clazz, jobject bitmap, jintArray carray, jbyteArray marray, jfloat fqmax)
{
    jint *colors = (*env)->GetIntArrayElements(env, carray, NULL);
    jbyte *map = (*env)->GetByteArrayElements(env, marray, NULL);

    AndroidBitmapInfo bitmapInfo;
    AndroidBitmap_getInfo(env, bitmap, &bitmapInfo);

    jint* buf;
    if (AndroidBitmap_lockPixels(env, bitmap, &buf) == 0) {

        jfloat logfqmax = log10(fqmax) - 2.0f;
        jint stride = bitmapInfo.stride / sizeof(jint);
        jint width = bitmapInfo.width;
        jint height = bitmapInfo.height;
        for (jint y = 0; y < height; y++) {
            jint line = y * stride;
            jint* lineStart = buf + line;
            for (jint x = 0; x < width; x++) {
                jfloat fq = width * pow(10, 2.0f + logfqmax * (jfloat) x / (jfloat) width) / fqmax;
                *lineStart++ = colors[map[line + (jint) fq]];
            }
        }
        AndroidBitmap_unlockPixels(env, bitmap);
    }
    (*env)->ReleaseIntArrayElements(env, carray, colors, 0);
    (*env)->ReleaseByteArrayElements(env, marray, map, 0);
}

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_views_WaveformView_traceWave(JNIEnv *env, jclass clazz, jobject bitmap, jshortArray array, jbyteArray sarray, jint offset, jint playHead, jint selStart, jint selEnd, jboolean nightMode)
{
    jshort *data = (*env)->GetShortArrayElements(env, array, NULL);
    jbyte *state = (*env)->GetByteArrayElements(env, sarray, NULL);

    AndroidBitmapInfo bitmapInfo;
    AndroidBitmap_getInfo(env, bitmap, &bitmapInfo);

    jint* buf;
    if (AndroidBitmap_lockPixels(env, bitmap, &buf) == 0) {

        jint width = bitmapInfo.width;
        jint height = bitmapInfo.height;
        jint stride = bitmapInfo.stride / sizeof(jint);
        jint center = width / 2;
        for (jint y = 0; y < height; y++) {
            jint y2 = y + offset;
            jint* b = buf + stride * y;
            jint color;
            if (state[y] == 2)  // recording
                color = nightMode ? 0xff222222 : 0xff0000ff;
            else
                color = nightMode ? 0xff0000ff : 0xff00ffff;
            memset(b, (y2 >= selStart && y2 < selEnd) ? 40 : 0, width * sizeof(jint));

            jint limit = center * data[y] / 32768;
            b += center - limit;
            jint* b0 = b + limit + limit;
            while (b <= b0) *b++ = color;
        }
        AndroidBitmap_unlockPixels(env, bitmap);
    }
    (*env)->ReleaseShortArrayElements(env, array, data, 0);
    (*env)->ReleaseByteArrayElements(env, sarray, state, 0);
}

#define PALETTE_COUNT   256

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_views_PowerView_linearSpectrum(JNIEnv *env, jclass clazz, jobject bitmap, jbyteArray array, jintArray barray, jfloatArray marray, jintArray carray)
{
    jbyte *data = (*env)->GetByteArrayElements(env, array, NULL);
    jfloat *mx = (*env)->GetFloatArrayElements(env, marray, NULL);
    jint *colors = (*env)->GetIntArrayElements(env, carray, NULL);
    jint *bins = (*env)->GetIntArrayElements(env, barray, NULL);

    AndroidBitmapInfo bitmapInfo;
    AndroidBitmap_getInfo(env, bitmap, &bitmapInfo);

    jint* buf;
    if (AndroidBitmap_lockPixels(env, bitmap, &buf) == 0) {

        bins[0] = 0;
        bins[1] = -1;
        jint stride = bitmapInfo.stride / sizeof(jint);
        jint width = bitmapInfo.width;
        jint height = bitmapInfo.height;
        assert(height == PALETTE_COUNT);
        jint* lineStart = buf;
        for (jint y = 0; y < height; y++, lineStart += stride) {
            jint* bp = lineStart;
            uint8_t* dp = (uint8_t*) data;
            jfloat* mp = mx;
            jint y2 = PALETTE_COUNT - y;  // flip display so that histogram faces up
            for (jint x = 0; x < width; x++, bp++, dp++, mp++) {
                if (*dp > bins[0]) {
                    bins[0] = *dp;
                    bins[1] = x;
                }
                if (y2 > *mp) {
                    if (y2 > *dp)
                        *bp = 0xbb000000;
                    else {
                        *bp = colors[(y2 < 52) ? 52 : y2];
                    }
                }
                else {
                    if (y2 > *dp)
                        *bp = 0xdd222222;   // fading historical power spectrum
                    else {
                        *bp = colors[(y2 < 52) ? 52 : y2];
                    }
                }
            }
        }
        AndroidBitmap_unlockPixels(env, bitmap);
    }
    (*env)->ReleaseByteArrayElements(env, array, data, 0);
    (*env)->ReleaseFloatArrayElements(env, marray, mx, 0);
    (*env)->ReleaseIntArrayElements(env, carray, colors, 0);
    (*env)->ReleaseIntArrayElements(env, barray, bins, 0);
}

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_views_PowerView_logSpectrum(JNIEnv *env, jclass clazz, jobject bitmap, jbyteArray array, jintArray barray, jfloatArray marray, jfloat maxFreq, jintArray carray)
{
    jbyte *data = (*env)->GetByteArrayElements(env, array, NULL);
    jfloat *mx = (*env)->GetFloatArrayElements(env, marray, NULL);
    jint *colors = (*env)->GetIntArrayElements(env, carray, NULL);
    jint *bins = (*env)->GetIntArrayElements(env, barray, NULL);

    AndroidBitmapInfo bitmapInfo;
    AndroidBitmap_getInfo(env, bitmap, &bitmapInfo);

    jint* buf;
    if (AndroidBitmap_lockPixels(env, bitmap, &buf) == 0) {

        bins[0] = 0;
        bins[1] = -1;
        jint stride = bitmapInfo.stride / sizeof(jint);
        jint width = bitmapInfo.width;
        jint height = bitmapInfo.height;
        assert(height == PALETTE_COUNT);
        jfloat logfqmax = log10(maxFreq) - 2.0f;
        jint* lineStart = buf;
        for (jint y = 0; y < height; y++, lineStart += stride) {
            jint* bp = lineStart;
            uint8_t* dp = (uint8_t*) data;
            jfloat* mp = mx;
            jint y2 = PALETTE_COUNT - y;
            for (jint x = 0; x < width; x++, bp++, dp++, mp++) {

                float fq = pow(10, 2.0f + logfqmax * (jfloat) x / (jfloat) width);
                jbyte data_x = data[(jint) ((float) width * fq / maxFreq)];

                if (data_x > bins[0]) {
                    bins[0] = data_x;
                    bins[1] = x;
                }
                if (y2 > *mp) {
                    if (y2 > *dp)
                        *bp = 0x88000000;
                    else {
                        *bp = colors[(y2 < 52) ? 52 : y2];
                    }
                }
                else {
                    if (y2 > *dp)
                        *bp = 0xdd222222;
                    else {
                        *bp = colors[(y2 < 52) ? 52 : y2];
                    }
                }
            }
        }
        AndroidBitmap_unlockPixels(env, bitmap);
    }
    (*env)->ReleaseByteArrayElements(env, array, data, 0);
    (*env)->ReleaseFloatArrayElements(env, marray, mx, 0);
    (*env)->ReleaseIntArrayElements(env, carray, colors, 0);
    (*env)->ReleaseIntArrayElements(env, barray, bins, 0);
}