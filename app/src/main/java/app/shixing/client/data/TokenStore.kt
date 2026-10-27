package app.shixing.client.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "shixing")

/**
 * 令牌只存在玩家自己的设备上（官方要求：令牌只发给 vrcc.cc，不经过任何第三方）。
 * 这里不加密是刻意的取舍之外的遗留项：A1 会换成 EncryptedSharedPreferences。
 */
class TokenStore(private val context: Context) {

    private object K {
        val token = stringPreferencesKey("token")
        val star = intPreferencesKey("star")
        val vrc = stringPreferencesKey("vrc")
        val lastNotif = longPreferencesKey("last_notif")
        val lastPollAt = longPreferencesKey("last_poll_at")
        val dailyDate = stringPreferencesKey("daily_date")
        val dailyCount = intPreferencesKey("daily_count")
    }

    val tokenFlow: Flow<String?> = context.dataStore.data.map { it[K.token] }

    suspend fun token(): String? = context.dataStore.data.first()[K.token]

    suspend fun saveConnect(token: String, me: Me?) {
        context.dataStore.edit {
            it[K.token] = token
            me?.star?.let { s -> it[K.star] = s }
            me?.vrc?.let { v -> it[K.vrc] = v }
        }
    }

    suspend fun star(): Int? = context.dataStore.data.first()[K.star]

    suspend fun vrc(): String? = context.dataStore.data.first()[K.vrc]

    suspend fun clear() {
        context.dataStore.edit {
            it.remove(K.token)
            it.remove(K.star)
            it.remove(K.vrc)
            it.remove(K.lastNotif)
            it.remove(K.lastPollAt)
        }
    }

    suspend fun lastNotif(): Long? = context.dataStore.data.first()[K.lastNotif]

    suspend fun setLastNotif(id: Long?) {
        context.dataStore.edit { p ->
            if (id == null) p.remove(K.lastNotif) else p[K.lastNotif] = id
        }
    }

    suspend fun lastPollAt(): Long = context.dataStore.data.first()[K.lastPollAt] ?: 0L

    suspend fun setLastPollAt(at: Long) {
        context.dataStore.edit { it[K.lastPollAt] = at }
    }

    /**
     * 「第一份读」的本地日配额：最近的提醒、星轨相交、互相连上、所在的地图、我打过分的地图
     * 合计每天 300 次（官方是每个授权每天 1000 次，本地从严）。本地先扣，宁可少问也不越界。
     * 提醒轮询（`me/notifications`）官方没有每天上限、只要求两次间隔 25 秒，不在这里扣。
     */
    suspend fun takeDailyQuota(amount: Int = 1): Boolean {
        var allowed = false
        context.dataStore.edit { p ->
            val today = beijingDay()
            val sameDay = p[K.dailyDate] == today
            val used = if (sameDay) (p[K.dailyCount] ?: 0) else 0
            if (used + amount <= 300) {
                p[K.dailyDate] = today
                p[K.dailyCount] = used + amount
                allowed = true
            }
        }
        return allowed
    }

    suspend fun dailyUsed(): Int {
        val p = context.dataStore.data.first()
        return if (p[K.dailyDate] == beijingDay()) (p[K.dailyCount] ?: 0) else 0
    }

    /** 官方每天的次数在北京时间零点重置，本地的日期也按北京时间算，不跟着手机时区走。 */
    private fun beijingDay(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai") }
            .format(java.util.Date())
}
