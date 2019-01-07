package io.ted.saferideph;

import android.annotation.SuppressLint;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.places.GeoDataClient;
import com.google.android.gms.location.places.PlaceDetectionClient;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements
        ActivityCompat.OnRequestPermissionsResultCallback,
        SeekBar.OnSeekBarChangeListener
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
    private TextView traveledTextView;
    private Button startButton;
    private Button stopButton;

    private FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance();
    private DatabaseReference databaseReference = firebaseDatabase.getReference("trips");

    // Get Places
    protected GeoDataClient mGeoDataClient;
    protected PlaceDetectionClient mPlaceDetectionClient;

    Compass compass;
    SafeRide safeRide;
    WarningSystem warningSystem;

    private SeekBar zoomSeekBar;



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


        latText = findViewById(R.id.textViewLat);
        longText = findViewById(R.id.textViewLong);
        gpsUpdateTextView = findViewById(R.id.gpsUpdateTextView);
        speedTextView = findViewById(R.id.speedTextView);
        networkUpdateTextView = findViewById(R.id.netowkrUpdateTextView);
        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);
        traveledTextView = findViewById(R.id.traveledTextView);
        zoomSeekBar = findViewById(R.id.zoomSeekBar);
        zoomSeekBar.setOnSeekBarChangeListener(this);

        this.loadTripsValueListener();

        // Get Places
        mGeoDataClient = Places.getGeoDataClient(this);
        mPlaceDetectionClient = Places.getPlaceDetectionClient(this);

        this.compass = new Compass(this);
        this.warningSystem = new WarningSystem(this);
        this.safeRide = new SafeRide(this, mapView, compass, warningSystem, firebaseDatabase);
        this.safeRide.onCreate();
    }

    public void onTest(View view) {
//        warningSystem.newSpeedLimit(40);
        warningSystem.speedingWarning(40, 35);
//        this.playWarningSound();
    }



    public void playWarningSound() {
        if(warningSystem != null)
//        byte[] data = warningSystem.textToSpeech("Hello World!");
//        MediaPlayer.
        warningSystem.getSlowUpComing("restaurant", 100);

    }

    public void playSchoolZoneAhead() {

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

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
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
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        this.safeRide.setZoomPreference(10 + progress);
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    public void onClick_StartButton(View view) {
        safeRide.startRecording();

    }

    public void onClick_StopButton(View view) {
        safeRide.stopRecording();
    }

}

