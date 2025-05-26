package com.digitalbiology.audio.views;

import com.digitalbiology.audio.MainActivity;
import com.digitalbiology.audio.Palette;
import com.digitalbiology.audio.R;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

public class PaletteView extends View {

	public final static int MIN_DB = -48;
	public final static int MAX_DB = 0;
	
	private Bitmap 	mBitmap;
	private Matrix 	mMatrix;
	private Paint 	mLinePaint;
	private Paint 	mShadowPaint;
	private Rect	mTextRect;
	private RectF 	mShadowRect;
	
	private MainActivity activity;
	private GestureDetector mGestureDetector;
	
	 public PaletteView(Context context) {
		  super(context);
		  if(!isInEditMode()) init(context);
	 }
	
	 public PaletteView(Context context, AttributeSet attrs) {
		  super(context, attrs);
		  if(!isInEditMode()) init(context);
	 }
	
	 public PaletteView(Context context, AttributeSet attrs, int defStyleAttr) {
		  super(context, attrs, defStyleAttr);
		  if(!isInEditMode()) init(context);
	 }
	 
	 private void init(Context context) {
		 
		 mMatrix = null;
		 mBitmap = Bitmap.createBitmap(256, 1, Bitmap.Config.ARGB_8888);

		 mLinePaint = new Paint();
		 mLinePaint.setAntiAlias(true);
		 mLinePaint.setStrokeWidth(2.0f);
		 mLinePaint.setARGB(255, 255, 255, 255);
//		 mLinePaint.setTextSize(24f);
		 mLinePaint.setTextSize(14f * Resources.getSystem().getDisplayMetrics().density);

		 mShadowPaint = new Paint();
		 mShadowPaint.setStyle(Paint.Style.FILL);
		 mShadowPaint.setARGB(1460, 0, 0, 0);

		 mTextRect = new Rect();
		 mShadowRect = new RectF();

		 activity = (MainActivity) context;
	     mGestureDetector = new GestureDetector(context, new GestureListener());
		
	     SharedPreferences prefs = activity.getPreferences();
		for (int ii = 0; ii < Palette.NUM_PALETTES; ii++) Palette.setCoefficient(prefs.getFloat("pcoeff"+ii, 1.0f), ii);
		Palette.setType(prefs.getInt("palette", 0));
	 }


	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		mMatrix = null;
	}

	 protected void onDraw(Canvas canvas) {
		  
		 if(isInEditMode()) return;

		 int width = getWidth();
		 int height = getHeight();

		 if (mBitmap != null) {
			 Palette.buildRamp(mBitmap, Palette.getLinearColors());
			 if (mMatrix == null) {
				 mMatrix = new Matrix();	 		 
				 float sx = (float) width / (float) mBitmap.getWidth();
				 float sy = (float)  height / (float) mBitmap.getHeight();
				 mMatrix.postScale(sx, sy);
			 }
			 canvas.drawBitmap(mBitmap, mMatrix, null);			   
		 }

		 if (MainActivity.getNightMode())
			 mLinePaint.setARGB(255, 255, 0, 0);
		 else
			 mLinePaint.setARGB(255, 255, 255, 255);

		 int divisor = 5;
		 float interval = (float) width / (float) divisor;
		 String text = "-00 dB";
		 mLinePaint.getTextBounds(text, 0, text.length(), mTextRect);
		 int min_span = mTextRect.width() + 10;
		 while ((divisor > 1) && (interval < min_span)) {
			 divisor--;
			 interval = (float) width / (float) divisor;
		 }
		 
		 int db_interval = (MAX_DB - MIN_DB) / (divisor + 1);
		 int xpos;
		 int db = MIN_DB;
		 for (float x = 0; x < width; db += db_interval, x += interval) {
			 canvas.drawLine(x, 0, x, height, mLinePaint);
			 text = db + " dB";
			 mLinePaint.getTextBounds(text, 0, text.length(), mTextRect);
			 xpos = (int) (x + 10);
			 mShadowRect.left = xpos - 2f;
			 mShadowRect.right = mShadowRect.left + mTextRect.width() + 15f;
			 mShadowRect.top = (height - mTextRect.height()) / 2 - 2;
			 mShadowRect.bottom = height - mShadowRect.top;
			 canvas.drawRoundRect(mShadowRect, 4.0f, 4.0f, mShadowPaint);
			 canvas.drawText(text, xpos, (height + mTextRect.height()) / 2, mLinePaint);
		 }
	 }
	 
	   @Override
	   public boolean onTouchEvent(MotionEvent ev) {

	       mGestureDetector.onTouchEvent(ev);
	       return true;
	    }

	   private class GestureListener extends GestureDetector.SimpleOnGestureListener {

	        private static final int SWIPE_THRESHOLD = 100;
	        private static final int SWIPE_VELOCITY_THRESHOLD = 200;

	        public void onLongPress(MotionEvent e) {
					
				Palette.setCoefficient(1.0f, Palette.getType());
		      	activity.getPreferences().edit().putFloat("pcoeff"+Palette.getType(), Palette.getCoefficient(Palette.getType())).apply();
				
		      	invalidate();
				
				View spectrogram = activity.findViewById(R.id.spectrogram);
				spectrogram.setBackgroundColor(Palette.getColorsARGB()[0]);
				spectrogram.invalidate();

				if (activity.isPowerPopupVisible()) activity.getPowerView().invalidate();
			}
	   
	        public boolean onDoubleTap(MotionEvent e) {

				if (!MainActivity.getNightMode()) {
//				int pal = Palette.getType() + ((e.getX() > getWidth() / 2) ? 1 : -1);
//				if (pal < 0)
//					pal = Palette.NUM_PALETTES - 1;
//				else if (pal == Palette.NUM_PALETTES)
//					pal = 0;
					int pal = Palette.getType() + 1;
					if ((pal == Palette.NUM_PALETTES) || (pal == Palette.PALETTE_RED)) pal = 0;
					Palette.setType(pal);

					SharedPreferences prefs = activity.getPreferences();
					prefs.edit().putInt("palette", Palette.getType()).apply();

					invalidate();

					View spectrogram = activity.findViewById(R.id.spectrogram);
					spectrogram.setBackgroundColor(Palette.getLinearColors()[0]);
					spectrogram.invalidate();

					if (activity.isPowerPopupVisible()) activity.getPowerView().invalidate();
				}
				return true;
		    }
		    
		    @Override
	        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
				if (Math.abs(distanceY) < Math.abs(distanceX)) {
					if (distanceX < 0)
						onPaletteScroll(false);
					else
						onPaletteScroll(true);
				}
//	        	if (Math.abs(distanceY) > Math.abs(distanceX)) {
//	        		if (distanceY < 0)
//	        			onScrollDown(Math.abs(distanceY));
//	        		else
//	        			onScrollUp(distanceY);
//	        	}
//	        	else {
//		        	if (distanceX < 0)
//		        		onPaletteScroll(false);
//		        	else
//		        		onPaletteScroll(true);
//	        	}
	        	return true;
	        }
		    
//	       @Override
//	        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
//	            boolean result = false;
//	            try {
//	                float diffY = e2.getY() - e1.getY();
//	                float diffX = e2.getX() - e1.getX();
//	                if (Math.abs(diffX) > Math.abs(diffY)) {
//	                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
//	                        if (diffX > 0) {
//	                            onSwipeRight();
//	                        } else {
//	                            onSwipeLeft();
//	                        }
//	                    }
//	                    result = true;
//	                }
//
//	            } catch (Exception exception) {
//	                exception.printStackTrace();
//	            }
//	            return result;
//		    }

//		    public void onSwipeRight() {
//				onPaletteSwipe(false);
//		    }
//
//		    public void onSwipeLeft() {
//				onPaletteSwipe(true);
//		    }
		}
	   
	    private void onPaletteScroll(boolean direction) {
	    	
	    	float coef = Palette.getCoefficient(Palette.getType()) + (direction ? 0.1f : -0.1f);
			Palette.setCoefficient(coef, Palette.getType());
			activity.getPreferences().edit().putFloat("pcoeff"+Palette.getType(), Palette.getCoefficient(Palette.getType())).apply();
			  invalidate();
			
			View spectrogram = activity.findViewById(R.id.spectrogram);
			spectrogram.setBackgroundColor(Palette.getLinearColors()[0]);
			spectrogram.invalidate();
	     }

//	private void onScrollUp(float distance) {
//	   }
//
//	private void onScrollDown(float distance) {
//	   }
//
//	private void onPaletteSwipe(boolean direction) {
//		}

}
