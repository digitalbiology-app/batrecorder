package com.digitalbiology.audio;

import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.preference.ListPreference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;

public class TwoLinesListPreference extends ListPreference {

    private CharSequence[] mEntries;
    private CharSequence[] mEntryValues;
    private CharSequence[] mEntriesSubtitles;
    private String mValue;
    private int mClickedDialogEntryIndex;

    public TwoLinesListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onPrepareDialogBuilder(Builder builder) {
        super.onPrepareDialogBuilder(builder);

        mEntries = getEntries();
        mEntryValues = getEntryValues();
        mEntriesSubtitles = getEntriesSubtitles();
        mValue = getValue();
        mClickedDialogEntryIndex = getValueIndex();

        if (mEntries == null || mEntryValues == null || mEntriesSubtitles == null) {
            throw new IllegalStateException(
                    "ListPreference requires an entries array and an entryValues array.");
        }

//        String[] mEntriesString = (String[]) mEntries;

        // adapter
        ListAdapter adapter = new ArrayAdapter<CharSequence>(
                getContext(), R.layout.two_lines_list_preference_row, mEntries) {

            ViewHolder holder;

            class ViewHolder {
                TextView title;
                TextView subTitle;
                ImageView selectedIndicator;
            }

            public View getView(int position, View convertView, ViewGroup parent) {
                final LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                if (convertView == null) {
                    convertView = inflater.inflate(R.layout.two_lines_list_preference_row, null);

                    holder = new ViewHolder();
                    holder.title = (TextView) convertView.findViewById(R.id.custom_list_view_row_text_view);
                    holder.subTitle = (TextView) convertView.findViewById(R.id.custom_list_view_row_subtext_view);

                    convertView.setTag(holder);
                } else {
                    // view already defined, retrieve view holder
                    holder = (ViewHolder) convertView.getTag();
                }

                holder.title.setText(mEntries[position]);
                holder.subTitle.setText(mEntriesSubtitles[position]);

                return convertView;
            }
        };

        builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mClickedDialogEntryIndex = which;
				/*
				 * Clicking on an item simulates the positive button click, and
				 * dismisses the dialog.
				 */
                TwoLinesListPreference.this.onClick(dialog, DialogInterface.BUTTON_POSITIVE);
                dialog.dismiss();
            }
        });

        /*
         * The typical interaction for list-based dialogs is to have
         * click-on-an-item dismiss the dialog instead of the user having to
         * press 'Ok'.
         */
        builder.setPositiveButton(null, null);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult && mClickedDialogEntryIndex >= 0 && mEntryValues != null) {
            String value = mEntryValues[mClickedDialogEntryIndex].toString();
            if (callChangeListener(value)) {
                setValue(value);
            }
        }
    }

    /**
     * Returns the index of the given value (in the entry values array).
     *
     * @param value The value whose index should be returned.
     * @return The index of the value, or -1 if not found.
     */
    public int findIndexOfValue(String value) {
        if (value != null && mEntryValues != null) {
            for (int i = mEntryValues.length - 1; i >= 0; i--) {
                if (mEntryValues[i].equals(value)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private int getValueIndex() {
        return findIndexOfValue(mValue);
    }

    public CharSequence[] getEntriesSubtitles() {
//        return mEntriesSubtitles;
        return getEntryValues();
    }

//    public void setEntriesSubtitles(CharSequence[] mEntriesSubtitles) {
//        this.mEntriesSubtitles = mEntriesSubtitles;
//    }

}
