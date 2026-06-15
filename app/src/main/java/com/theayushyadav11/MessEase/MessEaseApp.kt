package com.theayushyadav11.MessEase

import android.app.Application
import com.cloudinary.android.MediaManager

class MessEaseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val config = mapOf("cloud_name" to "dw6gpswrw")
        MediaManager.init(this, config)
    }
}
