/*
package com.digitalbiology.audio;


import java.util.Date;
import java.util.LinkedList;

public class NotificationToken {

    private static LinkedList<NotificationToken> mTriggerNotifications = new LinkedList<NotificationToken>();
    private static long mLastEmailTime = 0L;
    public static final long MIN_EMAIL_DELAY = 600000L;    // One email at most every 10 minutes.

    public static LinkedList<NotificationToken> getNotifications() {
        return mTriggerNotifications;
    }

    static void setLastEmailTime(long millisecs) {
        mLastEmailTime = millisecs;
    }

    static long getLastEmailTime() {
        return mLastEmailTime;
    }

    public Date timestamp = null;
    public String latitude = null;
    public String longitude = null;
}
*/