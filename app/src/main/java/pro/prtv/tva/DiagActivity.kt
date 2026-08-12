package pro.prtv.tva
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
/**
 * Экран состояния. Читается с телевизора, без adb и без компьютера.
 *
 * Причина существования в Э1: проверки на железе в этой фазе не будет,
 * значит первый же отказ должен объясняться сам.
 */
class DiagActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diag)
        val prefs = Prefs(this)
        val head = buildString {
            appendLine("PRTV TV A — ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("ID устройства: ${prefs.deviceId}")
            appendLine("Хост: ${prefs.host} (${Hosts.schemaName(prefs.host)})")
            appendLine("Последний тип: ${prefs.lastKind.name}")
            appendLine("Устройство: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            append("WebView: ${webViewVersion()}")
        }
        findViewById<TextView>(R.id.diagHead).text = head
        val log = findViewById<TextView>(R.id.diagLog)
        log.text = EventLog.snapshot().joinToString("\n").ifEmpty { "— событий нет —" }
        findViewById<Button>(R.id.btnClearLog).setOnClickListener {
            EventLog.clear()
            log.text = "— событий нет —"
        }
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
    }
    private fun webViewVersion(): String = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.webkit.WebView.getCurrentWebViewPackage()?.versionName ?: "неизвестно"
        } else "недоступно на API ${Build.VERSION.SDK_INT}"
    }.getOrDefault("ошибка чтения")
}
