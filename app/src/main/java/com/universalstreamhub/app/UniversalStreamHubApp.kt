package com.universalstreamhub.app

import android.app.Application
import com.universalstreamhub.app.core.AppGraph

class UniversalStreamHubApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.initialise(this)
    }
}
