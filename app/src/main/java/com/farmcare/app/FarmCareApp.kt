package com.farmcare.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.*
import com.farmcare.app.work.ReminderWorker
import com.farmcare.app.work.SyncWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class FarmCareApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    override val workManagerConfiguration get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(ReminderWorker.CHANNEL, "Health reminders", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Vaccinations and treatment follow-ups that are due" })
        SyncWorker.schedule(this)  // sends anything queued while offline
        // Daily check. Android may delay it under battery restrictions or Doze; it is a reminder, not a guarantee.
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("health-reminders", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
    }
}
