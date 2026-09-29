/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;


public final class TemperatureUtils {
    private TemperatureUtils() {}

    public static Float getBatteryTemperature(Context context) {
        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.registerReceiver(null, filter);
            Integer tenthsCelsius = batteryStatus != null ? 
                batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE) : 
                Integer.MIN_VALUE;
            if (tenthsCelsius == Integer.MIN_VALUE) return null;
            return tenthsCelsius / 10.0f;
        } catch (Throwable e) {
            return null;
        }
    }

    public static String getThermalInfo() {
        StringBuilder result = new StringBuilder();
        int zoneIndex = 0;
        while (true) {
            String typePath = "/sys/class/thermal/thermal_zone" + zoneIndex + "/type";
            String tempPath = "/sys/class/thermal/thermal_zone" + zoneIndex + "/temp";

            String type = readSysFile(typePath);
            if (type == null) break;
            
            String tempRaw = readSysFile(tempPath);
            String tempFormatted = formatTemperature(tempRaw);

            if (tempFormatted != null && !tempFormatted.equals("0.0")) {
                result.append(type).append(": ").append(tempFormatted).append('\n');
            }

            zoneIndex++;
        }
        return result.toString();
    }

    private static String readSysFile(String path) {
        try {
            File file = new File(path);
            if (!file.exists() || !file.canRead()) return null;
            try (FileInputStream fis = new FileInputStream(file);
                 BufferedReader br = new BufferedReader(new InputStreamReader(fis))) {
                String line = br.readLine();
                return line != null ? line.trim() : null;
            }
        } catch (Throwable e) {
            return null;
        }
    }

    private static String formatTemperature(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            float value = Float.parseFloat(raw);
            int intValue = (int) value;
            if (intValue == 0) return "0.0";

            float adjusted;
            if (intValue > 10000) {
                adjusted = value / 1000f;
            } else if (intValue > 1000) {
                adjusted = value / 100f;
            } else if (intValue > 100) {
                adjusted = value / 10f;
            } else {
                adjusted = value;
            }
            return String.valueOf(adjusted);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
