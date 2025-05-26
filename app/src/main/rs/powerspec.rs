#pragma version(1)
#pragma rs java_package_name(com.digitalbiology.audio)

uchar4* 	colors;
uint8_t* 	data;
float* 	    mx;
int32_t*    bin;
uint32_t	width;
float       fqmax;
float       logfqmax;

void linearscale(uchar4 *out, uint32_t x, uint32_t y)
{
    if (data[x] > bin[0]) {
        bin[0] = data[x];
        bin[1] = (int32_t) x;
    }

	y = 255 - y;
	if (y > (uint8_t) mx[x]) {
	    if (y > data[x])
		    *out = rsPackColorTo8888(0.0f, 0.0f, 0.0f, 0.9f);
	    else {
	        if (y < 52) y = 52;
	        *out = colors[y];
	    }
	}
	else {
	    if (y > data[x])
	        *out = rsPackColorTo8888(0.2f, 0.2f, 0.2f, 0.9f);
	    else {
	        if (y < 52) y = 52;
	        *out = colors[y];
	    }
	}
}

void logscale(uchar4 *out, uint32_t x, uint32_t y)
{
     float fq = exp10(2.0f + (logfqmax - 2.0f) * (float) x / (float) width);
     uint8_t data_x = data[(int) ((float) width * fq / fqmax)];
    if (data_x > bin[0]) {
        bin[0] = data_x;
        bin[1] = (int32_t) x;
    }
	y = 255 - y;
	if (y > (uint8_t) mx[x]) {
	    if (y > data[x])
		    *out = rsPackColorTo8888(0.0f, 0.0f, 0.0f, 0.9f);
	    else {
	        if (y < 52) y = 52;
	        *out = colors[y];
	    }
	}
	else {
	    if (y > data[x])
	        *out = rsPackColorTo8888(0.2f, 0.2f, 0.2f, 0.9f);
	    else {
	        if (y < 52) y = 52;
	        *out = colors[y];
	    }
	}
}