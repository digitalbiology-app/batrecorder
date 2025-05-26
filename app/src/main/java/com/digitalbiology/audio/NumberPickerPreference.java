package com.digitalbiology.audio;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.NumberPicker;

public class NumberPickerPreference extends DialogPreference {

    private NumberPicker mPicker;
    private Integer mMinNumber;
    private Integer mMaxNumber;
    private Integer mNumber = 1;
    private final Context mContext;

    public NumberPickerPreference(Context context, AttributeSet attrs) {
//        this(context, attrs, 0);
        super(context, attrs);
        mContext = context;
    }

//    public NumberPickerPreference(Context context, AttributeSet attrs, int defStyle) {
//        super(context, attrs, defStyle);
//        setPositiveButtonText(android.R.string.ok);
//        setNegativeButtonText(android.R.string.cancel);
//        mPicker = new NumberPicker(getContext());
//    }

    public void setMinValue(Integer value) {
        mMinNumber = value;
    }

    public void setMaxValue(Integer value) {
        mMaxNumber = value;
    }

    public void setDefaultValue(Integer value) {
        mNumber = value;
    }

    @Override
    protected View onCreateDialogView() {
        mPicker = new NumberPicker(getContext());
        mPicker.setMinValue(mMinNumber);
        mPicker.setMaxValue(mMaxNumber);
        mPicker.setValue(mNumber);
        return mPicker;
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            // needed when user edits the text field and clicks OK
            mPicker.clearFocus();
            setValue(mPicker.getValue());
        }
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        setValue(restoreValue ? getPersistedInt(mNumber) : (Integer) defaultValue);
    }

    public void setValue(int value) {
        if (shouldPersist()) {
            persistInt(value);
        }

        if (value != mNumber) {
            mNumber = value;
            notifyChanged();
            String summary = Integer.toString(mNumber) + " ";
            if (mNumber == 1)
                summary += mContext.getString(R.string.timer_unit);
            else
                summary += mContext.getString(R.string.timer_units);
            setSummary(summary);
        }
    }

    public int getValue() {
        return mNumber;
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInt(index, 0);
    }
}
