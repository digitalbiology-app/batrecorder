package com.digitalbiology.audio.views;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import com.digitalbiology.audio.utils.TimeAxisFormat;

import java.text.DecimalFormat;

public class InfoOverlayView extends View {

	private TimeAxisFormat  mTimeFormat;
	private DecimalFormat	mZoomFormat;
	private String			mDisplayText;
	private int				mTextWidth;
	private Paint			mLinePaint;
//	private Rect			mTextBounds;
	private boolean 		mDisplayZoom;
	private volatile boolean mDirty = false;
	
	 public InfoOverlayView(Context context, AttributeSet attrs) {
		  super(context, attrs);
		  if(!isInEditMode()) init();
	 }
	
	 public InfoOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
		  super(context, attrs, defStyleAttr);
		  if(!isInEditMode()) init();
	 }
	 
	 private void init() {
		 
		mZoomFormat = new DecimalFormat("#0.00x");
		mTimeFormat = new TimeAxisFormat();
		mDisplayText = "";
		mTextWidth = 0;
//		mTextBounds = new Rect();
		
		 mLinePaint = new Paint();
		 mLinePaint.setAntiAlias(true);
		 mLinePaint.setStrokeWidth(2.0f);
		 mLinePaint.setARGB(255, 255, 0, 0);
		 mLinePaint.setTextSize(20f * Resources.getSystem().getDisplayMetrics().density);

		 mDisplayZoom = false;

		 mDirty = false;
	 }

	 public void displayZoomFactor() {
		 mDisplayZoom = true;
	 }

	 protected void onDraw(Canvas canvas) {
		   
		 if(isInEditMode()) return;
		 canvas.drawText(mDisplayText, getWidth()-mTextWidth, getHeight(), mLinePaint);
//		 canvas.drawText(mDisplayText, 0, getHeight(), mLinePaint);
		 mDirty = false;
	 }
	 
	 public void makeDirty(float value)
	 {
		 if (mDisplayZoom) {
			 mDisplayText = mZoomFormat.format(value);
		 } else {
			 if (value < 0.0f)
				 mDisplayText = "";
			 else
				 mDisplayText = mTimeFormat.formatFull(value);
		 }
		 Rect bounds = new Rect();
		 mLinePaint.getTextBounds(mDisplayText, 0, mDisplayText.length(), bounds);
		 mTextWidth = bounds.width();

		 postInvalidateOnAnimation();
		 mDirty = true;
	 }
}