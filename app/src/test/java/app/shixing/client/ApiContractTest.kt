package app.shixing.client

import app.shixing.client.data.ApiException
import app.shixing.client.data.SignedImages
import app.shixing.client.data.Throttle
import app.shixing.client.data.VrccClient
import app.shixing.client.ui.describe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 钉住「合规契约」里最容易写错的几件事：
 * 1. 每个请求都带 X-Client；2. 路径一定是 /api/v1/ 下的相对路径；
 * 3. 错误体里的 error/need/Retry-After 能被解析出来，`429` 按 Retry-After 等；
 * 4. `404 gone` 是令牌失效，别的 `*_gone` 不是；
 * 5. 官方示例回答（`v1-examples.json`，原样取自开放接口文档 1.14.0 的 examples/responses.json，
 *    只留拾星用到的 19 个）原样能解析，把弃用字段删掉以后也能解析。
 */
class ApiContractTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        Throttle.clearPauses()
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client(token: String? = null, onGone: suspend () -> Unit = {}) =
        VrccClient(base = server.url("/api/v1/").toString(), onGone = onGone) { token }

    private fun failOf(block: suspend () -> Unit): ApiException = runBlocking {
        val e = runCatching { block() }.exceptionOrNull()
        assertTrue("应该抛 ApiException：$e", e is ApiException)
        e as ApiException
    }

    @Test
    fun boards_sendsClientHeader_andRelativePath() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"board":"maps","min":1,"at":1,
                   "maps":[{"world":"wrld_1","name":"M","avg":7.0,"raters":5,"rank":1}]}""",
            ),
        )
        val r = client().boards(3)
        val req = server.takeRequest()
        assertEquals(BuildConfig.CLIENT_ID, req.getHeader("X-Client"))
        assertEquals("/api/v1/boards/maps?limit=3", req.path)
        assertEquals(1, r.maps.size)
        assertEquals("wrld_1", r.maps[0].world)
    }

    @Test
    fun notifications_firstCallOmitsSince() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"last":1042,"items":[],"every":60,"dot":2}"""))
        val r = client("tok").notifications(null)
        val req = server.takeRequest()
        assertEquals("/api/v1/me/notifications", req.path)
        assertEquals("Bearer tok", req.getHeader("Authorization"))
        assertEquals(1042L, r.last)
        assertEquals(2, r.dot)
    }

    @Test
    fun notifications_laterCallCarriesSince() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"last":1050,"items":[],"every":60}"""))
        client("tok").notifications(1042)
        assertEquals("/api/v1/me/notifications?since=1042", server.takeRequest().path)
    }

    @Test
    fun rate_postsScoreAndText_andParsesResult() = runBlocking {
        // 2026-12-26 以后的回答：没有 can / need。
        server.enqueue(
            MockResponse().setBody(
                """{"world":"wrld_1","board":true,"avg":8.6,"raters":12,"mine":9,
                   "words":"好地方","wordsOk":"ok"}""",
            ),
        )
        val m = client("tok").rate("wrld_1", 9, "好地方")
        val req = server.takeRequest()
        assertEquals("/api/v1/me/maps/wrld_1/rating", req.path)
        val body = req.body.readUtf8()
        assertTrue(body.contains("\"score\":9"))
        assertTrue(body.contains("好地方"))
        assertEquals(9, m.mine)
        assertEquals("好地方", m.words)
    }

    @Test
    fun scopeError_exposesNeed() {
        // 错误里的 need 是缺的权限，和已经弃用的 me/maps/{world} 的 need 不是一回事。
        server.enqueue(
            MockResponse().setResponseCode(403).setBody("""{"error":"scope","need":"maps.rate"}"""),
        )
        val e = failOf { client("tok").rate("wrld_1", 9, null) }
        assertEquals(403, e.status)
        assertEquals("scope", e.error)
        assertEquals("maps.rate", e.need)
    }

    @Test
    fun textOver30Chars_isTruncated() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"world":"wrld_1"}"""))
        client("tok").rate("wrld_1", 5, "一二三四五六七八九十一二三四五六七八九十一二三四五六七八九十一二三四五")
        val body = server.takeRequest().body.readUtf8()
        assertTrue("短评必须被截到 30 字以内：$body", body.length < 200)
    }

    // ---- 429：按 Retry-After 等，不是一律一分钟 ----

    @Test
    fun tooMany_waitsForRetryAfter_onThatEndpointOnly() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(429).setHeader("Retry-After", "3600")
                .setBody("""{"error":"too_many"}"""),
        )
        val c = client("tok")
        val first = failOf { c.mutuals() }
        assertEquals(429, first.status)
        assertEquals("too_many", first.error)
        assertEquals(3600L, first.retryAfter)

        // 到点之前同一个接口不再发请求，直接回同一个错误和剩下的秒数。
        val again = failOf { c.mutuals() }
        assertEquals("too_many", again.error)
        assertTrue("还剩 ${again.retryAfter} 秒", again.retryAfter!! in 3590L..3600L)
        assertEquals(1, server.requestCount)

        // 别的接口照常问。
        server.enqueue(MockResponse().setBody("""{"stars":[],"n":0}"""))
        c.crossings()
        assertEquals(2, server.requestCount)
    }

    @Test
    fun tooSoon_withoutRetryAfter_isNotPaused() = runBlocking {
        // 旧版服务器的 too_soon 不带 Retry-After：不暂停，交给调用方。
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":"too_soon","every":60}"""))
        server.enqueue(MockResponse().setBody("""{"last":1,"items":[],"every":60}"""))
        val c = client("tok")
        assertNull(failOf { c.notifications(1) }.retryAfter)
        c.notifications(1)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun linkQuota_doesNotPauseTheEndpoint() = runBlocking {
        // quota 只说这一轮连满了：别的连法（连回连向我的星）可能还行，不把整个接口停掉。
        server.enqueue(
            MockResponse().setResponseCode(429).setHeader("Retry-After", "600").setBody("""{"error":"quota"}"""),
        )
        server.enqueue(MockResponse().setBody("""{"mutual":true}"""))
        val c = client("tok")
        assertEquals(600L, failOf { c.link(2) }.retryAfter)
        assertTrue(c.link(3).mutual)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun rateLimitRemainingZero_waitsForReset() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"stars":[],"n":0}""")
                .setHeader("RateLimit-Limit", "1000")
                .setHeader("RateLimit-Remaining", "0")
                .setHeader("RateLimit-Reset", "120"),
        )
        val c = client("tok")
        c.mutuals()
        val e = failOf { c.mutuals() }
        assertEquals(429, e.status)
        assertEquals("too_many", e.error)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun rateLimitRemaining_aboveZero_keepsGoing() = runBlocking {
        repeat(2) {
            server.enqueue(
                MockResponse().setBody("""{"stars":[],"n":0}""")
                    .setHeader("RateLimit-Remaining", "5")
                    .setHeader("RateLimit-Reset", "120"),
            )
        }
        val c = client("tok")
        c.mutuals()
        c.mutuals()
        assertEquals(2, server.requestCount)
    }

    // ---- 404：gone 只表示令牌失效 ----

    @Test
    fun gone_meansTokenLost_starGoneDoesNot() {
        var lost = 0
        val c = client("tok", onGone = { lost++ })

        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"star_gone"}"""))
        assertEquals("star_gone", failOf { c.link(2) }.error)
        assertEquals(0, lost)

        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"gone"}"""))
        assertEquals("gone", failOf { c.inbox() }.error)
        assertEquals(1, lost)
    }

    @Test
    fun noToken_isTreatedAsLost_withoutRequest() {
        var lost = 0
        val e = failOf { client(null, onGone = { lost++ }).card() }
        assertEquals("token_missing", e.error)
        assertEquals(1, lost)
        assertEquals(0, server.requestCount)
    }

    // ---- 其他错误 ----

    @Test
    fun stop_carriesSayAndUntil() {
        server.enqueue(
            MockResponse().setResponseCode(503)
                .setBody("""{"error":"stop","say":"今晚维护","until":1792830000000}"""),
        )
        val e = failOf { client("tok").today() }
        assertEquals(503, e.status)
        assertEquals("stop", e.error)
        assertEquals("今晚维护", e.say)
        assertEquals(1792830000000L, e.until)
        assertTrue(describe(e).startsWith("今晚维护"))
    }

    @Test
    fun methodNotAllowed_is405Method() {
        server.enqueue(
            MockResponse().setResponseCode(405).setHeader("Allow", "GET").setBody("""{"error":"method"}"""),
        )
        val e = failOf { client("tok").card() }
        assertEquals(405, e.status)
        assertEquals("method", e.error)
        assertTrue(describe(e).contains("405"))
    }

    @Test
    fun describe_usesRetryAfter_andFallsBackOnStatus() {
        assertTrue(describe(ApiException(429, "too_many", retryAfter = 30)).contains("30 秒后"))
        assertTrue(describe(ApiException(429, "too_many", retryAfter = 5L * 3600)).contains("5 小时后恢复"))
        assertTrue(describe(ApiException(429, "too_soon", retryAfter = 12)).contains("12 秒后"))
        assertTrue(describe(ApiException(429, "a_new_code", retryAfter = 90)).contains("2 分钟后"))
        assertTrue(describe(ApiException(503, "a_new_code")).contains("服务器"))
    }

    // ---- 请求写法 ----

    @Test
    fun deviceCode_sendsScopeInJsonBody_once() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"invalid_scope"}"""))
        assertEquals("invalid_scope", failOf { client().deviceCode("profile nope") }.error)
        assertEquals(1, server.requestCount)
        val req = server.takeRequest()
        assertEquals("/api/v1/device/code", req.path)
        assertTrue(req.body.readUtf8().contains("\"scope\":\"profile nope\""))
    }

    @Test
    fun lightOff_sendsLiteralFalse() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"post":{"id":1}}"""))
        client("tok").light(1, on = false)
        val req = server.takeRequest()
        assertEquals("/api/v1/me/posts/1/light", req.path)
        assertEquals("""{"on":false}""", req.body.readUtf8())
    }

    @Test
    fun light_readsLitAndLitMeBack() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"post":{"id":1,"lit":3,"litMe":true,"cmts":0}}"""))
        val r = client("tok").light(1, on = true)
        assertEquals(3, r.post?.lit)
        assertEquals(true, r.post?.litMe)
    }

    @Test
    fun writePost_sendsRequiredKindText() = runBlocking {
        // openapi：POST me/posts 的正文 required: [kind, text]，kind 只能是 "text"。
        server.enqueue(MockResponse().setBody("""{"ok":true,"post":{"id":9}}"""))
        client("tok").writePost("你好")
        val req = server.takeRequest()
        assertEquals("/api/v1/me/posts", req.path)
        assertEquals("""{"kind":"text","text":"你好"}""", req.body.readUtf8())
    }

    @Test
    fun defaultScope_asksOnlyForWhatTheAppUses() {
        val scopes = app.shixing.client.data.Repository.DEFAULT_SCOPE.split(' ').toSet()
        // 点亮、发动态要 posts.write；没做的功能不申请（使用要求第 10 条）。
        assertTrue("posts.write" in scopes)
        assertFalse("vrchat" in scopes)
        assertFalse("safety" in scopes)
        assertFalse("maps.write" in scopes)
    }

    @Test
    fun mark_onlyWhenAsked() = runBlocking {
        repeat(4) { server.enqueue(MockResponse().setBody("{}")) }
        val c = client("tok")
        c.inbox()
        c.inbox(mark = true)
        c.recent()
        c.recent(mark = true)
        assertEquals("/api/v1/me/inbox", server.takeRequest().path)
        assertEquals("/api/v1/me/inbox?mark=1", server.takeRequest().path)
        assertEquals("/api/v1/me/notifications/recent", server.takeRequest().path)
        assertEquals("/api/v1/me/notifications/recent?mark=1", server.takeRequest().path)
    }

    @Test
    fun signedImages_areImgAndPhotoOnly() {
        assertTrue(SignedImages.isSigned("https://vrcc.cc/api/v1/img/1?client=x&g=1&exp=1&sig=ab"))
        assertTrue(SignedImages.isSigned("https://vrcc.cc/api/v1/photo/2?client=x&g=1&exp=1&sig=ab"))
        assertFalse(SignedImages.isSigned("https://vrcc.cc/api/v1/stickers/cat?fin=holo"))
        assertFalse(SignedImages.isSigned("https://vrcc.cc/maps/wrld_1.jpg"))
    }

    // ---- 官方示例回答：原样、删掉弃用字段 ----

    private val examples: JsonObject by lazy {
        val text = requireNotNull(javaClass.getResource("/v1-examples.json")) { "缺 v1-examples.json" }.readText()
        Json.parseToJsonElement(text).jsonObject
    }

    /** 弃用中的字段：2026-11-26 / 2026-12-26 起不再返回。 */
    private val deprecated = setOf("can", "need", "vibe", "teen", "adj", "regenToday", "styles")

    private fun strip(e: JsonElement): JsonElement = when (e) {
        is JsonObject -> JsonObject(e.filterKeys { it !in deprecated }.mapValues { strip(it.value) })
        is JsonArray -> JsonArray(e.map { strip(it) })
        else -> e
    }

    private fun example(key: String, stripped: Boolean): String {
        val body = examples.getValue(key)
        return (if (stripped) strip(body) else body).toString()
    }

    private fun call(key: String, f: suspend (VrccClient) -> Any) = key to f

    private val calls = listOf(
        call("GET boards/maps") { it.boards(20) },
        call("GET boards/maps?sort=week") { it.boards(20) },
        call("GET boards/maps?sort=new") { it.boards(20) },
        call("POST device/code") { it.deviceCode("profile") },
        call("POST device/token") { it.deviceToken("dev") },
        call("GET me/notifications") { it.notifications(null) },
        call("GET me/notifications/recent") { it.recent() },
        call("GET me/crossings") { it.crossings() },
        call("GET me/mutuals") { it.mutuals() },
        call("GET me/card") { it.card() },
        call("GET me/maps") { it.myMaps() },
        call("GET me/maps/{world}") { it.myMap("wrld_1") },
        call("POST me/maps/{world}/rating") { it.rate("wrld_1", 9, null) },
        call("GET me/today") { it.today() },
        call("GET me/inbox") { it.inbox() },
        call("POST me/links") { it.link(2) },
        call("GET me/chats") { it.chats() },
        call("GET me/chats/{n}") { it.chat(2) },
        call("GET me/posts") { it.posts() },
    )

    @Test
    fun examples_parse_withAndWithoutDeprecatedFields() = runBlocking {
        assertEquals("示例和这张表要对上", examples.keys, calls.map { it.first }.toSet())
        val c = client("tok")
        for ((key, f) in calls) {
            for (stripped in listOf(false, true)) {
                server.enqueue(MockResponse().setBody(example(key, stripped)))
                val r = runCatching { f(c) }
                assertTrue("$key（删掉弃用字段：$stripped）解析失败：${r.exceptionOrNull()}", r.isSuccess)
            }
        }
    }

    @Test
    fun examples_stillCarryTheDeprecatedFields() {
        // 确认上面那条真的测到了「缺了也不坏」：示例里本来有这些字段。
        assertTrue(example("GET me/maps/{world}", false).contains("\"can\""))
        assertFalse(example("GET me/maps/{world}", true).contains("\"can\""))
        assertTrue(example("GET me/card", false).contains("\"vibe\""))
        assertTrue(example("GET me/inbox", false).contains("\"regenToday\""))
        assertFalse(example("GET me/inbox", true).contains("\"styles\""))
    }

    @Test
    fun examples_keyFieldsSurvive() = runBlocking {
        val c = client("tok")
        server.enqueue(MockResponse().setBody(example("GET me/maps/{world}", true)))
        val m = c.myMap("wrld_1")
        assertEquals(9, m.mine)
        assertEquals("光影很舒服", m.words)
        assertTrue(m.visible)

        server.enqueue(MockResponse().setBody(example("GET me/inbox", true)))
        val i = c.inbox()
        assertEquals(2, i.cards.size)
        assertEquals(1, i.cross?.fresh)
        assertTrue(i.counts.containsKey("in"))

        server.enqueue(MockResponse().setBody(example("GET me/card", true)))
        assertEquals("彩蛋猎人", c.card().name)
    }
}
