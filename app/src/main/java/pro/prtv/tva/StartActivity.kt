package pro.prtv.tva

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
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
 * Управление рассчитано на пульт. Показ запускает действие «Готово»
 * экранной клавиатуры, а не отдельная кнопка: цветные кнопки только
 * переводят фокус на соответствующее поле.
 *
 * Обход фокуса — линейная цепочка (FocusChain), а не пространственная
 * навигация средствами системы. Клавиши проходят через одну лестницу
 * приоритетов (KeyRouter).
 */
class StartActivity : Activity(), KeyRouter.Screen {

    private lateinit var prefs: Prefs
    private lateinit var router: KeyRouter
    private var panels: PanelRotator? = null
    private lateinit var slideshow: EditText
    private lateinit var stream: EditText
    private lateinit var set: EditText

    /** Гард от повторного запуска: 2500 мс. */
    private var lastLaunchAt = 0L

    /**
     * Гард «назад»: 900 мс после появления экрана.
     *
     * Нужен из-за возврата с показа. Пульт успевает прислать второе
     * нажатие раньше, чем главный экран отрисуется, и приложение
     * закрывается сразу после выхода из слайд-шоу.
     */
    private var backAllowedAt = 0L

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(applyLanguage(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)
        prefs = Prefs(this)
        router = KeyRouter(this)
        EventLog.add("start", "device=${prefs.deviceId} build=${BuildConfig.VERSION_NAME}")
        // touchMode=true объясняет отказ requestFocus на кнопках без
        // focusableInTouchMode — одна строка вместо часа гаданий
        EventLog.add("start", "touchMode=" + window.decorView.isInTouchMode)

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
        renderHostHint()

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

        panels = PanelRotator(
            findViewById(R.id.panelBack),
            findViewById(R.id.panelFront),
        )

        applyChainState()
    }

    /**
     * Смена картинок идёт, только пока окно действительно на экране
     * и владеет фокусом. Экранная клавиатура на телевизоре открывается
     * поверх и забирает фокус окна — этого условия достаточно, чтобы
     * панель замерла на время набора номера, и не нужно угадывать
     * высоту клавиатуры.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) panels?.start() else panels?.stop()
    }

    override fun onPause() {
        panels?.stop()
        savePreferences()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        backAllowedAt = SystemClock.elapsedRealtime() + BACK_GUARD_MS
        applyChainState()
    }

    /* ──────────────────────── клавиши ──────────────────────── */

    /**
     * Перехват до передачи в иерархию представлений. Иначе стрелки
     * съедала бы система своей пространственной навигацией, и линейная
     * цепочка не работала бы вовсе.
     *
     * Цифры и OK через лестницу не проходят и попадают в поле ввода —
     * без этого номер нечем набрать.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!::router.isInitialized) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN && router.dispatch(event.keyCode)) return true
        // отпускание «назад» глушим отдельно: иначе нажатие съедено,
        // а системный возврат всё равно сработает по ACTION_UP
        if (event.keyCode == KeyEvent.KEYCODE_BACK && isBackGuarded()) return true
        return super.dispatchKeyEvent(event)
    }

    private fun isBackGuarded(): Boolean =
        SystemClock.elapsedRealtime() < backAllowedAt

    override fun onBackGuard(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_BACK && isBackGuarded()

    /**
     * Влево и вправо ведут по цепочке. Вверх и вниз намеренно не
     * перехватываются: в оригинале цепочка описана только для
     * горизонтали, а придумывать за него поведение вертикали — ровно
     * та ошибка, от которой предостерегает A-6.
     *
     * Элементы вне цепочки сделаны нефокусируемыми, поэтому системная
     * навигация вверх-вниз не может увести фокус туда, куда цепочка
     * его не пускает.
     */
    override fun onDirection(keyCode: Int): Boolean {
        val forward = keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
        val backward = keyCode == KeyEvent.KEYCODE_DPAD_LEFT
        if (!forward && !backward) return false

        val currentId = currentFocus?.id ?: View.NO_ID
        var targetId = if (forward) {
            FocusChain.next(currentId) { isChainEnabled(it) }
        } else {
            FocusChain.previous(currentId) { isChainEnabled(it) }
        }

        // Фокус оказался вне цепочки — например, на контейнере без id.
        // Раньше это молча ничего не делало: нажатие мы съедали, а фокус
        // не двигали, и стрелка выглядела мёртвой. Заводим его внутрь.
        if (targetId == View.NO_ID || !FocusChain.contains(targetId)) {
            targetId = FocusChain.firstEnabled { isChainEnabled(it) } ?: return true
        }

        if (targetId == currentId) {
            EventLog.add("focus", nameOf(currentId) + " край цепочки")
            return true
        }

        val target = findViewById<View>(targetId)
        if (target == null) {
            EventLog.add("focus", "цель " + nameOf(targetId) + " не найдена")
            return true
        }
        var ok = target.requestFocus()
        if (!ok) {
            // requestFocus отказывает, если окно в touch mode, а элемент
            // не помечен focusableInTouchMode. На части приставок так и есть.
            target.isFocusableInTouchMode = true
            ok = target.requestFocusFromTouch() || target.requestFocus()
        }
        EventLog.add(
            "focus",
            nameOf(currentId) + " -> " + nameOf(targetId) + (if (ok) " ok" else " ОТКАЗ"),
        )
        return true
    }

    /** Имя элемента вместо голого числа: журнал читают с экрана телевизора. */
    private fun nameOf(id: Int): String =
        if (id == View.NO_ID) "-"
        else runCatching { resources.getResourceEntryName(id) }.getOrDefault("id" + id)

    /**
     * Цветные кнопки переводят фокус на соответствующее поле — как в
     * приложении PRTV. Показ они не запускают.
     *
     * Каждое нажатие пишется в журнал: пульты разных производителей шлют
     * разные коды, и увидеть их можно только на живом устройстве.
     */
    override fun onColorKey(keyCode: Int): Boolean {
        val kind = RemoteKeys.kindFor(keyCode) ?: return false
        fieldFor(kind).requestFocus()
        return true
    }

    override fun logKey(keyCode: Int, verdict: String) {
        EventLog.add("key", "code=$keyCode ${KeyEvent.keyCodeToString(keyCode)} → $verdict")
    }

    /* ──────────────────── доступность элементов ──────────────────── */

    /**
     * Условия доступности из оригинала (isIdEnabled). Отключённый элемент
     * выпадает из цепочки и перестаёт быть фокусируемым.
     *
     * Два места, где мы пока отступаем от оригинала, и оба временные:
     *
     * playFlash — накопитель не поддерживается до Э4, поэтому элемент
     * всегда отключён. На устройстве без флешки боевое ведёт себя так же.
     *
     * timerToggle — «режим сна доступен» в оригинале означает поддержку
     * со стороны железа, а её мы узнаем только на Э6 вместе с CEC.
     * Пока считаем доступным всегда, иначе вся ветка сна выпадет из
     * цепочки и проверить её будет нечем.
     */
    private fun isChainEnabled(id: Int): Boolean = when (id) {
        R.id.btnUsbSlideshow -> USB_SUPPORTED
        R.id.inputCacheThreshold, R.id.btnCacheClear -> prefs.cacheEnabled
        R.id.btnSleepToggle -> SLEEP_AVAILABLE
        R.id.inputWakeAt, R.id.inputSleepAt -> SLEEP_AVAILABLE && prefs.sleepEnabled
        else -> true
    }

    private fun applyChainState() {
        for (id in FocusChain.ORDER) {
            val view = findViewById<View>(id) ?: continue
            val on = isChainEnabled(id)
            view.isEnabled = on
            view.isFocusable = on
            // Не только для полей: если окно окажется в touch mode, кнопка
            // без этого флага откажет в requestFocus и стрелка не сработает.
            view.isFocusableInTouchMode = on
        }
        val current = currentFocus
        if (current == null || !FocusChain.contains(current.id) || !isChainEnabled(current.id)) {
            FocusChain.firstEnabled { isChainEnabled(it) }?.let { id ->
                val ok = findViewById<View>(id)?.requestFocus() ?: false
                EventLog.add("focus", "начальный " + nameOf(id) + (if (ok) " ok" else " ОТКАЗ"))
            }
        }
    }

    /* ──────────────────────── запуск показа ──────────────────────── */

    /**
     * Номер набирается в поле, а запускает его действие «Готово»
     * экранной клавиатуры.
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

    private fun fieldFor(kind: Hosts.Kind): EditText = when (kind) {
        Hosts.Kind.SLIDESHOW -> slideshow
        Hosts.Kind.STREAM -> stream
        Hosts.Kind.SET -> set
    }

    private fun launch(kind: Hosts.Kind, field: EditText) {
        val code = field.text.toString().trim()
        if (code.isEmpty()) {
            Toast.makeText(this, R.string.enter_code, Toast.LENGTH_SHORT).show()
            field.requestFocus()
            return
        }
        // повторное нажатие в пределах гарда игнорируется молча
        if (SystemClock.elapsedRealtime() - lastLaunchAt < LAUNCH_GUARD_MS) return
        lastLaunchAt = SystemClock.elapsedRealtime()
        prefs.setCode(kind, code)
        prefs.lastKind = kind

        // Хост выводится из кода, как в боевом приложении
        // (resolveBaseUrlForCode). Ручной выбор перекрывает автоопределение.
        val host = Hosts.resolveForCode(code, prefs.host)
        EventLog.add(
            "host",
            "код " + code.length + " знаков · настройка " + Hosts.label(prefs.host) +
                " -> " + host + " " + Hosts.schemaName(host),
        )

        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_HOST, host)
                .putExtra(PlayerActivity.EXTRA_KIND, kind.name)
                .putExtra(PlayerActivity.EXTRA_CODE, code)
        )
    }

    /* ──────────────────────── панель настроек ──────────────────────── */

    private fun wireHosts() {
        val apply = { host: String ->
            prefs.host = host
            renderHostHint()
            val note = if (host == Hosts.AUTO) {
                getString(R.string.host_auto) + " · по длине кода"
            } else {
                host + " · " + Hosts.schemaName(host)
            }
            Toast.makeText(this, note, Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnHostAuto).setOnClickListener { apply(Hosts.AUTO) }
        findViewById<Button>(R.id.btnHostSu).setOnClickListener { apply(Hosts.SU) }
        findViewById<Button>(R.id.btnHostPro).setOnClickListener { apply(Hosts.PRO) }
    }

    private fun renderHostHint() {
        val shown = if (prefs.host == Hosts.AUTO) {
            getString(R.string.host_auto_hint)
        } else {
            prefs.host
        }
        findViewById<TextView>(R.id.instructions).text =
            getString(R.string.instructions_fmt, shown)
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
     *
     * При этом переключатели уже управляют доступностью соседних
     * элементов — механика цепочки работает на реальных условиях,
     * а не на заглушках.
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
            applyChainState()
            Toast.makeText(this, R.string.not_implemented_yet, Toast.LENGTH_SHORT).show()
        }
        sleepToggle.setOnClickListener {
            prefs.sleepEnabled = !prefs.sleepEnabled
            renderSleep()
            applyChainState()
            Toast.makeText(this, R.string.not_implemented_yet, Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnCacheClear).setOnClickListener {
            Toast.makeText(this, R.string.not_implemented_yet, Toast.LENGTH_SHORT).show()
        }
    }

    /** Пресеты из B3: пары «то же СШ, разный хост». Технический элемент. */
    private fun wirePresets() {
        val apply = { su: String, pro: String ->
            // В режиме «Авто» подставляем семизначный код: он сам уедет на pro.
            // Чтобы прогнать ту же пару на su, надо явно выбрать prtv.su —
            // тогда подставится короткий номер.
            slideshow.setText(if (prefs.host == Hosts.SU) su else pro)
            slideshow.requestFocus()
        }
        findViewById<Button>(R.id.presetCalendar).setOnClickListener { apply("11211", "0012276") }
        findViewById<Button>(R.id.presetPizza).setOnClickListener { apply("11472", "0012277") }
        findViewById<Button>(R.id.presetOlympic).setOnClickListener { apply("32024", "0012278") }
    }

    private fun savePreferences() {
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

    companion object {
        private const val LAUNCH_GUARD_MS = 2500L
        private const val BACK_GUARD_MS = 900L

        /** Накопитель появится на Э4. */
        private const val USB_SUPPORTED = false

        /** Поддержку сна железом узнаем на Э6 вместе с CEC. */
        private const val SLEEP_AVAILABLE = true
    }
}
