package io.ted.saferideph;

import android.app.Activity;
import android.speech.tts.TextToSpeech;

import java.util.Locale;
import java.util.UUID;

public class WarningSystem implements TextToSpeech.OnInitListener {

    private TextToSpeech textToSpeech;
    private String currentSpeedUnit = "kilometers per hour";

    public WarningSystem(Activity activity) {
        textToSpeech = new TextToSpeech(activity.getApplicationContext(), this );

    }

    public void getSlowUpComing(String placeType, double distance) {
        String text = String.format(Locale.ENGLISH, "Slow Down! %s %.0f meters up ahead", placeType, distance);
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString());
    }

    public void speedingWarning(double currentSpeed, double speedLimit) {
        String text = String.format(Locale.ENGLISH, "Slow Down! You are over speeding by %.0f %s.", currentSpeed - speedLimit, currentSpeedUnit);
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString());
    }

    public void newSpeedLimit(double speedLimit) {
        String text = String.format(Locale.ENGLISH, "Speed Limit updated to %.0f %s", speedLimit, currentSpeedUnit);
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString());
    }

    @Override
    public void onInit(int status) {
        textToSpeech.setLanguage(Locale.ENGLISH);
    }

}
