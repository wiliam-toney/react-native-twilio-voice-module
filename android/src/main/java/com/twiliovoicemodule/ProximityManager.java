package com.twiliovoicemodule;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.PowerManager;
import android.util.Log;

import com.facebook.react.bridge.ReactApplicationContext;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ProximityManager {
    ReactApplicationContext reactContext;
    private SensorManager sensorManager;
    private Sensor proximitySensor;
    private PowerManager.WakeLock proximityWakeLock = null;
    private PowerManager powerManager;

    private SensorEventListener sensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
                boolean isNear = false;
                if (event.values[0] < proximitySensor.getMaximumRange()) {
                    isNear = true;
                }
                if (isNear) {
                    turnOffScreen();
                } else {
                    turnOnScreen();
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };

    public ProximityManager(ReactApplicationContext reactContext) {
        this.reactContext = reactContext;
        sensorManager = (SensorManager) reactContext.getSystemService(Context.SENSOR_SERVICE);
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        powerManager = (PowerManager) reactContext.getSystemService(Context.POWER_SERVICE);
        initProximityWakeLock();
    }

    @SuppressLint("InvalidWakeLockTag")
    private void initProximityWakeLock() {
        try {
            boolean isSupported;
            int proximityScreenOffWakeLock;
            if (android.os.Build.VERSION.SDK_INT < 21) {
                Field field = PowerManager.class.getDeclaredField("PROXIMITY_SCREEN_OFF_WAKE_LOCK");
                proximityScreenOffWakeLock = (Integer) field.get(null);

                Method method = powerManager.getClass().getDeclaredMethod("getSupportedWakeLockFlags");
                int powerManagerSupportedFlags = (Integer) method.invoke(powerManager);
                isSupported = ((powerManagerSupportedFlags & proximityScreenOffWakeLock) != 0x0);
            } else {
                proximityScreenOffWakeLock = powerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK;
                isSupported = powerManager.isWakeLockLevelSupported(proximityScreenOffWakeLock);
            }
            if (isSupported) {
                proximityWakeLock = powerManager.newWakeLock(proximityScreenOffWakeLock, "TwilioVoiceModule");
                proximityWakeLock.setReferenceCounted(false);
            }
        } catch (Exception e) {
            Log.e("TwilioVoiceModule", "Failed to get proximity screen locker.");
        }
    }

    private void turnOffScreen() {
        if (proximityWakeLock == null) {
            return;
        }
        synchronized (proximityWakeLock) {
            if (!proximityWakeLock.isHeld()) {
                proximityWakeLock.acquire();
            }
        }
    }

    private void turnOnScreen() {
        if (proximityWakeLock == null) {
            return;
        }
        synchronized (proximityWakeLock) {
            if (proximityWakeLock.isHeld()) {
                if (android.os.Build.VERSION.SDK_INT >= 21) {
                    proximityWakeLock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY);
                }
            }
        }
    }

    public void startProximityDetector() {
        if (sensorManager != null) {
            sensorManager.registerListener(sensorEventListener, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    public void stopProximityDetector() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(sensorEventListener);

        }
    }

}
