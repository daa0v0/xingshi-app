package app.shixing.client.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.shixing.client.data.ApiException
import app.shixing.client.data.TokenStore
import app.shixing.client.data.VrccClient
import app.shixing.client.notify.ReminderNotifier

/**
 * 后台提醒。用官方 `/me/notifications` 的游标 `since` 只取增量，不做全量拉取。
 *
 * 三条自我约束：
 * 1. 没有令牌就直接返回，什么都不发；
 * 2. 15 分钟一轮，远大于官方要求的 25 秒间隔；提醒轮询官方没有每天上限，不扣本地日配额，
 *    免得后台把「星轨 / 互相连上 / 最近提醒」这些前台要用的次数吃掉；
 * 3. 首次只记基线不提醒，避免刚连上就弹一堆历史提醒。
 */
class ReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val store = TokenStore(applicationContext)
        store.token() ?: return Result.success()

        val client = VrccClient(token = { store.token() })

        return try {
            val since = store.lastNotif()
            val page = client.notifications(since)

            if (since == null) {
                // 第一次跑：只记基线。
                store.setLastNotif(page.last)
                return Result.success()
            }

            val fresh = page.items.filter { it.id > since }
            if (fresh.isNotEmpty()) {
                ReminderNotifier.show(applicationContext, fresh)
            }
            store.setLastNotif(page.last ?: since)
            Result.success()
        } catch (e: ApiException) {
            when (e.error) {
                // 官方要求：两个提醒接口至少间隔 25 秒，太频繁会 429。带 Retry-After 时
                // VrccClient 已经让这个接口停到那个时候，这一轮就不重试，等下一轮（15 分钟后）；
                // 旧版服务器的 too_soon 不带这个头，才交给 WorkManager 退避。
                "too_soon", "too_many" -> if (e.retryAfter != null) Result.success() else Result.retry()
                // 令牌失效（断开、重新授权、90 天没用）：删掉本机令牌，提醒一次，之后这里直接返回。
                "gone", "token_missing" -> {
                    store.clear()
                    ReminderNotifier.expired(applicationContext)
                    Result.success()
                }
                else -> Result.success()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
