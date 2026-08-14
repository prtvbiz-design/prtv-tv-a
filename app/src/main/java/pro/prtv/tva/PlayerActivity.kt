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
 * Клавиши показа маршрутизируются по семейству хоста (A-PL-01).
 * Это не косметика: одна и та же кнопка пульта доходит до страницы
 * разными путями. На pro страница слушает сами события клавиш и листает
 * сама. На su она их не слушает — ждёт сообщение от оболочки, и без него
 * стрелки не делают ничего. В боевом приложении это разведено тремя
 * режимами resolveRouteMode(); здесь воспроизводится минимально
 * необходимая часть.
 *
 * Watchdog — следующий заход. Здесь только замер готовности страницы.
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
    private var legacyRoute = false
    private var bridgeReady = false
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
        legacyRoute = Hosts.isLegacySchema(host)
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
                if (legacyRoute) installBridge()
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
        EventLog.add(
            "player",
            "open $host ${Hosts.schemaName(host)} ${kind.name} route=" +
                (if (legacyRoute) "legacy" else "direct") + " -> " + url,
        )
        startedAt = SystemClock.elapsedRealtime()
        web.loadUrl(url)
    }
    /** MENU на пульте открывает диагностику, не выходя из показа. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_INFO) {
            startActivity(Intent(this, SystemActivity::class.java))
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * Мост для su-семейства.
     *
     * Страница на prtv.su не реагирует на события клавиш — она ждёт
     * сообщение от оболочки. Сообщение шлём тремя способами сразу:
     * window.postMessage, MessageEvent на window и на document. Какой
     * из них слушает конкретная версия плеера, снаружи не видно, а
     * лишние доставки безвредны.
     *
     * Только ES5: на приставках встречается старый WebView, и стрелочные
     * функции с let его роняют.
     */
    private fun installBridge() {
        if (bridgeReady) return
        bridgeReady = true
        val js = """
            (function () {
              if (window.__prtvSend) { return; }
              window.__prtvSend = function (msg) {
                var payload = String(msg);
                try { window.postMessage(payload, '*'); } catch (e) {}
                try {
                  var ev = new MessageEvent('message', { data: payload });
                  window.dispatchEvent(ev);
                  document.dispatchEvent(ev);
                } catch (e) {
                  try {
                    var ev2 = document.createEvent('Event');
                    ev2.initEvent('message', true, true);
                    ev2.data = payload;
                    window.dispatchEvent(ev2);
                    document.dispatchEvent(ev2);
                  } catch (e2) {}
                }
              };
              true;
            })();
        """.trimIndent()
        web.evaluateJavascript(js, null)
        EventLog.add("player", "мост legacy установлен")
    }

    private fun sendLegacy(message: String) {
        if (!bridgeReady) return
        web.evaluateJavascript("window.__prtvSend && window.__prtvSend('" + message + "');", null)
        EventLog.add("player", "postMessage " + message)
    }

    /**
     * Событие клавиши НЕ поглощается: оно уходит дальше в WebView.
     * На pro этого достаточно — страница листает сама. На su вдобавок
     * отправляется сообщение, потому что событий она не слышит.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                // A-PL-02: сначала история страницы, потом выход
                if (web.canGoBack()) {
                    EventLog.add("player", "back -> история страницы")
                    web.goBack()
                    return true
                }
            } else if (legacyRoute) {
                legacyMessageFor(event.keyCode)?.let { sendLegacy(it) }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * Соответствие взято из боевого приложения: вправо и влево листают,
     * вверх и вниз прокручивают, цифры выбирают позицию в подборке.
     */
    private fun legacyMessageFor(keyCode: Int): String? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_RIGHT -> "next_slide"
        KeyEvent.KEYCODE_DPAD_LEFT -> "prev_slide"
        KeyEvent.KEYCODE_DPAD_UP -> "scroll_fastForward"
        KeyEvent.KEYCODE_DPAD_DOWN -> "scroll_rewind"
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 ->
            "button_" + (keyCode - KeyEvent.KEYCODE_0)
        else -> null
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
