package io.ted.saferideph;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.places.GeoDataClient;
import com.google.android.gms.location.places.PlaceDetectionClient;
import com.google.android.gms.location.places.PlaceLikelihood;
import com.google.android.gms.location.places.PlaceLikelihoodBufferResponse;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import io.ted.saferideph.models.Trip;

public class MainActivity extends AppCompatActivity implements
        ActivityCompat.OnRequestPermissionsResultCallback,
        OnMapReadyCallback,
        GoogleMap.OnMyLocationButtonClickListener,
        GoogleMap.OnMyLocationClickListener,
        GoogleMap.OnCameraMoveListener,
        LocationListener
{
    private static final boolean AUTO_HIDE = true;
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    private static final int UI_ANIMATION_DELAY = 300;
    private final Handler mHideHandler = new Handler();
    private View mContentView;
    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };
    private View mControlsView;
    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
            mControlsView.setVisibility(View.VISIBLE);
        }
    };
    private boolean mVisible;
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };
    private final View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (AUTO_HIDE) {
                delayedHide(AUTO_HIDE_DELAY_MILLIS);
            }
            return false;
        }
    };

    private static final String MAP_VIEW_BUNDLE_KEY = "MapViewBundleKey";
    private static final int MY_PERMISSIONS_REQUEST_LOCATION = 10001;
    private final double SCAN_RADIUS_IN_METERS = 200;
    private final float ARC_LENGTH = 20;
    private final double DISTANCE_COUNTER_FETCH_CAP = SCAN_RADIUS_IN_METERS * 0.75;

    private MapView mapView;
    private TextView latText;
    private TextView longText;
    private GoogleMap mMap;
    private TextView gpsUpdateTextView;
    private TextView speedTextView;
    private TextView networkUpdateTextView;
    private Button startButton;
    private Button stopButton;
    private long gpsUpdatesCount = 0;
    private long networkUpdatesCount = 0;
    private boolean wentToLocation = false;
    private boolean isRecording = false;
    private Trip currentTrip;
    private Marker myLocation = null;

    private FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance();
    private DatabaseReference databaseReference = firebaseDatabase.getReference("trips");

    // Get Places
    protected GeoDataClient mGeoDataClient;
    protected PlaceDetectionClient mPlaceDetectionClient;

    Compass compass;
    Polygon scanArea;

    private float lastAngle;
    float currentDirection = 0.0f;
    double distanceTraveled = 0.0f;
    double distanceCounterForFetch = 0.0f;

    private TextView traveledTextView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        mVisible = true;
        mControlsView = findViewById(R.id.fullscreen_content_controls);
        mContentView = findViewById(R.id.fullscreen_content);


        // Set up the user interaction to manually show or hide the system UI.
        mContentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggle();
            }
        });

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        findViewById(R.id.startButton).setOnTouchListener(mDelayHideTouchListener);
        findViewById(R.id.stopButton).setOnTouchListener(mDelayHideTouchListener);

        Bundle mapViewBundle = null;
        if (savedInstanceState != null) {
            mapViewBundle = savedInstanceState.getBundle(MAP_VIEW_BUNDLE_KEY);
        }

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(mapViewBundle);
        mapView.getMapAsync(this);


        latText = findViewById(R.id.textViewLat);
        longText = findViewById(R.id.textViewLong);
        gpsUpdateTextView = findViewById(R.id.gpsUpdateTextView);
        speedTextView = findViewById(R.id.speedTextView);
        networkUpdateTextView = findViewById(R.id.netowkrUpdateTextView);
        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);
        traveledTextView = findViewById(R.id.traveledTextView);


        this.checkLocationPermission();
        this.setUpLocationSource();
        this.loadTripsValueListener();

        // Get Places
        mGeoDataClient = Places.getGeoDataClient(this);
        mPlaceDetectionClient = Places.getPlaceDetectionClient(this);

        this.compass = new Compass(this);
        compass.setListener(getCompassListener());

    }

    private void getCurrentPlaces() {
//        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//            this.checkLocationPermission();
//        }
//        mPlaceDetectionClient.getCurrentPlace(null).addOnCompleteListener(new OnCompleteListener<PlaceLikelihoodBufferResponse>() {
//            @Override
//            public void onComplete(@NonNull Task<PlaceLikelihoodBufferResponse> task) {
//                if (task.isSuccessful()) {
//                    PlaceLikelihoodBufferResponse likelyPlaces = task.getResult();
//                    for (PlaceLikelihood placeLikelihood : likelyPlaces) {
//                        Log.i("PLACES", String.format("Place '%s' has likelihood: %g",
//                                placeLikelihood.getPlace().getName(),
//                                placeLikelihood.getLikelihood()));
//                    }
//                    likelyPlaces.release();
//                } else {
//
//                }
//            }
//        });
        Toast.makeText(this, "Get Current Places", Toast.LENGTH_SHORT).show();
    }

    private void loadTripsValueListener() {
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Object value = dataSnapshot.getValue();
                Log.d("Trips Listener", "Value is: " + value);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.w("Trips Listener", "Failed to read value.", databaseError.toException());
            }
        });
    }

    private void setUpLocationSource() {
        LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            this.checkLocationPermission();
        }
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
    }

    private void updateDirection(float angle){
        Log.i("COMPASS", "Direction: "+angle);
        if(this.lastLocation == null) return;
        this.currentDirection = angle;
        if(this.scanArea != null) {
            if(Math.floor(this.lastAngle) != angle) {
                this.scanArea.setPoints(getScanCoordinates(this.lastLocation, angle, SCAN_RADIUS_IN_METERS, ARC_LENGTH));
            }
        }
        else {
            this.scanArea = mMap.addPolygon(getScanAreaOptions(this.lastLocation, angle, SCAN_RADIUS_IN_METERS, ARC_LENGTH));
        }
        this.lastAngle = angle;

    }

    private float degreesToRad(float angle) { return (float)(Math.PI / 180 * angle); }

    private Compass.CompassListener getCompassListener() {
        return new Compass.CompassListener() {
            @Override
            public void onNewAzimuth(final float azimuth) {
                // UI updates only in UI thread
                // https://stackoverflow.com/q/11140285/444966
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateDirection(azimuth);
                    }
                });
            }
        };
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }

    private void toggle() {
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }

    private void hide() {
        // Hide UI first
        mControlsView.setVisibility(View.GONE);
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        Bundle mapViewBundle = outState.getBundle(MAP_VIEW_BUNDLE_KEY);
        if (mapViewBundle == null) {
            mapViewBundle = new Bundle();
            outState.putBundle(MAP_VIEW_BUNDLE_KEY, mapViewBundle);
        }

        mapView.onSaveInstanceState(mapViewBundle);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        compass.start();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();
        compass.start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
        compass.stop();
    }

    @Override
    protected void onPause() {
        mapView.onPause();
        compass.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mapView.onDestroy();
        compass.stop();
        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @SuppressLint("InlinedApi")
    private void show() {
        // Show the system bar
        mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        mVisible = true;

        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    /**
     * Schedules a call to hide() in delay milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setOnMyLocationButtonClickListener(this);
        mMap.setOnMyLocationClickListener(this);
        mMap.setMinZoomPreference(12);
        mMap.setOnCameraMoveListener(this);
        if (mMap != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                this.checkLocationPermission();
                mMap.setMyLocationEnabled(true);
            }
            mMap.setMyLocationEnabled(true);
        }
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

    private final double KM_TO_GPS = 110.567f;
    private final double METERS_TO_KM = 1000;

    private double gpsPointsToMetes(double gpsPoints) {
        return gpsPoints * KM_TO_GPS * METERS_TO_KM;
    }

    private  PolygonOptions getScanAreaOptions(Location location, float angle, double radiusInMeters, double arcLength ) {

        return new PolygonOptions()
                .addAll(getScanCoordinates(location, angle, radiusInMeters, arcLength))
                .fillColor(Color.argb(150, 200, 0 ,0 ))
                .strokeColor(Color.argb(50, 200,0,0))
                ;
    }

    public void checkLocationPermission() {

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSIONS_REQUEST_LOCATION );
        }
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
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED) {
                    }
                }
            } break;
        }
    }

    private double getDistance(Location firstLoc, Location secondLoc) {
        return Math.sqrt(
                Math.pow(firstLoc.getLatitude() - secondLoc.getLatitude(),2) +
                Math.pow(firstLoc.getLongitude() - secondLoc.getLongitude(),2)
        );
    }

    public void updateDistanceTraveled(Location newLocation) {
        double distance = gpsPointsToMetes(getDistance(this.lastLocation, newLocation));
        this.distanceTraveled +=  distance;
        this.distanceCounterForFetch += distance;
        if(this.distanceCounterForFetch > DISTANCE_COUNTER_FETCH_CAP) {
            this.distanceCounterForFetch = 0;
            this.getCurrentPlaces();
        }
        traveledTextView.setText(String.format("Traveled: %.2f meters", this.distanceTraveled));
    }

    Location lastLocation;
    @Override
    public void onLocationChanged(Location location) {
        if(this.lastLocation != null && location.getSpeed() != 0) { this.updateDistanceTraveled(location); }
        this.lastLocation = location;
        this.longText.setText("Longitude: "+ location.getLongitude());
        this.latText.setText("Latitude: "+ location.getLatitude());
        String provider = location.getProvider();
        if(provider.equals(LocationManager.GPS_PROVIDER)) {
            this.gpsUpdateTextView.setText("GPS" + (gpsUpdatesCount++));
        } else if(provider.equals(LocationManager.NETWORK_PROVIDER)) {
            this.networkUpdateTextView.setText("Network:" + (networkUpdatesCount++));
        }
        this.speedTextView.setText(String.format("%.2f km/h", location.getSpeed() * 3.6));

        LatLng latlng = new LatLng(location.getLatitude(), location.getLongitude());

        if(this.myLocation != null) {
            this.myLocation.remove();
        }
        this.myLocation = mMap.addMarker(new MarkerOptions().title("Your Location").position(latlng));

        if(!this.wentToLocation){
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.builder(mMap.getCameraPosition()).target(latlng).zoom(20).tilt(45).build()));
            this.wentToLocation = true;
        } else {
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.builder(mMap.getCameraPosition()).bearing(this.lastAngle).tilt(45).target(latlng).build()));
        }
        if(location.getSpeed() != 0) { locations.add(location); }
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

    @Override
    public void onCameraMove() {
        Log.i("Camera", mMap.getCameraPosition().toString());
    }

    ArrayList<Location> locations = new ArrayList<>();

    public void onClick_StartButton(View view){
        locations.clear();
        this.isRecording = true;
        this.currentTrip = new Trip();
        this.currentTrip.id = Calendar.getInstance().getTime().toString();
        this.startButton.setEnabled(false);
        this.stopButton.setEnabled(true);
    }

    public void onClick_StopButton(View view){
        this.startButton.setEnabled(true);
        this.stopButton.setEnabled(false);
        FirebaseDatabase database = firebaseDatabase.getInstance();
        this.isRecording = false;
        this.currentTrip.locations = locations;
        DatabaseReference tripDBRef = database.getReference().child("trips").child(this.currentTrip.id);
        tripDBRef.setValue(this.currentTrip).addOnSuccessListener(
                new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.i("Trip", "Update Success");
                    }
                }
        );

    }
}
