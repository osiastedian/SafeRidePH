package io.ted.saferideph.models;

import android.location.Location;

import com.google.firebase.database.IgnoreExtraProperties;

import java.util.ArrayList;

@IgnoreExtraProperties
public class Trip {
    public ArrayList<Location> locations;
    public String id;

    public Trip() {
    }
}
