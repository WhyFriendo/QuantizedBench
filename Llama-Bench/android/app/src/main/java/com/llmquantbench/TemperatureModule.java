package com.llmquantbench;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class TemperatureModule extends ReactContextBaseJavaModule {
    public TemperatureModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return "TemperatureModule";
    }

    @ReactMethod
    public void getBatteryTemperature(Promise promise) {
        try {
            ReactApplicationContext context = getReactApplicationContext();
            IntentFilter iFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.registerReceiver(null, iFilter);

            if (batteryStatus != null) {
                int temperature = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                float tempCelsius = temperature / 10.0f;
                promise.resolve(tempCelsius);
            } else {
                promise.reject("ERROR", "Unable to get battery status");
            }
        } catch (Exception e) {
            promise.reject("ERROR", e.getMessage());
        }
    }
    
    @ReactMethod
    public void getThermalInfo(Promise promise) {
        try {
            StringBuilder result = new StringBuilder();
            int zoneIndex = 0;
            boolean zoneExists = true;
            
            while (zoneExists) {
                String temp = thermalTemp(zoneIndex);
                String type = thermalType(zoneIndex);
                
                if (type == null) {
                    zoneExists = false;
                } else if (temp != null && !temp.equals("0.0")) {
                    result.append(type).append(": ").append(temp).append("\n");
                }
                
                zoneIndex++;
            }
            
            promise.resolve(result.toString());
        } catch (Exception e) {
            promise.reject("ERROR", e.getMessage());
        }
    }

    private String thermalTemp(int i) {
        Process process;
        BufferedReader reader;
        String line;
        String t = "0.0";
        float temp = 0;
        try {
            process = Runtime.getRuntime().exec("cat sys/class/thermal/thermal_zone" + i + "/temp");
            process.waitFor();
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            line = reader.readLine();
            if (line != null) {
                temp = Float.parseFloat(line);
            }
            reader.close();
            process.destroy();
            if (!((int) temp == 0)) {
                if ((int) temp > 10000) {
                    temp = temp / 1000;
                } else if ((int) temp > 1000) {
                    temp = temp / 100;
                } else if ((int) temp > 100) {
                    temp = temp / 10;
                }
                t = String.valueOf(temp);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return t;
    }

    private String thermalType(int i) {
        Process process;
        BufferedReader reader;
        String line, type = null;
        try {
            process = Runtime.getRuntime().exec("cat sys/class/thermal/thermal_zone" + i + "/type");
            process.waitFor();
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            line = reader.readLine();
            if (line != null) {
                type = line;
            }
            reader.close();
            process.destroy();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return type;
    }
}
