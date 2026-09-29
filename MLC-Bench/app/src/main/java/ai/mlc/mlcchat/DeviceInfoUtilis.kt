package ai.mlc.mlcchat

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

object DeviceInfoUtils {

    fun getTotalMemory(context: Context): Long {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            if (mi.totalMem > 0L) mi.totalMem else readMemTotalFromProc()
        } catch (_: Throwable) {
            readMemTotalFromProc()
        }
    }

    fun getBatteryLevel(context: Context): Float {
        return try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus: Intent? = context.registerReceiver(null, filter)
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) level.toFloat() / scale.toFloat() else 0f
        } catch (_: Throwable) {
            0f
        }
    }

    fun getModel(): String = Build.MODEL ?: ""

    fun getBrand(): String = Build.BRAND ?: ""

    fun getSystemName(): String = "Android"

    fun getSystemVersion(): String = Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString()

    private fun readMemTotalFromProc(): Long {
        return try {
            val file = File("/proc/meminfo")
            if (!file.exists() || !file.canRead()) return 0L
            FileInputStream(file).use { fis ->
                BufferedReader(InputStreamReader(fis)).use { br ->
                    val firstLine = br.readLine() ?: return 0L
                    val parts = firstLine.trim().split(Regex("\\s+"))
                    if (parts.size >= 2) {
                        val kb = parts[1].toLongOrNull() ?: return 0L
                        kb * 1024L
                    } else 0L
                }
            }
        } catch (_: Throwable) {
            0L
        }
    }
}

