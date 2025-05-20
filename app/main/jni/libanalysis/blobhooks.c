#include <jni.h>
#include <math.h>
#include <android/log.h>
#include "quickblob.h"
#include "analysis.h"

#ifdef __ANDROID__
#define STRINGIFY(x) #x
#define LOG_TAG    "blobhooks"
#define LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)
#endif

typedef struct {
    int32_t     x1;
    int32_t     x2;
    int32_t     y1;
    int32_t     y2;
    uint8_t     valid;
} Blob;

//#define MIN_BLOB_AREA               60
#define MIN_BLOB_AREA       30
//#define NOISE_THRESHOLD     140

//#define OVERALL_THRESHOLD           0.8f
#define NOISE_REMOVAL_THRESHOLD     0.4f

#define BIN_COUNT           256
#define BLOB_SLOT_ALLOC     40

struct misc
{
    FILE*       file;
    uint32_t    offset;
    uint32_t    stride;
    uint8_t*    buffer;
    uint32_t    w;
    uint32_t    h;
    uint32_t    frame;
    Blob        *blobs;
    uint16_t    blob_slots;
    uint16_t    blob_count;
    uint16_t    blob_valid_count;
    uint8_t     blobs_valid;
//    float       threshold;
    uint16_t    min_area;
};

Blob*       master_list = NULL;
uint16_t    master_count = 0;
uint16_t    master_slots = 0;

void insert_master(Blob* blob) {
    if (++master_count > master_slots) {
        master_slots += BLOB_SLOT_ALLOC;
        if (master_list == 0L)
            master_list = (Blob*) malloc(sizeof(Blob) * master_slots);
        else
            master_list = (Blob*) realloc(master_list, sizeof(Blob) * master_slots);
    }
    int ii = master_count-1;
    master_list[ii].x1 = blob->x1;
    master_list[ii].x2 = blob->x2;
    master_list[ii].y1 = blob->y1;
    master_list[ii].y2 = blob->y2;
}

void log_blob_hook(void* user_struct, struct blob* b) {

    struct misc* options = user_struct;

    if ((options->blobs_valid == 0) || (b->color == 0)) {
//        LOGI("COLD SPOT [ %d %d %d %d ]", b->bb_x1, b->bb_x2, b->bb_y1, b->bb_y2);
        return;       // cold spot
    }

    int w = b->bb_x2 - b->bb_x1;
    int h = b->bb_y2 - b->bb_y1;
     if (w >= (options->w-1) && h >= (options->h-1)) {
//         LOGI("TOO LARGE [ %d %d %d %d ]", b->bb_x1, b->bb_x2, b->bb_y1, b->bb_y2);
         return;
     }

    if (w*h < options->min_area) {
//       LOGI("TOO SMALL [ %d %d %d %d ]", b->bb_x1, b->bb_x2, b->bb_y1, b->bb_y2);
//        options->blobs_valid = 0;
        return;
   }

    if (++options->blob_count > options->blob_slots) {
        options->blob_slots += BLOB_SLOT_ALLOC;
        if (options->blobs == 0L)
            options->blobs = (Blob*) malloc(sizeof(Blob) * options->blob_slots);
        else
            options->blobs = (Blob*) realloc(options->blobs, sizeof(Blob) * options->blob_slots);
    }
    int ii = options->blob_count-1;
    options->blobs[ii].x1 = b->bb_x1;
    options->blobs[ii].x2 = b->bb_x2;
    options->blobs[ii].y1 = b->bb_y1;
    options->blobs[ii].y2 = b->bb_y2;
    options->blobs[ii].valid = 1;

//    LOGI("VALID [ %d %d %d %d ]", b->bb_x1, b->bb_x2, b->bb_y1, b->bb_y2);
}

int init_pixel_stream_hook(void* user_struct, struct stream_state* stream)
{
    struct misc* options = user_struct;
    stream->w = options->w;
    stream->h = options->h;
    return 0;
}

int close_pixel_stream_hook(void* user_struct, struct stream_state* stream)
{
    return 0;
}

int next_row_hook(void* user_struct, struct stream_state* stream)
{
    // load the (grayscale) row at stream->y into the (8 bit) stream->row array
    struct misc* options = user_struct;
//    fseek(options->file, options->offset, SEEK_SET);
    fread(options->buffer, 1, stream->w, options->file);
//    options->offset += options->stride;
    //    int* ip = options->data + (stream->y * stream->w);
    memcpy(stream->row, options->buffer, stream->w);
/*    uint8_t* ip = options->buffer;
    uint8_t* s = stream->row;
    uint8_t* last = s + stream->w;
    while (s < last) {
        *s++ = (*ip++ > 0) ? 255 : 0;
    }
 */   return 0;
}

int next_frame_hook(void* user_struct, struct stream_state* stream)
{
    // single-image application, so this is a no-op
    struct misc* options = user_struct;
    options->frame++;
    return options->frame;
}

void combine_blobs(struct misc* options) {

    Blob* bb = options->blobs;
    for (int jj = 0; jj < options->blob_count; ++jj) {
       if (bb[jj].valid == 1) {
           for (int ii = jj+1; ii < options->blob_count; ++ii) {
                if (bb[ii].valid == 1) {
                   if ((bb[ii].y1 <= bb[jj].y2) && (bb[ii].y2 >= bb[jj].y1)) {
                        if ((bb[ii].x1 <= bb[jj].x2) && (bb[ii].x2 >= bb[jj].x1)) {

                            // There is overlap!
                            if (bb[ii].y1 < bb[jj].y1) bb[jj].y1 = bb[ii].y1;
                            if (bb[ii].y2 > bb[jj].y2) bb[jj].y2 = bb[ii].y2;
                            if (bb[ii].x1 < bb[jj].x1) bb[jj].x1 = bb[ii].x1;
                            if (bb[ii].x2 > bb[jj].x2) bb[jj].x2 = bb[ii].x2;

                            bb[ii].valid = 0;
                            options->blob_valid_count--;
                            jj = -1;
                            break;
                      }
                   }
                   else if ((bb[jj].y1 <= bb[ii].y2) && (bb[jj].y2 >= bb[ii].y1)) {
                        if ((bb[jj].x1 <= bb[ii].x2) && (bb[jj].x2 >= bb[ii].x1)) {

                            // There is overlap!
                            if (bb[ii].y1 < bb[jj].y1) bb[jj].y1 = bb[ii].y1;
                            if (bb[ii].y2 > bb[jj].y2) bb[jj].y2 = bb[ii].y2;
                            if (bb[ii].x1 < bb[jj].x1) bb[jj].x1 = bb[ii].x1;
                            if (bb[ii].x2 > bb[jj].x2) bb[jj].x2 = bb[ii].x2;

                            bb[ii].valid = 0;
                            options->blob_valid_count--;
                            jj = -1;
                            break;
                      }
                   }
//                   if (((bb[ii].y1 >= bb[jj].y1) && (bb[ii].y2 <= bb[jj].y2))
//                    && ((bb[ii].x1 >= bb[jj].x1) && (bb[ii].x2 <= bb[jj].x2))) {
//                        bb[ii].valid = 0;
//                        blobs.valid_count--;
//                    }
//                    else if (((bb[jj].y1 >= bb[ii].y1) && (bb[jj].y2 <= bb[ii].y2))
//                        && ((bb[jj].x1 >= bb[ii].x1) && (bb[jj].x2 <= bb[ii].x2))) {
//                        bb[jj].valid = 0;
//                        blobs.valid_count--;
//                        break;
//                    }
                }
            }
       }
    }
}

void remove_background(struct misc *options, const char* out_path) {

    uint32_t* rowbins = (uint32_t*) malloc(options->w * BIN_COUNT * sizeof(uint32_t));
    memset(rowbins, 0, options->w * BIN_COUNT * sizeof(uint32_t));

    uint8_t *buffer = (uint8_t*) malloc(options->stride);

    // FIRST PASS - calculate median background for each frequency band...
    FILE *in = fopen(cachePath, "rb");
    if (in != 0L) {
        fseek(in, options->offset, SEEK_SET);
        for (int ii = 0; ii < options->h; ii++) {
            if (fread(buffer, 1, options->stride, in) == options->stride) {
                uint8_t *bp = buffer;
                uint8_t *blast = buffer + options->w;
                uint32_t* bnp = rowbins;
                while (bp < blast) {
                    bnp[*bp++]++;
                    bnp += BIN_COUNT;
                }
            }
       }
        fclose(in);
    }

    uint8_t *thresholds = (uint8_t*) malloc(options->w);
    memset(thresholds, 255, options->w);
//    uint32_t threshold = options->h * options->threshold;
    uint32_t threshold = options->h * NOISE_REMOVAL_THRESHOLD;
    uint32_t running_total;
    for (int jj = 0; jj < options->w; ++jj) {
        uint32_t* bnp = rowbins + (jj * BIN_COUNT);
        running_total = 0;
        for (int ii = 0; ii < BIN_COUNT; ++ii) {
            running_total += bnp[ii];
            if (running_total > threshold) {
               thresholds[jj] = ii;
               break;
            }
        }
    }
    free(rowbins);

    // SECOND PASS - subtract background and build histogram, saving results to temporary file.
    in = fopen(cachePath, "rb");
    if (in != 0L) {
        FILE *out = fopen(out_path, "wb");
        if (out != 0L) {
            fseek(in, options->offset, SEEK_SET);
            while (fread(buffer, 1, options->stride, in) == options->stride) {
                uint8_t *bp = buffer;
                uint8_t *blast = buffer + options->w;
                uint8_t *tp = thresholds;
                while (bp < blast) {
                    if (*bp > *tp) {
                        if ((*bp - *tp) < *tp)
                            *bp = 0;
                         else
                            *bp = 255;
                    }
                    else
                        *bp = 0;
                    bp++;
                    tp++;
                }
                fwrite(buffer, 1, options->w, out);
            }
            fclose(out);
        }
        fclose(in);
    }

    free(thresholds);
    free(buffer);

//    rename(out_path, cachePath);        // DEBUG
}
/*
void remove_background2(struct misc *options, const char* out_path) {

//    uint8_t *buffer = (uint8_t*) malloc(options->w);
    uint8_t *buffer = (uint8_t*) malloc(options->stride);

    char tmp_path[256];
    strcpy(tmp_path, out_path);
    int len = strlen(tmp_path) - 3;
    tmp_path[len++] = 'a';
    tmp_path[len++] = 'l';
    tmp_path[len]   = '2';

    size_t bytesRead;
    uint8_t threshold = NOISE_THRESHOLD;
    // SECOND PASS - subtract background and build histogram, saving results to temporary file.
    FILE* in = fopen(cachePath, "rb");
    if (in != 0L) {
        FILE *out = fopen(tmp_path, "wb");
        if (out != 0L) {
//            uint32_t offset = options->offset;
//            fseek(in, offset, SEEK_SET);
            fseek(in, options->offset, SEEK_SET);
//            while ((bytesRead = fread(buffer, 1, options->w, in)) > 0) {
            while (fread(buffer, 1, options->stride, in) == options->stride) {
                uint8_t *bp = buffer;
//                uint8_t *blast = buffer + bytesRead;
                uint8_t *blast = buffer + options->w;
                while (bp < blast) {
                    if (*bp < threshold) *bp = 0;
                    bp++;
                }
//                offset += options->stride;
 //               fseek(in, offset, SEEK_SET);
                fwrite(buffer, 1, options->w, out);
            }
            fclose(out);
        }
        fclose(in);
    }

    uint8_t *buffer2 = (uint8_t*) malloc(options->w);
    uint8_t max;

    in = fopen(tmp_path, "rb");
    if (in != 0L) {
        FILE *out = fopen(out_path, "wb");
        if (out != 0L) {
            while ((bytesRead = fread(buffer2, 1, options->w, in)) > 0) {
                uint8_t *bp2 = buffer2;
                uint8_t *blast = buffer2 + (bytesRead-1);
                uint8_t *bp = buffer;
                max = *bp2;
                if (*(bp2+1) > max) max = *(bp2+1);
                *bp = max;
                bp2++;
                bp++;
                while (bp2 < blast) {
                    max = *bp2;
                    if (*(bp2-1) > max) max = *(bp2-1);
                    if (*(bp2+1) > max) max = *(bp2+1);
                    *bp = max;
                    bp2++;
                    bp++;
                }
                max = *bp2;
                if (*(bp2-1) > max) max = *(bp2-1);
                *bp = max;
                fwrite(buffer, 1, options->w, out);
            }
            fclose(out);
        }
        fclose(in);
        remove(tmp_path);
    }

    free(buffer2);
    free(buffer);

//     rename(out_path, cachePath);        // DEBUG
}
*/
void detect_blobs(struct misc *options, const char* path) {

    // Subtract background.
//    remove_background2(options, path);
    remove_background(options, path);

    options->blobs = NULL;
    options->blob_slots = 0;
    options->blob_count = 0;
    options->blob_valid_count = 0;
    options->blobs_valid = 1;

   FILE* file = fopen(path, "rb");
    if (file != 0L) {
        options->buffer = (uint8_t*) malloc(options->w);
        options->file = file;
        extract_image((void*) options);
        fclose(file);
        remove(path);
        free(options->buffer);
     }
}

uint8_t analyze_blob(Blob* blob, int offset, int stride, const char* path) {

    uint8_t valid = 1;
 //   insert_master(blob);

    struct misc options;
    options.frame = -1;
    // Need to reverse height and width
    options.h = blob->y2 - blob->y1;
    options.w = blob->x2 - blob->x1;
    options.offset = offset + blob->y1 * stride + blob->x1;
    options.stride = stride;
//    options.threshold = OVERALL_THRESHOLD;
    options.min_area = MIN_BLOB_AREA;
    detect_blobs(&options, path);
    combine_blobs(&options);
    for (int ii = 0; ii < options.blob_count; ii++) {
        Blob tmp = options.blobs[ii];
        if(tmp.valid == 1) {
            tmp.x1 += blob->x1;
            tmp.x2 += blob->x1;
            tmp.y1 += blob->y1;
            tmp.y2 += blob->y1;
            insert_master(&tmp);
         }
    }
    if (options.blobs != 0L) free(options.blobs);
    return valid;
}
/*
uint8_t analyze_blob(int x, int y, int w, int h, int stride, const char* path) {

    uint8_t valid = 0;

    uint32_t* rowbins = (uint32_t*) malloc(w * BIN_COUNT * sizeof(uint32_t));
    memset(rowbins, 0, w * BIN_COUNT * sizeof(uint32_t));

    uint32_t* bnp;
    uint8_t *buffer = (uint8_t*) malloc(w);
    FILE *in = fopen(path, "rb");
    if (in != 0L) {
        size_t bytesRead;
        uint32_t offset = y * w + x;
        fseek(in, offset, SEEK_SET);
        for (int ii = 0; ii < h; ii++) {
            if ((bytesRead = fread(buffer, 1, w, in)) > 0) {
                uint8_t *bp = buffer;
                uint8_t *blast = buffer + bytesRead;
                bnp = rowbins;
                while (bp < blast) {
                    bnp[*bp++]++;
                    bnp += BIN_COUNT;
                }
            }
            offset += stride;
            fseek(in, offset, SEEK_SET);
       }
        fclose(in);
    }
    free(buffer);

    uint32_t running_total = 0;
    uint32_t threshold = (h * w) * BLOB_THRESHOLD;
    bnp = rowbins;
    for (uint8_t ii = 0; ii < BIN_COUNT; ++ii) {
        running_total += bnp[ii];
        if (running_total > threshold) {
            valid = (ii > 128) ? 1 : 0;
            break;
        }
    }
    free(rowbins);
//    return valid;
    return 1;
}
*/
void analyze_blobs(struct misc *options, const char* path) {
    for (int jj = 0; jj < options->blob_count; ++jj) {
       if (options->blobs[jj].valid == 1) {
 //         insert_master(&(options->blobs[jj]));
          analyze_blob(&(options->blobs[jj]), options->offset, options->stride, path);
       }
    }
}

JNIEXPORT jintArray JNICALL Java_com_digitalbiology_audio_FeatureExtractor_detect(JNIEnv* env, jclass clazz, jint stride, jint x0, jint x1)
{
    jintArray array = NULL;

    char tmp_path[256];
    strcpy(tmp_path, cachePath);
    int len = strlen(tmp_path) - 3;
    tmp_path[len++] = 'a';
    tmp_path[len++] = 'n';
    tmp_path[len]   = 'a';

 //    filter(tmp_path2, tmp_path, height);

    // Blur the data.
//    blur(cachePath, tmp_path, height);

//    // Subtract background from blurred data.
//    subtract_background(tmp_path, height, thresholds);
//    free(thresholds);

    struct misc options;
    options.frame = -1;
/*
    FILE* fp = fopen(cachePath, "rb");
    if (fp != NULL) {
        fseek(fp, 0L, SEEK_END);
         options.h = ftell(fp) / stride;
        fclose(fp);
    }
    else
        options.h = 0;
*/
     // Need to reverse height and width
    options.h = x1 - x0;
    options.w = stride;
    options.offset = x0 * stride;
    options.stride = stride;
//    options.threshold = NOISE_REMOVAL_THRESHOLD;
    options.min_area = MIN_BLOB_AREA;
    detect_blobs(&options, tmp_path);

    if (options.blobs_valid == 1) {
        options.blob_valid_count = options.blob_count;
        combine_blobs(&options);
        analyze_blobs(&options, tmp_path);
    }
    if (options.blobs != 0L) free(options.blobs);

    if (master_count > 0) {
        array = (*env)->NewIntArray(env, 4*master_count);
        jint *ba = (*env)->GetIntArrayElements(env, array, NULL);
        int jj = 0;
        for (int ii = 0; ii < master_count; ii++) {
                ba[jj++] = master_list[ii].y1;       // left
                ba[jj++] = master_list[ii].y2;       // right
                ba[jj++] = master_list[ii].x1;       // top
                ba[jj++] = master_list[ii].x2;       // bottom
//                LOGI(":FeatureExtractor_detect L %d R %d T %d B %d", master_list[ii].y1, master_list[ii].y2, master_list[ii].x1, master_list[ii].x2);
        }
        (*env)->ReleaseIntArrayElements(env, array, ba, 0);
    }
    if (master_list != 0L) {
        free(master_list);
        master_list = 0L;
        master_count = 0;
        master_slots = 0;
    }
    return array;
}
