package pro.prtv.tva
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
/**
 * Главный экран. Три сущности показа, панель настроек, вход в системные
 * данные. Подписи и раскладка повторяют макет приложения.
 *
 * Управление рассчитано на пульт: цветные кнопки запускают показ напрямую,
 * без перехода к полю ввода.
 */
class StartActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var slideshow: EditText
    private lateinit var stream: EditText
    private lateinit var set: EditText
    /** Гард от повторного запуска: 2500 мс. */
    private var lastLaunchAt = 0L
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(applyLanguage(base))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)
        prefs = Prefs(this)
        EventLog.add("start", "device=${prefs.deviceId} build=${BuildConfig.VERSION_NAME}")
        slideshow = findViewById(R.id.inputSlideshow)
        stream = findViewById(R.id.inputStream)
        set = findViewById(R.id.inputSet)
        slideshow.setText(prefs.code(Hosts.Kind.SLIDESHOW))
        stream.setText(prefs.code(Hosts.Kind.STREAM))
        set.setText(prefs.code(Hosts.Kind.SET))
        findViewById<TextView>(R.id.deviceLine).text =
            getString(R.string.device_line, prefs.deviceId, BuildConfig.VERSION_NAME)
        findViewById<TextView>(R.id.versionLine).text = getString(
            R.string.version_line,
            android.os.Build.VERSION.RELEASE,
            BuildConfig.VERSION_NAME,
        )
        findViewById<TextView>(R.id.instructions).text =
            getString(R.string.instructions_fmt, prefs.host)
        wireHosts()
        wireLanguage()
        wireCacheAndSleep()
        wirePresets()
        wireLaunchControls()
        findViewById<Button>(R.id.btnSystemData).setOnClickListener {
            startActivity(Intent(this, SystemActivity::class.java))
        }
        findViewById<Button>(R.id.btnUsbSlideshow).setOnClickListener {
            Toast.makeText(this, R.string.not_implemented_yet, Toast.LENGTH_SHORT).show()
        }
        slideshow.requestFocus()
    }
    /**
     * Цветные кнопки пульта переводят фокус на соответствующее поле —
     * как в приложении PRTV. Показ они не запускают.
     *
     * Каждое нажатие пишется в журнал: пульты разных производителей шлют
     * разные коды, и увидеть их можно только на живом устройстве —
     * экран «Системные данные» показывает, что именно пришло.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        EventLog.add("key", "code=$keyCode ${KeyEvent.keyCodeToString(keyCode)}")
        RemoteKeys.kindFor(keyCode)?.let { kind ->
            fieldFor(kind).requestFocus()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
    /**
     * Запуск показа. Повторяет поведение приложения PRTV: номер набирается
     * в поле, а запускает его действие «Готово» экранной клавиатуры.
     *
     * OK на поле намеренно НЕ перехватывается — этим нажатием система
     * открывает клавиатуру, без него набрать номер нечем.
     */
    private fun wireLaunchControls() {
        for ((kind, field) in fields()) {
            field.setOnEditorActionListener { _, actionId, event ->
                val done = actionId == EditorInfo.IME_ACTION_DONE ||
                    actionId == EditorInfo.IME_ACTION_GO ||
                    actionId == EditorInfo.IME_NULL
                val enterUp = event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                    event.action == KeyEvent.ACTION_UP
                if (done || enterUp) {
                    launch(kind, field)
                    true
                } else {
                    false
                }
            }
        }
    }
    private fun fields(): List<Pair<Hosts.Kind, EditText>> = listOf(
        Hosts.Kind.SLIDESHOW to slideshow,
        Hosts.Kind.STREAM to stream,
        Hosts.Kind.SET to set,
    )
    private fun wireHosts() {
        val apply = { host: String ->
            prefs.host = host
            findViewById<TextView>(R.id.instructions).text =
                getString(R.string.instructions_fmt, host)
            Toast.makeText(this, "${host} · ${Hosts.schemaName(host)}", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnHostSu).setOnClickListener { apply(Hosts.SU) }
        findViewById<Button>(R.id.btnHostPro).setOnClickListener { apply(Hosts.PRO) }
        findViewById<Button>(R.id.btnHostTest).setOnClickListener { apply(Hosts.NEW_TEST) }
    }
    private fun wireLanguage() {
        val switch = { code: String ->
            if (prefs.language != code) {
                prefs.language = code
                recreate()
            }
        }
        findViewById<Button>(R.id.btnLangRu).setOnClickListener { switch("ru") }
        findViewById<Button>(R.id.btnLangEn).setOnClickListener { switch("en") }
    }
    /**
     * Настройки кэша и сна сохраняются, но пока ни на что не влияют:
     * сами механизмы появятся на следующих этапах. Кнопки честно
     * сообщают об этом, чтобы интерфейс не обещал несуществующего.
     */
    private fun wireCacheAndSleep() {
        val cacheToggle = findViewById<Button>(R.id.btnCacheToggle)
        val threshold = findViewById<EditText>(R.id.inputCacheThreshold)
        val sleepToggle = findViewById<Button>(R.id.btnSleepToggle)
        val wakeAt = findViewById<EditText>(R.id.inputWakeAt)
        val sleepAt = findViewById<EditText>(R.id.inputSleepAt)
        if (prefs.cacheThresholdKb > 0) threshold.setText(prefs.cacheThresholdKb.toString())
        wakeAt.setText(prefs.wakeAt)
        sleepAt.setText(prefs.sleepAt)
        val renderCache = {
            cacheToggle.setText(
                if (prefs.cacheEnabled) R.string.cache_enabled else R.string.cache_enable
            )
        }
        val renderSleep = {
            sleepToggle.setText(if (prefs.sleepEnabled) R.string.sleep_on else R.string.sleep_off)
        }
        renderCache()
        renderSleep()
        cacheToggle.setOnClickListener {
            prefs.cacheEnabled = !prefs.cacheEnabled
            renderCache()
            Toast.makeText(this, R.string.not_implemented_yet, Toast.LENGTH_SHORT).show()
        }
        sleepToggle.setOnClickListener {
            prefs.sleepEnabled = !prefs.sleepEnabled
            renderSleep()
            Toast.makeText(this, R.string.not_implemented_yet, Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnCacheClear).setOnClickListener {
            Toast.makeText(this, R.string.not_implemented_yet, Toast.LENGTH_SHORT).show()
        }
    }
    /** Пресеты из B3: пары «то же СШ, разный хост». Технический элемент. */
    private fun wirePresets() {
        val apply = { su: String, pro: String ->
            slideshow.setText(if (Hosts.isLegacySchema(prefs.host)) su else pro)
            slideshow.requestFocus()
        }
        findViewById<Button>(R.id.presetCalendar).setOnClickListener { apply("11211", "0012276") }
        findViewById<Button>(R.id.presetPizza).setOnClickListener { apply("11472", "0012277") }
        findViewById<Button>(R.id.presetOlympic).setOnClickListener { apply("32024", "0012278") }
    }
    override fun onPause() {
        super.onPause()
        prefs.setCode(Hosts.Kind.SLIDESHOW, slideshow.text.toString())
        prefs.setCode(Hosts.Kind.STREAM, stream.text.toString())
        prefs.setCode(Hosts.Kind.SET, set.text.toString())
        findViewById<EditText>(R.id.inputCacheThreshold).text.toString().trim()
            .toIntOrNull()?.let { prefs.cacheThresholdKb = it }
        prefs.wakeAt = findViewById<EditText>(R.id.inputWakeAt).text.toString()
        prefs.sleepAt = findViewById<EditText>(R.id.inputSleepAt).text.toString()
    }
    private fun applyLanguage(base: Context): Context {
        val code = base.getSharedPreferences("prtv_tv_a", Context.MODE_PRIVATE)
            .getString("lang", "") ?: ""
        if (code.isEmpty()) return base
        val locale = Locale(code)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
