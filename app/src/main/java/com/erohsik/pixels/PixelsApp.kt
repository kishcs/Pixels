package com.erohsik.pixels

import android.app.Application
import com.erohsik.pixels.di.ServiceLocator

class PixelsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
