#pragma version(1)
#pragma rs java_package_name(com.digitalbiology.audio)

uchar4* 	colors;
uchar* 		map;
uint32_t	offset;
uint32_t	width;
uint32_t	maplength;
float       fqmax;
float       logfqmax;

void linearscale(uchar4 *out, uint32_t x, uint32_t y)
{
//	uint32_t index = (start + y) * width + x;
//	uint32_t index = y * width + x;
	if ((y + offset) < maplength)
		*out = colors[map[y * width + x]];
	else
		*out = colors[0];
}

void logscale(uchar4 *out, uint32_t x, uint32_t y)
{
//	uint32_t index = (start + y) * width + x;
//	uint32_t index = y * width + x;
	if ((y + offset) < maplength) {
	    float fq = exp10(2.0f + (logfqmax - 2.0f) * (float) x / (float) width);
		*out = colors[map[y * width + (int) ((float) width * fq / fqmax)]];
	}
	else
		*out = colors[0];
}