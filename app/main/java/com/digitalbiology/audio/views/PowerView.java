package com.digitalbiology.audio.views;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import com.digitalbiology.audio.MainActivity;
import com.digitalbiology.audio.Palette;

import java.text.DecimalFormat;

public class PowerView extends View {

	private DecimalFormat mFreqFormat;
	private DecimalFormat mDBFormat;
	private Paint mLinePaint;
	private Rect mTextRect;

	private Bitmap	mBitmap;
	private byte[]	mDataBuffer;
	private float[]	mMaxBuffer;
	private int[] mBinBuffer;
	private Matrix mMatrix;
	private int mSampleSize;

	private FreqTickView mFreqTickView;

	public PowerView(Context context, AttributeSet attrs) {
		  super(context, attrs);
		  if(!isInEditMode()) init(context);
	 }

	 public PowerView(Context context, AttributeSet attrs, int defStyleAttr) {
		  super(context, attrs, defStyleAttr);
		  if(!isInEditMode()) init(context);
	 }
	 
	 private void init(Context context) {

		 mFreqFormat = new DecimalFormat("#0.00 kHz");
		 mDBFormat = new DecimalFormat("#0.00 dB");

		 mLinePaint = new Paint();
		 mLinePaint.setAntiAlias(true);
		 mLinePaint.setStrokeWidth(2.0f);
		 mLinePaint.setARGB(255, 255, 0, 0);
		 mLinePaint.setTextSize(18f * Resources.getSystem().getDisplayMetrics().density);

		 mTextRect = new Rect();

		 mBinBuffer = new int[2];
		 mSampleSize = 0;
	 }

	public void setSampleSize(int size) {

		if (size != mSampleSize) {
			mBitmap = Bitmap.createBitmap(size, Palette.PALETTE_SIZE, Bitmap.Config.ARGB_8888);

			mDataBuffer = new byte[size];
			mMaxBuffer = new float[size];

			mMatrix = new Matrix();
			mSampleSize = size;
		}
	}

	public void setFreqTickView(FreqTickView view) {
		mFreqTickView = view;
	}

	public void updateDataFromCacheFile(int offset, boolean reset) {
		readCache(mDataBuffer, mMaxBuffer, offset, mSampleSize, reset);
	}

	public void updateDataFromCacheBuffer(boolean reset) {
		copyCache(mDataBuffer, mMaxBuffer, reset);
	}

	 protected void onDraw(Canvas canvas) {
		   
		 if(isInEditMode() || (mSampleSize == 0)) return;

		 mMatrix.reset();
		 mMatrix.postScale((float) getWidth() / (float) mBitmap.getWidth(), (float) getHeight() / (float) mBitmap.getHeight());

		 float maxFreq = mFreqTickView.getMaxFreq();
		 if (mFreqTickView.getLogScale())
			 logSpectrum(mBitmap, mDataBuffer, mBinBuffer, mMaxBuffer, maxFreq, Palette.getLinearColors());
		 else
			 linearSpectrum(mBitmap, mDataBuffer, mBinBuffer, mMaxBuffer, Palette.getLinearColors());
		 canvas.drawBitmap(mBitmap, mMatrix, null);

		 String text;
		 if (mBinBuffer[0] >= 0) {
			 mLinePaint.setColor(Palette.getColorsARGB()[Math.max(mBinBuffer[0], 60)]);
			 float frequency;
			 if (mFreqTickView.getLogScale()) {
				 frequency = (mBinBuffer[1] + 0.5f) * ((float) Math.log10(maxFreq) - 2.0f) / (float) mSampleSize + 2.0f;
				 frequency = (float) Math.pow(10.0f, frequency) / 1000.0f;
			 }
			 else {
				 frequency = (mBinBuffer[1] + 0.5f) * maxFreq / ((float) mSampleSize * 1000.0f);
			 }
			 text = mFreqFormat.format(frequency);
			 mLinePaint.getTextBounds(text, 0, text.length(), mTextRect);
			 int ypos = mTextRect.height()+10;
			 canvas.drawText(text, getWidth() - mTextRect.width() - 10, ypos, mLinePaint);
			 float db = PaletteView.MIN_DB + (float) mBinBuffer[0] * (PaletteView.MAX_DB - PaletteView.MIN_DB) / 255;
			 text = mDBFormat.format(db);
			 mLinePaint.getTextBounds(text, 0, text.length(), mTextRect);
			 canvas.drawText(text, getWidth() - mTextRect.width() - 10, ypos+mTextRect.height()+10, mLinePaint);
		 }
	 }

	public static native void openReadCache();
	private static native void readCache(byte[] dst, float[] max, int offset, int len, boolean reset);
	public static native void closeReadCache();
	private static native void copyCache(byte[] dst, float[] max, boolean reset);
	private static native void linearSpectrum(Bitmap buffer, byte[] data, int[] bins, float[] max, int[] colors);
	private static native void logSpectrum(Bitmap buffer, byte[] data, int[] bins, float[] max, float maxFreq, int[] colors);
}