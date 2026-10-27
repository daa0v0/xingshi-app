package app.shixing.client.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 客户端侧的限流。官方要求：
 * - 每个网络地址合计每分钟 120 次左右；
 * - 轮询提醒两次之间至少 25 秒，建议按返回的 every（60 秒）；
 * - 收到 `429` 按 `Retry-After` 等（见 [pause]）。
 * 「第一份读」（最近提醒、星轨、互相连上、所在地图、我打过分的地图）本地另外合计每天 300 次（见 TokenStore）：
 * 这是 1.8.4 以前的官方上限，现在官方是每个授权每天 1000 次，本地照旧从严；提醒轮询不算在里面。
 */
object Throttle {

    private const val MINUTE = 60_000L
    private const val PER_MINUTE = 120

    private val mutex = Mutex()
    private val stamps = ArrayDeque<Long>(PER_MINUTE + 8)
    private val lastAt = HashMap<String, Long>()

    /** 挂起直到允许发下一个请求；key 用来区分不同接口的最小间隔。 */
    suspend fun acquire(key: String, minGapMs: Long) {
        val wait = mutex.withLock {
            val now = System.currentTimeMillis()
            while (stamps.isNotEmpty() && now - stamps.first() >= MINUTE) stamps.removeFirst()
            val gapOfKey = (lastAt[key] ?: 0L) + minGapMs - now
            val gapOfMinute = if (stamps.size >= PER_MINUTE) MINUTE - (now - stamps.first()) else 0L
            val wait = maxOf(gapOfKey, gapOfMinute, 0L)
            if (wait == 0L) {
                stamps.addLast(now)
                lastAt[key] = now
            }
            wait
        }
        if (wait > 0) {
            delay(wait)
            acquire(key, minGapMs)
        }
    }

    /** 接口 → (到什么时候, 当时的错误码)。 */
    private val pausedUntil = HashMap<String, Pair<Long, String>>()

    /**
     * 服务器让等的（`429` 的 `Retry-After`，或 `RateLimit-Remaining` 到 0）：在那之前这个接口不再发请求。
     * 每分钟的限制等 60 秒，每天的次数等到北京时间零点，不是一律一分钟。
     */
    fun pause(key: String, ms: Long, error: String) {
        if (ms <= 0) return
        synchronized(pausedUntil) { pausedUntil[key] = (System.currentTimeMillis() + ms) to error }
    }

    /** 还要等多少毫秒和当时的错误码；不用等时是 null。 */
    fun paused(key: String): Pair<Long, String>? {
        synchronized(pausedUntil) {
            val (until, error) = pausedUntil[key] ?: return null
            val left = until - System.currentTimeMillis()
            if (left > 0) return left to error
            pausedUntil.remove(key)
            return null
        }
    }

    /** 换了令牌（断开、重新连接）就从头算：每天的次数是按授权算的。 */
    fun clearPauses() {
        synchronized(pausedUntil) { pausedUntil.clear() }
    }
}
