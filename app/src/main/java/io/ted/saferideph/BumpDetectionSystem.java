package io.ted.saferideph;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

import io.ted.saferideph.models.Bump;

public class BumpDetectionSystem implements SensorEventListener {

    final static String LOG_TAG = "BMP_DETECT";
    final static String LOG_TAG_FIREBASE = "BMP_DETECT_FIREBASE";
    Activity ownerActivty;

    SensorManager sensorManager;
    Sensor accelerometer;

    public interface BumpListener {
        void onBump(long timestamp);
    }

    BumpListener listener;


    int maxQueueSize = 50;
    ArrayBlockingQueue xQueue = new ArrayBlockingQueue<Double>(maxQueueSize);
    ArrayBlockingQueue yQueue = new ArrayBlockingQueue<Double>(maxQueueSize);
    ArrayBlockingQueue zQueue = new ArrayBlockingQueue<Double>(maxQueueSize);

    public BumpDetectionSystem(Activity ownerActivty) {
        this.ownerActivty = ownerActivty;
        sensorManager = (SensorManager) ownerActivty.getApplicationContext()
                .getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    public void start() {
        sensorManager.registerListener(this, accelerometer,
                SensorManager.SENSOR_DELAY_GAME);
    }

    public void stop() {
        sensorManager.unregisterListener(this);
    }

    public void setListener(BumpListener listener) {
        this.listener = listener;
    }

    public double xThreshold = 0;
    public double yThreshold = 0;
    public double zThreshold = 7.0f;
    public static final double MAX_THRESHOLD = 15;
    @Override
    public void onSensorChanged(SensorEvent event) {
        if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

            try {

                double x = event.values[0];
                double y = event.values[1];
                double z = event.values[2];

                if(xQueue.size() >= maxQueueSize) {
                    xQueue.take();
                }
                if(yQueue.size() >= maxQueueSize) {
                    yQueue.take();
                }
                if(zQueue.size() >= maxQueueSize) {
                    zQueue.take();
                }
                if(xQueue.offer(x) && yQueue.offer(y) && zQueue.offer(z)) {
                    Log.i(BumpDetectionSystem.LOG_TAG, String.format(Locale.ENGLISH, "X:%f, Y:%f, Z:%f", x, y, z));
                }

                if(xQueue.size() == maxQueueSize) {
//                    Object[] rawDataX = xQueue.toArray(new Double[xQueue.size()]);
//                    Object[] rawDataY = yQueue.toArray(new Double[yQueue.size()]);
                    Object[] rawDataZ = zQueue.toArray(new Double[zQueue.size()]);
                    if(
//                            rawDataX instanceof Double[] &&
//                            rawDataY instanceof Double[] &&
                            rawDataZ instanceof Double[]
                        ) {
//                        Double []dataX = (Double[]) rawDataX; // {3.3, 3.4, 3.6,15.0, 5.4, -10.4, 4.8};//
//                        Double []dataY = (Double[]) rawDataY; // {3.3, 3.4, 3.6,15.0, 5.4, -10.4, 4.8};//
                        Double []dataZ = (Double[]) rawDataZ; // {3.3, 3.4, 3.6,15.0, 5.4, -10.4, 4.8};//
//                        boolean resultX = this.processData(dataX, xThreshold);
//                        boolean resultY = this.processData(dataY, yThreshold);
                        boolean resultZ = this.processData(dataZ, zThreshold);
                        if(
//                                resultX &&
//                                resultY &&
                                resultZ
                        ) {
                            if(listener != null) {
                                listener.onBump(event.timestamp);
                            }
//                        Log.i(BumpDetectionSystem.LOG_TAG, String.format(Locale.ENGLISH, "X:%s, Y:%s, Z:%s", resultX, resultY, resultZ));
                            Log.i(BumpDetectionSystem.LOG_TAG, String.format(Locale.ENGLISH, "[Bump On] X:%f, Y:%f, Z:%f", x, y, z));
                            this.xQueue.clear();
                            this.yQueue.clear();
                            this.zQueue.clear();
                        }
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    final public double influence = 0.3f;
    private boolean processData(Double values[], double threshold) {
        if(values.length != maxQueueSize) return false;
        double rawValues[] = new double[values.length];
        for(int i = 0; i < values.length; i++) {
            rawValues[i] = values[i];
        }
        return processData(rawValues, threshold);
    }
    private boolean processData(double values[], double threshold) {
        double signals[] = new double[values.length];
        double filtered[] = values.clone();
        double avgFilters[] = new double[values.length];
        double stdFilters[] = new double[values.length];
        avgFilters[values.length - 1] = mean(values);
        stdFilters[values.length - 1] = std(values);
        for (int i = 0; i < values.length; i++) {
            int prevIndex = i - 1;
            if(prevIndex < 0) prevIndex = values.length + prevIndex;
            if(Math.abs(values[i] - avgFilters[prevIndex]) > threshold * stdFilters[prevIndex]) {
                if(values[i] > avgFilters[prevIndex]) {
                    signals[i] = 1;
                } else {
                    signals[i] = -1;
                }
                filtered[i] = influence * values[i] + (1 - influence) * filtered[prevIndex];
            } else {
                signals[i] = 0;
                filtered[i] = values[i];
            }

            double[] extracted = extract(filtered, Math.abs(i-values.length + 1), i + 1);
            avgFilters[i] = mean(extracted);
            stdFilters[i] = std(extracted);

        }

        boolean hasPositive = false;
        boolean hasNegative = false;
        for (double signal : signals) {
            if(signal > 0) {
                hasPositive = true;
            } else if(signal < 0) {
                hasNegative = true;
            }
        }

        return hasPositive && hasNegative;
    }

    private double[] extract(double []orig, int from, int to) {
        double[] retArray = orig.clone();
        int length = orig.length;
        int index = 0;
        do {
            if(index >= length) break;
            int origIndex = (from +index) % length;
            retArray[index] = orig[origIndex];
            index++;
        }while(((from + index) % length) != to);
        return retArray;
    }

    private double mean(double []arr) {
        double sum = 0.0f;
        for (double val : arr) {
            sum += val;
        }
        return sum / arr.length;
    }

    private double std(double numArray[])
    {
        double sum = 0.0, standardDeviation = 0.0;
        int length = numArray.length;

        for(double num : numArray) {
            sum += num;
        }

        double mean = sum/length;

        for(double num: numArray) {
            standardDeviation += Math.pow(num - mean, 2);
        }

        return Math.sqrt(standardDeviation/length);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
    ChildEventListener bumpListener;
    private  ConcurrentHashMap<String, Bump> bumpsMap = new ConcurrentHashMap<>();
    public ChildEventListener startEventListener(FirebaseDatabase firebaseDatabase) {
        DatabaseReference databaseReference = firebaseDatabase.getReference("bumps");

        bumpListener = databaseReference.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                String key = dataSnapshot.getKey();
                Bump bump = mapToBump((HashMap)dataSnapshot.getValue());
                bumpsMap.put(key, bump);
                Log.i(BumpDetectionSystem.LOG_TAG_FIREBASE, "ChildAdded" + bump.toString());
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                Log.i(BumpDetectionSystem.LOG_TAG_FIREBASE, "onChildChanged");
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {
                Log.i(BumpDetectionSystem.LOG_TAG_FIREBASE, "ChildRemoved");
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                Log.i(BumpDetectionSystem.LOG_TAG_FIREBASE, "ChildMoved");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.i(BumpDetectionSystem.LOG_TAG_FIREBASE, "onCancelled");
            }
        });
        return  bumpListener;
    }

    public ConcurrentHashMap<String, Bump> getBumpsMap() {
        return bumpsMap;
    }

    public static Bump mapToBump(HashMap hashMap) {
        Bump bump = new Bump();
        bump.latitude = ((Double)hashMap.get("latitude")).doubleValue();
        bump.longitude = ((Double)hashMap.get("longitude")).doubleValue();
        return bump;

    }
}
