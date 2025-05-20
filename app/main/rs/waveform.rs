#pragma version(1)
#pragma rs java_package_name(com.digitalbiology.audio)

int16_t* 	data;
int8_t* 	state;
int8_t 	    night;
int32_t	    offset;
uint32_t	center;
int32_t	    playHead;
int32_t	    selStart;
int32_t	    selEnd;

void root(uchar4 *out, uint32_t x, uint32_t y)
{
	int32_t xo = x + offset;
	int32_t height = (center * data[x]) / 32768;
	if ((y >= (center - height)) && (y <= (center + height))) {
		if (xo >= selStart && xo < playHead) {
			if (night == 0)
			    *out = rsPackColorTo8888(1.0f, 1.0f, 0.0f, 1.0f);
			else
			    *out = rsPackColorTo8888(0.4f, 0.0f, 0.0f, 1.0f);
		}
		else if (state[x] == 1) {
			if (night == 0)
			    *out = rsPackColorTo8888(1.0f, 1.0f, 0.0f, 1.0f);
			else
			    *out = rsPackColorTo8888(0.4f, 0.0f, 0.0f, 1.0f);
		}
		else if (state[x] == 2)
			*out = rsPackColorTo8888(1.0f, 0.0f, 0.0f, 1.0f);
		else {
			if (night == 0)
			    *out = rsPackColorTo8888(0.4f, 0.4f, 0.4f);
			else
			    *out = rsPackColorTo8888(0.2f, 0.0f, 0.0f);
		}
	}
	else {
		if (xo >= selStart && xo < selEnd) {			// hilite selection if present
			if (night == 0)
			    *out = rsPackColorTo8888(0.4f, 0.4f, 0.4f, 1.0f);
			else
			    *out = rsPackColorTo8888(0.2f, 0.0f, 0.0f, 1.0f);
		}
		else
			*out = rsPackColorTo8888(0.0f, 0.0f, 0.0f, 1.0f);
	}
}

