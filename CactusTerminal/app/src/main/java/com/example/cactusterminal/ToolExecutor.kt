package com.example.cactusterminal

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.cactus.models.CactusTool
import com.cactus.models.ToolParameter
import com.cactus.models.createTool
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Everything the on-device model (Needle 2) is allowed to do to the phone.
 * Add a new capability by: 1) declaring it in `allTools`, 2) handling its name in `execute`.
 */
class ToolExecutor(private val context: Context) {

    private var torchOn = false

    val allTools: List<CactusTool> = listOf(
        createTool(
            name = "get_battery_level",
            description = "Get the current battery percentage and charging status of the phone.",
            parameters = emptyMap()
        ),
        createTool(
            name = "toggle_flashlight",
            description = "Turn the phone's camera flashlight (torch) on or off.",
            parameters = mapOf(
                "state" to ToolParameter(
                    type = "string",
                    description = "Either \"on\" or \"off\"",
                    required = true
                )
            )
        ),
        createTool(
            name = "vibrate",
            description = "Vibrate the phone for a given duration.",
            parameters = mapOf(
                "duration_ms" to ToolParameter(
                    type = "string",
                    description = "How long to vibrate, in milliseconds, e.g. \"300\"",
                    required = false
                )
            )
        ),
        createTool(
            name = "open_app",
            description = "Launch another app installed on the phone by its display name, e.g. \"Camera\" or \"Chrome\".",
            parameters = mapOf(
                "app_name" to ToolParameter(
                    type = "string",
                    description = "The visible name of the app to open",
                    required = true
                )
            )
        ),
        createTool(
            name = "get_device_info",
            description = "Get the phone's model name and Android OS version.",
            parameters = emptyMap()
        ),
        createTool(
            name = "get_time",
            description = "Get the current date and time on the phone.",
            parameters = emptyMap()
        )
    )

    /** Runs the requested tool and returns a short text result to feed back to the model. */
    fun execute(name: String, arguments: Map<String, String>): String {
        return try {
            when (name) {
                "get_battery_level" -> getBatteryLevel()
                "toggle_flashlight" -> toggleFlashlight(arguments["state"].orEmpty())
                "vibrate" -> vibrate(arguments["duration_ms"])
                "open_app" -> openApp(arguments["app_name"].orEmpty())
                "get_device_info" -> getDeviceInfo()
                "get_time" -> getTime()
                else -> "Error: unknown tool '$name'"
            }
        } catch (e: Exception) {
            "Error running $name: ${e.message}"
        }
    }

    private fun getBatteryLevel(): String {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = bm.isCharging
        return "Battery at $level%${if (charging) " (charging)" else ""}"
    }

    private fun toggleFlashlight(state: String): String {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "No flashlight available on this device"

        val turnOn = state.trim().lowercase() != "off"
        cameraManager.setTorchMode(cameraId, turnOn)
        torchOn = turnOn
        return "Flashlight turned ${if (turnOn) "on" else "off"}"
    }

    private fun vibrate(durationMsRaw: String?): String {
        val durationMs = durationMsRaw?.toLongOrNull() ?: 300L
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        return "Vibrated for ${durationMs}ms"
    }

    private fun openApp(appName: String): String {
        if (appName.isBlank()) return "No app name given"
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)

        val match = activities.firstOrNull {
            it.loadLabel(pm).toString().contains(appName, ignoreCase = true)
        } ?: return "Couldn't find an installed app matching \"$appName\""

        val intent = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            ?: return "Found ${match.loadLabel(pm)} but couldn't build a launch intent for it"
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return "Opened ${match.loadLabel(pm)}"
    }

    private fun getDeviceInfo(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    }

    private fun getTime(): String {
        val fmt = SimpleDateFormat("EEE, MMM d yyyy HH:mm:ss", Locale.getDefault())
        return fmt.format(Date())
    }
}
