package app.shixing.client

import android.app.Application
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import app.shixing.client.data.Repository
import app.shixing.client.data.SignedImages
import app.shixing.client.data.TokenStore
import coil.EventListener
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.network.HttpException
import coil.request.ErrorResult
import coil.request.ImageRequest

class ShixingApplication : Application(), ImageLoaderFactory {

    companion object {
        /** 全局 Application 上下文：更新检查这种不需要 Activity 的地方用它。 */
        lateinit var ctx: Context
            private set

        /** 1.0.1 及以前「通过网页登录」存下的网页设备密钥。 */
        private const val OLD_WEB_KEY_PREFS = "shixing-legacy"
    }


    lateinit var repo: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        ctx = applicationContext
        repo = Repository(TokenStore(this))
        forgetOldWebKey()
    }

    /**
     * 旧版本走过网页自己的接口（`/api/match`），在本机存了网页的设备密钥，
     * WebView 里也还登着网页。现在只用 `/api/v1`，升级上来时把这两样都删掉。
     */
    private fun forgetOldWebKey() {
        val prefs = getSharedPreferences(OLD_WEB_KEY_PREFS, MODE_PRIVATE)
        if (prefs.all.isEmpty()) return
        prefs.edit().clear().commit()
        deleteSharedPreferences(OLD_WEB_KEY_PREFS)
        runCatching {
            WebStorage.getInstance().deleteAllData()
            CookieManager.getInstance().removeAllCookies(null)
        }
    }

    /** Coil 的默认加载器，多一个监听：签名图片过期（404）时让界面重新拿地址。 */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .eventListener(object : EventListener {
                override fun onError(request: ImageRequest, result: ErrorResult) {
                    val url = request.data as? String ?: return
                    SignedImages.failed(url, (result.throwable as? HttpException)?.response?.code)
                }
            })
            .build()
}
