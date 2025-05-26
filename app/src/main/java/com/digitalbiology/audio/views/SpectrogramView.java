package com.digitalbiology.audio.views;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;

import android.util.AttributeSet;
import android.view.View;

import com.digitalbiology.audio.FeatureExtractor;
import com.digitalbiology.audio.MainActivity;
import com.digitalbiology.audio.Palette;

import java.text.DecimalFormat;

public class SpectrogramView extends View {

	final private static int SECTION_HEIGHT = 512;
	final private static int FEATURE_RECT_OUTSET = 15;

	private Matrix 	mMatrix;
	private float 	mZoomX;
	private float 	mScaleY;
	private float	mScrollYOffset;
	private Rect	mClipRect;
	private int 	mLastUpdateStep;
	private byte[] 	mBuffer;
	
	private int 	mFFTHeight;

	private Paint 	mFeatureTimeSpanPaint;
	private Paint	mFeatureFreqPaint;
	private Paint	mGridPaint;
	private Rect	mFeatureRect;
	private Rect	mCacheRect;
	private Rect	mTextRect;
	private boolean	mFeatureSelected;
//	private boolean mShowFeatures;
	private int		mMaxFrequency;
	private float 	mSecsPerTimeSlice;
	private DecimalFormat mFreqFormatter;
	private DecimalFormat mTimeFormatter;

	private boolean mUseLogScale;
	private boolean mShowGridLines;

//	private Rect[]	mDebugFeatures;
//	private Paint	mDebugPaint;

	private volatile boolean mDirty = false;

	private Bitmap	mColorBitmapSlice;

    private final Object lock = new Object();

    public SpectrogramView(Context context) {
		  super(context);
		  if(!isInEditMode()) init(context);
	 }
	
	 public SpectrogramView(Context context, AttributeSet attrs) {
		  super(context, attrs);
		  if(!isInEditMode()) init(context);
	 }
	
	 public SpectrogramView(Context context, AttributeSet attrs, int defStyleAttr) {
		  super(context, attrs, defStyleAttr);
		  if(!isInEditMode()) init(context);
	 }
	
	 public int getFFTHeight() {
		 return mFFTHeight;
	 }
	 
	 private void init(Context context) {
		  
		  mMatrix = new Matrix();
		  mZoomX = 1.0f;
		  mScaleY = 1.0f;
		  mScrollYOffset = 0;

		 mClipRect = new Rect();
		  
		  mFFTHeight = 0;

		 mFeatureTimeSpanPaint = new Paint();
		 mFeatureTimeSpanPaint.setAntiAlias(true);
		 mFeatureTimeSpanPaint.setStrokeWidth(2.0f);
		 mFeatureTimeSpanPaint.setARGB(255, 255, 255, 255);
		 mFeatureTimeSpanPaint.setTextSize(14f * Resources.getSystem().getDisplayMetrics().density);
		 mFeatureTimeSpanPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));

		 mFeatureFreqPaint = new Paint();
		 mFeatureFreqPaint.setAntiAlias(true);
		 mFeatureFreqPaint.setStrokeWidth(2.0f);
		 mFeatureFreqPaint.setARGB(255, 255, 0, 0);
		 mFeatureFreqPaint.setTextSize(14f * Resources.getSystem().getDisplayMetrics().density);
		 mFeatureFreqPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));

//		 mDebugPaint = new Paint();
//		 mDebugPaint.setAntiAlias(true);
//		 mDebugPaint.setStrokeWidth(4.0f);
//		// mDebugPaint.setStyle(Paint.Style.STROKE);
//		 mDebugPaint.setStyle(Paint.Style.FILL_AND_STROKE);
//		 mDebugPaint.setARGB(255, 255, 0, 0);

		 mGridPaint = new Paint();
		 mGridPaint.setAntiAlias(true);
		 mGridPaint.setStrokeWidth(1.0f);
		 mGridPaint.setStyle(Paint.Style.STROKE);
		 mGridPaint.setARGB(220, 0, 0, 255);

		 mFeatureRect = null;
		 mCacheRect = new Rect();
		 mTextRect = new Rect();
		 mFeatureSelected = false;
//		 mShowFeatures = false;

		 mMaxFrequency = 0;
		 mSecsPerTimeSlice = 0.0f;
		 mFreqFormatter = new DecimalFormat("#0.0 kHz");
		 mTimeFormatter = new DecimalFormat("#0 ms");

		 mUseLogScale = false;
		 mShowGridLines = false;
	 }

//	public boolean getLogScale() {
//		return mUseLogScale;
//	}

	public void setLogScale(boolean logscale) {
		mUseLogScale = logscale;
	}

	public boolean showingGridLines() {
		return mShowGridLines;
	}

	public void showGridLines(boolean show) {
		mShowGridLines = show;
	}

//	public void setShowFeatures(boolean show) {
//		mShowFeatures = show;
//	}
//
//	public boolean getShowFeatures() {
//		return mShowFeatures;
//	}

	public void setFFTDimension(int height, int fftSize, int maxFreq, float secsPerTimeslice) {
		 
		 if(isInEditMode()) return;
		 synchronized(lock) {
			 if ((mColorBitmapSlice == null) || ((2 * mColorBitmapSlice.getWidth()) != fftSize)) {
				
				 mColorBitmapSlice = Bitmap.createBitmap(fftSize / 2, SECTION_HEIGHT, Bitmap.Config.ARGB_8888);
				 int bSize = SECTION_HEIGHT * fftSize / 2;
				 mBuffer = new byte[bSize];
			 }
			 mLastUpdateStep = 0;
			 mFFTHeight = height;
			 mMaxFrequency = maxFreq;
			 mSecsPerTimeSlice = secsPerTimeslice;
//			 mDebugFeatures = null;
			 mFeatureRect = null;
			 mFeatureSelected = false;
		 }
	}

	public void offsetYBy(float offset) {
		synchronized(lock) {
			mScrollYOffset -= offset;
			mScrollYOffset = Math.min(Math.max(0.0f, mScrollYOffset), getHeight() * mScaleY - getHeight());
		}
	}

	public void setYOffset(float offset) {
		synchronized(lock) {
			mScrollYOffset = offset;
			mScrollYOffset = Math.min(Math.max(0.0f, mScrollYOffset), getHeight() * mScaleY - getHeight());
		}
	}

	public void setZoomX(float zoom) {
		 synchronized(lock) {
			 mZoomX = zoom;
		 }
	 }

	public void setScaleY(float scale) {
		if (scale != mScaleY) {
			synchronized (lock) {
				float half_height = getHeight() / 2;
				float center = mScrollYOffset + half_height / mScaleY;
				mScaleY = scale;
				mScrollYOffset = center - half_height / mScaleY;
				mScrollYOffset = Math.min(Math.max(0.0f, mScrollYOffset), getHeight() * mScaleY - getHeight());
			}
		}
	}

	protected void onDraw(Canvas canvas) {
		   
		 synchronized(lock) {
						 
		   if (mFFTHeight > 0) {
		   
			   canvas.getClipBounds(mClipRect);

			   int data_per_timeslice = mColorBitmapSlice.getWidth();
			   int data_block_size = data_per_timeslice * SECTION_HEIGHT;
			   int section_start = data_per_timeslice * (int) Math.floor((float) mClipRect.left / mZoomX);
			   int section_end = data_per_timeslice * (int) Math.ceil((float) mClipRect.right / mZoomX);

//			   int data_block_size = SECTION_LENGTH * mZoomX;
//			   int section_start = (int) Math.floor((float) mClipRect.left / mZoomX);
//			   int section_end = (int) Math.ceil((float) mClipRect.right / mZoomX);

			   float screen_block_size = (float) SECTION_HEIGHT * mZoomX;
			   float screen_xpos = mClipRect.left;
			   float screen_ypos = getHeight() + mScrollYOffset;

			   float sy = mScaleY * (float) getHeight() / (float) data_per_timeslice;

			   openReadCache(section_start);

			   for (; section_start < section_end; section_start += data_block_size) {

					copyCache(mBuffer, section_start, SECTION_HEIGHT *  mColorBitmapSlice.getWidth());

                    if (mUseLogScale)
					   logScale(mColorBitmapSlice, Palette.getLinearColors(), mBuffer, mMaxFrequency);
				   else
					   linearScale(mColorBitmapSlice, Palette.getLinearColors(), mBuffer);

                    mMatrix.reset();
					mMatrix.postRotate(-90f);
					mMatrix.postScale(mZoomX, sy);
					mMatrix.postTranslate(screen_xpos, screen_ypos);

				   	canvas.drawBitmap(mColorBitmapSlice, mMatrix, null);
				   	
					screen_xpos += screen_block_size;
			   }
			   closeReadCache();

			   if (mShowGridLines) {
				   if (MainActivity.getNightMode())
					   mGridPaint.setARGB(180, 255, 0, 0);
				   else
					   mGridPaint.setARGB(220, 0, 0, 255);
				   for (Float gridLine : FreqTickView.getGridLines()) {
					   canvas.drawLine(mClipRect.left, gridLine, mClipRect.right, gridLine, mGridPaint);
				   }
			   }

//			   if (mDebugFeatures != null) {
//				   for (Rect signal : mDebugFeatures) {
//					   mCacheRect.set(signal);
//					   mCacheRect.left *= mZoomX;
//					   mCacheRect.right *= mZoomX;
//					   if (mUseLogScale) {
//						   float freqPerPixel = (float) mMaxFrequency / (float) data_per_timeslice;
//						   float maxLogFreq = (float) Math.log10(mMaxFrequency);
//						   float pixelsPerLogFreq = ((float) getHeight() * mScaleY) / (maxLogFreq - 2.0f);
//						   float logFreq = (float) Math.log10((float) signal.bottom * freqPerPixel) - 2.0f;
//						   mCacheRect.bottom = getHeight() - (int) (pixelsPerLogFreq * logFreq - mScrollYOffset);
//						   logFreq = (float) Math.log10((float) signal.top * freqPerPixel) - 2.0f;
//						   mCacheRect.top = getHeight() - (int) (pixelsPerLogFreq * logFreq - mScrollYOffset);
//					   }
//					   else {
//						   mCacheRect.bottom = getHeight() - (int) (signal.bottom * sy - mScrollYOffset);
//						   mCacheRect.top = getHeight() - (int) (signal.top * sy - mScrollYOffset);
//					   }
//					   canvas.drawRect(mCacheRect, mDebugPaint);
//				   }
//			   }

			   if (mFeatureRect != null) {

				   mCacheRect.set(mFeatureRect);
				   mCacheRect.left *= mZoomX;
				   mCacheRect.right *= mZoomX;
				   if (mUseLogScale) {
					   float freqPerPixel = (float) mMaxFrequency / (float) data_per_timeslice;
					   float maxLogFreq = (float) Math.log10(mMaxFrequency);
					   float pixelsPerLogFreq = ((float) getHeight() * mScaleY) / (maxLogFreq - 2.0f);
					   float logFreq = (float) Math.log10((float) mFeatureRect.bottom * freqPerPixel) - 2.0f;
					   mCacheRect.top = getHeight() - (int) (pixelsPerLogFreq * logFreq - mScrollYOffset);
					   logFreq = (float) Math.log10((float) mFeatureRect.top * freqPerPixel) - 2.0f;
					   mCacheRect.bottom = getHeight() - (int) (pixelsPerLogFreq * logFreq - mScrollYOffset);
				   }
				   else {
					   mCacheRect.top = getHeight() - (int) (mFeatureRect.bottom * sy - mScrollYOffset);
					   mCacheRect.bottom = getHeight() - (int) (mFeatureRect.top * sy - mScrollYOffset);
				   }

				   float val;
				   String text;
				   if (mCacheRect.top != mCacheRect.bottom) {
					   int left = mCacheRect.left - FEATURE_RECT_OUTSET;
					   canvas.drawLine(left, mCacheRect.top, left, mCacheRect.bottom, mFeatureFreqPaint);
					   canvas.drawLine(left - 10, mCacheRect.top, left, mCacheRect.top, mFeatureFreqPaint);
					   canvas.drawLine(left - 10, mCacheRect.bottom, left, mCacheRect.bottom, mFeatureFreqPaint);

					   val = (float) mMaxFrequency * (float) mFeatureRect.bottom / ((float) data_per_timeslice * 1000.0f);
					   text = mFreqFormatter.format(val);
					   mFeatureFreqPaint.getTextBounds(text, 0, text.length(), mTextRect);
					   canvas.drawText(text, left - mTextRect.width() - 15, mCacheRect.top + mTextRect.height() / 2, mFeatureFreqPaint);

					   val = (float) mMaxFrequency * (float) mFeatureRect.top / ((float) data_per_timeslice * 1000.0f);
					   text = mFreqFormatter.format(val);
					   mFeatureFreqPaint.getTextBounds(text, 0, text.length(), mTextRect);
					   canvas.drawText(text, left - mTextRect.width() - 15, mCacheRect.bottom + mTextRect.height() / 2, mFeatureFreqPaint);

					   if (mFeatureSelected) {
						   canvas.drawCircle(left, mCacheRect.top, 5.0f, mFeatureFreqPaint);
						   canvas.drawCircle(left, mCacheRect.bottom, 5.0f, mFeatureFreqPaint);
					   }
				   }

				   if (MainActivity.getNightMode())
				   		mFeatureTimeSpanPaint.setARGB(255, 255, 0, 0);
					else
					   mFeatureTimeSpanPaint.setARGB(255, 255, 255, 255);

				   int bottom = mCacheRect.bottom + FEATURE_RECT_OUTSET;
				   int center = mCacheRect.left + mCacheRect.width() / 2;
				   canvas.drawLine(mCacheRect.left, bottom, mCacheRect.right, bottom, mFeatureTimeSpanPaint);
				   canvas.drawLine(center, bottom+10, center, bottom, mFeatureTimeSpanPaint);

				   val = mFeatureRect.width() * mSecsPerTimeSlice * 1000.0f;
				   text = mTimeFormatter.format(val);
				   mFeatureTimeSpanPaint.getTextBounds(text, 0, text.length(), mTextRect);
				   canvas.drawText(text, center - mTextRect.width() / 2, bottom + mTextRect.height() + 15, mFeatureTimeSpanPaint);

				   if (mFeatureSelected) {
					   canvas.drawCircle(mCacheRect.left, bottom, 5.0f, mFeatureTimeSpanPaint);
					   canvas.drawCircle(mCacheRect.right, bottom, 5.0f, mFeatureTimeSpanPaint);
				   }
			   }
		   }
//		   mLastUpdateStep = mClipRect.left;
		   mDirty = false;
		 }
	}

//	public void setFeatures(Rect[] features) {
//		mFeatures = features;
//	}

	public void resetUpdateStep() {
		mLastUpdateStep = 0;
		mFeatureRect = null;
		mFeatureSelected = false;
	}

	public void makeDirty(int timestep)
	 {
		 if (!mDirty) {
//			 int step = (int) ((float) timestep * mScale + 0.5f);
			 if (timestep >= mLastUpdateStep)
//				 postInvalidateOnAnimation();
//			 else
				 postInvalidateOnAnimation(mLastUpdateStep, 0, timestep, getHeight());
//			 Log.i("SPEC", "p=" + mLastUpdateStep + " c=" + timestep);
			 mLastUpdateStep = timestep;
			 mDirty = true;
		 }
	 }

	private int getLocalX(int x) {
		return (int) ((float) x / mZoomX);
	}

	private int getLocalY(int y) {
		int sig_y;
		if (mUseLogScale) {
			float freqPerPixel = (float) mMaxFrequency / (float) mColorBitmapSlice.getWidth();
			float maxLogFreq = (float) Math.log10(mMaxFrequency);
			float pixelsPerLogFreq = ((float) getHeight() * mScaleY) / (maxLogFreq - 2.0f);
			float logFreq = (float) Math.log10((float) y * freqPerPixel) - 2.0f;
			sig_y = getHeight() - (int) (pixelsPerLogFreq * logFreq - mScrollYOffset);
		} else {
			float sy = (float) mColorBitmapSlice.getWidth() / (mScaleY * (float) getHeight());
			sig_y = (int) ((getHeight() - y + mScrollYOffset) * sy);
		}
		return sig_y;
	}

	public boolean featureSelected() {
		return mFeatureSelected;
	}

	private boolean featureContains(int x, int y) {
		if (mFeatureRect != null) {
			if (mFeatureRect.height() > 0) return mFeatureRect.contains(x, y);
			return ((x >= mFeatureRect.left && x <= mFeatureRect.right) && (y >= mFeatureRect.bottom-60 && y <= mFeatureRect.bottom+60));
		}
		return false;
	}

	public void resizeFeature(float xfactor, float yfactor) {
		if (mFeatureSelected && (mFeatureRect != null)) {
			if (mFeatureRect.height() > 0) {
				mFeatureRect.right = mFeatureRect.left + (int) Math.max(5, xfactor * xfactor * mFeatureRect.width());
				mFeatureRect.bottom = mFeatureRect.top + (int) Math.max(20, yfactor * yfactor * mFeatureRect.height());
			}
			else {
				mFeatureRect.inset((mFeatureRect.width() - (int) Math.max(5, xfactor * xfactor * mFeatureRect.width())) / 2, 0);
			}
			invalidate();
		}
	}

	private final Point mFeatureDragAnchor = new Point();

	public boolean handleStartDrag(int x, int y) {
		if (mColorBitmapSlice != null) {
			int sig_x = getLocalX(x);
			int sig_y = getLocalY(y);
			if (mFeatureSelected && featureContains(sig_x, sig_y)) {
				mFeatureDragAnchor.x = sig_x;
				mFeatureDragAnchor.y = sig_y;
				return true;
			}
		}
		return false;
	}

	public boolean handleDrag(int x, int y) {
		if (mFeatureSelected) {
			int sig_x = getLocalX(x);
			int sig_y = getLocalY(y);
			mFeatureRect.offset(sig_x - mFeatureDragAnchor.x, sig_y - mFeatureDragAnchor.y);
			mFeatureDragAnchor.x = sig_x;
			mFeatureDragAnchor.y = sig_y;
			invalidate();
			return true;
		}
		return false;
	}

	public void handleSingleTap(int x, int y) {
		if (mFeatureSelected && (mFeatureRect != null)) {
			int sig_x = getLocalX(x);
			int sig_y = getLocalY(y);
			if (!featureContains(sig_x, sig_y)) {
				// touch inside current feature
				mFeatureSelected = false;
				invalidate();
			}
		}
	}

	public boolean handleLongTouch(int x, int y) {
		if (mFeatureRect != null) {
			int sig_x = getLocalX(x);
			int sig_y = getLocalY(y);
			if (featureContains(sig_x, sig_y)) {
				// touch inside current feature
				if (!mFeatureSelected) {
					mFeatureSelected = true;
					invalidate();
				}
				return true;
			}
		}
		if (mFeatureSelected) {
			mFeatureSelected = false;
			invalidate();
		}
		return false;
	}

	public boolean handleDoubleTap(int x, int y) {

		mFeatureSelected = false;

		if (mColorBitmapSlice == null) return false;

		int sig_x = getLocalX(x);
		int sig_y = getLocalY(y);
		 // Analyze a 1 second interval to look for features
		 int timeSlicesPerSecond = (int) (0.5f / mSecsPerTimeSlice);
		 Rect[] features = FeatureExtractor.extractFeatures(
				 mColorBitmapSlice.getWidth(),
				 Math.max(0, sig_x - timeSlicesPerSecond),
				 Math.min(sig_x + timeSlicesPerSecond, mFFTHeight));
//		 mDebugFeatures = features;
		 if (features != null) {
			 int closest = -1;
			 int dist = Integer.MAX_VALUE;
			 int ii = 0;
			 for (Rect signal : features) {
//				 Log.d("SpectrogramView Touch", "[ X " + sig_x + " Y " + sig_y + " ]");
				 if ((sig_y >= signal.top) && (sig_y < signal.bottom)) {
//				if (true) {
//						 signal.top = sig_y - 20;
//						 signal.bottom = sig_y + 20;
//						 signal.left = sig_x - 20;
//						 signal.right = sig_x + 20;
					 if ((sig_x >= signal.left) && (sig_x < signal.right)) {
						 if ((mFeatureRect == null) || !mFeatureRect.equals(signal))
							 mFeatureRect = new Rect(signal);
						 else
							 mFeatureRect = null;
						 invalidate();
						 return true;
					 } else {
						int d  = Math.abs(sig_x - signal.left);
						if (Math.abs(sig_x - signal.right) < d) d = Math.abs(sig_x - signal.right);
						if (d < dist) {
							dist = d;
							closest = ii;
						}

//						if (signal.right <= sig_x) {
//							 if ((sig_x - signal.right) < dist) {
//								 dist = sig_x - signal.right;
//								 closest = ii;
//							 }
//						 } else if (signal.left >= sig_x) {
//							 if ((signal.left - sig_x) < dist) {
//								 dist = signal.left - sig_x;
//								 closest = ii;
//							 }
//						 }
					 }
				 }
				 ii++;
			 }
			 if (closest >= 0) {
				 if (features[closest].right <= sig_x) {
					 for (int jj = closest + 1; jj < features.length; jj++) {
						 if ((features[jj].left > features[closest].right)
								 && (sig_y >= features[jj].top)
								 && (sig_y < features[jj].bottom)) {
							 mFeatureRect = new Rect();
							 mFeatureRect.left = features[closest].right;
							 mFeatureRect.right = features[jj].left;
							 if (features[jj].height() < features[closest].height()) {
								 mFeatureRect.bottom = features[jj].top + (features[jj].bottom - features[jj].top) / 2;
							 } else {
								 mFeatureRect.bottom = features[closest].top + (features[closest].bottom - features[closest].top) / 2;
							 }
							 mFeatureRect.top = mFeatureRect.bottom;
							 invalidate();
							 return true;
						 }
					 }
				 } else {
					 for (int jj = closest - 1; jj >= 0; jj--) {
						 if ((features[jj].right < features[closest].left)
								 && (sig_y >= features[jj].top)
								 && (sig_y < features[jj].bottom)) {
							 mFeatureRect = new Rect();
							 mFeatureRect.left = features[jj].right;
							 mFeatureRect.right = features[closest].left;
							 if (features[jj].height() < features[closest].height()) {
								 mFeatureRect.bottom = features[jj].top + (features[jj].bottom - features[jj].top) / 2;
							 } else {
								 mFeatureRect.bottom = features[closest].top + (features[closest].bottom - features[closest].top) / 2;
							 }
							 mFeatureRect.top = mFeatureRect.bottom;
							 invalidate();
							 return true;
						 }
					 }
				 }
			 }
		 }
		 if (mFeatureRect != null) {
			mFeatureRect = null;
			invalidate();
		}
		return false;
	}

	private static native void closeReadCache();
	private static native void openReadCache(int offset);
	private static native void copyCache(byte[] dst, int from, int length);
	private static native void linearScale(Bitmap buffer, int[] colors, byte[] map);
	private static native void logScale(Bitmap buffer, int[] colors, byte[] map, float fqmax);
}