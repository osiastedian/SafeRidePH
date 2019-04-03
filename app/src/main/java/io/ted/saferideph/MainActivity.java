package io.ted.saferideph;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.CardView;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.gms.location.places.GeoDataClient;
import com.google.android.gms.location.places.PlaceDetectionClient;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.MapView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Locale;

import io.ted.saferideph.models.Camera2Preview;
import io.ted.saferideph.models.Camera2TextureView;

import static io.ted.saferideph.SafeRide.MINIMUM_ZOOM_PREF;
import static io.ted.saferideph.SettingsActivity.BUNDLE_BUMP_THRESHOLD;
import static io.ted.saferideph.SettingsActivity.BUNDLE_BUMP_VOICE_OUT;
import static io.ted.saferideph.SettingsActivity.BUNDLE_OVER_SPEED_TOLERATE;
import static io.ted.saferideph.SettingsActivity.BUNDLE_SCAN_RADIUS;

public class MainActivity extends AppCompatActivity implements
        ActivityCompat.OnRequestPermissionsResultCallback,
        SeekBar.OnSeekBarChangeListener,
        SafeRide.SafeRideListener
{
    private static final boolean AUTO_HIDE = true;
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    private static final int UI_ANIMATION_DELAY = 100;
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

    private MapView mapView;
    private TextView latText;
    private TextView longText;
    private TextView gpsUpdateTextView;
    private TextView speedTextView;
    private TextView networkUpdateTextView;
    private TextView traveledTextView;
    private TextView scannedPlacesTextView;
    private CardView speedCard;
    private TextView excessSpeedTextView;
    private TextView speedLimitTextView;
    private Button startButton;
    private Button stopButton;
    private Button settingsButton;
    private CardView locationDetailsCard;

    private FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance();
    private DatabaseReference databaseReference = firebaseDatabase.getReference("trips");

    // Get Places
    protected GeoDataClient mGeoDataClient;
    protected PlaceDetectionClient mPlaceDetectionClient;

    Compass compass;
    SafeRide safeRide;
    WarningSystem warningSystem;
    BumpDetectionSystem bumpDetectionSystem;

    private SeekBar zoomSeekBar;

    private Camera mCamera;
    CameraPreview mPreview;
    Camera2TextureView mCamera2TextureView;
    Camera2Preview mCamera2Preview;



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
        traveledTextView = findViewById(R.id.traveledTextView);
        scannedPlacesTextView = findViewById(R.id.scannedPlacesCount);
        zoomSeekBar = findViewById(R.id.zoomSeekBar);
        zoomSeekBar.setOnSeekBarChangeListener(this);
        speedCard = findViewById(R.id.speedCard);
        excessSpeedTextView = findViewById(R.id.excessSpeedTextView);
        speedLimitTextView = findViewById(R.id.speedLimitTextView);
        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);
        settingsButton = findViewById(R.id.settingsButton);
        locationDetailsCard = findViewById(R.id.locationDetailsCard);

        this.loadTripsValueListener();

        // Get Places
        mGeoDataClient = Places.getGeoDataClient(this);
        mPlaceDetectionClient = Places.getPlaceDetectionClient(this);

        this.compass = new Compass(this);
        this.warningSystem = new WarningSystem(this);
        this.bumpDetectionSystem = new BumpDetectionSystem(this);
        this.safeRide = new SafeRide(this, mapView, compass, warningSystem, firebaseDatabase, bumpDetectionSystem);
        this.safeRide.onCreate();
        this.safeRide.addListener(this);


        // DEBUG
//        locationDetailsCard.setVisibility(View.INVISIBLE);

//        FrameLayout previewLayout = findViewById(R.id.cameraPreview);
//        if(checkCameraHardware(this)) {
//            this.mCamera = getCameraInstance();
//            mPreview = new CameraPreview(this, mCamera);
//            previewLayout.addView(mPreview);
//        } else {
//         previewLayout.setVisibility(View.INVISIBLE);
//        }
        mCamera2TextureView = findViewById(R.id.cameraPreview);
        try {
            mCamera2Preview = new Camera2Preview(this, this, mCamera2TextureView);
        } catch (Exception e) {

        }

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
        bumpDetectionSystem.start();
        mCamera2Preview.onResume();
//        this.mPreview.setCamera(getCameraInstance());

    }

    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();
        compass.start();
        bumpDetectionSystem.start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
        compass.stop();
        bumpDetectionSystem.stop();
    }

    @Override
    protected void onPause() {
        mapView.onPause();
        compass.stop();
        bumpDetectionSystem.stop();
        mCamera2Preview.onPause();
//        releaseCamera();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mapView.onDestroy();
        compass.stop();
        bumpDetectionSystem.stop();
        releaseCamera();
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
        switch (seekBar.getId()) {
            case R.id.zoomSeekBar: {
                this.safeRide.setZoomPreference(MINIMUM_ZOOM_PREF + progress);
                break;
            }
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    public void onClick_StartButton(View view) {
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        safeRide.startRecording();

    }

    public void onClick_StopButton(View view) {
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        safeRide.stopRecording();
    }

    final int SETTINGS_REQUEST_CODE = 0xFF00;

    public void onClick_SettingsButton(View view) {
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.putExtra(BUNDLE_BUMP_THRESHOLD, bumpDetectionSystem.zThreshold);
        intent.putExtra(BUNDLE_SCAN_RADIUS,(int) safeRide.getScanRadius());
        intent.putExtra(BUNDLE_BUMP_VOICE_OUT, safeRide.isVoiceOutBumpDetection());
        startActivityForResult(intent, SETTINGS_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case SETTINGS_REQUEST_CODE: {
                Bundle bundle = data.getExtras();
                if(bundle != null) {
                    double threshold = bundle.getDouble(BUNDLE_BUMP_THRESHOLD, -1);
                    if(threshold != -1) {
                        this.bumpDetectionSystem.zThreshold = threshold;
                    }
                    boolean voiceOut = bundle.getBoolean( BUNDLE_BUMP_VOICE_OUT, false);
                    safeRide.setVoiceOutBumpDetection(voiceOut);

                    // RADIUS

                    int radius = bundle.getInt(BUNDLE_SCAN_RADIUS, -1);
                    if(threshold != -1) {
                        safeRide.setScanRadius(radius);
                    }

                    // OVER SPEED TOLERATE

                    int tolerate = bundle.getInt(BUNDLE_OVER_SPEED_TOLERATE, -1);
                    if(tolerate >=0 ) {
                        safeRide.setOverSpeedTolerate(tolerate);
                    }

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            hide();
                        }
                    });
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onSpeedChanged(final double speed) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                speedTextView.setText(String.format(Locale.ENGLISH, "%.0fkm/h", speed));
            }
        });
    }

    @Override
    public void onLatitudeChanged(final double latitude) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                latText.setText(String.format(Locale.ENGLISH, "Latitude: %f", latitude));
            }
        });
    }

    @Override
    public void onLongitudeChanged(final double longitude) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                longText.setText(String.format(Locale.ENGLISH, "Longitude: %f", longitude));
            }
        });
    }

    @Override
    public void onGPSCountChanged(final long gpsCount) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                gpsUpdateTextView.setText(String.format(Locale.ENGLISH, "GPS: %d", gpsCount));
            }
        });
    }

    @Override
    public void onNetworkCountChanged(final long networkCount) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                networkUpdateTextView.setText(String.format(Locale.ENGLISH, "Network: %d", networkCount));
            }
        });
    }

    @Override
    public void onDistanceTraveled(final double distance) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                traveledTextView.setText(String.format(Locale.ENGLISH, "Traveled: %.2f", distance));
            }
        });
    }

    @Override
    public void onScannedPlaces(final ArrayList<NearbyPlace> scannedPlaces) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                scannedPlacesTextView.setText(String.format(Locale.ENGLISH, "Scanned Places: %d", scannedPlaces.size()));
            }
        });
    }

    @Override
    public void onOverSpeedingUpdate(final double excessSpeed) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(excessSpeed > safeRide.getOverSpeedTolerate()) {
                    double maxExcess = 10;
                    double colorPercent = excessSpeed/maxExcess;
                    double color = 255 - (255 * colorPercent);
                    color = color < 0 ? 0: color;
                    speedCard.setBackgroundColor(Color.argb(255, 255, (int) color, (int)color));
                    if(colorPercent < .3) {
                        speedTextView.setTextColor(Color.BLACK);
                        excessSpeedTextView.setTextColor(Color.BLACK);
                    } else {
                        speedTextView.setTextColor(Color.WHITE);
                        excessSpeedTextView.setTextColor(Color.WHITE);
                    }
                    excessSpeedTextView.setText(String.format(Locale.ENGLISH,"(+%.0f)", excessSpeed));
                    warningSystem.speedingWarning(excessSpeed);
                } else {
                    speedCard.setBackgroundColor(Color.WHITE);
                    speedTextView.setTextColor(Color.BLACK);
                    excessSpeedTextView.setText("");
                }
            }
        });
    }

    @Override
    public void onSpeedLimitChanged(final double speedLimit) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                speedLimitTextView.setText(String.format(Locale.ENGLISH, "Limit: %.0fkm/h", speedLimit ));
            }
        });
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case SafeRide.MY_PERMISSIONS_REQUEST_LOCATION: {
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED) {
                        safeRide.onCreate();
                    }
                }
            } break;
        }
    }


    private boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            return false;
        }
    }

    private static Camera getCameraInstance()
    {
        Camera camera1;
        try
        {
            if (android.os.Build.VERSION.SDK_INT >= 9)
            {
                return Camera.open(0);
            }
            camera1 = Camera.open();
        }
        catch (Exception exception)
        {
            return null;
        }
        return camera1;
    }

    private void releaseCamera(){
        if (mCamera != null){
            mCamera.release();        // release the camera for other applications
            mCamera = null;
        }
    }
}

