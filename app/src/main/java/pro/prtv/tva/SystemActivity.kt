package pro.prtv.tva
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
/**
 * «Системные данные»: статус устройства, хранилища, журнал событий
 * и вход в проверку слайд-шоу.
 *
 * Доктор находится здесь, а не отдельной кнопкой на главном экране:
 * проверка нагружает устройство и искажает то, что измеряет, — она
 * не должна запускаться случайным нажатием.
 */
class SystemActivity : Activity() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(applyLanguage(base))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_system)
        val prefs = Prefs(this)
        findViewById<TextView>(R.id.systemStatus).text = buildString {
            appendLine("PRTV TV A ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("ID устройства: ${prefs.deviceId}")
            appendLine("Хост: ${prefs.host} (${Hosts.schemaName(prefs.host)})")
            appendLine("Последний тип: ${prefs.lastKind.name}")
            appendLine("Устройство: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("WebView: ${webViewVersion()}")
            append(storageLine())
        }
        val log = findViewById<TextView>(R.id.eventLog)
        val render = {
            log.text = EventLog.snapshot().joinToString("\n")
                .ifEmpty { getString(R.string.no_events) }
        }
        render()
        findViewById<Button>(R.id.btnClearLog).setOnClickListener {
            EventLog.clear()
            render()
        }
        findViewById<Button>(R.id.btnRunDoctor).setOnClickListener {
            Toast.makeText(this, R.string.not_implemented_yet, Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
    }
    /** Свободное место важно заранее: кэш упрётся именно в него. */
    private fun storageLine(): String = runCatching {
        val stat = StatFs(Environment.getDataDirectory().path)
        val freeMb = stat.availableBytes / (1024 * 1024)
        val totalMb = stat.totalBytes / (1024 * 1024)
        "Хранилище: свободно $freeMb МБ из $totalMb"
    }.getOrDefault("Хранилище: не определено")
    private fun webViewVersion(): String = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.webkit.WebView.getCurrentWebViewPackage()?.versionName ?: "неизвестно"
        } else "недоступно на API ${Build.VERSION.SDK_INT}"
    }.getOrDefault("ошибка чтения")
    private fun applyLanguage(base: Context): Context {
        val code = base.getSharedPreferences("prtv_tv_a", Context.MODE_PRIVATE)
            .getString("lang", "") ?: ""
        if (code.isEmpty()) return base
        val locale = Locale(code)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
