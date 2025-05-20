package com.digitalbiology.audio;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.View;

import com.yahoo.mobile.client.android.util.rangeseekbar.RangeSeekBar;

public class RangeSeekBarPreference extends Preference implements RangeSeekBar.OnRangeSeekBarChangeListener {

    private static final int MIN_FREQUENCY = 1;
    private static final int MAX_FREQUENCY = 200;

    private RangeSeekBar<Integer> mSeekBar;
    private Integer mMinVal;
    private Integer mMaxVal;
    private final SharedPreferences mPrefs;

    public RangeSeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        mMinVal = mPrefs.getInt(getKey()+"_min", 15);
        mMaxVal = mPrefs.getInt(getKey()+"_max", 200);
    }

    @Override
    protected void onBindView(View rootView) {

        super.onBindView(rootView);

        mSeekBar = (RangeSeekBar) rootView.findViewById(R.id.rangebar);
        mSeekBar.setRangeValues(MIN_FREQUENCY, MAX_FREQUENCY);
        mSeekBar.setUnits("kHz");
        mSeekBar.setSelectedMinValue(mMinVal);
        mSeekBar.setSelectedMaxValue(mMaxVal);

        mSeekBar.setOnRangeSeekBarChangeListener(this);
    }

    @Override
    public void onRangeSeekBarValuesChanged(RangeSeekBar bar, Object minValue, Object maxValue) {
        mMinVal = (Integer) minValue;
        mMaxVal = (Integer) maxValue;
        mPrefs.edit().putInt(getKey()+"_min", mMinVal).apply();
        mPrefs.edit().putInt(getKey()+"_max", mMaxVal).apply();
    }
}