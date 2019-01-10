package io.ted.saferideph;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.nio.channels.SelectionKey;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity implements SeekBar.OnSeekBarChangeListener {

    public static final String BUNDLE_BUMP_THRESHOLD = "bump_threshold";
    public static final String BUNDLE_BUMP_VOICE_OUT = "bump_voice_out";
    public static final String BUNDLE_SCAN_RADIUS = "scan_radius";
    public static final String BUNDLE_OVER_SPEED_TOLERATE = "over_speed_tolerate";
    public static final int SUCCESS = 1;
    public static final int FAILED = 0;
    public static final int ERROR = -1;

    SeekBar bumpSeekBar;
    TextView bumpTextView;
    SeekBar scanRadius;
    TextView scanRadiusTextView;
    TextView speedTolerateText;
    SeekBar speedTolerateSeekBar;
    Switch bumpVoiceOut;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle("Settings");
        setSupportActionBar(toolbar);

        bumpSeekBar = findViewById(R.id.bumpSeekbar);
        bumpSeekBar.setOnSeekBarChangeListener(this);
        bumpTextView = findViewById(R.id.bumpTextView);
        scanRadius = findViewById(R.id.scannedRadiusSeekBar);
        scanRadius.setOnSeekBarChangeListener(this);
        scanRadiusTextView = findViewById(R.id.scanRadiusText);
        speedTolerateText = findViewById(R.id.speedTolerateText);
        speedTolerateSeekBar = findViewById(R.id.speedTolerateSeekBar);
        speedTolerateSeekBar.setOnSeekBarChangeListener(this);
        bumpVoiceOut = findViewById(R.id.bumpVoiceOut);

        Bundle bundle = this.getIntent().getExtras();
        if(bundle != null) {
            double threshold = bundle.getDouble(BUNDLE_BUMP_THRESHOLD);
            int seekBarValue = (int)(threshold / BumpDetectionSystem.MAX_THRESHOLD * 100);
            bumpSeekBar.setProgress(seekBarValue);

            boolean voiceOut = bundle.getBoolean(BUNDLE_BUMP_VOICE_OUT);
            bumpVoiceOut.setChecked(voiceOut);


            // radius

            int radius = bundle.getInt(BUNDLE_SCAN_RADIUS);
            radius -= SafeRide.MIN_RADIUS;
            double scannedRadius = (double)radius/(double)(SafeRide.MAX_RADIUS - SafeRide.MIN_RADIUS);
            scanRadius.setProgress((int)(scannedRadius*100.0f));

            int toleration = bundle.getInt(BUNDLE_OVER_SPEED_TOLERATE);
            speedTolerateSeekBar.setProgress(toleration);

        }
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();
    }

    @Override
    public void onBackPressed() {

        setResult(SUCCESS, getResultIntent());
        finish();
        super.onBackPressed();
    }

    private Intent getResultIntent() {
        Intent intent = new Intent();
        intent.putExtra(BUNDLE_BUMP_THRESHOLD, ((double)bumpSeekBar.getProgress()/100.0f) * BumpDetectionSystem.MAX_THRESHOLD);
        intent.putExtra(BUNDLE_SCAN_RADIUS, getRadiusInMeters());
        intent.putExtra(BUNDLE_OVER_SPEED_TOLERATE, speedTolerateSeekBar.getProgress());
        intent.putExtra(BUNDLE_BUMP_VOICE_OUT, bumpVoiceOut.isChecked());
        return intent;
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
        switch (seekBar.getId()) {
            case R.id.bumpSeekbar: {
                double progress = (double)bumpSeekBar.getProgress();
                double percent = (progress/100.0f);
                bumpTextView.setText(String.format(Locale.ENGLISH, "Threshold: %.2f", percent  * BumpDetectionSystem.MAX_THRESHOLD));
                break;
            }

            case R.id.scannedRadiusSeekBar: {
                scanRadiusTextView.setText(String.format(Locale.ENGLISH, "Radius: %d meters", getRadiusInMeters()));
                break;
            }

            case R.id.speedTolerateSeekBar: {
                speedTolerateText.setText(String.format(Locale.ENGLISH, "Over Speeding Toleration: %d KMPH", speedTolerateSeekBar.getProgress()));
            }
        }
    }

    private int getRadiusInMeters(){
        double progress = (double)scanRadius.getProgress();
        double percent = (progress/100.0f);
        int radius = (int)(percent * (SafeRide.MAX_RADIUS - SafeRide.MIN_RADIUS));
        radius += SafeRide.MIN_RADIUS;
        return radius;
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }
}
