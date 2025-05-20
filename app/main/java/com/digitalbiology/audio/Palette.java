package com.digitalbiology.audio;

import android.graphics.Bitmap;

public class Palette {

	public static final int PALETTE_INVALID = -1;
	public static final int PALETTE_RAINBOW = 0;
	public static final int PALETTE_IRON = 1;
	public static final int PALETTE_ARCTIC = 2;
	public static final int PALETTE_WHITE_HOT = 3;
	public static final int PALETTE_RED = 4;
	public static final int NUM_PALETTES = 5;
	
	public static final int PALETTE_SIZE = 256;
	private static final int MAX_STEPS = 8;
	
	private static int type = PALETTE_INVALID;
	
	private static final int[] colorsARGB = new int[PALETTE_SIZE];
	private static final int[] colorsLinearRGBA = new int[PALETTE_SIZE];
	private static final int[] values = new int[MAX_STEPS];
	private static final int[] steps = new int[MAX_STEPS];
	private static final float[] coefficients = new float[NUM_PALETTES];

	public static int getType() {
		return type;
	}
	
	public static void setType(int tp) {
		type = tp;
		refresh();
	}
	
	public static float getCoefficient(int index) {
		return coefficients[index];
	}
	
	public static void setCoefficient(float coef, int index) {
		coefficients[index] = Math.max(Math.min(5.0f, coef), 0.2f);
		refresh();
	}

	private static void refresh() {
		if (type == PALETTE_WHITE_HOT)
			grayscale(false);
//		else if (type == PALETTE_BLACK_HOT)
//			grayscale(true);
		else if (type == PALETTE_RAINBOW)
			rainbow();
		else if (type == PALETTE_IRON)
			iron();
		else if (type == PALETTE_ARCTIC)
			arctic();
		else if (type == PALETTE_RED)
			red();
	}

	public static int[] getLinearColors() {
		return colorsLinearRGBA;
	}
	
	public static int[] getColorsARGB() {
		return colorsARGB;
	}
	
	private static void rainbow() {
		
		type = PALETTE_RAINBOW;
		
		values[0] = 0xFF000000;	// black
		values[1] = 0xFFFF0000;	// blue
		values[2] = 0xFFFFFF00;	// cyan
		values[3] = 0xFF00FFFF;	// yellow
		values[4] = 0xFF0000FF;	// red
		values[5] = 0xFFFFFFFF;	// white

//		values[0] = 0xFFFFFFFF;	// white
//		values[1] = 0xFF0000FF;	// red
//		values[2] = 0xFF00FFFF;	// yellow
//		values[3] = 0xFFFFFF00;	// cyan
//		values[4] = 0xFFFF0000;	// blue
//		values[5] = 0xFF000000;	// black

		steps[0] = 0;
		steps[1] = 52;
		steps[2] = 104;
		steps[3] = 156;
		steps[4] = 208;
		steps[5] = 256;
		
		interpRGB(colorsLinearRGBA, values, steps, 6, coefficients[type]);
		RGBA2ARGB(colorsLinearRGBA, colorsARGB);
	}

	private static void iron() {
		
		type = PALETTE_IRON;
		
		values[0] = 0xFF000000;	// black
		values[1] = 0xFF960045;	// purple
		values[2] = 0xFF8513CA;
		values[3] = 0xFF026AF1;	// orange
		values[4] = 0xFF00FFFF;	// yellow
		values[5] = 0xFFFFFFFF;	// white
		
		steps[0] = 0;
		steps[1] = 52;
		steps[2] = 104;
		steps[3] = 156;
		steps[4] = 220;
		steps[5] = 256;
		
		interpRGB(colorsLinearRGBA, values, steps, 6, coefficients[type]);
		RGBA2ARGB(colorsLinearRGBA, colorsARGB);
	}

	private static void arctic() {
		
		type = PALETTE_ARCTIC;
		
		values[0] = 0xFF000000;	// black
		values[1] = 0xFFFF0000;	// blue
		values[2] = 0xFFFFFF00;	// cyan
		values[3] = 0xFF026AF1;	// orange
		values[4] = 0xFF00FFFF;	// yellow
		values[5] = 0xFFFFFFFF;	// white
		
		steps[0] = 0;
		steps[1] = 32;
		steps[2] = 80;
		steps[3] = 148;
		steps[4] = 220;
		steps[5] = 256;
		
		interpRGB(colorsLinearRGBA, values, steps, 6, coefficients[type]);
		RGBA2ARGB(colorsLinearRGBA, colorsARGB);
	}

	private static void grayscale(boolean inverse) {
		
//		if (inverse) {
//			type = PALETTE_BLACK_HOT;
//			values[0] = 0xFFFFFFFF;	// white
//			values[1] = 0xFFC0C0C0; 
//			values[2] = 0xFF808080;
//			values[3] = 0xFF404040;
//			values[4] = 0xFF000000;	// black
//		}
//		else {
			type = PALETTE_WHITE_HOT;
			values[0] = 0xFF000000;	// black
			values[1] = 0xFF404040;
			values[2] = 0xFF808080;
			values[3] = 0xFFC0C0C0;
			values[4] = 0xFFFFFFFF;	// white
//		}
		
		steps[0] = 0;
		steps[1] = 64;
		steps[2] = 128;
		steps[3] = 192;
		steps[4] = 256;
		
		interpRGB(colorsLinearRGBA, values, steps, 5, coefficients[type]);
		RGBA2ARGB(colorsLinearRGBA, colorsARGB);
	}

	private static void red() {

		type = PALETTE_RED;
		values[0] = 0xFF000000;
		values[1] = 0xFF000044;
		values[2] = 0xFF000088;
		values[3] = 0xFF0000CC;
		values[4] = 0xFF0000FF;

		steps[0] = 0;
		steps[1] = 64;
		steps[2] = 128;
		steps[3] = 192;
		steps[4] = 256;

		interpRGB(colorsLinearRGBA, values, steps, 5, coefficients[type]);
		RGBA2ARGB(colorsLinearRGBA, colorsARGB);
	}

	private static native void interpRGB(int[] ramp, int[] colors, int[] steps, int len, float coef);
    private static native void RGBA2ARGB(int[] rgba, int[] argb);
    public static native void buildRamp(Bitmap bitmap, int[] colors);
    
    static {
        System.loadLibrary("palette");
        for (int ii = 0; ii < NUM_PALETTES; ii++) coefficients[ii] = 1.0f;
    }
}
