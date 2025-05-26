/*
 * Setting Activity screen
 */

package com.digitalbiology.audio;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.Preference.OnPreferenceClickListener;

import androidx.core.content.ContextCompat;

import java.text.DecimalFormat;

public class SettingsActivity extends PreferenceActivity
{
	private static String version_n;
	private static Context context;

	static NumberPickerPreference timerOn;
	static NumberPickerPreference timerOff;

//	private static final int REQUEST_CODE_EMAIL = 1;

	@Override
	protected void onCreate(final Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);

		getFragmentManager().beginTransaction()
				.replace(android.R.id.content, new MyPreferenceFragment())
				.commit();

		PackageInfo pInfo = null;
		try {
			pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
		}
		catch (NameNotFoundException e) {
			e.printStackTrace();
		}
		
		if (pInfo != null)
			version_n = pInfo.versionName;
		else
			version_n = "";
		context = getApplicationContext();
	}

	public static class MyPreferenceFragment extends PreferenceFragment implements OnSharedPreferenceChangeListener
	{
		@Override
		public void onResume() 
		{
			super.onResume();
			getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
		}

		@Override
		public void onCreate(final Bundle savedInstanceState) 
		{
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.prefs);

			for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); i++) {
				updatePrefsSummary(getPreferenceManager()
						.getSharedPreferences(), getPreferenceScreen()
						.getPreference(i));
			}

			timerOn = (NumberPickerPreference) findPreference("timer_on");
			timerOn.setMinValue(1);
			timerOn.setMaxValue(60);
			timerOn.setDefaultValue(timerOn.getValue());

			timerOff = (NumberPickerPreference) findPreference("timer_off");
			timerOff.setMinValue(1);
			timerOff.setMaxValue(120);
			timerOff.setDefaultValue(timerOff.getValue());

			if ((Build.VERSION.SDK_INT >= 23) && (ContextCompat.checkSelfPermission(getActivity().getApplicationContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
				ContextCompat.checkSelfPermission(getActivity().getApplicationContext(), android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
				findPreference("geo").setEnabled(false);
				findPreference("geofreq").setEnabled(false);
				findPreference("geodist").setEnabled(false);
			}

//			if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
//				getPreferenceScreen().removePreference(findPreference("sms"));
//				getPreferenceScreen().removePreference(findPreference("sms_recp"));
//			}
//			else {
//				if ((Build.VERSION.SDK_INT >= 23) && (ContextCompat.checkSelfPermission(getActivity().getApplicationContext(), Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED)) {
//					findPreference("sms").setEnabled(false);
//					findPreference("sms_recp").setEnabled(false);
//				}
//				else {
//					findPreference("sms_recp").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
//						@Override
//						public boolean onPreferenceChange(Preference preference, Object value) {
//							return (value != null && PhoneNumberUtils.isGlobalPhoneNumber(value.toString()));
//						}
//					});
//					findPreference("sms_recp").setEnabled(((CheckBoxPreference) findPreference("sms")).isChecked());
//				}
//			}
//
//			findPreference("email_recp").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
//				@Override
//				public boolean onPreferenceChange(Preference preference, Object value) {
//					return (value != null && android.util.Patterns.EMAIL_ADDRESS.matcher(value.toString()).matches());
//				}
//			});
//			findPreference("email_recp").setEnabled(((CheckBoxPreference) findPreference("email")).isChecked());
//
//			findPreference("email").setOnPreferenceClickListener(new OnPreferenceClickListener() {
//				@Override
//				public boolean onPreferenceClick(Preference preference) {
//					if (((CheckBoxPreference) preference).isChecked()) {
//						try {
//							Intent intent = AccountPicker.newChooseAccountIntent(null, null,
//									new String[]{GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE}, false, getString(R.string.reply_email), null, null, null);
//							startActivityForResult(intent, REQUEST_CODE_EMAIL);
//						} catch (ActivityNotFoundException e) {
//							// TODO
//						}
//					}
//					return true;
//				}
//			});

			findPreference("help").setOnPreferenceClickListener(new OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					Intent intent = new Intent(context, HelpActivity.class);
					startActivity(intent);
					return true;
				}
			});

			ListPreference sampleRate = (ListPreference) findPreference("samplerate");
			Microphone microphone = MainActivity.getMicrophone();
			int[] sampleRates = microphone.getSampleRates();
			CharSequence[] entries = new CharSequence[sampleRates.length];
			CharSequence[] entryValues = new CharSequence[sampleRates.length];
			DecimalFormat form = new DecimalFormat("#0.0");
			for (int ii = 0; ii < sampleRates.length; ii++) {
				entries[ii] = form.format((float)sampleRates[ii]/1000.0f)+" kHz";
				entryValues[ii] = Integer.toString(sampleRates[ii]);
			}
			sampleRate.setEntries(entries);
			sampleRate.setEntryValues(entryValues);
			String rateString = Integer.toString(microphone.getSampleRate());
			sampleRate.setValue(rateString);
			sampleRate.setSummary(sampleRate.getEntry());
			if (sampleRates.length < 2) sampleRate.setEnabled(false);

			Preference version = findPreference("version");
			version.setSummary(version_n);
		}

//		@Override
//		public void onActivityResult(int requestCode, int resultCode, Intent data) {
//			if (requestCode == REQUEST_CODE_EMAIL) {
//				String accountName;
//				if (resultCode == RESULT_OK) {
//					accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
//				}
//				else {
//					accountName = "";
//				}
//				PreferenceManager.getDefaultSharedPreferences(context).edit().putString("account", accountName).apply();
//				findPreference("email").setSummary(context.getString(R.string.reply) + " " + accountName);
//
//				if (accountName.isEmpty()) {
//					((CheckBoxPreference) findPreference("email")).setChecked(false);
//					findPreference("email_recp").setEnabled(false);
//				}
//			}
//			else
//				super.onActivityResult(requestCode, resultCode, data);
//		}

		@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) 
		{
			if (key.equals("locale")) {
				new AlertDialog.Builder(this.getActivity(), R.style.CustomDialog)
					.setMessage(R.string.locale_change)
					.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
						}
					})
						.show();
//			} else if (key.equals("sms")) {
//				findPreference("sms_recp").setEnabled(((CheckBoxPreference) findPreference("sms")).isChecked());
//			} else if (key.equals("email")) {
//				findPreference("email_recp").setEnabled(((CheckBoxPreference) findPreference("email")).isChecked());
			}
			else if (key.equals("samplerate")) {
				int sampleRate = Integer.parseInt(sharedPreferences.getString("samplerate", "0"));
				Microphone microphone = MainActivity.getMicrophone();
				if (microphone != null) {
					microphone.setSampleRate(sampleRate);
					if (microphone.getType() == Microphone.USB_MICROPHONE) {
						String prefTag = String.format("%08X%08X", ((UsbMicrophone) microphone).getVendorId(), ((UsbMicrophone) microphone).getProductId());
						sharedPreferences.edit().putInt(prefTag, sampleRate).apply();
					}
				}
			}
			updatePrefsSummary(sharedPreferences, findPreference(key));
		}
	}

	private static void updatePrefsSummary(SharedPreferences sharedPreferences, Preference pref)
	{
		if (pref == null) return;

		if (pref instanceof ListPreference) {
			ListPreference listPref = (ListPreference) pref;
			listPref.setSummary(listPref.getEntry());
		}
		else if (pref instanceof EditTextPreference) {
			EditTextPreference editPref = (EditTextPreference) pref;
//			if (editPref.getKey().equals("sms_recp")) {
//				String phone = editPref.getText();
//				if (phone != null) {
//					if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
//						pref.setSummary(PhoneNumberUtils.formatNumber(phone, Locale.getDefault().getISO3Country()));
//					else
//						pref.setSummary(PhoneNumberUtils.formatNumber(phone));
//				}
//			}
//			else
				editPref.setSummary(editPref.getText());
		}
		else if (pref instanceof NumberPickerPreference) {
			NumberPickerPreference pickerPref = (NumberPickerPreference) pref;
			String summary = Integer.toString(pickerPref.getValue()) + " ";
			if (pickerPref.getValue() == 1)
				summary += context.getString(R.string.timer_unit);
			else
				summary += context.getString(R.string.timer_units);
			pickerPref.setSummary(summary);
		}
//		else if (pref.hasKey() && pref.getKey().equals("email")) {
//			pref.setSummary(context.getString(R.string.reply));
//		}
	}
}
