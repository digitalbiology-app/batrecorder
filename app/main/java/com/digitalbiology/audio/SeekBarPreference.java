package com.digitalbiology.audio;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.View;

import com.yahoo.mobile.client.android.util.rangeseekbar.RangeSeekBar;

public class SeekBarPreference extends Preference implements RangeSeekBar.OnRangeSeekBarChangeListener {

    private static final int MIN_DECIBELS = -40;
    private static final int MAX_DECIBELS = 0;

    private RangeSeekBar<Integer> mSeekBar;
    private Integer mVal;
    private final SharedPreferences mPrefs;

    public SeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        mVal = Integer.parseInt(mPrefs.getString("decibels", "-5"));
    }

    @Override
    protected void onBindView(View rootView) {

        super.onBindView(rootView);

        mSeekBar = (RangeSeekBar) rootView.findViewById(R.id.rangebar);
        mSeekBar.setRangeValues(MIN_DECIBELS, MAX_DECIBELS);
        mSeekBar.setUnits("dB");
        mSeekBar.setSingleThumb(true);
        mSeekBar.setReversed(true);
        mSeekBar.setSelectedMinValue(mVal);
        mSeekBar.setSelectedMaxValue(MAX_DECIBELS);

        mSeekBar.setOnRangeSeekBarChangeListener(this);
    }

    @Override
    public void onRangeSeekBarValuesChanged(RangeSeekBar bar, Object minValue, Object maxValue) {
        mVal = (Integer) minValue;
        mPrefs.edit().putString("decibels", mVal.toString()).apply();
    }
}