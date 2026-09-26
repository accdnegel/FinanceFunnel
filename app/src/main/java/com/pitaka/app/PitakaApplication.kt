package com.pitaka.app

import android.app.Application
import android.os.Build
import android.util.Log

class PitakaApplication : Application() {
    private val preferences by lazy {
        getSharedPreferences("startup_diagnostics", MODE_PRIVATE)
    }

    val previousCrash: String?
        get() = preferences.getString(KEY_PREVIOUS_CRASH, null)

    override fun onCreate() {
        super.onCreate()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val report = buildString {
                appendLine("Pitaka ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
                appendLine("${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Thread: ${thread.name}")
                appendLine()
                append(Log.getStackTraceString(throwable))
            }.take(MAX_REPORT_LENGTH)

            // A synchronous commit is intentional: Android may terminate the process as
            // soon as this handler returns, before an asynchronous apply reaches disk.
            runCatching {
                preferences.edit().putString(KEY_PREVIOUS_CRASH, report).commit()
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    fun clearPreviousCrash() {
        preferences.edit().remove(KEY_PREVIOUS_CRASH).apply()
    }

    private companion object {
        const val KEY_PREVIOUS_CRASH = "previous_crash"
        const val MAX_REPORT_LENGTH = 50_000
    }
}