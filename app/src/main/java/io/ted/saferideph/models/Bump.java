package io.ted.saferideph.models;

import java.util.Date;
import java.util.Locale;

/**
 * Created by osias on 1/10/2019.
 */

public class Bump {
    public double longitude;
    public double latitude;
    public Date timeStamp;

    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "[%f, %f, %s]",latitude, longitude, timeStamp != null ? timeStamp.toString(): "none");
    }
}
