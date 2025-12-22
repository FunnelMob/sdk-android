package com.funnelmob.sdk.internal

import android.util.Log
import com.funnelmob.sdk.FunnelMobConfiguration.LogLevel

/**
 * Internal logger for FunnelMob SDK
 */
internal object Logger {

    private const val TAG = "FunnelMob"

    var logLevel: LogLevel = LogLevel.NONE

    fun error(message: String, throwable: Throwable? = null) {
        if (logLevel.priority >= LogLevel.ERROR.priority) {
            if (throwable != null) {
                Log.e(TAG, message, throwable)
            } else {
                Log.e(TAG, message)
            }
        }
    }

    fun warning(message: String) {
        if (logLevel.priority >= LogLevel.WARNING.priority) {
            Log.w(TAG, message)
        }
    }

    fun info(message: String) {
        if (logLevel.priority >= LogLevel.INFO.priority) {
            Log.i(TAG, message)
        }
    }

    fun debug(message: String) {
        if (logLevel.priority >= LogLevel.DEBUG.priority) {
            Log.d(TAG, message)
        }
    }

    fun verbose(message: String) {
        if (logLevel.priority >= LogLevel.VERBOSE.priority) {
            Log.v(TAG, message)
        }
    }
}
