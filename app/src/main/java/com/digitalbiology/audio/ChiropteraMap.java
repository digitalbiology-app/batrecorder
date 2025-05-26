package com.digitalbiology.audio;

import android.content.Context;
import android.graphics.PointF;

import androidx.annotation.NonNull;

import com.digitalbiology.SpeciesDataModel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Scanner;

/**
 * Created by bkraus on 4/2/2016.
 */
class ChiropteraMap {

    public static final int GLOBAL_REGION = 0;

    private ArrayList<SpeciesDataModel> species = null;
    private RegionPoly[] regions = null;
    private int regionLocale;

    public ChiropteraMap(@NonNull Context context) {

        species = new ArrayList<>();
        regionLocale = GLOBAL_REGION;
        try {
//            Pattern regex = Pattern.compile("^#.*$");
            Scanner scanner = new Scanner(context.getAssets().open("regions.txt")).useLocale(Locale.ENGLISH);
            int count = scanner.nextInt();
            regions = new RegionPoly[count];
            for (int ii = 0; ii < count; ++ii) {
                RegionPoly region = new RegionPoly();
                scanner.nextLine(); // skip the empty string
                scanner.nextLine(); // skip the comment string
                region.id = scanner.nextInt();
                int vcount = scanner.nextInt();
                region.vertices = new PointF[vcount];
                for (int jj = 0; jj < vcount; ++jj) {
                    region.vertices[jj] = new PointF();
                    region.vertices[jj].x = scanner.nextFloat();
                    region.vertices[jj].y = scanner.nextFloat();
                }
                regions[ii] = region;
            }
            scanner.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int calcRegion(float latitude, float longitude) {
        if (regions != null) {
            for (RegionPoly region : regions) {
                if (region.isInside(latitude, longitude)) {
                    return region.id;
                }
            }
        }
        return GLOBAL_REGION;
    }

    public int getRegion() {
        return regionLocale;
    }

    private class RegionPoly {

        public int id;
        public PointF[] vertices;

        public boolean isInside(float latitude, float longitude) {

            boolean inside = false;
            int i = 0;
            int j = vertices.length - 1;
            for (; i < vertices.length; j = i++) {
                if (((vertices[i].y > latitude) != (vertices[j].y > latitude))
                        && (longitude < (vertices[j].x - vertices[i].x) * (latitude - vertices[i].y) / (vertices[j].y - vertices[i].y) + vertices[i].x))
                    inside = !inside;
            }
            return inside;
        }
    }

    public ArrayList<SpeciesDataModel> loadSpeciesList(@NonNull Context context, int region) {

        regionLocale = region;
        species.clear();
        try {
            InputStream inputreader = context.getAssets().open("bat"+region+".txt");
            BufferedReader br = new BufferedReader(new InputStreamReader(inputreader));
            String line;
            while ((line = br.readLine()) != null) {
                species.add(new SpeciesDataModel(line, false));
            }
            br.close();
        }
        catch (Exception e) {
        }
        return species;
    }
}
