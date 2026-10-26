package com.farmcare.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.farmcare.app.data.FarmRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncWorker @AssistedInject constructor(@Assisted ctx: Context, @Assisted params: WorkerParameters, private val repo: FarmRepository) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try { if (repo.syncPending()) Result.success() else Result.retry() } catch (e: Exception) { Result.retry() }
    companion object {
        fun schedule(ctx: Context) = WorkManager.getInstance(ctx).enqueueUniqueWork("sync-pending", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
    }
}
