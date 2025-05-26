package com.digitalbiology.audio.views;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import com.digitalbiology.audio.MainActivity;
import com.digitalbiology.audio.R;
import com.digitalbiology.audio.utils.TimeAxisFormat;

public class TimeTickView extends View {

	private float	mSecsPerSample;
	private float	mSecsVisible;
	private Paint mLinePaint;
	private TimeAxisFormat mFormatter;
	private float mZoom;
	private float mLabelDelta;
	private Rect mClipRect;
	private Rect mBoundsRect;
	
	 public TimeTickView(Context context) {
		  super(context);
		  if(!isInEditMode()) init();
	 }
	
	 public TimeTickView(Context context, AttributeSet attrs) {
		  super(context, attrs);
		  if(!isInEditMode()) init();
	 }
	
	 public TimeTickView(Context context, AttributeSet attrs, int defStyleAttr) {
		  super(context, attrs, defStyleAttr);
		  if(!isInEditMode()) init();
	 }
	
	 private void init() {
		 
		 mFormatter = new TimeAxisFormat();

		 mLinePaint = new Paint();
		 mLinePaint.setAntiAlias(true);
		 mLinePaint.setStrokeWidth(2.0f);
//		 mLinePaint.setTextSize(24f);
		 mLinePaint.setTextSize(14f * Resources.getSystem().getDisplayMetrics().density);

		 mZoom = 1.0f;
		 mSecsPerSample = 0.0f;
		 mSecsVisible = 0.0f;

		 mClipRect = new Rect();
		 mBoundsRect = new Rect();

		 String text = "99.999";
		 mLinePaint.getTextBounds(text, 0, text.length(), mBoundsRect);
		 mLabelDelta = 1.5f * mBoundsRect.width();
	 }
	 
	 public void setSecondsPerSample(float secs) {
		 mSecsPerSample = secs;
		 mSecsVisible = mSecsPerSample * getWidth();		// when zoom is 1
	 }
	 
	 public float getSecondsPerSample() {
		 return mSecsPerSample;
	 }

	public float getSecondsVisibleInView() {
		return mSecsVisible;
	}

	public void setZoomLevel(float zoom) {
		mZoom = zoom;
	 }

	 protected void onDraw(Canvas canvas) {

		 final float[] factors = { 2.0f, 4.0f, 5.0f, 10.0f };
		 if(isInEditMode() || mSecsPerSample == 0.0f) return;

		 String text;
		 float interval;
		 float delta = 0.005f;
		 float d = delta;
		 int jj = 0;
		 while ((interval = mZoom * delta / mSecsPerSample) < mLabelDelta) {
			 if (jj == 0) d = delta;
			 delta = d * factors[jj];
			 jj = (jj + 1) % 4;
		 }
		 if (interval > 1.0f) interval = (float) Math.floor(interval + 0.5f);

		 canvas.drawColor(Color.BLACK);

		 if (MainActivity.getNightMode())
			 mLinePaint.setARGB(255, 255, 0, 0);
		 else
			 mLinePaint.setARGB(255, 255, 255, 255);

		 canvas.getClipBounds(mClipRect);
		 int x = (int) ((int) ((float) mClipRect.left / interval) * interval);
		 float secs = x / interval * delta;
		 for (; x < mClipRect.right; x += interval, secs += delta) {
			 canvas.drawLine(x, 0, x, 5, mLinePaint);
			 if (x == 0) {
				 text = "0";
				 mLinePaint.getTextBounds(text, 0, text.length(), mBoundsRect);
				 canvas.drawText(text, x, mBoundsRect.height() + 10, mLinePaint);
			 }
			 else {
				 text = mFormatter.format(secs);
				 mLinePaint.getTextBounds(text, 0, text.length(), mBoundsRect);
				 canvas.drawText(text, x - mBoundsRect.width() / 2, mBoundsRect.height() + 10, mLinePaint);
			 }
		 }
	 }
}
