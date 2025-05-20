package com.digitalbiology.audio.utils;

import java.text.DecimalFormat;

public class TimeAxisFormat {

    final DecimalFormat secondsFormat;
    final DecimalFormat seconds2Format;

    public TimeAxisFormat() {

        secondsFormat = new DecimalFormat("#0.0##");
        seconds2Format = new DecimalFormat("00.00");
    }

    public String format(float secs) {

        if (secs >= 60.f) {
            int hours = (int) (secs / 3600.0f);
            secs -= (float) hours * 3600.f;
            int minutes = (int) (secs / 60.f);
            secs -= (float) minutes * 60.f;

            if (hours > 0)
                return (hours + ":" + String.format("%02d", minutes) + ":" + seconds2Format.format(secs));
            return (minutes + ":" + seconds2Format.format(secs));

        }
        return secondsFormat.format(secs);
    }

    public String formatFull(float secs) {

        int hours = (int) (secs / 3600.0f);
        secs -= (float) hours * 3600.f;
        int minutes = (int) (secs / 60.f);
        secs -= (float) minutes * 60.f;

        return (String.format("%02d", hours) + ":" + String.format("%02d", minutes) + ":" + seconds2Format.format(secs));
    }
}
