package com.digitalbiology.audio;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
//import android.os.Build;

import android.preference.PreferenceManager;
import androidx.multidex.MultiDex;
import androidx.multidex.MultiDexApplication;

import java.util.Locale;

public class BatRecorderApp extends MultiDexApplication
{
    private Locale locale = null;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);
        MultiDex.install(this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
        if (locale != null) {
            newConfig.setLocale(locale);
            Locale.setDefault(locale);
            getBaseContext().getResources().updateConfiguration(newConfig, getBaseContext().getResources().getDisplayMetrics());
        }
    }

    @Override
    public void onCreate()
    {
        super.onCreate();

        Configuration config = getBaseContext().getResources().getConfiguration();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        String lang = settings.getString("locale", "");
        if (!lang.isEmpty() && !config.getLocales().get(0).getLanguage().equals(lang)) {
            locale = new Locale(lang);
            Locale.setDefault(locale);
            config.setLocale(locale);
            getBaseContext().getResources().updateConfiguration(config, getBaseContext().getResources().getDisplayMetrics());
        }
    }
}
