package com.example.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.FeedSurgeonApp
import com.example.data.repository.RulesetSyncResult
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class RulesetSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? FeedSurgeonApp ?: return Result.failure()
        val forceSync = inputData.getBoolean(KEY_FORCE_SYNC, false)

        app.rulesetRepository.ensureLocalCacheSeeded()
        val prefs = app.settingsDataStore.preferencesFlow.first()
        if (!forceSync && !prefs.autoUpdateRules) {
            return Result.success()
        }

        return when (val syncResult = app.rulesetRepository.syncFromRemoteOrFallback()) {
            is RulesetSyncResult.Success -> {
                if (syncResult.networkErrorReason != null) {
                    if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                        Result.retry()
                    } else {
                        revertToHardcodedAndComplete(syncResult.networkErrorReason)
                    }
                } else {
                    Result.success(
                        workDataOf(
                            KEY_RESULT_VERSION to syncResult.version,
                            KEY_RESULT_FROM_CACHE to syncResult.fromLocalCache,
                            KEY_RESULT_ETAG_UNCHANGED to syncResult.etagUnchanged,
                            KEY_RESULT_REVERTED_HARDCODED to syncResult.revertedToHardcoded
                        )
                    )
                }
            }
            is RulesetSyncResult.Error -> {
                if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                    Result.retry()
                } else {
                    revertToHardcodedAndComplete(syncResult.message)
                }
            }
        }
    }

    private suspend fun revertToHardcodedAndComplete(reason: String): Result {
        val app = applicationContext as? FeedSurgeonApp ?: return Result.failure()
        return when (val fallback = app.rulesetRepository.revertToHardcodedRuleset(reason)) {
            is RulesetSyncResult.Success -> Result.success(
                workDataOf(
                    KEY_RESULT_VERSION to fallback.version,
                    KEY_RESULT_FROM_CACHE to true,
                    KEY_RESULT_ETAG_UNCHANGED to false,
                    KEY_RESULT_REVERTED_HARDCODED to true
                )
            )
            is RulesetSyncResult.Error -> Result.failure(
                workDataOf(KEY_RESULT_ERROR to fallback.message)
            )
        }
    }

    companion object {
        const val UNIQUE_PERIODIC_WORK_NAME = "outloop_ruleset_periodic_sync"
        const val UNIQUE_ONE_TIME_WORK_NAME = "outloop_ruleset_immediate_sync"
        const val KEY_FORCE_SYNC = "force_sync"
        const val KEY_RESULT_VERSION = "result_version"
        const val KEY_RESULT_FROM_CACHE = "result_from_cache"
        const val KEY_RESULT_ETAG_UNCHANGED = "result_etag_unchanged"
        const val KEY_RESULT_REVERTED_HARDCODED = "result_reverted_hardcoded"
        const val KEY_RESULT_ERROR = "result_error"
        const val MAX_RETRY_ATTEMPTS = 3

        private const val TAG = "RulesetSyncWorker"

        private fun getWorkManager(context: Context): WorkManager? = try {
            WorkManager.getInstance(context.applicationContext)
        } catch (e: IllegalStateException) {
            android.util.Log.w(TAG, "WorkManager is not initialized yet; skipping work update", e)
            null
        }

        fun schedulePeriodicSync(context: Context) {
            val workManager = getWorkManager(context) ?: return
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<RulesetSyncWorker>(
                6L, TimeUnit.HOURS,
                30L, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15L, TimeUnit.MINUTES)
                .build()

            workManager.enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancelPeriodicSync(context: Context) {
            getWorkManager(context)?.cancelUniqueWork(UNIQUE_PERIODIC_WORK_NAME)
        }

        fun enqueueImmediateSync(context: Context) {
            val workManager = getWorkManager(context) ?: return
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<RulesetSyncWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_FORCE_SYNC to true))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1L, TimeUnit.MINUTES)
                .build()

            workManager.enqueueUniqueWork(
                UNIQUE_ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
