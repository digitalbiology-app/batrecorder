package com.digitalbiology.audio.views;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.PopupWindow;

import androidx.core.content.ContextCompat;

import com.digitalbiology.audio.MainActivity;
import com.digitalbiology.audio.R;

public class TouchHorizontalScrollView extends HorizontalScrollView {

	private MainActivity activity;
	private ScaleGestureDetector 	mScaleDetector;
	private ScaleListener			mScaleListener;
	private GestureDetector 		mGestureDetector;
	private int						mResizeViewDragOffset;
	private int						mMinWaveHeight;
	private boolean 				mHasLongPress = false;
	private boolean					mResizing = false;
	private boolean 				mDraggingFeature = false;
	private boolean 				mDraggingHead = false;

   public TouchHorizontalScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        if(!isInEditMode()) init(context);
   }
   
   private void init(Context context) {
	   activity = (MainActivity) context;
	   mScaleListener = new ScaleListener();
	   mScaleDetector = new ScaleGestureDetector(context, mScaleListener);
	   mGestureDetector = new GestureDetector(context, new GestureListener());
   }

	public void setMinWaveHeight(int height) {
		mMinWaveHeight = height;
//		mMinWaveHeight = (int) (120f * Resources.getSystem().getDisplayMetrics().density);
	}

	public void resizeChildViews(int height) {

		View waveFormView = findViewById(R.id.waveform);
		int max_height = getHeight() - mMinWaveHeight - activity.findViewById(R.id.time_tick).getHeight();
		height = Math.max(mMinWaveHeight, Math.min(height, max_height));
		ViewGroup.LayoutParams lp = waveFormView.getLayoutParams();
		lp.height = height;
		waveFormView.setLayoutParams(lp);

		View view = activity.findViewById(R.id.waveform_control_layout);
		lp = view.getLayoutParams();
		lp.height = height;
		view.setLayoutParams(lp);

		FreqTickView freqTickView = (FreqTickView) activity.findViewById(R.id.freq_tick);
		lp = freqTickView.getLayoutParams();
		lp.height = getHeight() - height - activity.findViewById(R.id.time_tick).getHeight();
		freqTickView.setLayoutParams(lp);
		freqTickView.rebuildBitmap();

//		DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
//		View navigateView = activity.findViewById(R.id.navigate_overlay);
//		ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) navigateView.getLayoutParams();
//		mlp.setMargins(0, (int) ((float) (height-navigateView.getHeight()) / (2.0f * metrics.density)), 20, 0);
//		navigateView.setLayoutParams(mlp);

		findViewById(R.id.spectrogram).invalidate();
		waveFormView.invalidate();
		freqTickView.invalidate();
//		navigateView.invalidate();
	}

   @Override
   public boolean onTouchEvent(MotionEvent ev) {

	   if (mResizing && (ev.getAction() == MotionEvent.ACTION_MOVE)) {

		   int pointerIndex = ev.findPointerIndex(ev.getPointerId(0));
		   int height = (int) ev.getY(pointerIndex) - mResizeViewDragOffset;
		   resizeChildViews(height);

		   activity.getPreferences().edit().putInt("waveheight", height).apply();
		   return true;
	   }
        mResizing = false;

        mScaleDetector.onTouchEvent(ev);
		if (!mScaleDetector.isInProgress()) {
			mScaleListener.dismissPopup();
			if ((ev.getPointerCount() > 2) && (ev.getAction() == MotionEvent.ACTION_MOVE)) {
				int pointerIndex = ev.findPointerIndex(ev.getPointerId(0));
				int height = (int) ev.getY(pointerIndex) - mResizeViewDragOffset;
				resizeChildViews(height);

				activity.getPreferences().edit().putInt("waveheight", height).apply();
				mResizing = true;
			}
			else if (mHasLongPress) {
				if (MainActivity.getDataMode() == MainActivity.DATA_MODE_PLAY) {
					if (ev.getAction() == MotionEvent.ACTION_UP) {
						mHasLongPress = false;
						activity.setPlayEnd(getScrollX() + (int) ev.getX());
						return true;
					} else if (ev.getAction() == MotionEvent.ACTION_MOVE) {
						activity.setPlayEnd(getScrollX() + (int) ev.getX());
						return true;
					}
				}
				else {
					if (ev.getAction() == MotionEvent.ACTION_UP) {
						mHasLongPress = false;
						activity.setPlayStart(getScrollX() + (int) ev.getX());
						return true;
					} else if (ev.getAction() == MotionEvent.ACTION_MOVE) {
						activity.setPlayStart(getScrollX() + (int) ev.getX());
						return true;
					}
				}
			} else if (!mGestureDetector.onTouchEvent(ev))
				if (!mDraggingFeature && !mDraggingHead) super.onTouchEvent(ev);
		}
       return true;
    }

	private class GestureListener extends GestureDetector.SimpleOnGestureListener {

	   	private SpectrogramView mSpectrogramView = null;
		private WaveformView	mWaveformView = null;
	   
	   private WaveformView getWaveformView() {
		   if (mWaveformView == null)
			   mWaveformView = (WaveformView) activity.findViewById(R.id.waveform);
		   return mWaveformView;
	   }
	   private SpectrogramView getSpectrogramView() {
		   if (mSpectrogramView == null)
			   mSpectrogramView = (SpectrogramView) activity.findViewById(R.id.spectrogram);
		   return mSpectrogramView;
	   }

	   public boolean onDown(MotionEvent e) {
		   int wv_height = getWaveformView().getHeight();
		   if ((int) e.getY() < wv_height) {
			   int xpos = getScrollX() + (int) e.getX();
			   if ((xpos <= activity.getZoomedPlayStart()) || (xpos >= activity.getZoomedPlayEnd())) {
				   if (!activity.isListening() && !activity.isPlaying()) {
					   activity.setPlayStart(xpos);
					   mDraggingHead = getWaveformView().handleStartDrag(xpos);
				   }
			   }
			   mDraggingFeature = false;
		   }
		   else {
			   mDraggingHead = false;
			   mDraggingFeature = getSpectrogramView().handleStartDrag(getScrollX() + (int) e.getX(), (int) e.getY() - wv_height);
			   if (!mDraggingFeature) {
				   int pointerIndex = e.findPointerIndex(e.getPointerId(0));
				   mResizeViewDragOffset = (int) e.getY(pointerIndex) - getWaveformView().getHeight();
				   return super.onDown(e);
			   }
		   }
		   return true;
	   }

	   public boolean onSingleTapConfirmed(MotionEvent e) {
		   int wv_height = getWaveformView().getHeight();
		   if ((int) e.getY() >= wv_height)
//			   if ((MainActivity.getDataMode() == MainActivity.DATA_MODE_PLAY) && !activity.isPlaying()) {
////			   		activity.doStop();
//				   activity.setPlayStart(getScrollX() + (int) e.getX());
//			   }
//		   }
//		   else
			   getSpectrogramView().handleSingleTap(getScrollX() + (int) e.getX(), (int) e.getY() - wv_height);
		   return super.onSingleTapConfirmed(e);
	   }

	   public void onLongPress(MotionEvent e) {

		   int wv_height = getWaveformView().getHeight();
		   if ((int) e.getY() < wv_height) {
			   if (!activity.isPlaying() && !activity.isListening()) {
				   int xpos = getScrollX() + (int) e.getX();
				   activity.setPlayStart(xpos);
//				   if (MainActivity.getDataMode() == MainActivity.DATA_MODE_PLAY)
					   mHasLongPress = true;
//				   else
//					   mDraggingHead = getWaveformView().handleStartDrag(xpos);
			   }
		   }
		   else
			   getSpectrogramView().handleLongTouch(getScrollX() + (int) e.getX(), (int) e.getY() - wv_height);
	   }

	   public boolean onDoubleTap(MotionEvent e) {

		   int xpos = getScrollX() + (int) e.getX();
		   int wv_height = getWaveformView().getHeight();
		   if ((int) e.getY() < wv_height) {
			   if ((xpos > activity.getZoomedPlayStart()) && (xpos < activity.getZoomedPlayEnd())) {
				   activity.snipRecording();
				   return true;
			   }
		   }
		   else
			   getSpectrogramView().handleDoubleTap(xpos, (int) e.getY() - wv_height);
		   return true;
	   }

	   @Override
	   public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
		   if (mDraggingFeature) {
			   int wv_height = getWaveformView().getHeight();
			   getSpectrogramView().handleDrag(getScrollX() + (int) e2.getX(), (int) e2.getY() - wv_height);
			   return true;
		   }
		   else if (mDraggingHead) {
			   activity.setPlayStart(getScrollX() + (int) e2.getX());
			   return true;
		   }
		   if (Math.abs(distanceY) > Math.abs(distanceX)) {
			   activity.onVerticalScroll(distanceY);
			   return true;
		   }
		   return false;
	   }
   }
   
	private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {

		private boolean mIsHorizontal;
		private boolean mFeatureSelected;
		private SpectrogramView	mSpectrogramView;
		private InfoOverlayView mZoomView = null;
		private PopupWindow mZoomPopup = null;

		@Override
		public boolean onScaleBegin(ScaleGestureDetector detector) {
			mIsHorizontal = (detector.getCurrentSpanX() > detector.getCurrentSpanY());
			mSpectrogramView = (SpectrogramView) activity.findViewById(R.id.spectrogram);
			mFeatureSelected = mSpectrogramView.featureSelected();
			if (!mFeatureSelected) {
				LayoutInflater layoutInflater = (LayoutInflater) activity.getBaseContext().getSystemService(MainActivity.LAYOUT_INFLATER_SERVICE);
				View popupView = layoutInflater.inflate(R.layout.record, null);
				mZoomPopup = new PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

				DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
				int xpos = (int) (20f * metrics.density);
				int ypos = (int) (100f * metrics.density);

				// Ordering of showAtLocation here is important!!!!
				mZoomPopup.showAtLocation(mSpectrogramView, Gravity.RIGHT | Gravity.BOTTOM, xpos, ypos);

				mZoomPopup.setBackgroundDrawable(ContextCompat.getDrawable(activity, R.drawable.translucent_background));
				mZoomPopup.setOutsideTouchable(true);
				mZoomPopup.setFocusable(false);
				mZoomView = (InfoOverlayView) popupView.findViewById(R.id.record_time);
				mZoomView.displayZoomFactor();
				mZoomView.makeDirty(mFeatureSelected ? activity.getZoomX() : activity.getZoomY());
			}
			return true;
		}
		
		@Override
		public boolean onScale(ScaleGestureDetector detector) {
			if (mFeatureSelected) {
				mSpectrogramView.resizeFeature(detector.getCurrentSpanX() / detector.getPreviousSpanX(), detector.getCurrentSpanY() / detector.getPreviousSpanY());
			}
			else {
				if (mIsHorizontal) {
					activity.onHorizontalScale(detector.getCurrentSpanX() / detector.getPreviousSpanX(), detector.getFocusX());
					mZoomView.makeDirty(activity.getZoomX());
				}
				else {
					activity.onVerticalScale(detector.getCurrentSpanY() / detector.getPreviousSpanY(), detector.getFocusY());
					mZoomView.makeDirty(activity.getZoomY());
				}
			}
			return true;
		}

		@Override
		public void onScaleEnd(ScaleGestureDetector detector) {
			if (mZoomView != null) {
				mZoomView.makeDirty(mIsHorizontal ? activity.getZoomX() : activity.getZoomY());
			}
		}

		public void dismissPopup() {
			if (mZoomPopup != null) {
				mZoomPopup.dismiss();
				mZoomPopup = null;
				mZoomView = null;
			}
		}
   }
}
