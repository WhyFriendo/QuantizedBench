package com.llmquantbench;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;


public class BatteryModule extends ReactContextBaseJavaModule {
    private final ReactApplicationContext reactContext;

    public BatteryModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return "BatteryModule";
    }
    
    @ReactMethod
    public void getPreciseBatteryCapacity(Promise promise) {
        try {
            WritableMap resultMap = Arguments.createMap();
            
            double maxCapacity = getBatteryDesignCapacity();
            resultMap.putDouble("batteryCapacityMah", maxCapacity);
            
            double remainingCapacity = getRemainingCapacityDirect();
            
            if (remainingCapacity <= 0) {
                IntentFilter iFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus = reactContext.registerReceiver(null, iFilter);
                
                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                
                double batteryPct = level / (double) scale;
                
                remainingCapacity = batteryPct * maxCapacity;
            }
            
            double usedCapacity = maxCapacity - remainingCapacity;
            
            resultMap.putDouble("remainingCapacityMah", remainingCapacity);
            resultMap.putDouble("usedCapacityMah", usedCapacity);
            
            IntentFilter iFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = reactContext.registerReceiver(null, iFilter);
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            double batteryPct = level / (double) scale;
            resultMap.putDouble("batteryPercentage", batteryPct * 100);
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                BatteryManager batteryManager = (BatteryManager) reactContext.getSystemService(Context.BATTERY_SERVICE);
                
                double currentNow = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) / 1000.0;
                resultMap.putDouble("currentDrawMa", Math.abs(currentNow));
                resultMap.putBoolean("isCharging", currentNow > 0);
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    long energyCounter = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER);
                    if (energyCounter > 0) {
                        double energyMwh = energyCounter / 1000000.0;
                        resultMap.putDouble("energyMwh", energyMwh);
                    }
                }
                
                double currentAvg = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE) / 1000.0;
                if (currentAvg != 0) {
                    resultMap.putDouble("currentAvgMa", Math.abs(currentAvg));
                }
                
                long chargeCounter = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
                if (chargeCounter > 0) {
                    double chargeCounterMah = chargeCounter / 1000.0;
                    resultMap.putDouble("chargeCounterMah", chargeCounterMah);
                    
                    if (remainingCapacity <= 0 || chargeCounterMah > 0) {
                        remainingCapacity = chargeCounterMah;
                        usedCapacity = maxCapacity - remainingCapacity;
                        
                        resultMap.putDouble("remainingCapacityMah", remainingCapacity);
                        resultMap.putDouble("usedCapacityMah", usedCapacity);
                    }
                }
            }
            
            int voltageRaw = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
            if (voltageRaw > 0) {
                double voltageV = voltageRaw / 1000.0;
                resultMap.putDouble("voltageV", voltageV);
            }
            
            promise.resolve(resultMap);
        } catch (Exception e) {
            promise.reject("ERR_BATTERY_CAPACITY", "Failed to get precise battery capacity: " + e.getMessage());
        }
    }
    
    private double getBatteryDesignCapacity() {
        double capacity = 0;
        
        try {
            final Class<?> powerProfileClass = Class.forName("com.android.internal.os.PowerProfile");
            Constructor<?> constructor = powerProfileClass.getConstructor(Context.class);
            Object mPowerProfile = constructor.newInstance(reactContext);
            Method getBatteryCapacityMethod = powerProfileClass.getMethod("getBatteryCapacity");
            capacity = (double) getBatteryCapacityMethod.invoke(mPowerProfile);
            
            if (capacity > 0) {
                return capacity;
            }
        } catch (Exception e) {
        }
        
        try {
            String[] designCapacityPaths = {
                "/sys/class/power_supply/battery/charge_full_design",
                "/sys/class/power_supply/battery/design_capacity",
                "/sys/class/power_supply/battery/power_supply/battery/charge_full_design",
                "/sys/class/power_supply/max170xx_battery/charge_full_design"
            };
            
            for (String path : designCapacityPaths) {
                File f = new File(path);
                if (f.exists()) {
                    BufferedReader reader = new BufferedReader(new FileReader(f));
                    String line = reader.readLine();
                    reader.close();
                    
                    if (line != null && !line.isEmpty()) {
                        double value = Double.parseDouble(line.trim());
                        
                        if (value > 10000000) { // Likely in µAh
                            capacity = value / 1000.0;
                        } else {
                            capacity = value;
                        }
                        
                        if (capacity > 0) {
                            return capacity;
                        }
                    }
                }
            }
        } catch (Exception e) {
        }
        
        return 3000.0;
    }
    
    private double getRemainingCapacityDirect() {
        double remainingCapacity = 0;
        
        try {
            String[] possiblePaths = {
                "/sys/class/power_supply/battery/charge_now",
                "/sys/class/power_supply/battery/charge_counter",
                "/sys/class/power_supply/battery/capacity_now",
                "/sys/class/power_supply/battery/batt_current_capacity",
                "/sys/class/power_supply/battery/fg_current_capacity",
                "/sys/class/power_supply/battery/current_capacity"
            };
            
            for (String path : possiblePaths) {
                File f = new File(path);
                if (f.exists()) {
                    BufferedReader reader = new BufferedReader(new FileReader(f));
                    String line = reader.readLine();
                    reader.close();
                    
                    if (line != null && !line.isEmpty()) {
                        double value = Double.parseDouble(line.trim());
                        
                        if (value > 10000000) { // Likely in µAh
                            remainingCapacity = value / 1000.0;
                        } else {
                            remainingCapacity = value;
                        }
                        
                        if (remainingCapacity > 0) {
                            return remainingCapacity;
                        }
                    }
                }
            }
        } catch (Exception e) {
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            try {
                BatteryManager batteryManager = (BatteryManager) reactContext.getSystemService(Context.BATTERY_SERVICE);
                long chargeCounter = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
                if (chargeCounter > 0) {
                    // Convert µAh to mAh
                    return chargeCounter / 1000.0;
                }
            } catch (Exception e) {
            }
        }
        
        return 0;
    }
    
    @ReactMethod
    public void startBatteryMonitoring(final Promise promise) {
        try {
            Thread monitorThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        String bestPath = null;
                        String[] possiblePaths = {
                            "/sys/class/power_supply/battery/charge_now",
                            "/sys/class/power_supply/battery/capacity_now",
                            "/sys/class/power_supply/battery/batt_current_capacity"
                        };
                        
                        for (String path : possiblePaths) {
                            File f = new File(path);
                            if (f.exists() && f.canRead()) {
                                bestPath = path;
                                break;
                            }
                        }
                        
                        if (bestPath != null) {
                            promise.resolve(true);
                        } else {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                promise.resolve(true);
                            } else {
                                promise.reject("ERR_MONITORING", "No suitable battery monitoring method found");
                            }
                        }
                    } catch (Exception e) {
                        promise.reject("ERR_MONITORING", "Failed to start battery monitoring: " + e.getMessage());
                    }
                }
            });
            monitorThread.start();
        } catch (Exception e) {
            promise.reject("ERR_MONITORING", "Failed to start battery monitoring: " + e.getMessage());
        }
    }
}