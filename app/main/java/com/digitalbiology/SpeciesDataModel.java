package com.digitalbiology;

public class SpeciesDataModel {

    public final String name;
    public boolean selected;

    public SpeciesDataModel(String name, boolean checked) {
        this.name = name;
        this.selected = checked;
    }
}
