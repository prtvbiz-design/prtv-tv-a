package pro.prtv.tva
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
/**
 * Показ. Системный WebView, без моста и без бандла JS.
 *
 * Доступ к файлам выключен целиком: allowFileAccess,
 * allowFileAccessFromFileURLs, allowUniversalAccessFromFileURLs.
 * Офлайн-пакет из Э4 будет включать их точечно и только для собственного
 * каталога — страница со сменного носителя не должна получать доступ
 * к приватным файлам приложения.
 *
 * Watchdog — Э2. Здесь только замер времени до готовности страницы.
 */
class PlayerActivity : Activity() {
    companion object {
        const val EXTRA_HOST = "host"
        const val EXTRA_KIND = "kind"
        const val EXTRA_CODE = "code"
    }
    private lateinit var web: WebView
    private var startedAt = 0L
    private var firstPaintReported = false
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        goImmersive()
        val host = intent.getStringExtra(EXTRA_HOST) ?: Hosts.PRO
        val kind = runCatching { Hosts.Kind.valueOf(intent.getStringExtra(EXTRA_KIND) ?: "") }
            .getOrDefault(Hosts.Kind.SLIDESHOW)
        val code = intent.getStringExtra(EXTRA_CODE).orEmpty()
        val url = Hosts.playbackUrl(host, kind, code)
        web = WebView(this)
        web.setBackgroundColor(Color.BLACK)
        setContentView(web)
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        }
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                if (!firstPaintReported) {
                    firstPaintReported = true
                    val ms = SystemClock.elapsedRealtime() - startedAt
                    EventLog.add("player", "page finished in ${ms}ms")
                }
            }
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                if (request?.isForMainFrame == true) {
                    EventLog.add("player", "main frame error: ${error?.description}")
                }
            }
        }
        EventLog.add("player", "open $host ${Hosts.schemaName(host)} ${kind.name} -> $url")
        startedAt = SystemClock.elapsedRealtime()
        web.loadUrl(url)
    }
    /** MENU на пульте открывает диагностику, не выходя из показа. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_INFO) {
            startActivity(Intent(this, DiagActivity::class.java))
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goImmersive()
    }
    @Suppress("DEPRECATION")
    private fun goImmersive() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }
    override fun onDestroy() {
        EventLog.add("player", "closed")
        web.loadUrl("about:blank")
        web.destroy()
        super.onDestroy()
    }
}
