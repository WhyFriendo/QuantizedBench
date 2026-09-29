/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;

/**
 * Android-native equivalents of selected react-native-device-info APIs.
 */
public final class DeviceInfoUtils {
    private DeviceInfoUtils() {}

    /**
     * Returns total system memory in bytes.
     * Mirrors react-native-device-info getTotalMemory.
     */
    public static long getTotalMemory(Context context) {
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            return mi.totalMem > 0L ? mi.totalMem : readMemTotalFromProc();
        } catch (Throwable e) {
            return readMemTotalFromProc();
        }
    }

    /**
     * Returns battery level as a value in [0.0, 1.0].
     * Mirrors react-native-device-info getBatteryLevel.
     */
    public static float getBatteryLevel(Context context) {
        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.registerReceiver(null, filter);
            int level = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : -1;
            int scale = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1) : -1;
            return (level >= 0 && scale > 0) ? (float) level / (float) scale : 0f;
        } catch (Throwable e) {
            return 0f;
        }
    }

    /** Model name of the device, e.g., "Pixel 7 Pro". */
    public static String getModel() {
        return Build.MODEL != null ? Build.MODEL : "";
    }

    /** Device brand, e.g., "google", "samsung". */
    public static String getBrand() {
        return Build.BRAND != null ? Build.BRAND : "";
    }

    /** Operating system name. */
    public static String getSystemName() {
        return "Android";
    }

    /** Operating system version string, e.g., "14" or "8.1.0". */
    public static String getSystemVersion() {
        return Build.VERSION.RELEASE != null ? Build.VERSION.RELEASE : String.valueOf(Build.VERSION.SDK_INT);
    }

    private static long readMemTotalFromProc() {
        try {
            File file = new File("/proc/meminfo");
            if (!file.exists() || !file.canRead()) return 0L;
            try (FileInputStream fis = new FileInputStream(file);
                 BufferedReader br = new BufferedReader(new InputStreamReader(fis))) {
                String firstLine = br.readLine();
                if (firstLine == null) return 0L;
                // Expected format: "MemTotal:        12345678 kB"
                String[] parts = firstLine.trim().split("\\s+");
                if (parts.length >= 2) {
                    try {
                        long kb = Long.parseLong(parts[1]);
                        return kb * 1024L;
                    } catch (NumberFormatException e) {
                        return 0L;
                    }
                } else {
                    return 0L;
                }
            }
        } catch (Throwable e) {
            return 0L;
        }
    }
}
