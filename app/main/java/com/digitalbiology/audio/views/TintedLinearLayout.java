package com.digitalbiology.audio.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import com.digitalbiology.audio.MainActivity;

public class TintedLinearLayout extends LinearLayout {
    private Paint m_paint;

    public TintedLinearLayout(Context context) {
        super(context);
        _Init();
    }

    public TintedLinearLayout(Context context, AttributeSet attrs)  {
        super(context, attrs);
        _Init();
    }

    public TintedLinearLayout(Context context, AttributeSet attrs, int defStyleAttr)  {
        super(context, attrs, defStyleAttr);
        _Init();
    }

    public TintedLinearLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        _Init();
    }

    private void
    _Init()
    {

        float[] colorTransform =
                {
                        1, 0, 0, 0, 0, // R color
                        0, 0, 0, 0, 0,  // G color
                        0, 0, 0, 0, 0,  // B color
                        0, 0, 0, 1f, 0f // alpha
                };

        ColorMatrix cm = new ColorMatrix();
        cm.set(colorTransform);
        m_paint = new Paint();
        m_paint.setColorFilter(new ColorMatrixColorFilter(cm));
//        m_paint.setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
    }

    @Override protected void
    dispatchDraw(Canvas canvas)
    {
        if (MainActivity.getNightMode()) {
            canvas.saveLayer(null, m_paint, Canvas.ALL_SAVE_FLAG);
            super.dispatchDraw(canvas);
            canvas.restore();
        }
        else
            super.dispatchDraw(canvas);
    }
}
