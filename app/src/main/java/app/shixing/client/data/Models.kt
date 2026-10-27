package app.shixing.client.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** `connect` 与星轨等接口里通用的「我」：点亮编号 + VRChat 名字。 */
@Serializable
data class Me(
    val star: Int? = null,
    val vrc: String? = null,
)

/** 地图榜 / 查地图：`{"board":"maps","min":1,"maps":[...],"at":...}` */
@Serializable
data class Boards(
    val board: String? = null,
    val min: Int? = null,
    val maps: List<BoardMap> = emptyList(),
    val at: Long? = null,
)

@Serializable
data class BoardMap(
    val world: String,
    val name: String? = null,
    val author: String? = null,
    val avg: Double? = null,
    val raters: Int? = null,
    val rank: Int? = null,
    val weight: String? = null,
    val page: String? = null,
    val img: String? = null,
)

@Serializable
data class ConnectResult(
    val token: String,
    val me: Me? = null,
)

/** POST /device/code 的返回：给玩家看的 user_code + 同意链接。 */
@Serializable
data class DeviceCode(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String? = null,
    @SerialName("verification_uri_complete") val verificationUriComplete: String? = null,
    @SerialName("expires_in") val expiresIn: Int = 600,
    val interval: Int = 5,
    val scope: String? = null,
)

/** POST /device/token 成功后返回的令牌（只返回一次）。 */
@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String? = null,
    val scope: String? = null,
    val me: Me? = null,
)


@Serializable
data class NotificationItem(
    val id: Long,
    val title: String? = null,
    val body: String? = null,
    val url: String? = null,
    val at: Long? = null,
)

@Serializable
data class Notifications(
    val last: Long? = null,
    val items: List<NotificationItem> = emptyList(),
    val every: Int? = null,
    val dot: Int? = null,
)

@Serializable
data class Recent(
    val items: List<NotificationItem> = emptyList(),
    val read: Long? = null,
)

/** 星轨相交。`why` 非空表示这一块不该显示或缺前置条件。 */
@Serializable
data class CrossStar(
    val n: Long,
    val name: String? = null,
    val line: String? = null,
    val group: String? = null,
    val star: Int? = null,
    val intro: String? = null,
    val vrc: String? = null,
    val linked: Boolean = false,
    val mutual: Boolean = false,
    val at: Long? = null,
    val fresh: Boolean = false,
)

@Serializable
data class Crossings(
    val stars: List<CrossStar> = emptyList(),
    val n: Int? = null,
    val why: String? = null,
    val me: Me? = null,
)

@Serializable
data class MutualStar(
    val n: Long,
    val name: String? = null,
    val group: String? = null,
    val star: Int? = null,
    val vrc: String? = null,
    val vrcUser: String? = null,
    val at: Long? = null,
)

@Serializable
data class Mutuals(
    val stars: List<MutualStar> = emptyList(),
    val n: Int? = null,
)

/**
 * 「所在的地图」与打分接口返回同一个形状。
 * 回答里的 `can` / `need` 已弃用（谁都能打分，恒为 true / null，2026-12-26 起不再返回），这里不读。
 */
@Serializable
data class MyMap(
    val world: String,
    val name: String? = null,
    val board: Boolean = false,
    val pending: Boolean = false,
    val off: Boolean = false,
    val avg: Double? = null,
    val raters: Int? = null,
    val mine: Int? = null,
    val words: String? = null,
    val wordsOk: String? = null,
    val img: String? = null,
) {
    /** 官方要求：`off` 为真时这块不给这位玩家看。 */
    val visible: Boolean get() = !off
}

// ---- 我的星卡（GET /me/card）----

@Serializable
data class CardScores(
    @SerialName("G") val g: Int = 0,
    @SerialName("W") val w: Int = 0,
    @SerialName("Z") val z: Int = 0,
    @SerialName("L") val l: Int = 0,
) {
    fun pairs(): List<Pair<String, Int>> = listOf("G" to g, "W" to w, "Z" to z, "L" to l)
}

@Serializable
data class MyCard(
    val star: Int? = null,
    /** 自己的星编号（1.8.x 新增）：用它请求照片永远是清晰的。 */
    val n: Int? = null,
    val name: String = "",
    val line: String = "",
    val group: String? = null,
    val groupName: String? = null,
    val scores: CardScores? = null,
    val intro: String? = null,
    val photo: Boolean = false,
    val deco: JsonObject? = null,
)

// ---- 今天 / 连向我 里的星卡 ----

@Serializable
data class Qa(val q: String? = null, val a: String? = null)

@Serializable
data class StarCard(
    val n: Long,
    val name: String? = null,
    val line: String? = null,
    val group: String? = null,
    val groupName: String? = null,
    val photo: Boolean = false,
    val deco: JsonObject? = null,
    val intro: String? = null,
    val about: List<Qa> = emptyList(),
    val star: Int? = null,
    val verified: Boolean = false,
    val linked: Boolean = false,
    val mutual: Boolean = false,
    val vrc: String? = null,
    val vrcUser: String? = null,
    val friends: Int? = null,
    val at: Long? = null,
    /** 网页内部接口才有：推荐位、共同点、带队语。 */
    val pos: String? = null,
    val lead: String? = null,
    val common: List<String> = emptyList(),
    val sprout: Boolean = false,
    val scores: Map<String, Int>? = null,
    /** 已经打过的分（null 表示没打过）。 */
    val rating: JsonElement? = null,
    /** 网页给这两页塞的额外字段（比如「新」标记），这里不解析。 */
    val fresh: Boolean = false,
)

@Serializable
data class TodayRound(
    val at: Long? = null,
    val next: Long? = null,
    val used: Int? = null,
    val max: Int? = null,
    val remind: Boolean = false,
)

@Serializable
data class TodayResp(
    val day: String? = null,
    val cards: List<StarCard> = emptyList(),
    val round: TodayRound? = null,
)

@Serializable
data class CrossCount(
    val n: Int = 0,
    val fresh: Int = 0,
)

@Serializable
data class InboxResp(
    val cards: List<StarCard> = emptyList(),
    val counts: Map<String, JsonElement> = emptyMap(),
    val seen: Long? = null,
    val cross: CrossCount? = null,
)

@Serializable
data class LinkResp(
    val mutual: Boolean = false,
    val card: StarCard? = null,
)

// ---- 私聊 ----

@Serializable
data class Peer(
    val n: Long,
    val name: String? = null,
    val group: String? = null,
    val photo: Boolean = false,
    val mutual: Boolean = false,
    val vrc: String? = null,
)

@Serializable
data class Msg(
    val id: Long,
    val text: String? = null,
    val at: Long? = null,
    val kind: String? = null,
    val mine: Boolean = false,
    val check: String? = null,
    val sticker: String? = null,
)

@Serializable
data class Thread(
    val peer: Peer? = null,
    val last: Msg? = null,
    val unread: Int = 0,
)

@Serializable
data class ChatsResp(val threads: List<Thread> = emptyList())

@Serializable
data class ChatResp(
    val msgs: List<Msg> = emptyList(),
    val more: Boolean = false,
    val peer: Peer? = null,
    val max: Int? = null,
)

// ---- 星友圈 ----

@Serializable
data class Post(
    val id: Long,
    val kind: String? = null,
    val text: String? = null,
    val vis: String? = null,
    val at: Long? = null,
    val mine: Boolean = false,
    val check: String? = null,
    val lit: Int = 0,
    /** 我点亮过没有：点亮按钮按它决定这次是点亮还是取消。 */
    val litMe: Boolean = false,
    val cmts: Int = 0,
    /** 作者：网页里可能叫 who 或 peer，星卡是 card，三种都收。 */
    val who: Peer? = null,
    val peer: Peer? = null,
    val card: Peer? = null,
    /** 动态配图：接口给的是签名地址对象 `{id,url,w,h}`，也可能没有。 */
    val img: JsonElement? = null,
) {
    val author: Peer? get() = who ?: peer ?: card

    /** 签名地址 10 分钟内有效，直接给 AsyncImage 用，不用带请求头。 */
    val imgUrl: String?
        get() = runCatching {
            img?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
        }.getOrNull()?.takeIf { it.startsWith("https://") }
}

@Serializable
data class PostsResp(
    val posts: List<Post> = emptyList(),
    val page: Int? = null,
)

/** 点亮 / 取消的回答：带着改完以后的那条动态（`lit`、`litMe`）。 */
@Serializable
data class LightResp(val post: Post? = null)

// ---- 我打过分的地图 ----

@Serializable
data class MyRating(
    val world: String,
    val name: String? = null,
    val score: Int = 0,
    val words: String? = null,
    val wordsOk: String? = null,
    val at: Long? = null,
    val page: String? = null,
)

@Serializable
data class MyMapsResp(val maps: List<MyRating> = emptyList())

/** 星群的配色，与网页 `{G,W,Z,L,A}` 对齐。 */
object Groups {
    fun label(g: String?): String = when (g) {
        "G" -> "漫游"
        "W" -> "游戏"
        "Z" -> "创作"
        "L" -> "聊天"
        "A" -> "万象"
        else -> "未知"
    }

    fun color(g: String?): Long = when (g) {
        "G" -> 0xFF4DB4FF
        "W" -> 0xFFFFB547
        "Z" -> 0xFFD69BFF
        "L" -> 0xFF5FE0B4
        "A" -> 0xFFF3F4F8
        else -> 0xFF9AA3B5
    }
}
