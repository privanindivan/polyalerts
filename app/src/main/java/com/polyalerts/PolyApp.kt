package com.polyalerts

import android.app.Application
import com.polyalerts.alerts.AlertScheduler
import com.polyalerts.alerts.Notifications

class PolyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannel(this)
        // Start the periodic price-check worker on first launch.
        AlertScheduler.ensureScheduled(this)
    }
}
