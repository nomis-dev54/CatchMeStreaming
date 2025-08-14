package com.example.catchmestreaming.util

import android.util.Log

/**
 * Secure logging utility for CatchMeStreaming application.
 * Provides controlled logging with security considerations.
 */
object Logger {
    
    private const val APP_TAG = "CatchMeStreaming"
    private var isLoggingEnabled = true
    
    /**
     * Enable or disable logging globally
     */
    fun setLoggingEnabled(enabled: Boolean) {
        isLoggingEnabled = enabled
    }
    
    /**
     * Check if logging is enabled
     */
    fun isLoggingEnabled(): Boolean = isLoggingEnabled
    
    /**
     * Debug level logging
     */
    fun d(tag: String, message: String) {
        if (isLoggingEnabled) {
            Log.d("$APP_TAG:$tag", message)
        }
    }
    
    /**
     * Info level logging
     */
    fun i(tag: String, message: String) {
        if (isLoggingEnabled) {
            Log.i("$APP_TAG:$tag", message)
        }
    }
    
    /**
     * Warning level logging
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (isLoggingEnabled) {
            if (throwable != null) {
                Log.w("$APP_TAG:$tag", message, throwable)
            } else {
                Log.w("$APP_TAG:$tag", message)
            }
        }
    }
    
    /**
     * Error level logging
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (isLoggingEnabled) {
            if (throwable != null) {
                Log.e("$APP_TAG:$tag", message, throwable)
            } else {
                Log.e("$APP_TAG:$tag", message)
            }
        }
    }
    
    /**
     * Verbose level logging
     */
    fun v(tag: String, message: String) {
        if (isLoggingEnabled) {
            Log.v("$APP_TAG:$tag", message)
        }
    }
}