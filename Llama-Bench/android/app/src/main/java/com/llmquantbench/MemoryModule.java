package com.llmquantbench;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Debug;
import android.os.Process;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class MemoryModule extends ReactContextBaseJavaModule {
    private final ReactApplicationContext reactContext;

    public MemoryModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return "MemoryModule";
    }

    @ReactMethod
    public void getMemoryInfo(Promise promise) {
        try {
            long vmRSS = getVmRSSFromProcFile();

            promise.resolve((double) vmRSS);
        } catch (Exception e) {
            promise.reject("ERROR", "Failed to get memory info: " + e.getMessage());
        }
    }

    private long getVmRSSFromProcFile() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/self/status"));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("VmRSS:")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 2) {
                        return Long.parseLong(parts[1]) / 1024; // Convert kB to MB
                    }
                }
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;
    }
}