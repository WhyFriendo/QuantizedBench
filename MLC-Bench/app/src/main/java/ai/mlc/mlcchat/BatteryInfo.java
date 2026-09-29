package ai.mlc.mlcchat;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public final class BatteryInfo {
    private BatteryInfo() {}

    public static final class BatteryStats {
        public final double batteryCapacityMah;
        public final double remainingCapacityMah;
        public final double usedCapacityMah;
        public final double batteryPercentage; // 0..100
        public final Double currentDrawMa;     // may be null
        public final Boolean isCharging;       // may be null
        public final Double energyMwh;         // may be null
        public final Double currentAvgMa;      // may be null
        public final Double chargeCounterMah;  // may be null
        public final Double voltageV;          // may be null

        public BatteryStats(
                double batteryCapacityMah,
                double remainingCapacityMah,
                double usedCapacityMah,
                double batteryPercentage,
                Double currentDrawMa,
                Boolean isCharging,
                Double energyMwh,
                Double currentAvgMa,
                Double chargeCounterMah,
                Double voltageV
        ) {
            this.batteryCapacityMah = batteryCapacityMah;
            this.remainingCapacityMah = remainingCapacityMah;
            this.usedCapacityMah = usedCapacityMah;
            this.batteryPercentage = batteryPercentage;
            this.currentDrawMa = currentDrawMa;
            this.isCharging = isCharging;
            this.energyMwh = energyMwh;
            this.currentAvgMa = currentAvgMa;
            this.chargeCounterMah = chargeCounterMah;
            this.voltageV = voltageV;
        }

        @Override
        public String toString() {
            return "BatteryStats{" +
                    "batteryCapacityMah=" + batteryCapacityMah +
                    ", remainingCapacityMah=" + remainingCapacityMah +
                    ", usedCapacityMah=" + usedCapacityMah +
                    ", batteryPercentage=" + batteryPercentage +
                    ", currentDrawMa=" + currentDrawMa +
                    ", isCharging=" + isCharging +
                    ", energyMwh=" + energyMwh +
                    ", currentAvgMa=" + currentAvgMa +
                    ", chargeCounterMah=" + chargeCounterMah +
                    ", voltageV=" + voltageV +
                    '}';
        }
    }

    public static BatteryStats getPreciseBatteryStats(Context context) {
        double maxCapacityMah = getBatteryDesignCapacity(context);

        double remainingCapacityMah = getRemainingCapacityDirect(context);

        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, filter);

        double batteryPct = 0.0;
        Double voltageV = null;
        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level >= 0 && scale > 0) {
                batteryPct = level / (double) scale;
            }
            int voltageRaw = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
            if (voltageRaw > 0) {
                voltageV = voltageRaw / 1000.0;
            }
        }

        if (remainingCapacityMah <= 0 && maxCapacityMah > 0) {
            remainingCapacityMah = batteryPct * maxCapacityMah;
        }

        Double currentNowMa = null;
        Boolean isCharging = null;
        Double energyMwh = null;
        Double currentAvgMa = null;
        Double chargeCounterMah = null;

        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (bm != null) {
            try {
                int currentNowUa = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                if (currentNowUa != Integer.MIN_VALUE && currentNowUa != 0) {
                    currentNowMa = Math.abs(currentNowUa / 1000.0);
                    isCharging = currentNowUa > 0;
                }
            } catch (Throwable ignored) {}

            try {
                long energyCounterNwh = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER);
                if (energyCounterNwh > 0) {
                    energyMwh = energyCounterNwh / 1_000_000.0; // nWh -> mWh
                }
            } catch (Throwable ignored) {}

            try {
                int currentAvgUa = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);
                if (currentAvgUa != Integer.MIN_VALUE && currentAvgUa != 0) {
                    currentAvgMa = Math.abs(currentAvgUa / 1000.0);
                }
            } catch (Throwable ignored) {}

            try {
                long chargeCounterUah = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
                if (chargeCounterUah > 0) {
                    chargeCounterMah = chargeCounterUah / 1000.0;
                    if (chargeCounterMah > 0) {
                        remainingCapacityMah = chargeCounterMah;
                    }
                }
            } catch (Throwable ignored) {}
        }

        double usedCapacityMah = Math.max(0.0, maxCapacityMah - Math.max(0.0, remainingCapacityMah));

        return new BatteryStats(
                maxCapacityMah,
                remainingCapacityMah,
                usedCapacityMah,
                batteryPct * 100.0,
                currentNowMa,
                isCharging,
                energyMwh,
                currentAvgMa,
                chargeCounterMah,
                voltageV
        );
    }

    private static double getBatteryDesignCapacity(Context context) {
        double capacity = 0.0;

        // Method 1: PowerProfile (reflection)
        try {
            final Class<?> powerProfileClass = Class.forName("com.android.internal.os.PowerProfile");
            Constructor<?> constructor = powerProfileClass.getConstructor(Context.class);
            Object powerProfile = constructor.newInstance(context);
            Method getBatteryCapacity = powerProfileClass.getMethod("getBatteryCapacity");
            Object result = getBatteryCapacity.invoke(powerProfile);
            if (result instanceof Double) {
                capacity = (Double) result;
                if (capacity > 0) return capacity;
            }
        } catch (Throwable ignored) {}

        // Method 2: Read from common sysfs paths
        String[] designCapacityPaths = new String[] {
                "/sys/class/power_supply/battery/charge_full_design",
                "/sys/class/power_supply/battery/design_capacity",
                "/sys/class/power_supply/battery/power_supply/battery/charge_full_design",
                "/sys/class/power_supply/max170xx_battery/charge_full_design"
        };

        for (String path : designCapacityPaths) {
            Double value = readFirstDoubleFromFile(path);
            if (value != null) {
                double v = value;
                // Heuristic: values in microampere-hours are very large
                if (v > 10_000_000) v = v / 1000.0; // µAh -> mAh
                if (v > 0) return v;
            }
        }

        // Method 3: conservative default
        return 3000.0;
    }

    private static double getRemainingCapacityDirect(Context context) {
        String[] paths = new String[] {
                "/sys/class/power_supply/battery/charge_now",
                "/sys/class/power_supply/battery/charge_counter",
                "/sys/class/power_supply/battery/capacity_now",
                "/sys/class/power_supply/battery/batt_current_capacity",
                "/sys/class/power_supply/battery/fg_current_capacity",
                "/sys/class/power_supply/battery/current_capacity"
        };
        for (String path : paths) {
            Double value = readFirstDoubleFromFile(path);
            if (value != null) {
                double v = value;
                if (v > 10_000_000) v = v / 1000.0; // µAh -> mAh
                if (v > 0) return v;
            }
        }

        // Fallback using BatteryManager charge counter if available
        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (bm != null) {
            try {
                long chargeCounterUah = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
                if (chargeCounterUah > 0) {
                    return chargeCounterUah / 1000.0; // µAh -> mAh
                }
            } catch (Throwable ignored) {}
        }
        return 0.0;
    }

    private static Double readFirstDoubleFromFile(String path) {
        File f = new File(path);
        if (!f.exists()) return null;
        try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
            String line = reader.readLine();
            if (line == null || line.isEmpty()) return null;
            return Double.parseDouble(line.trim());
        } catch (IOException | NumberFormatException e) {
            return null;
        }
    }
}