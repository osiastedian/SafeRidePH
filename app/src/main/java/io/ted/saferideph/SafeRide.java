package io.ted.saferideph;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.LocationSource;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SafeRide implements
        OnMapReadyCallback,
        GoogleMap.OnMyLocationButtonClickListener,
        GoogleMap.OnCameraMoveListener,
        GoogleMap.OnMyLocationClickListener,
        ActivityCompat.OnRequestPermissionsResultCallback,
        LocationSource.OnLocationChangedListener,
        LocationListener
{

    private static final String MAP_VIEW_BUNDLE_KEY = "MapViewBundleKey";
    private static final int MY_PERMISSIONS_REQUEST_LOCATION = 10001;
    private final double SCAN_RADIUS_IN_METERS = 200;
    private final float ARC_LENGTH = 20;
    private final double DISTANCE_COUNTER_FETCH_CAP = SCAN_RADIUS_IN_METERS * 0.75;

    private Context context;
    private Activity ownerActivity;
    private MapView mapView;
    private GoogleMap mMap;


    private final double KM_TO_GPS = 110.567f;
    private final double METERS_TO_KM = 1000;

    public Location lastLocation;
    private float lastAngle = 0.0f;
    private List<LatLng> lastScanArea;

    private float currentDirection = 0.0f;
    private long gpsUpdatesCount = 0;
    private long networkUpdatesCount = 0;
    private Marker myLocationMarker;
    private final String YOUR_LOCATION = "Your Location";
    private boolean firstAnimateFinished = false;
    private ArrayList<Location> locations;
    private double distanceTraveled = 0.0f;
    private double distanceCounterForFetch = 0.0f;
    private float zoomPreference = 10;

    private Polygon scanArea;

    private SafeRideListener mListener;
    private Compass compass;

    private ArrayList<Circle> nearbyPlacesCircles = new ArrayList<>();
    private ArrayList<Marker> nearbyScannedMarkers = new ArrayList<>();
    private ArrayList<NearbyPlace> nearbyPlacesCollection = new ArrayList<>();


    public interface  SafeRideListener {
        void onSpeedChanged(double speed);
        void onlatitudeChanged(double latitude);
        void onLongitudeChanged(double longitude);
        void onGPSCountChanged(long gpsCount);
        void onNetworkCountChanged(long networkCount);
        void onDistanceTraveled(double distance);
    }

    public SafeRide(final Activity activity, MapView mapView, Compass compass) {
        this.ownerActivity = activity;
        this.context = activity;
        this.mapView = mapView;
        this.compass = compass;
        this.compass.setListener(new Compass.CompassListener() {
            @Override
            public void onNewAzimuth(final float azimuth) {
                activity.runOnUiThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                updateDirection(azimuth);
                            }
                        }
                );
            }
        });
        this.locations = new ArrayList<>();

    }

    // Public Methods

    public void onCreate() {
        mapView.getMapAsync(this);
        setUpLocationSource();
    }

    public void setListener(SafeRideListener listener) {
        this.mListener = listener;
    }

    public void setZoomPreference(final float zoomPreference) {
        this.zoomPreference = zoomPreference;
        ownerActivity.runOnUiThread(
            new Runnable() {
                @Override
                public void run() {
                    mMap.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.builder(mMap.getCameraPosition()).zoom(zoomPreference).build()));
                    }
            }
        );

    }

    // Helper Methods

    private void checkLocationPermission() {

        if (ActivityCompat.checkSelfPermission(this.context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(ownerActivity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this.ownerActivity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSIONS_REQUEST_LOCATION );
        }
    }

    private double getDistance(Location firstLoc, Location secondLoc) {
        return Math.sqrt(
                Math.pow(firstLoc.getLatitude() - secondLoc.getLatitude(),2) +
                        Math.pow(firstLoc.getLongitude() - secondLoc.getLongitude(),2)
        );
    }


    private void setUpLocationSource() {
        LocationManager locationManager = (LocationManager) ownerActivity.getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(ownerActivity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(ownerActivity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            this.checkLocationPermission();
        }
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0,  this);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
    }



    private double gpsPointsToMetes(double gpsPoints) {
        return gpsPoints * KM_TO_GPS * METERS_TO_KM;
    }

    private List<LatLng> getScanCoordinates(Location location, float angle, double radiusInMeters, double arcLength ) {
        double x[] = {0, 0, 0};
        double y[] = {0, 0 ,0};
        radiusInMeters /= 1000; // Meters to KM
        double radius = radiusInMeters / 110.567;  // KM to POINTS

        x[0] = location.getLatitude();
        y[0] = location.getLongitude();
        x[1] = location.getLatitude()  + (radius * Math.cos(Math.PI / 180 * (angle + arcLength/2)));
        y[1] = location.getLongitude() + (radius * Math.sin(Math.PI / 180 * (angle + arcLength/2)));
        x[2] = location.getLatitude()  + (radius * Math.cos(Math.PI / 180 * (angle - arcLength/2)));
        y[2] = location.getLongitude() + (radius * Math.sin(Math.PI / 180 * (angle - arcLength/2)));
        ArrayList<LatLng> list = new ArrayList<>();
        for(int i = 0; i < x.length; i++) {
            list.add(new LatLng(x[i],y[i]));
        }
        return list;
    }

    private PolygonOptions getScanAreaOptions(Location location, float angle, double radiusInMeters, double arcLength ) {

        return new PolygonOptions()
                .addAll(getScanCoordinates(location, angle, radiusInMeters, arcLength))
                .fillColor(Color.argb(150, 200, 0 ,0 ))
                .strokeColor(Color.argb(50, 200,0,0))
                ;
    }

    private void updateDistanceTraveled(Location newLocation) {
        double distance = gpsPointsToMetes(getDistance(this.lastLocation, newLocation));
        this.distanceTraveled +=  distance;
        this.distanceCounterForFetch += distance;
        if(this.distanceCounterForFetch > DISTANCE_COUNTER_FETCH_CAP) {
            this.distanceCounterForFetch = 0;
            this.getCurrentPlaces();
        }
        if(mListener != null) { mListener.onDistanceTraveled(this.distanceTraveled); }
    }

    private void getCurrentPlaces() {

    }
    private float directionUpdateAngleThreshold = 5;
//    private float lastRecordAngle = 0.0f;
    private void updateDirection(float angle){
        Log.i("COMPASS", "Direction: "+angle);
        float diff = Math.abs(lastAngle - angle);
        // Added
        if( diff > directionUpdateAngleThreshold) {
            if(this.lastLocation == null) return;
            currentDirection = angle;
            lastScanArea = getScanCoordinates(this.lastLocation, angle, SCAN_RADIUS_IN_METERS, ARC_LENGTH);
            if(this.scanArea != null) {
                if(Math.floor(this.lastAngle) != angle) {
                    this.scanArea.setPoints(lastScanArea);
                }
            }
            else {
                this.scanArea = mMap.addPolygon(getScanAreaOptions(this.lastLocation, angle, SCAN_RADIUS_IN_METERS, ARC_LENGTH));
            }

            this.clearNearbyMarkers();
            this.addNearbyMarkers(null);
            this.lastAngle = angle;

        }
    }

    // Override Methods

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setOnMyLocationButtonClickListener(this);
        mMap.setOnMyLocationClickListener(this);
        mMap.setMinZoomPreference(12);
        mMap.setOnCameraMoveListener(this);
        if (mMap != null) {
            if (ActivityCompat.checkSelfPermission(ownerActivity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(ownerActivity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                this.checkLocationPermission();
                mMap.setMyLocationEnabled(true);
            }
            mMap.setMyLocationEnabled(true);
        }
    }

    @Override
    public void onCameraMove() {
        Log.i("Camera", mMap.getCameraPosition().toString());
    }


    @Override
    public boolean onMyLocationButtonClick() {
        return false;
    }

    @Override
    public void onMyLocationClick(@NonNull Location location) {

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_LOCATION: {
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ContextCompat.checkSelfPermission(ownerActivity, Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED) {
                    }
                }
            } break;
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if(this.lastLocation != null && location.getSpeed() != 0) {
            this.updateDistanceTraveled(location);
        }
        this.lastLocation = location;
        String provider = location.getProvider();
        if(provider.equals(LocationManager.GPS_PROVIDER)) {
            gpsUpdatesCount++;
        } else if(provider.equals(LocationManager.NETWORK_PROVIDER)) {
            networkUpdatesCount++;
        }
        LatLng latlng = new LatLng(location.getLatitude(), location.getLongitude());

        if(this.myLocationMarker != null) {
            this.myLocationMarker.remove();
        }
        this.myLocationMarker = mMap.addMarker(new MarkerOptions().title(YOUR_LOCATION).position(latlng));

        if(!this.firstAnimateFinished){
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.builder(mMap.getCameraPosition()).target(latlng).zoom(this.zoomPreference).tilt(45).build()));
            this.firstAnimateFinished = true;
        } else {
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.builder(mMap.getCameraPosition()).bearing(this.lastAngle).tilt(45).target(latlng).build()));
        }
        if(location.getSpeed() != 0) { locations.add(location); }
        if(mListener != null) {
            mListener.onlatitudeChanged(location.getLatitude());
            mListener.onLongitudeChanged(location.getLongitude());
            mListener.onSpeedChanged(location.getSpeed());
            mListener.onGPSCountChanged(this.gpsUpdatesCount);
            mListener.onNetworkCountChanged(this.networkUpdatesCount);
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    public void addNearbyMarkers(List<NearbyPlace> places) {
        if(places != null) {
            for (NearbyPlace place : places) {
                if (!nearbyPlacesCollection.contains(place)) {
                    nearbyPlacesCollection.add(place);
                }
            }
        }
        // Filter Nearby places within radius

        List<NearbyPlace> filteredNearbyPlaces = nearbyPlacesCollection;

        for (NearbyPlace pos :
                filteredNearbyPlaces) {
            if(this.lastScanArea != null && isPointOnScanArea(pos.createLatLng(), this.lastScanArea)) {
                Marker marker = mMap.addMarker(new MarkerOptions().position(pos.createLatLng()).title(pos.name+"_"+joinStringArray(pos.getTypes(),",")));
                nearbyScannedMarkers.add(marker);
            } else {
                CircleOptions options = new CircleOptions().center(pos.createLatLng()).radius(5).strokeColor(Color.BLACK).fillColor(Color.BLACK);
                Circle marker = mMap.addCircle(options);
                nearbyPlacesCircles.add(marker);
            }
        }
    }

    private String joinStringArray(String[] array, String joiner) {
        String retString = "";
        for (int i = 0; i < array.length; i++) {
            retString += array[i];
            if(i != array.length - 1) { // not last
                retString+=joiner;
            }
        }
        return  retString;
    }

    public NearbyPlace createNearbyPlace(double latitude, double longitude, String name) {
        return new NearbyPlace(latitude, longitude, name);
    }

    public class NearbyPlace  {
        private String id;
        private double latitude;
        private double longitude;
        private String name;
        private String[] types;

        private NearbyPlace() {
            this.id = UUID.randomUUID().toString();
        }

        private NearbyPlace(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.id = UUID.randomUUID().toString();
        }

        private NearbyPlace(double latitude, double longitude, String name) {
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


    public void clearNearbyMarkers() {
        for (Circle place :
                nearbyPlacesCircles) {
            place.remove();
        }

        for (Marker place :
                nearbyScannedMarkers) {
            place.remove();
        }
    }
    
    private boolean isPointOnScanArea(LatLng loc, List<LatLng> scanArea) {
        LatLng p2 = scanArea.get(scanArea.size() - 1);
        boolean result = false;
        for (LatLng p1 :
                scanArea) {
            if ((p1.longitude > loc.longitude) != (p2.longitude > loc.longitude) &&
                    (loc.latitude < (p2.latitude - p1.latitude) * (loc.longitude - p1.longitude) / (p2.longitude-p1.longitude) + p1.latitude)) {
                result = !result;
            }
        }
        return result;
    }

}
