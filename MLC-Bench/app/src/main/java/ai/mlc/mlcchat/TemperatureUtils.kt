package ai.mlc.mlcchat

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

object TemperatureUtils {
    fun getBatteryTemperature(context: Context): Float? {
        return try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, filter)
            val tenthsCelsius = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            if (tenthsCelsius == null || tenthsCelsius == Int.MIN_VALUE) null else tenthsCelsius / 10.0f
        } catch (_: Throwable) {
            null
        }
    }

    fun getThermalInfo(): String {
        val result = StringBuilder()
        var zoneIndex = 0
        while (true) {
            val typePath = "/sys/class/thermal/thermal_zone$zoneIndex/type"
            val tempPath = "/sys/class/thermal/thermal_zone$zoneIndex/temp"

            val type = readSysFile(typePath) ?: break
            val tempRaw = readSysFile(tempPath)
            val tempFormatted = formatTemperature(tempRaw)

            if (tempFormatted != null && tempFormatted != "0.0") {
                result.append(type).append(": ").append(tempFormatted).append('\n')
            }

            zoneIndex++
        }
        return result.toString()
    }

    private fun readSysFile(path: String): String? {
        return try {
            val file = File(path)
            if (!file.exists() || !file.canRead()) return null
            FileInputStream(file).use { fis ->
                BufferedReader(InputStreamReader(fis)).use { br ->
                    br.readLine()
                }
            }?.trim()
        } catch (_: Throwable) {
            null
        }
    }

    private fun formatTemperature(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val value = raw.toFloatOrNull() ?: return null
        val intValue = value.toInt()
        if (intValue == 0) return "0.0"

        val adjusted = when {
            intValue > 10000 -> value / 1000f
            intValue > 1000 -> value / 100f
            intValue > 100 -> value / 10f
            else -> value
        }
        return adjusted.toString()
    }
}

