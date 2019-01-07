package io.ted.saferideph;

import android.app.Activity;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.Locale;

public class WarningSystem implements TextToSpeech.OnInitListener {

    private TextToSpeech textToSpeech;
    private String currentSpeedUnit = "kilometers per hour";
    private boolean onGoingWarning = false;

    public WarningSystem(Activity activity) {
        textToSpeech = new TextToSpeech(activity.getApplicationContext(), this );
        textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                Log.i("TextToSpeech Start", utteranceId);
                onGoingWarning = true;
            }

            @Override
            public void onDone(String utteranceId) {
                Log.i("TextToSpeech Success", utteranceId);
                onGoingWarning = false;
            }

            @Override
            public void onError(String utteranceId) {
                Log.e("TextToSpeech Error", utteranceId);
                onGoingWarning = false;
            }
        });

    }
    public void getSlowUpComing(String placeType, double distance) {
        getSlowUpComing("", placeType, distance);
    }

    public void getSlowUpComing(String name, String placeType, double distance) {
        if(onGoingWarning) return;
        placeType = placeType.replace("_"," ");
        String text = String.format(Locale.ENGLISH, "Slow Down! %s %s %.0f meters up ahead", name, placeType, distance);
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, text);
    }

    public void speedingWarning(double currentSpeed, double speedLimit) {
        if(onGoingWarning) return;
        String text = String.format(Locale.ENGLISH, "Slow Down! You are over speeding by %.0f %s.", currentSpeed - speedLimit, currentSpeedUnit);
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, text);
    }

    public void newSpeedLimit(double speedLimit) {
        if(onGoingWarning) return;
        String text = String.format(Locale.ENGLISH, "Speed Limit updated to %.0f %s", speedLimit, currentSpeedUnit);
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, text);
    }

    @Override
    public void onInit(int status) {
        textToSpeech.setLanguage(Locale.ENGLISH);
    }


}
