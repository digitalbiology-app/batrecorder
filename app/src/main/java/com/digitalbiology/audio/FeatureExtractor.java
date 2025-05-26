package com.digitalbiology.audio;

import android.graphics.Rect;

public class FeatureExtractor {

    public static Rect[] extractFeatures(int stride, int x_min, int x_max) {

        Rect[] signals = null;
        int[] dim = detect(stride, x_min, x_max);
        if (dim != null && dim.length > 0) {
            int count = dim.length / 4;
            signals = new Rect[count];
            int jj = 0;
            for (int ii = 0; ii < count; ++ii) {
                signals[ii] = new Rect();
                signals[ii].left = dim[jj++] + x_min;
                signals[ii].right = dim[jj++] + x_min;
                signals[ii].top = dim[jj++];
                signals[ii].bottom = dim[jj++];
            }
        }
        return signals;
    }

    private static native int[] detect(int stride, int x0, int x1);
}
