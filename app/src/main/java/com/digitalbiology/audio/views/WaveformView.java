package com.digitalbiology.audio.views;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Region;
import android.util.AttributeSet;
import android.view.View;

import com.digitalbiology.audio.MainActivity;

public class WaveformView extends View {

	final private static int SECTION_WIDTH = 512;
	final private static int SECTION_HEIGHT = 128;

	private Paint	mPaint;

	private volatile int mPlayHead;
	private int mSelStart;
	private int mSelEnd;
//	private volatile int mListenHead;

	private short[]	mDataBuffer;
	private byte[]	mStateBuffer;

	private float mZoomX;

	private Matrix mMatrix;
	private Rect mClipRect;
	private int mLastUpdateStep;
	private volatile boolean mDirty = false;

	private final Object lock = new Object();
	private Bitmap	mColorBitmapSlice;

	public WaveformView(Context context) {
		super(context);
		if(!isInEditMode()) init(context);
	}

	public WaveformView(Context context, AttributeSet attrs) {
		super(context, attrs);
		if(!isInEditMode()) init(context);
	}

	public WaveformView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		if(!isInEditMode()) init(context);
	}

	private void init(Context context) {

		mMatrix = new Matrix();
		mClipRect = new Rect();

		mPlayHead = 0;
		mSelStart = 0;
		mSelEnd = 0;
//		 mListenHead = 0;

		mZoomX = 1.0f;

		mPaint = new Paint();
		mPaint.setARGB(255, 255, 255, 255);
		mPaint.setStyle(Paint.Style.STROKE);
		mPaint.setStrokeWidth(2);

		mDataBuffer = new short[SECTION_WIDTH];
		mStateBuffer = new byte[SECTION_WIDTH];
	}

	public void setNumSamples(int size) {

		synchronized(lock) {
			mPlayHead = 0;
			mLastUpdateStep = 0;
			allocateWaveCaches(size);
		}
	}

	public void reset() {
		synchronized(lock) {
			mPlayHead = 0;
			clearWaveCaches();
		}
	}

	public int getPlayhead() {
		return mPlayHead;
	}

//	public void setListenhead(int head) {
//		synchronized(lock) {
//			mListenHead = head;
//		}
//	}

	public void setPlayhead(int head) {
		synchronized(lock) {
			mPlayHead = head;
		}
	}

	public void resetPlayhead() {
		synchronized(lock) {
			mPlayHead = 0;
		}
	}

//	 public void advancePlayhead() {
//	     synchronized(lock) {
//			 mPlayHead++;
//	     }
//	 }

	public void setZoomX(float zoom) {
		synchronized(lock) {
			mZoomX = zoom;
//			 mListenHead = 0;
		}
	}

	public void setSelRange(int start, int end) {
		mSelStart = start;
		mSelEnd = end;
	}

	public boolean handleStartDrag(int x) {
		return (Math.abs(mPlayHead * mZoomX - x) < 40);
	}

	protected void onDraw(Canvas canvas) {

		if(isInEditMode()) return;
		synchronized(lock) {

			if (mColorBitmapSlice == null) {
				mColorBitmapSlice = Bitmap.createBitmap(SECTION_HEIGHT, SECTION_WIDTH, Bitmap.Config.ARGB_8888);
			}

			canvas.getClipBounds(mClipRect);

			int section_start = (int) Math.floor((float) mClipRect.left / mZoomX);
			int section_end = (int) Math.ceil((float) mClipRect.right / mZoomX);

			float screen_block_size = (float) SECTION_WIDTH * mZoomX;
			float screen_xpos = (float) mClipRect.left;

			float sy = (float) getHeight() / (float) mColorBitmapSlice.getWidth();

			boolean nightMode = MainActivity.getNightMode();
			if (nightMode) {
				mPaint.setARGB(255, 255, 0, 0);
			}
			else {
				mPaint.setARGB(255, 255, 255, 255);
			}

			int playHead = (MainActivity.getDataMode() == MainActivity.DATA_MODE_PLAY) ? mPlayHead : 0;
			for (; section_start < section_end; section_start += SECTION_WIDTH) {

				copyWaveDataCache(mDataBuffer, section_start, SECTION_WIDTH);
				copyWaveStateCache(mStateBuffer, section_start, SECTION_WIDTH);

				traceWave(mColorBitmapSlice, mDataBuffer, mStateBuffer, section_start, playHead, mSelStart, mSelEnd, nightMode);

				mMatrix.reset();
				mMatrix.postRotate(-90f);
				mMatrix.postScale(mZoomX, sy);
				mMatrix.postTranslate(screen_xpos, getHeight());

				canvas.drawBitmap(mColorBitmapSlice, mMatrix, null);

				screen_xpos += screen_block_size;
			}
//			 if (MainActivity.getDataMode() == MainActivity.DATA_MODE_PLAY) {
			if (mPlayHead >= 0) {
				int hdpos = (int) ((float) mPlayHead * mZoomX);
				canvas.drawLine(hdpos, 0, hdpos, getHeight(), mPaint);
			}
//			 }
//			 else if (mListenHead > 0) {
//				 canvas.drawLine(mListenHead, 0, mListenHead, getHeight(), mPaint);
//			 }
			mDirty = false;
		}
	}

	public void resetUpdateStep() {
		mLastUpdateStep = 0;
	}

	public void makeDirty(int timestep, int playHead)
	{
		if (!mDirty) {
			if (timestep < mLastUpdateStep)
				postInvalidateOnAnimation();
			else {
				int leftEdge = mLastUpdateStep;
				int rightEdge = timestep;
				if (mPlayHead >= 0) {
					int hdpos = (int) ((float) mPlayHead * mZoomX) - 1;
					if (hdpos < leftEdge) leftEdge = hdpos;
				}
				mPlayHead = playHead;
				if (mPlayHead >= 0) {
					int hdpos = (int) ((float) mPlayHead * mZoomX);
					if (hdpos > rightEdge) rightEdge = hdpos;
				}
				postInvalidateOnAnimation(leftEdge, 0, rightEdge, getHeight());
			}
			mLastUpdateStep = timestep;
			mDirty = true;
		}
	}

	private static native void allocateWaveCaches(int cacheLen);
	private static native void clearWaveCaches();
	private static native void copyWaveDataCache(short[] dst, int from, int length);
	private static native void copyWaveStateCache(byte[] dst, int from, int length);
	private static native void traceWave(Bitmap buffer, short[] data, byte[] state, int offset, int playHead, int selStart, int selEnd, boolean nightMode);
}
