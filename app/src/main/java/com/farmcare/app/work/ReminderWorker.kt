package com.farmcare.app.work

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.farmcare.app.data.FarmRepository
import com.farmcare.app.domain.HealthRules
import com.farmcare.app.domain.VaccStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted private val ctx: Context, @Assisted params: WorkerParameters, private val repo: FarmRepository
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        if (repo.membership() == null) Result.success() else {
            val pending = HealthRules.pending(repo.vaccinationsOnly(), LocalDate.now())
            val overdue = pending.count { it.status == VaccStatus.OVERDUE }
            val soon = pending.count { it.status == VaccStatus.DUE_TODAY || it.status == VaccStatus.DUE_SOON }
            val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if ((overdue + soon) > 0 && granted) {
                val n = NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(android.R.drawable.stat_notify_more)
                    .setContentTitle("FarmCare health tasks").setContentText("$overdue overdue, $soon due soon. Open Health Tasks to act.")
                    .setAutoCancel(true).build()
                NotificationManagerCompat.from(ctx).notify(1, n)  // informational only; completion needs the health-record workflow
            }
            Result.success()
        }
    } catch (e: Exception) { Result.retry() }
    companion object { const val CHANNEL = "health_reminders" }
}
