package io.ted.saferideph;

import com.google.android.gms.maps.model.LatLng;

import java.util.UUID;

public class NearbyPlace  {
    private String id;
    private double latitude;
    private double longitude;
    private String name;
    private String[] types;

    public NearbyPlace() {
        this.id = UUID.randomUUID().toString();
    }

    public NearbyPlace(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.id = UUID.randomUUID().toString();
    }

    public NearbyPlace(double latitude, double longitude, String name) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.name = name;
        this.id = UUID.randomUUID().toString();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String[] getTypes() {
        return types;
    }

    public void setTypes(String[] types) {
        this.types = types;
    }

    public LatLng createLatLng() {
        return new LatLng(latitude, longitude);
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof NearbyPlace)) {
            return  false;
        }
        NearbyPlace param = (NearbyPlace)obj;
        return (param.id == this.id) || (param.latitude == latitude && param.longitude == longitude);
    }
}