package app.shixing.client.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 后台提醒的排班。
 *
 * 用 15 分钟这个系统允许的最小周期：官方文档建议按返回的 `every`（60 秒）轮询，
 * 但那是给网页端前台用的；后台按 60 秒醒一次会把手机电池打空。提醒轮询官方没有每天上限，
 * 只要求两次间隔 25 秒，所以不扣本地日配额；15 分钟 ≈ 每天 96 次。
 */
object ReminderScheduler {

    private const val NAME = "reminders"

    fun ensure(context: Context) {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(NAME)
    }
}
