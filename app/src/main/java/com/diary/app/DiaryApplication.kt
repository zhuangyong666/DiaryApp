package com.diary.app

import android.app.Application
import android.util.Log
import com.diary.app.data.AppDatabase

class DiaryApplication : Application() {

    val database: AppDatabase by lazy {
        AppDatabase.getInstance(this)
    }

    override fun onCreate() {
        super.onCreate()

        // Catch crashes and log them
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("DiaryApp", "CRASH on thread ${thread.name}", throwable)
            throwable.printStackTrace()
            // Try to save crash log
            try {
                val crashDir = java.io.File(filesDir, "crash_logs")
                if (!crashDir.exists()) crashDir.mkdirs()
                val crashFile = java.io.File(crashDir, "crash_${System.currentTimeMillis()}.txt")
                val sw = java.io.StringWriter()
                throwable.printStackTrace(java.io.PrintWriter(sw))
                crashFile.writeText("Thread: ${thread.name}\n${sw.toString()}")
                Log.e("DiaryApp", "Crash log saved to: ${crashFile.absolutePath}")
            } catch (e: Exception) {
                Log.e("DiaryApp", "Failed to save crash log", e)
            }
        }

        Log.d("DiaryApp", "DiaryApplication onCreate")
    }
}
