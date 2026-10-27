package app.shixing.client.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import app.shixing.client.BuildConfig
import com.caverock.androidsvg.SVG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 贴纸图片：直接拿字节自己解码。
 *
 * 走自己的 OkHttp 而不是 Coil，原因有两条：
 * 1. `/api/v1` 要求每个请求带 `X-Client`，Coil 默认不带；
 * 2. 同一个接口会返回两种东西——**图片贴纸是 webp，其余是 SVG**（文档原话），
 *    所以这里按首字节分流：`<` 开头的当 SVG 渲染，其余交给 BitmapFactory。
 */
object StickerImages {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val cache = ConcurrentHashMap<String, Bitmap>()

    @Volatile
    var lastError: String? = null

    /** 贴纸素材不用令牌（文档：不用 client，也不用令牌），所以这里只带 X-Client，不带 Authorization。 */
    suspend fun bitmap(url: String): Bitmap? {
        cache[url]?.let { return it }
        val bmp = withContext(Dispatchers.IO) {
            runCatching {
                val b = Request.Builder()
                    .url(url)
                    .header("X-Client", BuildConfig.CLIENT_ID)
                client.newCall(b.build()).execute().use { r ->
                    if (!r.isSuccessful) {
                        lastError = "HTTP " + r.code
                        return@use null
                    }
                    val bytes = r.body?.bytes() ?: run {
                        lastError = "no body"
                        return@use null
                    }
                    decode(bytes)
                }
            }.onFailure { lastError = it.javaClass.simpleName + ": " + it.message }.getOrNull()
        }
        if (bmp != null) cache[url] = bmp
        return bmp
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        // 先试位图（webp / png）
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { return it }
        // 再看是不是 SVG
        val head = String(bytes, 0, minOf(bytes.size, 512)).trimStart()
        if (!head.startsWith("<")) {
            lastError = "decode fail " + bytes.size + " head=" + String(bytes, 0, 4)
            return null
        }
        val svg = String(bytes, Charsets.UTF_8)
        // AndroidSVG 解不了 <mask> 里的内嵌位图，会把整张图弄没，所以先剔掉
        val cleaned = svg
            .replace(Regex("<mask[\\s\\S]*?</mask>"), "")
            .replace(Regex("\\smask=\"[^\"]*\""), "")
            .replace(Regex("<image[\\s\\S]*?/>"), "")
            .replace(Regex("<image[\\s\\S]*?</image>"), "")
        return runCatching {
            val doc = SVG.getFromString(cleaned)
            val w = doc.documentWidth
            val h = doc.documentHeight
            val target = 192
            doc.setDocumentWidth(target.toFloat())
            doc.setDocumentHeight(if (h > 0) target * h / w else target.toFloat())
            val bmp = Bitmap.createBitmap(target, (if (h > 0) target * h / w else target.toFloat()).toInt(), Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            doc.renderToPicture()?.let { c.drawPicture(it) }
            bmp
        }.onFailure { lastError = "svg fail: " + it.message }.getOrNull()
    }
}
