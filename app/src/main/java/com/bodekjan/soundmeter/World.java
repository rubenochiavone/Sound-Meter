package com.bodekjan.soundmeter;

import android.util.Log;

/**
 * Created by bodekjan on 2016/8/8.
 */
public class World {

    private static float previousDbValue = -1.0f;
    public static float dbCount = 40;
    public static float minDB =100;
    public static float maxDB =0;
    public static float lastDbCount = dbCount;
    private static float min = 0.5f;  //Set the minimum sound change
    private static float value = 0;   // Sound decibel value
    private static float threshold = 65;

    private static OnTooMuchNoiseListener tooMuchNoiseListener;
    private static OnUnknownCrashListener unknownCrashListener;

    public static void setOnTooMuchNoiseListener(OnTooMuchNoiseListener onTooMuchNoiseListener) {
        tooMuchNoiseListener = onTooMuchNoiseListener;
    }

    public static void setOnUnknownCrashListener(OnUnknownCrashListener onUnknownCrashListener) {
        unknownCrashListener = onUnknownCrashListener;
    }

    public static float getThreshold() {
        return World.threshold;
    }

    public static void setThreshold(float threshold) {
        World.threshold = threshold;
    }

    public static void setDbCount(float dbValue) {
        // ignore weird values
        if (Float.isInfinite(dbValue) || Float.isNaN(dbValue)) {
            if (BuildConfig.DEBUG) {
                Log.d("android-sound-meter", "received an out of bounds value");
            }
            return;
        }
        if (dbValue > lastDbCount) {
            value = dbValue - lastDbCount > min ? dbValue - lastDbCount : min;
        } else{
            value = dbValue - lastDbCount < -min ? dbValue - lastDbCount : -min;
        }
        dbCount = lastDbCount + value * 0.2f ; //To prevent the sound from changing too fast
        lastDbCount = dbCount;
        if (lastDbCount >= threshold && tooMuchNoiseListener != null) {
            // notify noise
            Log.w("android-sound-meter", "too much noise folks");
            tooMuchNoiseListener.onTooMuchNoise();
        } else if (previousDbValue == dbValue && unknownCrashListener != null) {
            Log.e("android-sound-meter", "crash: " + previousDbValue);
            unknownCrashListener.onUnknownCrash();
        }
        if (BuildConfig.DEBUG) {
            Log.d("android-sound-meter", "dbValue: " + dbValue + "; value: " + value + "; dbCount: " + dbCount + "; lastDbCount: " + lastDbCount);
        }
        if(dbCount<minDB) minDB=dbCount;
        if(dbCount>maxDB) maxDB=dbCount;
        previousDbValue = dbValue;
    }

    public interface OnTooMuchNoiseListener {
        void onTooMuchNoise();
    }

    public interface OnUnknownCrashListener {
        void onUnknownCrash();
    }
}