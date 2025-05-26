#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <cpu-features.h>

#include <math.h>
#include <stdlib.h>
#include <stdio.h>
#include <unistd.h>

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

#include <string.h>

typedef struct {
	int32_t		sampleRate;
	int16_t		bitsPerSample;
	int16_t		numChannels;
} fmtData;

#define WRITE_HEADER_SIZE 44

#define UNCOMPRESSED_PCM	1

#define RIFF_CHUNK_ID	0x46464952
#define WAV_CHUNK_ID	0x45564157
#define FMT_CHUNK_ID	0x20746d66
#define DATA_CHUNK_ID	0x61746164
#define XMP_CHUNK_ID	0x584d505f
#define GUANO_CHUNK_ID	0x6e617567
#define WAMD_CHUNK_ID	0x646d6177

#define CHANNEL_BYTE_OFFSET 22

#define EIGHT_BYTES     8

void isEndian() {
    unsigned short word=0x0102;
    if (*(char*)&word==2)
    	LOGI("Little Endian");
    else
    	LOGI("Big Endian");
}

#define ALPHA_CHANNEL	0xFF000000

#define INVALID_GPS	360.0f

FILE* recordFile = NULL;

JNIEXPORT jint JNICALL Java_com_digitalbiology_audio_MainActivity_HasNEON(JNIEnv *env, jclass clazz)
{
	if (android_getCpuFamily() == ANDROID_CPU_FAMILY_ARM && (android_getCpuFeatures() & ANDROID_CPU_ARM_FEATURE_NEON) != 0)
		return 1;
	return 0;
}

JNIEXPORT jint JNICALL Java_com_digitalbiology_audio_MainActivity_readWAVHeader(JNIEnv *env, jclass clazz, jstring path, jlongArray array)
{
	fmtData	data;

	uint32_t ckID;
	uint32_t ckSize;
	uint32_t fileOffset;
	uint32_t dataStart = 0;
	uint32_t dataSize = 0;
	uint32_t dataChunkSize = 0;
	uint16_t audioFormat;

	const char *cpath = (*env)->GetStringUTFChars(env, path, 0);
	FILE* fhandle = fopen(cpath, "rb");
	if (fhandle != 0L) {

		fread(&ckID, sizeof(uint32_t), 1, fhandle);
		if (ckID == RIFF_CHUNK_ID) {

			fseek(fhandle, sizeof(uint32_t), SEEK_CUR);      // skip file size
			fread(&ckID, sizeof(uint32_t), 1, fhandle);
			if (ckID == WAV_CHUNK_ID) {

				fileOffset = 12;

				while (fread(&ckID, sizeof(uint32_t), 1, fhandle) == 1) {
//					LOGI("id=%x", ckID);
					fileOffset += sizeof(uint32_t);
					if (ckID == FMT_CHUNK_ID) {
						fread(&ckSize, sizeof(uint32_t), 1, fhandle);					// chunk size
						fread(&audioFormat, sizeof(uint16_t), 1, fhandle);				// audio format
						if (audioFormat != UNCOMPRESSED_PCM) {
			                LOGE("Audio format is not UNCOMPRESSED_PCM!");
		                    fclose(fhandle);
	                        (*env)->ReleaseStringUTFChars(env, path, cpath);
						    return -1;
						}

						fread(&(data.numChannels), sizeof(uint16_t), 1, fhandle);		// number of channels
						fread(&(data.sampleRate), sizeof(uint32_t), 1, fhandle);			// sample rate
						fseek(fhandle, sizeof(int32_t) + sizeof(uint16_t), SEEK_CUR);	// skip data rate and block align
						fread(&(data.bitsPerSample), sizeof(uint16_t), 1, fhandle);		// bits per sample
						fileOffset += ckSize + sizeof(uint32_t);
						if (fseek(fhandle, fileOffset, SEEK_SET)) break;
					}
					else if (ckID == DATA_CHUNK_ID) {
						fread(&ckSize, sizeof(uint32_t), 1, fhandle);	// chunk size
						dataStart = fileOffset + sizeof(uint32_t);
						dataChunkSize = ckSize;
						fileOffset += ckSize + sizeof(uint32_t);
						if (fseek(fhandle, fileOffset, SEEK_SET)) break;
					}
					else {
						if ((dataStart > 0) && (dataSize == 0)) dataSize = fileOffset - dataStart - sizeof(uint32_t);
						fread(&ckSize, sizeof(uint32_t), 1, fhandle);	// chunk size
						fileOffset += ckSize + sizeof(uint32_t);
						if (fseek(fhandle, fileOffset, SEEK_SET)) break;
					}
				}
				// Data chunk sanity check
				if (dataSize == 0) {
					fseek(fhandle, 0L, SEEK_END);
					dataSize = ftell(fhandle) - dataStart;
				}
			}
		}
		fclose(fhandle);

		if (dataChunkSize != dataSize) {
			LOGI("Data count in WAV header (%d) does not match data chunk size (%d)!", dataChunkSize, dataSize);
			dataChunkSize = dataSize;
		}

		jlong *params = (*env)->GetLongArrayElements(env, array, 0);

		params[0] = data.numChannels;
		params[1] = data.sampleRate;
		params[2] = data.bitsPerSample;
		params[3] = dataChunkSize / (data.bitsPerSample / 8);
//        LOGI("dataChunkSize=%d", dataChunkSize);
//        LOGI("bitsPerSample=%d", data.bitsPerSample);

		(*env)->ReleaseLongArrayElements(env, array, params, 0);
	}
	else
		LOGE("Failed reading WAV header.");
	(*env)->ReleaseStringUTFChars(env, path, cpath);

	return dataStart / sizeof(jshort);
}

short* stereo_buffer = 0L;
uint32_t stereo_buffer_len = 0;

JNIEXPORT jint JNICALL Java_com_digitalbiology_audio_MainActivity_readWAVData(JNIEnv *env, jclass clazz,
    jstring path, jshortArray array, jint dataOffset, jint dataLen, jint fileOffset, jshort channels, jshort which_channel )
{
	jint shortsRead = 0;
	jshort *data = (*env)->GetShortArrayElements(env, array, 0);

	const char *cpath = (*env)->GetStringUTFChars(env, path, 0);
	FILE* fhandle = fopen(cpath, "rb");
	if (fhandle != 0L) {
        fseek(fhandle, fileOffset * sizeof(jshort) * channels, SEEK_SET);
	    if (channels == 1) {
            shortsRead = fread(data + dataOffset, sizeof(jshort), dataLen, fhandle); // Reading raw audio data
		}
		else {
            if (stereo_buffer == 0L) {
               stereo_buffer_len = dataLen * channels;
               stereo_buffer = (short*) malloc(stereo_buffer_len * sizeof(short));
           }
           else if (stereo_buffer_len != dataLen * channels) {
               free(stereo_buffer);
               stereo_buffer_len = dataLen * channels;
               stereo_buffer = (short*) malloc(stereo_buffer_len * sizeof(short));
           }
            shortsRead = fread(stereo_buffer, sizeof(jshort), stereo_buffer_len, fhandle); // Reading raw audio data
            short* dp = data + dataOffset;
            short* bp = stereo_buffer + (which_channel-1);
            short* last = stereo_buffer + stereo_buffer_len;
            while (bp < last) {
                *dp = *bp;
                dp++;
                bp += channels;
            }
            shortsRead = shortsRead / channels;
		}
		fclose(fhandle);
	}
	else
		LOGE("Failed reading WAV data.");

	(*env)->ReleaseShortArrayElements(env, array, data, 0);
	(*env)->ReleaseStringUTFChars(env, path, cpath);
	return shortsRead;
}

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_MainActivity_copyWAVSnippet(JNIEnv *env, jclass clazz, jstring path1, jstring path2, jint from, jint len, jshort channel)
{
	const char *cpath1 = (*env)->GetStringUTFChars(env, path1, 0);
	const char *cpath2 = (*env)->GetStringUTFChars(env, path2, 0);

	uint32_t		ckID;
	uint32_t		ckSize;
	uint32_t		FileSize = 0;
	uint8_t*	    buffer;
    uint16_t        channelNo = 1;

	FILE* in = fopen(cpath1, "rb");
	if (in != 0L) {
		FILE* out = fopen(cpath2, "wb");
		if (out != 0L) {

			fread(&ckID, sizeof(uint32_t), 1, in);
			if (ckID == RIFF_CHUNK_ID) {

				fwrite(&ckID, sizeof(uint32_t), 1, out);

				fread(&FileSize, sizeof(uint32_t), 1, in);
				FileSize = 0;									// need to fill in latter
				fwrite(&FileSize, sizeof(uint32_t), 1, out);

				fread(&ckID, sizeof(uint32_t), 1, in);
				if (ckID == WAV_CHUNK_ID) {

					fwrite(&ckID, sizeof(uint32_t), 1, out);

					while (fread(&ckID, sizeof(uint32_t), 1, in) == 1) {

						if (ckID == FMT_CHUNK_ID) {

							fwrite(&ckID, sizeof(uint32_t), 1, out);

							fread(&ckSize, sizeof(uint32_t), 1, in);
							fwrite(&ckSize, sizeof(uint32_t), 1, out);
							int32_t fileOffset = ftell(in);

                            int16_t sparam;
                            int32_t lparam;
                            fread(&sparam, sizeof(uint16_t), 1, in);				// audio format
 							fwrite(&sparam, sizeof(uint16_t), 1, out);

                            fread(&channelNo, sizeof(uint16_t), 1, in);		// number of channels
                            sparam = 1;
  							fwrite(&sparam, sizeof(uint16_t), 1, out);

                            fread(&lparam, sizeof(uint32_t), 1, in);			// sample rate
   							fwrite(&lparam, sizeof(uint32_t), 1, out);

                            fread(&lparam, sizeof(uint32_t), 1, in);			// data rate
    						fwrite(&lparam, sizeof(uint32_t), 1, out);

                            fread(&sparam, sizeof(uint16_t), 1, in);			// block align
  							fwrite(&sparam, sizeof(uint16_t), 1, out);

                            fread(&sparam, sizeof(uint16_t), 1, in);		// bits per sample
  							fwrite(&sparam, sizeof(uint16_t), 1, out);

							if (fseek(in, fileOffset + ckSize, SEEK_SET)) break;
						}
						else if (ckID == DATA_CHUNK_ID) {

							fwrite(&ckID, sizeof(uint32_t), 1, out);
							fread(&ckSize, sizeof(uint32_t), 1, in);

							uint32_t byteCount = len * sizeof(uint16_t);
							fwrite(&byteCount, sizeof(uint32_t), 1, out);
							int32_t fileOffset = ftell(in);

							if (fseek(in, from * sizeof(uint16_t) * channelNo, SEEK_CUR) == 0) {
								buffer = (uint8_t*) malloc(byteCount * channelNo);
								fread(buffer, 1, byteCount * channelNo, in);
								if (channelNo == 2) {
								    uint16_t* sp = (uint16_t*) buffer;
								    uint16_t* sp2 = sp + (channel-1);
								    uint16_t* last = sp + len;
								    while (sp < last) {
								        *sp = *sp2;
								        sp++;
								        sp2 += 2;
								    }
								}
								fwrite(buffer, 1, byteCount, out);
								free(buffer);
							}

							FileSize = ftell(out);
							if (fseek(in, fileOffset + ckSize, SEEK_SET)) break;
						}
						else if ((ckID == XMP_CHUNK_ID) || (ckID == GUANO_CHUNK_ID) || (ckID == WAMD_CHUNK_ID)) {

                            fwrite(&ckID, sizeof(uint32_t), 1, out);

                            fread(&ckSize, sizeof(uint32_t), 1, in);
                            fwrite(&ckSize, sizeof(uint32_t), 1, out);
                            int32_t fileOffset = ftell(in);

                            buffer = (uint8_t*) malloc(ckSize);
                            fread(buffer, 1, ckSize, in);
                            fwrite(buffer, 1, ckSize, out);
                            free(buffer);

                            if (fseek(in, fileOffset + ckSize, SEEK_SET)) break;
                        }
                        else {
                            fread(&ckSize, sizeof(uint32_t), 1, in);
                            if (fseek(in, ckSize, SEEK_CUR)) break;
                        }
					}
				}
			}
			fseek(out, sizeof(uint32_t), SEEK_SET);
			int32_t byteSize = FileSize - EIGHT_BYTES;
			fwrite(&byteSize, sizeof(uint32_t), 1, out);

			fseek(out, WRITE_HEADER_SIZE - 4, SEEK_SET);
			byteSize = FileSize - WRITE_HEADER_SIZE;
			fwrite(&byteSize, sizeof(uint32_t), 1, out);

			fclose(out);
		}
		fclose(in);
	}
	(*env)->ReleaseStringUTFChars(env, path2, cpath2);
	(*env)->ReleaseStringUTFChars(env, path1, cpath1);
}

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_metadata_MetaDataParser_updateMetadata(JNIEnv *env, jclass clazz, jstring path, int ns, jbyteArray metadata)
{
	int metaFound = 0;

	int32_t	ckID;
	int32_t ckSize;

	const char *cpath = (*env)->GetStringUTFChars(env, path, 0);
	FILE* in = fopen(cpath, "rb+");
	if (in != 0L) {

		fread(&ckID, sizeof(int32_t), 1, in);
		if (ckID == RIFF_CHUNK_ID) {

			fseek(in, sizeof(int32_t), SEEK_CUR);
			fread(&ckID, sizeof(int32_t), 1, in);
			if (ckID == WAV_CHUNK_ID) {

				while (fread(&ckID, sizeof(int32_t), 1, in) == 1) {
					if (ckID == FMT_CHUNK_ID) {
						fread(&ckSize, sizeof(int32_t), 1, in);					// chunk size
						int32_t fileOffset = ftell(in);
						if (fseek(in, fileOffset + ckSize, SEEK_SET)) break;
					}
					else if (ckID == DATA_CHUNK_ID) {
						fread(&ckSize, sizeof(int32_t), 1, in);
						if (fseek(in, ckSize, SEEK_CUR)) break;
					}
					else if ((ckID == XMP_CHUNK_ID) || (ckID == GUANO_CHUNK_ID) || (ckID == WAMD_CHUNK_ID)) {

                        if (ckID != ns) {
						    fseek(in, -sizeof(int32_t), SEEK_CUR);
						    fwrite(&ns, sizeof(int32_t), 1, in);
                        }

						jbyte *bytes = (*env)->GetByteArrayElements(env, metadata, 0);
						ckSize = (*env)->GetArrayLength(env, metadata);
						// Important - need to do the explicit fseeks here to switch from read to write mode
						fseek(in, 0, SEEK_CUR);
						fwrite(&ckSize, sizeof(int32_t), 1, in);
						fwrite(bytes, 1, ckSize, in);
						fflush(in);

						// IMPORTANT - This logic assumes metadata chunk is always at the end!
						int32_t fileSize = ftell(in) - EIGHT_BYTES;

                        // Need to update overall file size
                        fseek(in, sizeof(int32_t), SEEK_SET);
                        fwrite(&fileSize, sizeof(int32_t), 1, in);
						ftruncate(fileno(in), fileSize + EIGHT_BYTES);

						(*env)->ReleaseByteArrayElements(env, metadata, bytes, 0);
						metaFound = 1;
						break;
					}
					else {
						fread(&ckSize, sizeof(int32_t), 1, in);
						if (fseek(in, ckSize, SEEK_CUR)) break;
					}
				}
			}
		}
		if (metaFound == 0) {
			jbyte *bytes = (*env)->GetByteArrayElements(env, metadata, 0);
			ckSize = (*env)->GetArrayLength(env, metadata);
			// Important - need to do the explicit fseeks here to switch from read to write mode
			fseek(in, 0, SEEK_CUR);
			ckID = ns;
			fwrite(&ckID, sizeof(int32_t), 1, in);
			fwrite(&ckSize, sizeof(int32_t), 1, in);
			fwrite(bytes, 1, ckSize, in);
			fflush(in);

			// IMPORTANT - This logic assumes XMP chunk is always at the end!
			int32_t fileSize = ftell(in) - EIGHT_BYTES;

            // Need to update overall file size
            fseek(in, sizeof(int32_t), SEEK_SET);
            fwrite(&fileSize, sizeof(int32_t), 1, in);
			ftruncate(fileno(in), fileSize + EIGHT_BYTES);

			(*env)->ReleaseByteArrayElements(env, metadata, bytes, 0);
		}
		fclose(in);
	}
	(*env)->ReleaseStringUTFChars(env, path, cpath);
}

JNIEXPORT jbyteArray JNICALL Java_com_digitalbiology_audio_metadata_MetaDataParser_readMetadata(JNIEnv *env, jclass clazz, jstring path, jint ns)
{
	jbyteArray result = 0L;

	int32_t	ckID;
	int32_t ckSize;

	const char *cpath = (*env)->GetStringUTFChars(env, path, 0);
	FILE* in = fopen(cpath, "rb");
	if (in != 0L) {

		fread(&ckID, sizeof(int32_t), 1, in);
		if (ckID == RIFF_CHUNK_ID) {

			fseek(in, sizeof(int32_t), SEEK_CUR);
			fread(&ckID, sizeof(int32_t), 1, in);
			if (ckID == WAV_CHUNK_ID) {

				while (fread(&ckID, sizeof(int32_t), 1, in) == 1) {
					if (ckID == FMT_CHUNK_ID) {
						fread(&ckSize, sizeof(int32_t), 1, in);					// chunk size
						int32_t fileOffset = ftell(in);
						if (fseek(in, fileOffset + ckSize, SEEK_SET)) break;
					}
					else if (ckID == DATA_CHUNK_ID) {
						fread(&ckSize, sizeof(int32_t), 1, in);
						if (fseek(in, ckSize, SEEK_CUR)) break;
					}
					else if (ckID == ns) {

						fread(&ckSize, sizeof(int32_t), 1, in);

						jbyte *buffer =(jbyte*) malloc(ckSize + 1);
						fread(buffer, 1, ckSize, in);
						buffer[ckSize] = 0;
						result = (*env)->NewByteArray(env, ckSize);
						(*env)->SetByteArrayRegion(env, result, 0, ckSize, buffer);
						free(buffer);
						break;
					}
					else {
						fread(&ckSize, sizeof(int32_t), 1, in);
						if (fseek(in, ckSize, SEEK_CUR)) break;
					}
				}
			}
		}
		fclose(in);
	}
	(*env)->ReleaseStringUTFChars(env, path, cpath);
	return result;
}

JNIEXPORT jint JNICALL Java_com_digitalbiology_audio_metadata_MetaDataParser_getMetadataNamespace(JNIEnv *env, jclass clazz, jstring path)
{
	jint result = 0L;

	int32_t	ckID;
	int32_t ckSize;

	const char *cpath = (*env)->GetStringUTFChars(env, path, 0);
	FILE* in = fopen(cpath, "rb");
	if (in != 0L) {

		fread(&ckID, sizeof(int32_t), 1, in);
		if (ckID == RIFF_CHUNK_ID) {

			fseek(in, sizeof(int32_t), SEEK_CUR);
			fread(&ckID, sizeof(int32_t), 1, in);
			if (ckID == WAV_CHUNK_ID) {

				while (fread(&ckID, sizeof(int32_t), 1, in) == 1) {
					if (ckID == FMT_CHUNK_ID) {
						fread(&ckSize, sizeof(int32_t), 1, in);					// chunk size
						int32_t fileOffset = ftell(in);
						if (fseek(in, fileOffset + ckSize, SEEK_SET)) break;
					}
					else if (ckID == DATA_CHUNK_ID) {
						fread(&ckSize, sizeof(int32_t), 1, in);
						if (fseek(in, ckSize, SEEK_CUR)) break;
					}
					else if ((ckID == XMP_CHUNK_ID) || (ckID == GUANO_CHUNK_ID) || (ckID == WAMD_CHUNK_ID)) {
						result = ckID;
						break;
					}
					else {
						fread(&ckSize, sizeof(int32_t), 1, in);
						if (fseek(in, ckSize, SEEK_CUR)) break;
					}
				}
			}
		}
		fclose(in);
	}
	(*env)->ReleaseStringUTFChars(env, path, cpath);
	return result;
}

#define TEMP_BUFFER_SIZE 1024

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_ExportWAVTask_exportTimeExpandedWAV(JNIEnv *env, jclass clazz, jstring inpath, jstring outpath, jint sampleRate)
{
	const char *ip = (*env)->GetStringUTFChars(env, inpath, 0);
	const char *op = (*env)->GetStringUTFChars(env, outpath, 0);

	uint8_t* buffer = malloc(TEMP_BUFFER_SIZE);

	FILE* in = fopen(ip, "rb");
	if (in != 0L) {
		FILE* out = fopen(op, "wb+");
		if (out != 0L) {
			int bytesRead;
			while ((bytesRead = fread(buffer, 1, TEMP_BUFFER_SIZE, in)) > 0) {
				fwrite(buffer, 1, bytesRead, out);
			}
			if (fseek(out, 24L, SEEK_SET) == 0) {        // byte offset to sampling rate
				int32_t sr = sampleRate;
				fwrite(&sr, sizeof(int32_t), 1, out);
			}
			fclose(out);
		}
		fclose(in);
	}

	free(buffer);

	(*env)->ReleaseStringUTFChars(env, outpath, op);
	(*env)->ReleaseStringUTFChars(env, inpath, ip);
}

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_ExportWAVTask_exportWAVHeader(JNIEnv *env, jclass clazz, jstring inpath, jstring outpath, jint sampleRate)
{
	const char *ip = (*env)->GetStringUTFChars(env, inpath, 0);
	const char *op = (*env)->GetStringUTFChars(env, outpath, 0);

	uint8_t* buffer = malloc(WRITE_HEADER_SIZE);

	FILE* in = fopen(ip, "rb");
	if (in != 0L) {
		FILE* out = fopen(op, "wb+");
		if (out != 0L) {
			int bytesRead = fread(buffer, 1, WRITE_HEADER_SIZE, in);
			if (bytesRead > 0) {
				fwrite(buffer, 1, bytesRead, out);
			}
			if (fseek(out, 24L, SEEK_SET) == 0) {        // byte offset to sampling rate
				int32_t sr = sampleRate;
				fwrite(&sr, sizeof(int32_t), 1, out);
			}
			fclose(out);
		}
		fclose(in);
	}

	free(buffer);

	(*env)->ReleaseStringUTFChars(env, outpath, op);
	(*env)->ReleaseStringUTFChars(env, inpath, ip);
}

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_ExportWAVTask_exportWAVData(JNIEnv *env, jclass clazz, jstring outpath, jbyteArray array, jint byteCount)
{
	jbyte *bytes = (*env)->GetByteArrayElements(env, array, 0);
	const char *op = (*env)->GetStringUTFChars(env, outpath, 0);
	FILE* out = fopen(op, "ab");
	if (out != 0L) {
		fwrite(bytes, 1, byteCount, out);
		fclose(out);
	}
	(*env)->ReleaseStringUTFChars(env, outpath, op);
	(*env)->ReleaseByteArrayElements(env, array, bytes, 0);
}

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_ExportWAVTask_exportWAVFinalize(JNIEnv *env, jclass clazz, jstring outpath)
{
	const char *op = (*env)->GetStringUTFChars(env, outpath, 0);
	FILE* out = fopen(op, "rb+");
	if (out != 0L) {
	    // Update the file size
		fseek(out, 0L, SEEK_END);
		int32_t fileSize = ftell(out);

		fseek(out, sizeof(int32_t), SEEK_SET);
		int32_t byteSize = fileSize - EIGHT_BYTES;
		fwrite(&byteSize, sizeof(int32_t), 1, out);

		fseek(out, WRITE_HEADER_SIZE - 4, SEEK_SET);
		byteSize = fileSize - WRITE_HEADER_SIZE;
		fwrite(&byteSize, sizeof(int32_t), 1, out);

        // Make sure the channel count is set to 1!
		fseek(out, CHANNEL_BYTE_OFFSET, SEEK_SET);
		int16_t channelNo = 1;
		fwrite(&channelNo, sizeof(int16_t), 1, out);

		fclose(out);
	}
	(*env)->ReleaseStringUTFChars(env, outpath, op);
}

FILE*   wpFile = NULL;

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_MainActivity_openWaypoint(JNIEnv *env, jclass clazz, jstring outpath)
{
	const char *op = (*env)->GetStringUTFChars(env, outpath, 0);
	wpFile = fopen(op, "a+b");
	(*env)->ReleaseStringUTFChars(env, outpath, op);
}

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_MainActivity_writeWaypoint(JNIEnv *env, jclass clazz, jlong timestamp, jdouble lat, jdouble lon, jdouble ele)
{
    uint8_t b1[8];
    uint8_t b2[8];
	if (wpFile != NULL) {

        memcpy(b1, &timestamp, 8);
        b2[7] = b1[0];
        b2[6] = b1[1];
        b2[5] = b1[2];
        b2[4] = b1[3];
        b2[3] = b1[4];
        b2[2] = b1[5];
        b2[1] = b1[6];
        b2[0] = b1[7];
		fwrite(b2, 1, 8, wpFile);

        memcpy(b1, &lat, 8);
        b2[7] = b1[0];
        b2[6] = b1[1];
        b2[5] = b1[2];
        b2[4] = b1[3];
        b2[3] = b1[4];
        b2[2] = b1[5];
        b2[1] = b1[6];
        b2[0] = b1[7];
		fwrite(b2, 1, 8, wpFile);

        memcpy(b1, &lon, 8);
        b2[7] = b1[0];
        b2[6] = b1[1];
        b2[5] = b1[2];
        b2[4] = b1[3];
        b2[3] = b1[4];
        b2[2] = b1[5];
        b2[1] = b1[6];
        b2[0] = b1[7];
		fwrite(b2, 1, 8, wpFile);

        memcpy(b1, &ele, 8);
        b2[7] = b1[0];
        b2[6] = b1[1];
        b2[5] = b1[2];
        b2[4] = b1[3];
        b2[3] = b1[4];
        b2[2] = b1[5];
        b2[1] = b1[6];
        b2[0] = b1[7];
		fwrite(b2, 1, 8, wpFile);

		fflush(wpFile);
	}
}

JNIEXPORT void JNICALL Java_com_digitalbiology_audio_MainActivity_closeWaypoint(JNIEnv *env, jclass clazz)
{
	if (wpFile != NULL) {
	    fclose(wpFile);
	    wpFile = NULL;
	}
}
