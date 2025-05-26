package com.digitalbiology.audio.views;

import java.text.DecimalFormat;
import java.util.ArrayList;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import com.digitalbiology.audio.MainActivity;

public class FreqTickView extends View {

	private static final int[] sLinearFreq = { 1, 2, 5, 10, 20, 50, 100 };
	private static final float[] sLogFreq = { 0.1f, 0.2f, 0.5f, 1.0f };

	private static final ArrayList<Float> sFreqGridLines = new ArrayList<>();

	private float 	mMaxFreq;
	private Paint 	mLinePaint;
	private Paint 	mTunerPaint;
	private Rect	mTextRect;
	private int		mTextHalfHeight;
	private float 	mPixelOffset;
	private DecimalFormat mFormatter;
	private DecimalFormat mFormatter2;

	private volatile float	mTunerFreq;
	private boolean 		mShowTuner;

	private float			mScale;
	private float			mScrollOffset;

	private Bitmap			mBitmap;
	private Canvas			mBitmapCanvas;
	private boolean			mRebuildBitmap;

	private boolean			mAutoTune;
	private boolean			mUseLogScale;

	private boolean 		mTouchOnTuner;

	private MainActivity activity;

	private GestureDetector mGestureDetector;

	 public FreqTickView(Context context) {
		  super(context);
		  if(!isInEditMode()) init(context);
	 }
	
	 public FreqTickView(Context context, AttributeSet attrs) {
		  super(context, attrs);
		  if(!isInEditMode()) init(context);
	 }
	
	 public FreqTickView(Context context, AttributeSet attrs, int defStyleAttr) {
		  super(context, attrs, defStyleAttr);
		  if(!isInEditMode()) init(context);
	 }

	 private void init(Context context) {
		 
//		 setMinMaxFreq(0.0f, (float) (MainActivity.sRecordingAudioRateInHz / 2));
		 mMaxFreq = 0.0f;

		 activity = (MainActivity) context;
		 mTunerFreq = activity.getPreferences().getFloat("tuner", 0.5f);

		 mShowTuner = false;
		 mAutoTune = activity.getPreferences().getBoolean("autotune", false);

		 mFormatter = new DecimalFormat("#0.0");
		 mFormatter2 = new DecimalFormat("#0.00");

		 mLinePaint = new Paint();
		 mLinePaint.setAntiAlias(true);
		 mLinePaint.setStrokeWidth(2.0f);
//		 mLinePaint.setTextSize(24f);
		 mLinePaint.setTextSize(14f * Resources.getSystem().getDisplayMetrics().density);

		 mTunerPaint = new Paint();
		 mTunerPaint.setAntiAlias(true);
		 mTunerPaint.setStrokeWidth(6.0f);
		 mTunerPaint.setTextSize(18f * Resources.getSystem().getDisplayMetrics().density);

		 mTextRect = new Rect();

		 mGestureDetector = new GestureDetector(context, new GestureListener());
		 mTouchOnTuner = false;

		 mScale = 1.0f;
		 mScrollOffset = 0.0f;

		 mBitmap = null;
		 mBitmapCanvas = null;
		 mRebuildBitmap = true;

		 mUseLogScale = false;
	 }

	public static ArrayList<Float> getGridLines() {
		return sFreqGridLines;
	}

	public void setPixelOffset(int offset) {
		mPixelOffset = offset;
	}

	public void rebuildBitmap() {
		mRebuildBitmap = true;
	}

	public void setZoomLevel(float scale) {
		if (scale != mScale) {
			int height = getHeight() - (int) mPixelOffset;
			float half_height = height / 2;
			float center = mScrollOffset + half_height / mScale;
			mScale = scale;
			mScrollOffset = center - half_height / mScale;
			mScrollOffset = Math.min(Math.max(0.0f, mScrollOffset), height * mScale - height);
			mRebuildBitmap = true;
		}
	}

	 public float getMaxFreq() {
			return mMaxFreq * 1000.0f;
	 }

	public void setMaxFreq(float max) {
		mMaxFreq = max / 1000.0f;
		mRebuildBitmap = true;
		if (activity.getPowerFreqTickView() != null) {
			activity.getPowerFreqTickView().setMaxFreq(max);
		}
	}

	public boolean isAutoTune() {
		return mAutoTune;
	}

	public void offsetBy(float offset) {
		mScrollOffset -= offset;
		int height = getHeight() - (int) mPixelOffset;
		mScrollOffset = Math.min(Math.max(0.0f, mScrollOffset), height * mScale - height);
		mRebuildBitmap = true;
	}

	public void setOffset(float offset) {
		mScrollOffset = offset;
		int height = getHeight() - (int) mPixelOffset;
		mScrollOffset = Math.min(Math.max(0.0f, mScrollOffset), height * mScale - height);
		mRebuildBitmap = true;
	}

	public boolean getLogScale() {
		return mUseLogScale;
	}

	public void setLogScale(boolean logscale) {
		if (mUseLogScale != logscale) {
			mRebuildBitmap = true;
			mUseLogScale = logscale;
		}
		if (activity.getPowerFreqTickView() != null) {
			activity.getPowerFreqTickView().setLogScale(logscale);
		}
	}

	 protected void onDraw(Canvas canvas) {
		 
		 if (isInEditMode() || (mMaxFreq == 0)) return;

		 String text;
		 int width = getWidth();
		 int height = getHeight() - (int) mPixelOffset;

		 if (mRebuildBitmap) {

			 text = "000.0";
			 mLinePaint.getTextBounds(text, 0, text.length(), mTextRect);
			 mTextHalfHeight = mTextRect.height() / 2;
			 if ((mBitmap == null) || (mBitmap.getHeight() != getHeight()+mTextHalfHeight)) {
				 mBitmap = Bitmap.createBitmap(width, getHeight()+mTextHalfHeight, Bitmap.Config.ARGB_8888);
				 if (mBitmapCanvas == null) mBitmapCanvas = new Canvas();
				 mBitmapCanvas.setBitmap(mBitmap);
			 }
			 mBitmap.eraseColor(Color.BLACK);

			 float total_height = (float) height * mScale;
//			 float freq_range = (float) mMaxFreq / (float) mScale;

			 if (MainActivity.getNightMode())
				 mLinePaint.setARGB(255, 255, 0, 0);
			 else
				 mLinePaint.setARGB(255, 255, 255, 255);

			 sFreqGridLines.clear();
			 if (mUseLogScale) {
				 float maxLogFreq = (float) Math.log10(mMaxFreq * 1000.0f);
				 float freq_inc = sLinearFreq[sLogFreq.length-1];
				 for (float inc : sLogFreq) {
					 if ((int) (total_height * inc / maxLogFreq) >= 40) {
						 freq_inc = inc;
						 break;
					 }
				 }
				 float freqPerPixel = (maxLogFreq - 2.0f) / total_height;
				 float curr_freq = freq_inc * (int) (freqPerPixel * mScrollOffset / freq_inc + 0.5f);
				 if (curr_freq < 2.0f) curr_freq = 2.0f;
				 float pixelsPerInc = freq_inc / freqPerPixel;
				 float val;
				 for (float y = height - (curr_freq - 2.0f - mScrollOffset * freqPerPixel) / freqPerPixel + mTextHalfHeight; y >= 0; y -= pixelsPerInc) {
					 val = (float) Math.pow(10.0f, curr_freq) / 1000.0f;
					 text = mFormatter.format(val);
					 mLinePaint.getTextBounds(text, 0, text.length(), mTextRect);
					 mBitmapCanvas.drawLine(width - 5, y, width, y, mLinePaint);
					 mBitmapCanvas.drawText(text, width - mTextRect.width() - 10, y + mTextHalfHeight, mLinePaint);
					 sFreqGridLines.add(y-mTextHalfHeight);
					 curr_freq += freq_inc;
				 }
//				 int divisor = 10;
//				 int interval = getHeight() / divisor;
//				 while ((divisor > 1) && (interval < 40)) {
//					 divisor--;
//					 interval = getHeight() / divisor;
//				 }
//				 float maxLogFreq = (float) Math.log10(mMaxFreq * 1000.0f);
//				 float freq_interval = (maxLogFreq - 2.0f) / ((float) divisor * mScale);
//				 float freq = (maxLogFreq - 2.0f) * mScrollOffset / (height * mScale) + 2.0f;
//				 float val;
//				 for (int y = height + mTextHalfHeight; y >= 0; y -= interval) {
//					 val = (float) Math.pow(10.0f, freq) / 1000.0f;
//					 if (val < 10.0f)
//						 text = mFormatter2.format(val);
//					 else
//						 text = mFormatter.format(val);
//					 mLinePaint.getTextBounds(text, 0, text.length(), mTextRect);
//					 mBitmapCanvas.drawLine(width - 5, y, width, y, mLinePaint);
//					 mBitmapCanvas.drawText(text, width - mTextRect.width() - 10, y + mTextHalfHeight, mLinePaint);
//					 freq += freq_interval;
//				 }
			 }
			 else {
				 int freq_inc = sLinearFreq[sLinearFreq.length-1];
				 for (int inc : sLinearFreq) {
					 if ((int) (total_height * (float) inc / mMaxFreq) >= 40) {
						 freq_inc = inc;
						 break;
					 }
				 }
				 float freqPerPixel = mMaxFreq / total_height;
				 int curr_freq = freq_inc * (int) (freqPerPixel * mScrollOffset / (float) freq_inc + 0.5f);
				 float pixelsPerInc = freq_inc / freqPerPixel;
				 for (float y = height - (curr_freq - mScrollOffset * freqPerPixel) / freqPerPixel + mTextHalfHeight; y >= 0; y -= pixelsPerInc) {
					 text = Integer.toString(curr_freq);
					 mLinePaint.getTextBounds(text, 0, text.length(), mTextRect);
					 mBitmapCanvas.drawLine(width - 5, y, width, y, mLinePaint);
					 mBitmapCanvas.drawText(text, width - mTextRect.width() - 10, y + mTextHalfHeight, mLinePaint);
					 sFreqGridLines.add(y-mTextHalfHeight);
					 curr_freq += freq_inc;
				 }
//				 float freq_interval = mMaxFreq / ((float) divisor * mScale);
//				 float freq = mMaxFreq * mScrollOffset / (height * mScale);
//				 for (int y = height + mTextHalfHeight; y >= 0; y -= interval) {
//					 text = mFormatter.format(freq);
//					 mLinePaint.getTextBounds(text, 0, text.length(), mTextRect);
//					 mBitmapCanvas.drawLine(width - 5, y, width, y, mLinePaint);
//					 mBitmapCanvas.drawText(text, width - mTextRect.width() - 10, y + mTextHalfHeight, mLinePaint);
//					 freq += freq_interval;
//				 }
			 }
			 mRebuildBitmap = false;
		 }
		 canvas.drawBitmap(mBitmap, 0, -mTextHalfHeight, null);

		 if (mShowTuner) {
			 float freq;
			 float ratio;
			 if (mUseLogScale) {
				 float max_freq = mMaxFreq * 1000.0f;
				 ratio  = ((float) Math.log10(100.0f + mTunerFreq * (max_freq - 100.0f)) - 2.0f) / (float) (Math.log10(max_freq) - 2.0f);
				 freq = (0.1f + mTunerFreq * (mMaxFreq - 0.1f));
			 }
			else {
				 ratio = mTunerFreq;
				 freq = mTunerFreq * mMaxFreq;
			 }
			 float y = height - (float) height * mScale * ratio + mScrollOffset;
			 if (y >= 0 && y <= height) {
				 if (freq < 10.0f)
					 text = mFormatter2.format(freq);
				 else
				 	text = mFormatter.format(freq);
				 mTunerPaint.getTextBounds(text, 0, text.length(), mTextRect);
				 if (mAutoTune)
					 mTunerPaint.setARGB(255, 0, 255, 0);
				 else
					 mTunerPaint.setARGB(255, 255, 0, 0);
				canvas.drawText(text, width - mTextRect.width() - 10, -5, mTunerPaint);
				canvas.drawLine(0, y, width, y, mTunerPaint);
			 }
		 }
	 }

	public void showTuner(boolean show) {
		mShowTuner = show;
		postInvalidateOnAnimation();
	}

	public int getTuneFrequency() {
		return (int) (mTunerFreq * mMaxFreq * 1000.0f);
	}

	public void setTuneFrequency(int frequency) {

		if (frequency != getTuneFrequency()) {
			mTunerFreq = (float) frequency / (mMaxFreq * 1000.0f);
			postInvalidateOnAnimation();
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		if (mShowTuner) mGestureDetector.onTouchEvent(ev);
		return true;
	}

	private class GestureListener extends GestureDetector.SimpleOnGestureListener {

		private static final int CLOSE_ENOUGH = 80;

		public boolean onDoubleTap(MotionEvent e) {

			mAutoTune = !mAutoTune;
			if (mAutoTune) {
//				if (!activity.reopenRecording()) {
				if ((MainActivity.getDataMode() == MainActivity.DATA_MODE_PLAY) && (MainActivity.sTunings != null)) {
					float binFrequency = mMaxFreq * 1000.0f * 2.0f / (float) Integer.parseInt(activity.getPreferences().getString("fft", "1024"));
					mTunerFreq = binFrequency * ((float) MainActivity.sTunings[0] + 0.5f) / (mMaxFreq * 1000.0f);		// TBD - index should be offset
				}
				int minTuningFrequency = activity.getPreferences().getInt("hetfreq_min", 15) * 1000;
				int maxTuningFrequency = activity.getPreferences().getInt("hetfreq_max", 200) * 1000;
				if (getTuneFrequency() < minTuningFrequency) {
					mTunerFreq = (float) minTuningFrequency / (mMaxFreq * 1000.0f);
				}
				else if (getTuneFrequency() > maxTuningFrequency) {
					mTunerFreq = (float) maxTuningFrequency / (mMaxFreq * 1000.0f);
				}
//				}
			}
			else {
				int height = getHeight() - (int) mPixelOffset;
				float freq;
				if (mUseLogScale) {
					freq = (height - e.getY() + mScrollOffset) / ((float) height * mScale);
					float max_freq = mMaxFreq * 1000.0f;
					freq = (float) Math.pow(10.0f, freq * ((float) Math.log10(max_freq) - 2.0f) + 2.0f) / max_freq;
				}
				else {
					freq = (height - e.getY() + mScrollOffset) / ((float) height * mScale);
				}
				freq = Math.max(0.0f, Math.min(1.0f, freq));
				if (freq != mTunerFreq) {
					mTunerFreq = freq;
					activity.getPreferences().edit().putFloat("tuner", mTunerFreq).apply();
				}
			}
			activity.getPreferences().edit().putBoolean("autotune", mAutoTune).apply();
			invalidate();

			return true;
		}

		public boolean onDown(MotionEvent e) {

			if (!mAutoTune) {
				int height = getHeight() - (int) mPixelOffset;
				float ratio;
				if (mUseLogScale) {
					float max_freq = mMaxFreq * 1000.0f;
					ratio  = ((float) Math.log10(100.0f + mTunerFreq * (max_freq - 100.0f)) - 2.0f) / (float) (Math.log10(max_freq) - 2.0f);
				}
				else {
					ratio = mTunerFreq;
				}
				float y = height - (float) height * mScale * ratio + mScrollOffset;
				mTouchOnTuner = (Math.abs(e.getY() - y) < CLOSE_ENOUGH);
			}
			return true;
		}

		@Override
		public boolean onScroll(MotionEvent e1, MotionEvent e2,float distanceX, float distanceY){

			if (mTouchOnTuner && (Math.abs(distanceY) > Math.abs(distanceX))) {
				int height = getHeight() - (int) mPixelOffset;
				float freq;
				if (mUseLogScale) {
					freq = (height - e2.getY() + mScrollOffset) / ((float) height * mScale);
					float max_freq = mMaxFreq * 1000.0f;
					freq = (float) Math.pow(10.0f, freq * ((float) Math.log10(max_freq) - 2.0f) + 2.0f) / max_freq;
				}
				else {
					freq = (height - e2.getY() + mScrollOffset) / ((float) height * mScale);
				}
				freq = Math.max(0.0f, Math.min(1.0f, freq));
				mAutoTune = false;
				freq = Math.max(0.0f, Math.min(freq, 1.0f));
				if (freq != mTunerFreq) {
					mTunerFreq = freq;
					invalidate();
					activity.getPreferences().edit().putFloat("tuner", mTunerFreq).apply();
				}
				activity.getPreferences().edit().putBoolean("autotune", mAutoTune).apply();
			}
			return true;
		}
	}
}
