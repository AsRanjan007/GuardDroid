package com.guarddroid.app

import android.app.Application
import com.guarddroid.app.data.ScanRepository
import com.guarddroid.app.notification.NotificationHelper
import com.guarddroid.app.worker.AppScanWorker

/**
 * Application entry point. Wires up the persistent scan store, notification
 * channels and the recurring background scan when the process starts.
 */
class GuardDroidApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ScanRepository.load(this)
        NotificationHelper.createChannels(this)
        AppScanWorker.schedulePeriodicScan(this)
    }
}
