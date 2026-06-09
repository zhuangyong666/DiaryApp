package com.diary.app

import android.app.Application
import com.diary.app.data.AppDatabase

class DiaryApplication : Application() {

    val database: AppDatabase by lazy {
        AppDatabase.getInstance(this)
    }

    override fun onCreate() {
        super.onCreate()
    }
}
