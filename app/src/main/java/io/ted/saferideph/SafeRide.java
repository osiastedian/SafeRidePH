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
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
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
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.ted.saferideph.models.Trip;

public class SafeRide implements
        OnMapReadyCallback,
        GoogleMap.OnMyLocationButtonClickListener,
        GoogleMap.OnCameraMoveListener,
        GoogleMap.OnMyLocationClickListener,
        ActivityCompat.OnRequestPermissionsResultCallback,
        LocationSource.OnLocationChangedListener,
        LocationListener,
        BumpDetectionSystem.BumpListener
{

    private static final int MY_PERMISSIONS_REQUEST_LOCATION = 10001;
    public static final int MAX_RADIUS = 1000;
    public static final int MIN_RADIUS = 100;
    private double SCAN_RADIUS_IN_METERS = 200;
    private double DISTANCE_COUNTER_FETCH_CAP = SCAN_RADIUS_IN_METERS * 0.5;
    private final float ARC_LENGTH = 20;


    private Context context;
    private Activity ownerActivity;
    private MapView mapView;
    private GoogleMap mMap;


    private final double KM_TO_GPS = 110.567f;
    private final double METERS_TO_KM = 1000;

    public Location lastLocation;
    private float lastAngle = 0.0f;
    private List<LatLng> lastScanArea;
    private double lastSpeed;  // kmph
    private double lastSpeedLimit = 2; //kmph
    private int overSpeedTolerate = 0; //kmph

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
    private WarningSystem warningSystem;
    private BumpDetectionSystem bumpDetectionSystem;

    private ArrayList<Circle> nearbyPlacesCircles = new ArrayList<>();
    private ArrayList<Marker> nearbyScannedMarkers = new ArrayList<>();
    private ArrayList<NearbyPlace> nearbyPlacesCollection = new ArrayList<>();

    private boolean isRecording = false;
    private Trip currentTrip;
    FirebaseDatabase firebaseDatabase;


    public interface  SafeRideListener {
        void onSpeedChanged(double speed);
        void onLatitudeChanged(double latitude);
        void onLongitudeChanged(double longitude);
        void onGPSCountChanged(long gpsCount);
        void onNetworkCountChanged(long networkCount);
        void onDistanceTraveled(double distance);
        void onScannedPlaces(ArrayList<NearbyPlace> scannedPlaces);
        void onOverSpeedingUpdate(double excessSpeed);
    }

    public SafeRide(final Activity activity, MapView mapView, Compass compass, WarningSystem warningSystem, FirebaseDatabase firebaseDatabase, BumpDetectionSystem bumpDetectionSystem) {
        this.ownerActivity = activity;
        this.context = activity;
        this.mapView = mapView;
        this.compass = compass;
        this.warningSystem = warningSystem;
        this.bumpDetectionSystem = bumpDetectionSystem;
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
        this.bumpDetectionSystem.setListener(this);
        this.locations = new ArrayList<>();
        this.firebaseDatabase = firebaseDatabase;
    }

    // Public Methods

    public void onCreate() {
        mapView.getMapAsync(this);
        setUpLocationSource();
    }

    public void setListener(SafeRideListener listener) {
        this.mListener = listener;
    }

    public void startRecording() {

        locations.clear();
        this.isRecording = true;
        this.currentTrip = new Trip();
        this.currentTrip.id = Calendar.getInstance().getTime().toString();

    }

    public void stopRecording() {
        FirebaseDatabase database = firebaseDatabase.getInstance();
        this.isRecording = false;
        this.currentTrip.locations = locations;
        DatabaseReference tripDBRef = database.getReference().child("trips").child(this.currentTrip.id);
        tripDBRef.setValue(this.currentTrip).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                Log.i("Trip", "Update Success");
            }
        });
    }

    public double getScanRadius() { return SCAN_RADIUS_IN_METERS; }

    public void setScanRadius(double radius){
        SCAN_RADIUS_IN_METERS = radius;
        DISTANCE_COUNTER_FETCH_CAP = SCAN_RADIUS_IN_METERS * 0.5;
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

    public int currentPagesLoaded = 0;

    public void getCurrentPlaces() {
        currentPagesLoaded = 0;
        this.clearNearbyMarkers();
        this.nearbyPlacesCollection.clear();
        this.getCurrentPlaces(null);
    }

    public void getCurrentPlaces(String pageToken) {
        if(currentPagesLoaded > 10) {
            return;
        }
        RequestQueue queue = Volley.newRequestQueue(ownerActivity);
        LatLng ahead = new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude());
        ahead = translate(ahead, this.lastAngle, SCAN_RADIUS_IN_METERS);
        double latitude = ahead.latitude;
        double longitude = ahead.longitude;
        String key = "AIzaSyASG1Kwhyrz8XTJdJsGfKDJ8XYby-rjNMs";
        String baseUrl = "https://maps.googleapis.com/maps/api/place/nearbysearch/json?";
        String url = baseUrl +
                String.format(Locale.ENGLISH, "location=%f,%f", latitude, longitude)+
                String.format(Locale.ENGLISH, "&radius=%.0f", SCAN_RADIUS_IN_METERS * 1.0) +
//                "&type=store" +
                String.format(Locale.ENGLISH, "&key=%s", key);
        if(pageToken != null) {
            url = baseUrl + "pagetoken="+ pageToken + "&key=" + key;
        }
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                String responseStr = response.toString();
                try {
                    String nextPageToken = null;
                    if(response.has("next_page_token")) {
                        nextPageToken = response.getString("next_page_token");
                    }
                    JSONArray results = response.getJSONArray("results");
                    ArrayList<NearbyPlace> places = new ArrayList<>();
                    for(int i = 0; i < results.length() ; i++) {
                        JSONObject result = results.getJSONObject(i);
                        JSONObject geometry = result.getJSONObject("geometry");
                        JSONObject location = geometry.getJSONObject("location");
                        double latitude = location.getDouble("lat");
                        double longitude = location.getDouble("lng");
                        String name = result.getString("name");
                        ArrayList<String> types = new ArrayList<>();
                        JSONArray typesJson = result.getJSONArray("types");
                        for(int typesIndex = 0; typesIndex < typesJson.length(); typesIndex++) {
                            types.add(typesJson.getString(typesIndex));
                        }
                        NearbyPlace place = new NearbyPlace(latitude, longitude, name);
                        place.setTypes(types.toArray(new String[types.size()]));
                        places.add(place);
                    }
                    addNearbyMarkers(places);
//                    if(nextPageToken != null && nextPageToken.length() > 0) {
//                        currentPagesLoaded++;
//                        getCurrentPlaces(nextPageToken);
//                    }


                } catch (JSONException exception) {

                }
                Log.i("Current Places", responseStr);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e("Current Places", "Message -- "+error.getMessage());
            }
        });
        queue.add(request);
        Toast.makeText(ownerActivity, "Get Current Places", Toast.LENGTH_SHORT).show();
    }

    public int getOverSpeedTolerate() {
        return overSpeedTolerate;
    }

    public void setOverSpeedTolerate(int overSpeedTolerate) {
        this.overSpeedTolerate = overSpeedTolerate;
    }

    // Helper Methods

    private LatLng translate(LatLng original, float angle, double distance) {
        distance /= 1000;
        distance /= 110.567;
        double lat = original.latitude  + (distance * Math.cos(Math.PI / 180 * angle));
        double lng = original.longitude + (distance * Math.sin(Math.PI / 180 * angle));
        return new LatLng(lat, lng);
    }


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

    private double getDistance(NearbyPlace nearbyPlace, Location location) {
        return Math.sqrt(
                Math.pow(nearbyPlace.getLatitude() - location.getLatitude(),2) +
                        Math.pow(nearbyPlace.getLongitude() - location.getLongitude(),2)
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
        mListener.onDistanceTraveled(this.distanceTraveled);

        this.distanceCounterForFetch += distance;
        if(this.distanceCounterForFetch > DISTANCE_COUNTER_FETCH_CAP) {
            this.distanceCounterForFetch -= DISTANCE_COUNTER_FETCH_CAP;
            this.getCurrentPlaces();
        }
        if(mListener != null) { mListener.onDistanceTraveled(this.distanceTraveled); }
    }
    private float directionUpdateAngleThreshold = 1;
//    private float lastRecordAngle = 0.0f;

    private void updateDirection(float angle){
        Log.i("COMPASS", "Direction: "+angle);
        float diff = Math.abs(lastAngle - angle);
        // Added
        if( diff > directionUpdateAngleThreshold) {
            this.lastAngle = angle;
            this.drawScannedArea(angle);
        }
    }

    private void drawScannedArea(float angle) {
        if(this.lastLocation == null) return;
        currentDirection = angle;
        lastScanArea = getScanCoordinates(this.lastLocation, currentDirection, SCAN_RADIUS_IN_METERS, ARC_LENGTH);
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
        String provider = location.getProvider();
        if(provider.equals(LocationManager.GPS_PROVIDER)) {
            mListener.onGPSCountChanged(gpsUpdatesCount++);
        } else if(provider.equals(LocationManager.NETWORK_PROVIDER)) {
            mListener.onNetworkCountChanged(networkUpdatesCount++);
            return;
        }
        lastSpeed = location.getSpeed() * 3.6; // km/h

        if(this.lastLocation != null
//                && lastSpeed != 0
        ) {
            this.updateDistanceTraveled(location);
        }
        this.lastLocation = location;
        LatLng latlng = new LatLng(location.getLatitude(), location.getLongitude());
        mListener.onLatitudeChanged(location.getLatitude());
        mListener.onLongitudeChanged(location.getLongitude());
        if(this.myLocationMarker != null) {
            this.myLocationMarker.remove();
        }
        this.myLocationMarker = mMap.addMarker(new MarkerOptions().title(YOUR_LOCATION).position(latlng));

        if(!this.firstAnimateFinished){
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.builder(mMap.getCameraPosition()).target(latlng).zoom(this.zoomPreference).tilt(45).build()));
            this.getCurrentPlaces();
            this.firstAnimateFinished = true;
        } else {
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.builder(mMap.getCameraPosition()).bearing(this.lastAngle).tilt(45).target(latlng).build()));
        }
        if(location.getSpeed() != 0 && isRecording) { locations.add(location); }
        drawScannedArea(this.lastAngle);
        if(mListener != null) {
            mListener.onLatitudeChanged(location.getLatitude());
            mListener.onLongitudeChanged(location.getLongitude());
            mListener.onSpeedChanged(lastSpeed);
            mListener.onOverSpeedingUpdate(lastSpeed - lastSpeedLimit);
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

        ArrayList<NearbyPlace> scannedPlaces = new ArrayList<>();
        for (NearbyPlace pos :
                nearbyPlacesCollection) {
            if(this.lastScanArea != null && isPointOnScanArea(pos.createLatLng(), this.lastScanArea)) {
                scannedPlaces.add(pos);
                Marker marker = mMap.addMarker(new MarkerOptions().position(pos.createLatLng()).title(pos.getName()+"_"+joinStringArray(pos.getTypes(),",")));
                nearbyScannedMarkers.add(marker);
            } else {
                CircleOptions options = new CircleOptions().center(pos.createLatLng()).radius(5).strokeColor(Color.BLACK).fillColor(Color.BLACK);
                Circle marker = mMap.addCircle(options);
                nearbyPlacesCircles.add(marker);
            }
        }

        this.mListener.onScannedPlaces(scannedPlaces);
        if(scannedPlaces.size() > 0) {
            processScannedPlaces(scannedPlaces);
        }
    }

    private void processScannedPlaces(ArrayList<NearbyPlace> places) {
        NearbyPlace mostPopulatedPlace = null;
        NearbyPlace nearestPlace = null;
        long highestScore = 0;
        double nearestPlaceDistance = 0;

        long totalScore = 0;
        for (NearbyPlace place : places) {
            long tempScore = ScoringSystem.getScore(place);
            double tempDistance = getDistance(place, this.lastLocation);
            tempDistance = gpsPointsToMetes(tempDistance);
            if(mostPopulatedPlace == null || (highestScore < tempScore)) {
                mostPopulatedPlace = place;
                highestScore = tempScore;
            }
            if(nearestPlace == null || nearestPlaceDistance < tempDistance) {
                nearestPlace = place;
                nearestPlaceDistance = gpsPointsToMetes(getDistance(nearestPlace, this.lastLocation));
            }
            totalScore += tempScore;
        }
        double distance = getDistance(mostPopulatedPlace, this.lastLocation);
        distance = gpsPointsToMetes(distance);
        Log.i("Warning Nearest", ""+nearestPlaceDistance+"m "+nearestPlace.getName());
        Log.i("Warning Total Places", ""+places.size());
        Log.i("Warning Total Score", ""+totalScore);
        Log.i("Warning Highest", mostPopulatedPlace.getName()+" : "+highestScore);
        Log.i("Warning Highest Dist", mostPopulatedPlace.getName()+" : "+distance);
        double speed = this.lastSpeed;
        if(shouldSlowDownUpcomingPlace(nearestPlaceDistance, speed)) {
            warningSystem.getSlowUpComing(nearestPlace.getName(), ScoringSystem.getHighscorePlaceType(nearestPlace), distance);
        }
    }

    private boolean shouldSlowDownUpcomingPlace(double distance, double speed) {
        // Source: https://www.qld.gov.au/transport/safety/road-safety/driving-safely/stopping-distances/graph
        double excess = 0;
        double totalStopping = 0;
        if(speed >= 110) {
            totalStopping = 143;
            excess = speed - 110;
        }
        else if(speed >= 100) {
            totalStopping = 122;
            excess = speed - 100;
        }
        else if(speed >= 90) {
            totalStopping = 103;
            excess = speed - 90;
        }
        else if(speed >= 80) {
            totalStopping = 85;
            excess = speed - 80;
        }
        else if(speed >= 70) {
            totalStopping = 69;
            excess = speed - 70;
        }
        else if(speed >= 60) {
            totalStopping = 54;
            excess = speed - 60;
        }
        else if(speed >= 50) {
            totalStopping = 41;
            excess = speed - 50;
        }
        else if(speed >= 40) {
            totalStopping = 30;
            excess = speed - 40;
        }
        totalStopping += excess;

        return distance <= totalStopping;
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

    public void clearNearbyMarkers() {
        for (Circle place :
                nearbyPlacesCircles) {
            place.remove();
        }
        nearbyPlacesCircles.clear();

        for (Marker place :
                nearbyScannedMarkers) {
            place.remove();
        }
        nearbyScannedMarkers.clear();
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

    @Override
    public void onBump(final long timestamp) {
        ownerActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Date date = Calendar.getInstance().getTime();
                String dateFormatted = date.toString();
                if(lastLocation != null)
                Toast.makeText(context, String.format(Locale.ENGLISH, "Bump on %f, %f at %s", lastLocation.getLatitude(), lastLocation.getLongitude(), dateFormatted), Toast.LENGTH_LONG).show();
            }
        });
    }

}
