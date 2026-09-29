package com.llmquantbench;

import androidx.annotation.NonNull;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.Arguments;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import android.os.Process;
import android.os.Build;
import android.app.ActivityManager;
import android.content.Context;
import java.io.RandomAccessFile;

public class CPUMetricsModule extends ReactContextBaseJavaModule {
    private final ReactApplicationContext reactContext;
    private static final String MODULE_NAME = "CPUMetrics";
    
    private long[] prevTotalCpuTimes;
    private long[] prevIdleCpuTimes;
    private long prevAppCpuTime = 0;
    private long prevSystemUptime = 0;
    private int numCores = Runtime.getRuntime().availableProcessors();

    public CPUMetricsModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @NonNull
    @Override
    public String getName() {
        return MODULE_NAME;
    }
    
    @ReactMethod
    public void getCPUMetrics(Promise promise) {
        try {
            WritableMap result = Arguments.createMap();
            
            WritableArray cpuUsageArray = getCPUUsagePerCore();
            result.putArray("cpuUsagePerCore", cpuUsageArray);
            
            double appCpuUsage = getAppCpuUsage();
            result.putDouble("appCpuUsage", appCpuUsage);
            
            result.putInt("numCores", numCores);
            
            promise.resolve(result);
        } catch (Exception e) {
            promise.reject("CPU_METRICS_ERROR", "Failed to get CPU metrics: " + e.getMessage(), e);
        }
    }
    
    private WritableArray getCPUUsagePerCore() {
        WritableArray cpuUsageArray = Arguments.createArray();
        
        if (prevTotalCpuTimes == null || prevIdleCpuTimes == null) {
            prevTotalCpuTimes = new long[numCores + 1];
            prevIdleCpuTimes = new long[numCores + 1];
        }
        
        try {
            List<String> cpuStats = readCpuStats();
            
            if (!cpuStats.isEmpty()) {
                for (int i = 0; i < cpuStats.size(); i++) {
                    String line = cpuStats.get(i);
                    String[] parts = line.split("\\s+");
                    
                    if (parts.length < 8) {
                        continue;
                    }
                    
                    long user = Long.parseLong(parts[1]);
                    long nice = Long.parseLong(parts[2]);
                    long system = Long.parseLong(parts[3]);
                    long idle = Long.parseLong(parts[4]);
                    long iowait = Long.parseLong(parts[5]);
                    long irq = Long.parseLong(parts[6]);
                    long softirq = Long.parseLong(parts[7]);
                    long steal = 0;
                    if (parts.length > 8) {
                        steal = Long.parseLong(parts[8]);
                    }
                    
                    long totalCpuTime = user + nice + system + idle + iowait + irq + softirq + steal;
                    long idleCpuTime = idle + iowait;
                    
                    double cpuUsage = 0;
                    if (prevTotalCpuTimes[i] > 0 && prevIdleCpuTimes[i] > 0) {
                        long totalDiff = totalCpuTime - prevTotalCpuTimes[i];
                        long idleDiff = idleCpuTime - prevIdleCpuTimes[i];
                        
                        if (totalDiff > 0) {
                            cpuUsage = 100.0 * (1.0 - (double) idleDiff / totalDiff);
                        }
                    }
                    
                    prevTotalCpuTimes[i] = totalCpuTime;
                    prevIdleCpuTimes[i] = idleCpuTime;
                    
                    if (i > 0) {
                        cpuUsageArray.pushDouble(cpuUsage);
                    }
                }
            } else {
                fallbackCpuUsage(cpuUsageArray);
            }
        } catch (Exception e) {
            fallbackCpuUsage(cpuUsageArray);
        }
        
        return cpuUsageArray;
    }
    
    private void fallbackCpuUsage(WritableArray cpuUsageArray) {        
        try {
            ActivityManager activityManager = (ActivityManager) reactContext.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(memoryInfo);
            
            double systemLoad = getSystemLoad();
            
            for (int i = 0; i < numCores; i++) {
                cpuUsageArray.pushDouble(systemLoad);
            }
        } catch (Exception e) {
            for (int i = 0; i < numCores; i++) {
                cpuUsageArray.pushDouble(0.0);
            }
        }
    }
    
    private double getSystemLoad() {
        try {
            RandomAccessFile reader = new RandomAccessFile("/proc/loadavg", "r");
            String load = reader.readLine();
            reader.close();
            
            String[] parts = load.split("\\s+");
            if (parts.length > 0) {
                double loadAvg = Double.parseDouble(parts[0]);
                return Math.min(100.0, (loadAvg / numCores) * 100.0);
            }
        } catch (Exception e) {
        }
        
        return 0.0;
    }
    
    private double getAppCpuUsage() {
        int pid = Process.myPid();
        
        try {
            long appCpuTime = readAppCpuTime(pid);
            long systemUptime = readSystemUptime();
            
            double cpuUsage = 0;
            if (prevAppCpuTime > 0 && prevSystemUptime > 0) {
                long appTimeDiff = appCpuTime - prevAppCpuTime;
                long systemTimeDiff = systemUptime - prevSystemUptime;
                
                if (systemTimeDiff > 0) {
                    cpuUsage = 100.0 * ((double) appTimeDiff / systemTimeDiff);
                }
            }
            
            prevAppCpuTime = appCpuTime;
            prevSystemUptime = systemUptime;
            
            return cpuUsage;
        } catch (Exception e) {
            return getAppCpuUsageAlternative(pid);
        }
    }
    
    private double getAppCpuUsageAlternative(int pid) {
        try {
            ActivityManager activityManager = (ActivityManager) reactContext.getSystemService(Context.ACTIVITY_SERVICE);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
                activityManager.getMemoryInfo(memoryInfo);
                
                double memoryUsagePercent = 100.0 * (memoryInfo.totalMem - memoryInfo.availMem) / memoryInfo.totalMem;
                
                return Math.min(100.0, memoryUsagePercent * 0.8);
            }
        } catch (Exception e) {
        }
        
        return 0.0;
    }
    
    private List<String> readCpuStats() {
        List<String> stats = new ArrayList<>();
        BufferedReader reader = null;
        
        try {
            reader = new BufferedReader(new FileReader("/proc/stat"));
            String line;
            
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("cpu")) {
                    stats.add(line);
                }
            }
        } catch (IOException e) {
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                }
            }
        }
        
        return stats;
    }
    
    private long readAppCpuTime(int pid) {
        BufferedReader reader = null;
        long cpuTime = 0;
        
        try {
            reader = new BufferedReader(new FileReader("/proc/" + pid + "/stat"));
            String line = reader.readLine();
            if (line != null) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 15) {
                    cpuTime = Long.parseLong(parts[13]) + Long.parseLong(parts[14]);
                }
            }
        } catch (IOException e) {
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                }
            }
        }
        
        return cpuTime;
    }
    
    private long readSystemUptime() {
        BufferedReader reader = null;
        long uptime = 0;
        
        try {
            reader = new BufferedReader(new FileReader("/proc/uptime"));
            String line = reader.readLine();
            if (line != null) {
                String[] parts = line.split("\\s+");
                if (parts.length > 0) {
                    uptime = (long) (Float.parseFloat(parts[0]) * 100);
                }
            }
        } catch (IOException e) {
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                }
            }
        }
        
        return uptime;
    }
}