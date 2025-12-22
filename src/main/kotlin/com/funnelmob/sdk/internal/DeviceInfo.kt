package com.funnelmob.sdk.internal

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Device information collector
 */
internal class DeviceInfo(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    /**
     * Unique device identifier
     * Uses Android ID or generates a UUID if not available
     */
    val deviceId: String
        get() = getOrCreateDeviceId()

    /**
     * Operating system name
     */
    val osName: String = "Android"

    /**
     * Operating system version
     */
    val osVersion: String = Build.VERSION.RELEASE

    /**
     * Device manufacturer
     */
    val manufacturer: String = Build.MANUFACTURER

    /**
     * Device model
     */
    val deviceModel: String = Build.MODEL

    /**
     * App version name
     */
    val appVersion: String
        get() = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }

    /**
     * App version code
     */
    val appVersionCode: Long
        get() = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toLong()
            }
        } catch (e: Exception) {
            0L
        }

    /**
     * User locale
     */
    val locale: String
        get() = Locale.getDefault().toString()

    /**
     * User timezone
     */
    val timezone: String
        get() = TimeZone.getDefault().id

    /**
     * Screen width in pixels
     */
    val screenWidth: Int
        get() = context.resources.displayMetrics.widthPixels

    /**
     * Screen height in pixels
     */
    val screenHeight: Int
        get() = context.resources.displayMetrics.heightPixels

    // MARK: - Private

    private fun getOrCreateDeviceId(): String {
        val stored = prefs.getString(KEY_DEVICE_ID, null)
        if (stored != null) return stored

        val newId = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, newId).apply()
        return newId
    }

    /**
     * Create device context for API requests
     */
    fun toContext(): DeviceContext {
        return DeviceContext(
            appVersion = appVersion,
            osName = osName,
            osVersion = osVersion,
            deviceModel = "$manufacturer $deviceModel",
            locale = locale,
            timezone = timezone,
            screenWidth = screenWidth,
            screenHeight = screenHeight
        )
    }

    companion object {
        private const val PREFS_NAME = "com.funnelmob.sdk.device"
        private const val KEY_DEVICE_ID = "device_id"
    }
}

/**
 * Device context for API requests
 */
internal data class DeviceContext(
    val appVersion: String,
    val osName: String,
    val osVersion: String,
    val deviceModel: String,
    val locale: String,
    val timezone: String,
    val screenWidth: Int,
    val screenHeight: Int
)
