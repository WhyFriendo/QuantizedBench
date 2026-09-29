package com.example.executorchllamademo;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public final class MemoryInfo {
    private MemoryInfo() {}

    public static long getVmRssMb() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/status"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("VmRSS:")) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 2) {
                        long kb = Long.parseLong(parts[1]);
                        return kb / 1024L;
                    }
                }
            }
        } catch (IOException | NumberFormatException ignored) {
        }
        return 0L;
    }
}
