package com.digitalbiology.audio.views;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import com.digitalbiology.audio.MainActivity;

import java.text.DecimalFormat;

public class PowerFreqTickView extends View {

	private static final int[] sLinearFreq = { 1, 2, 5, 10, 20, 50, 100 };
	private static final float[] sLogFreq = { 0.1f, 0.2f, 0.5f, 1.0f };

	private Paint 	mLinePaint;
	private Rect	mTextRect;
	private DecimalFormat mFormatter;
//	private DecimalFormat mFormatter2;

	private Matrix			mMatrix;
	private Bitmap			mBitmap;
	private Canvas			mBitmapCanvas;
	private boolean			mRebuildBitmap;

	private float			mMaxFreq;
	private boolean			mUseLogScale;

//	private MainActivity activity;

	 public PowerFreqTickView(Context context) {
		  super(context);
		  if(!isInEditMode()) init();
	 }

	 public PowerFreqTickView(Context context, AttributeSet attrs) {
		  super(context, attrs);
		  if(!isInEditMode()) init();
	 }

	 public PowerFreqTickView(Context context, AttributeSet attrs, int defStyleAttr) {
		  super(context, attrs, defStyleAttr);
		  if(!isInEditMode()) init();
	 }

	 private void init() {

//		 activity = (MainActivity) context;

		 mFormatter = new DecimalFormat("#0.0");
//		 mFormatter2 = new DecimalFormat("#0.00");

		 mLinePaint = new Paint();
		 mLinePaint.setAntiAlias(true);
		 mLinePaint.setStrokeWidth(2.0f);
		 mLinePaint.setARGB(255, 255, 255, 255);
//		 mLinePaint.setTextSize(24f);
		 mLinePaint.setTextSize(14f * Resources.getSystem().getDisplayMetrics().density);

		 mTextRect = new Rect();
		 mMatrix = new Matrix();

		 mBitmap = null;
		 mBitmapCanvas = null;
		 mRebuildBitmap = true;

		 mMaxFreq = 0.0f;
		 mUseLogScale = false;
	 }

	public void setMaxFreq(float max) {
		mMaxFreq = max / 1000.0f;
		rebuildBitmap();
	}

	public void rebuildBitmap() {
		mRebuildBitmap = true;
		postInvalidateOnAnimation();
	}

	public void setLogScale(boolean logscale) {
		if (mUseLogScale != logscale) {
			mRebuildBitmap = true;
			mUseLogScale = logscale;
			postInvalidateOnAnimation();
		}
	}

	 protected void onDraw(Canvas canvas) {
		 
		 if (isInEditMode() || (mMaxFreq == 0)) return;

		 String text;
		 int height = getWidth();
		 int width = getHeight();

		 if (mRebuildBitmap) {

			 if (MainActivity.getNightMode())
				 mLinePaint.setARGB(255, 255, 0, 0);
			 else
				 mLinePaint.setARGB(255, 255, 255, 255);

			 text = "000.0";
			 mLinePaint.getTextBounds(text, 0, text.length(), mTextRect);
			 int textHalfHeight = mTextRect.height() / 2;
			 if ((mBitmap == null) || (mBitmap.getHeight() != height)) {
				 mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
				 if (mBitmapCanvas == null) mBitmapCanvas = new Canvas();
				 mBitmapCanvas.setBitmap(mBitmap);
			 }
			 mBitmap.eraseColor(Color.TRANSPARENT);

			 if (mUseLogScale) {
				 float maxLogFreq = (float) Math.log10(mMaxFreq * 1000.0f);
				 float freq_inc = sLinearFreq[sLogFreq.length-1];
				 for (float inc : sLogFreq) {
					 if ((int) (height * inc / maxLogFreq) >= 40) {
						 freq_inc = inc;
						 break;
					 }
				 }
				 float freqPerPixel = (maxLogFreq - 2.0f) / (float) height;
				 float curr_freq = 2.0f + freq_inc;
				 float pixelsPerInc = freq_inc / freqPerPixel;
				 float val;
				 for (float y = pixelsPerInc; y < height-textHalfHeight; y += pixelsPerInc) {
					 val = (float) Math.pow(10.0f, curr_freq) / 1000.0f;
					 text = mFormatter.format(val);
					 mLinePaint.getTextBounds(text, 0, text.length(), mTextRect);
					 mBitmapCanvas.drawLine(width - 5, y, width, y, mLinePaint);
					 mBitmapCanvas.drawText(text, width - mTextRect.width() - 10, y + textHalfHeight, mLinePaint);
					 curr_freq += freq_inc;
				 }
			 }
			 else {
				 int freq_inc = sLinearFreq[sLinearFreq.length-1];
				 for (int inc : sLinearFreq) {
					 if ((int) (height * (float) inc / mMaxFreq) >= 40) {
						 freq_inc = inc;
						 break;
					 }
				 }
				 float freqPerPixel = mMaxFreq / (float) height;
				 int curr_freq = freq_inc;
				 float pixelsPerInc = freq_inc / freqPerPixel;
				 for (float y = pixelsPerInc; y < height-textHalfHeight; y += pixelsPerInc) {
					 text = Integer.toString(curr_freq);
					 mLinePaint.getTextBounds(text, 0, text.length(), mTextRect);
					 mBitmapCanvas.drawLine(width - 5, y, width, y, mLinePaint);
					 mBitmapCanvas.drawText(text, width - mTextRect.width() - 10, y + textHalfHeight, mLinePaint);
					 curr_freq += freq_inc;
				 }
			 }
			 mRebuildBitmap = false;
		 }
		 mMatrix.reset();
		 mMatrix.postRotate(-90.0f);
		 mMatrix.postTranslate(0, getHeight());
		 canvas.drawBitmap(mBitmap, mMatrix, null);
	 }
}
