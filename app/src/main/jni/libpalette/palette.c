/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
#include <jni.h>
#include <android/log.h>
#include <android/bitmap.h>
#include <stdlib.h>
#include <math.h>
#include <string.h>

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

// The HSV color space corresponds much more closely to color as it is perceived by humans.
// if you want a "natural" interpolation between two colors you therefore want to use a color space that varies in an appropriate way
typedef struct RgbColor
{
    unsigned char r;
    unsigned char g;
    unsigned char b;
} RgbColor;


#define PALETTE_MAX		256

#define BLUE_CHANNEL	0x00FF0000
#define GREEN_CHANNEL	0x0000FF00
#define RED_CHANNEL		0x000000FF
#define ALPHA_CHANNEL	0xFF000000

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_Palette_interpRGB(JNIEnv *env, jclass clazz, jintArray array, jintArray carray, jintArray sarray, jint len, jfloat coef)
{
	jint buf[PALETTE_MAX];

	jint *colors = (*env)->GetIntArrayElements(env, carray, NULL);
	jint *steps = (*env)->GetIntArrayElements(env, sarray, NULL);

	RgbColor start;
	RgbColor stop;
	RgbColor rgb;

	int idx = (int) (255.0f * pow((float) steps[0] / 255.0f, coef));

	start.b = (BLUE_CHANNEL & colors[0]) >> 16;
	start.g = (GREEN_CHANNEL & colors[0]) >> 8;
	start.r = RED_CHANNEL & colors[0];

	for (int j = 1; j < len; j++) {

		stop.b = (BLUE_CHANNEL & colors[j]) >> 16;
		stop.g = (GREEN_CHANNEL & colors[j]) >> 8;
		stop.r = RED_CHANNEL & colors[j];

//		int start_idx = (int) (255.0f * pow((float) steps[j-1] / 255.0f, coef));
//		int stop_idx = (int) (255.0f * pow((float) steps[j] / 255.0f, coef));
		int start_idx = (int) (255.0f * pow((float) steps[j-1] / 256.0f, coef));
		int stop_idx = (int) (255.0f * pow((float) steps[j] / 256.0f, coef));
		int step_count = stop_idx - start_idx;

		float r_delta = (float) (stop.r - start.r) / (float) step_count;
		float g_delta = (float) (stop.g - start.g) / (float) step_count;
		float b_delta = (float) (stop.b - start.b) / (float) step_count;

		for (int i = 0; i < step_count; i++) {
//			float delta = (float) (i - start_idx) / (float) step_count;
//			float delta = pow((float) (i - start_idx) / (float) step_count, coef);
			rgb.r = start.r + i * r_delta;
			rgb.g = start.g + i * g_delta;
			rgb.b = start.b + i * b_delta;
			buf[idx++] = ALPHA_CHANNEL | (rgb.b << 16) | (rgb.g << 8) | rgb.r;
		}
		start = stop;
	}
	while (idx < PALETTE_MAX) {
		buf[idx++] = ALPHA_CHANNEL | (rgb.b << 16) | (rgb.g << 8) | rgb.r;
	}

	(*env)->SetIntArrayRegion(env, array, 0, PALETTE_MAX, buf);

	(*env)->ReleaseIntArrayElements(env, carray, colors, 0);
	(*env)->ReleaseIntArrayElements(env, sarray, steps, 0);
}

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_Palette_RGBA2ARGB(JNIEnv *env, jclass clazz, jintArray rgba_colors, jintArray argb_colors)
{
	jint *rgba = (*env)->GetIntArrayElements(env, rgba_colors, NULL);
	jint *argb = (*env)->GetIntArrayElements(env, argb_colors, NULL);
	jint len = (*env)->GetArrayLength(env, rgba_colors);

	for (int i = 0; i < len; i++) {
		argb[i] = ALPHA_CHANNEL
				| ((BLUE_CHANNEL & rgba[i]) >> 16)
				| (GREEN_CHANNEL & rgba[i])
				| ((RED_CHANNEL & rgba[i]) << 16);
	}

	(*env)->ReleaseIntArrayElements(env, rgba_colors, rgba, 0);
	(*env)->ReleaseIntArrayElements(env, argb_colors, argb, 0);
}

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_Palette_buildRamp(JNIEnv *env, jclass clazz, jobject bitmap, jintArray colors)
{
	int ret;
	AndroidBitmapInfo info;
	if ((ret = AndroidBitmap_getInfo(env, bitmap, &info)) < 0) {
		LOGE("AndroidBitmap_getInfo() failed ! error=%d", ret);
		return;
	}

	void* bitmapPixels;
	if ((ret = AndroidBitmap_lockPixels(env, bitmap, &bitmapPixels)) < 0) {
		LOGE("AndroidBitmap_lockPixels() failed ! error=%d", ret);
		return;
	}

	jint *col = (*env)->GetIntArrayElements(env, colors, NULL);
	memcpy((jint*) bitmapPixels, col, info.width * sizeof(jint));
	(*env)->ReleaseIntArrayElements(env, colors, col, 0);

	AndroidBitmap_unlockPixels(env, bitmap);
}
