package com.digitalbiology.audio.views;

import android.widget.ArrayAdapter;

import android.content.Context;
import androidx.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.digitalbiology.SpeciesDataModel;
import com.digitalbiology.audio.MainActivity;
import com.digitalbiology.audio.R;

import java.util.ArrayList;

public class SpeciesListAdapter extends ArrayAdapter {

        private final ArrayList<SpeciesDataModel> dataSet;
        final Context mContext;

        // View lookup cache
        private static class ViewHolder {
            TextView txtName;
            CheckBox checkBox;
        }

        public SpeciesListAdapter(Context context, ArrayList<SpeciesDataModel> data) {
            super(context, MainActivity.getNightMode() ? R.layout.species_night_list : R.layout.species_list, data);
            this.dataSet = data;
            this.mContext = context;

        }
        @Override
        public int getCount() {
            return dataSet.size();
        }

        public int getPositionForSpecies(String species) {
            for (int ii = 0; ii < dataSet.size(); ii++) {
                if (dataSet.get(ii).name.equals(species)) return ii;
            }
            return -1;
        }

        @Override
        public SpeciesDataModel getItem(int position) {
            return dataSet.get(position);
        }

        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {

            final ViewHolder viewHolder;
            final View result;
            final ArrayList<SpeciesDataModel> dataset = this.dataSet;

            if (convertView == null) {
                viewHolder = new ViewHolder();
                convertView = LayoutInflater.from(parent.getContext()).inflate(MainActivity.getNightMode() ? R.layout.species_night_list : R.layout.species_list, parent, false);

                viewHolder.txtName = (TextView) convertView.findViewById(R.id.species_list_item);
                viewHolder.txtName.setTag(dataset.get(position));

                viewHolder.checkBox = (CheckBox) convertView.findViewById(R.id.species_checkbox);
                viewHolder.checkBox.setTag(dataset.get(position));

                viewHolder.txtName.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        SpeciesDataModel item = (SpeciesDataModel) v.getTag();
                        item.selected = !item.selected;
                        viewHolder.checkBox.setChecked(item.selected);
                    }
                });

                viewHolder.checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    public void onCheckedChanged(CompoundButton cb, boolean isChecked) {
                        ((SpeciesDataModel) cb.getTag()).selected = isChecked;
                    }
                });

                result = convertView;
                convertView.setTag(viewHolder);

            } else {
                viewHolder = (ViewHolder) convertView.getTag();
                viewHolder.txtName.setTag(dataset.get(position));
                viewHolder.checkBox.setTag(dataset.get(position));
                result = convertView;
            }

            SpeciesDataModel item = getItem(position);
            viewHolder.txtName.setText(item.name);
            viewHolder.checkBox.setChecked(item.selected);

            return result;
        }
    }
