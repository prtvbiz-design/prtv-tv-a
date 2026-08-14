package pro.prtv.tva

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/**
 * Главный экран.
 *
 * Раскладка повторяет боевое приложение: рабочая колонка слева —
 * шапка, ввод, настройки, инструкции, служебное; панель изображения
 * справа во всю высоту. Выбора хоста на экране нет: адрес выводится
 * из длины кода, как в боевом (resolveBaseUrlForCode).
 *
 * Показ запускает действие «Готово» экранной клавиатуры. Цветные кнопки
 * только переводят фокус. Обход фокуса — линейная цепочка (FocusChain),
 * клавиши проходят через лестницу приоритетов (KeyRouter).
 */
class StartActivity : Activity(), KeyRouter.Screen {

    private lateinit var prefs: Prefs
    private lateinit var router: KeyRouter
    private lateinit var slideshow: EditText
    private lateinit var stream: EditText
    private lateinit var set: EditText
    private lateinit var cacheSwitch: Switch
    private lateinit var sleepSwitch: Switch
    private lateinit var threshold: Spinner
    private var panels: PanelRotator? = null

    /** Гард от повторного запуска: 2500 мс. */
    private var lastLaunchAt = 0L

    /**
     * Гард «назад»: 900 мс после появления экрана. Нужен из-за возврата
     * с показа — пульт успевает прислать второе нажатие раньше, чем
     * экран отрисуется, и приложение закрывается сразу после выхода.
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
        EventLog.add("start", "touchMode=" + window.decorView.isInTouchMode)

        slideshow = findViewById(R.id.inputSlideshow)
        stream = findViewById(R.id.inputStream)
        set = findViewById(R.id.inputSet)
        cacheSwitch = findViewById(R.id.switchCache)
        sleepSwitch = findViewById(R.id.switchSleep)
        threshold = findViewById(R.id.inputCacheThreshold)

        slideshow.setText(prefs.code(Hosts.Kind.SLIDESHOW))
        stream.setText(prefs.code(Hosts.Kind.STREAM))
        set.setText(prefs.code(Hosts.Kind.SET))

        renderStatusLine()
        wireLanguage()
        wireCache()
        wireSleep()
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
     * Смена картинок идёт, только пока окно на экране и владеет фокусом.
     * Экранная клавиатура открывается поверх и забирает фокус окна —
     * этого условия достаточно, чтобы панель замерла на время набора,
     * и не нужно угадывать высоту клавиатуры.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) panels?.start() else panels?.stop()
    }

    override fun onResume() {
        super.onResume()
        backAllowedAt = SystemClock.elapsedRealtime() + BACK_GUARD_MS
        applyChainState()
    }

    override fun onPause() {
        panels?.stop()
        savePreferences()
        super.onPause()
    }

    /* ──────────────────────── клавиши ──────────────────────── */

    /**
     * Перехват до передачи в иерархию представлений: иначе стрелки
     * съедала бы системная пространственная навигация и линейная
     * цепочка не работала бы. Цифры и OK через лестницу не проходят
     * и попадают в поле ввода — без этого номер нечем набрать.
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
     * горизонтали. Элементы вне цепочки сделаны нефокусируемыми,
     * поэтому системная вертикаль не уведёт фокус лишнее.
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

    /**
     * Цветные кнопки переводят фокус на соответствующее поле. Показ они
     * не запускают. Каждое нажатие пишется в журнал: пульты разных
     * производителей шлют разные коды, и увидеть их можно только живьём.
     */
    override fun onColorKey(keyCode: Int): Boolean {
        val kind = RemoteKeys.kindFor(keyCode) ?: return false
        fieldFor(kind).requestFocus()
        return true
    }

    override fun logKey(keyCode: Int, verdict: String) {
        EventLog.add("key", "code=$keyCode ${KeyEvent.keyCodeToString(keyCode)} → $verdict")
    }

    /** Имя элемента вместо голого числа: журнал читают с экрана телевизора. */
    private fun nameOf(id: Int): String =
        if (id == View.NO_ID) "-"
        else runCatching { resources.getResourceEntryName(id) }.getOrDefault("id" + id)

    /* ──────────────────── доступность элементов ──────────────────── */

    /**
     * Условия доступности из оригинала. Отключённый элемент выпадает
     * из цепочки и перестаёт быть фокусируемым.
     *
     * Кнопка USB отключена до Э4: накопителя приложение пока не знает.
     * На устройстве без флешки боевое ведёт себя так же.
     */
    private fun isChainEnabled(id: Int): Boolean = when (id) {
        R.id.btnUsbSlideshow -> USB_SUPPORTED
        R.id.inputCacheThreshold, R.id.btnCacheClear -> prefs.cacheEnabled
        R.id.switchSleep -> SLEEP_AVAILABLE
        R.id.inputWakeAt, R.id.inputSleepAt -> SLEEP_AVAILABLE && prefs.sleepEnabled
        else -> true
    }

    private fun applyChainState() {
        for (id in FocusChain.ORDER) {
            val view = findViewById<View>(id) ?: continue
            val on = isChainEnabled(id)
            view.isEnabled = on
            view.isFocusable = on
            view.isFocusableInTouchMode = on
        }
        findViewById<TextView>(R.id.sleepNote).visibility =
            if (SLEEP_AVAILABLE) View.GONE else View.VISIBLE

        val current = currentFocus
        if (current == null || !FocusChain.contains(current.id) || !isChainEnabled(current.id)) {
            FocusChain.firstEnabled { isChainEnabled(it) }?.let { id ->
                val ok = findViewById<View>(id)?.requestFocus() ?: false
                EventLog.add("focus", "начальный " + nameOf(id) + (if (ok) " ok" else " ОТКАЗ"))
            }
        }
    }

    /* ──────────────────────── запуск показа ──────────────────────── */

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

        // Хост выводится из кода, как в боевом (resolveBaseUrlForCode)
        val host = Hosts.resolveForCode(code, prefs.host)
        EventLog.add(
            "host",
            "код " + code.length + " знаков -> " + host + " " + Hosts.schemaName(host),
        )

        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_HOST, host)
                .putExtra(PlayerActivity.EXTRA_KIND, kind.name)
                .putExtra(PlayerActivity.EXTRA_CODE, code)
        )
    }

    /* ──────────────────────── панель настроек ──────────────────────── */

    private fun renderStatusLine() {
        findViewById<TextView>(R.id.statusLine).text = getString(
            R.string.status_line_fmt,
            Build.VERSION.SDK_INT.toString(),
            BuildConfig.VERSION_NAME,
            BuildConfig.BUILD_DATE,
            Hosts.label(prefs.host),
        )
    }

    /**
     * Активный выбор языка — заливка, фокус — рамка. Это два разных
     * состояния, и путать их нельзя: иначе непонятно, где ты находишься.
     */
    private fun wireLanguage() {
        val ru = findViewById<Button>(R.id.btnLangRu)
        val en = findViewById<Button>(R.id.btnLangEn)
        val isEn = prefs.language == "en"
        ru.setBackgroundResource(
            if (isEn) R.drawable.panel_button else R.drawable.panel_button_active
        )
        en.setBackgroundResource(
            if (isEn) R.drawable.panel_button_active else R.drawable.panel_button
        )
        val switch = { code: String ->
            if (prefs.language != code) {
                prefs.language = code
                recreate()
            }
        }
        ru.setOnClickListener { switch("ru") }
        en.setOnClickListener { switch("en") }
    }

    /**
     * Кэш: переключатель и порог списком, как в боевом. Сам механизм
     * появится на Э3, но переключатель уже управляет доступностью
     * соседних элементов — механика цепочки работает на реальных
     * условиях, а не на заглушках.
     */
    private fun wireCache() {
        cacheSwitch.isChecked = prefs.cacheEnabled
        cacheSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.cacheEnabled = checked
            applyChainState()
            Toast.makeText(this, R.string.not_implemented_yet, Toast.LENGTH_SHORT).show()
        }

        val labels = THRESHOLDS.map {
            if (it == 0) getString(R.string.threshold_off)
            else getString(R.string.threshold_fmt, it)
        }
        val adapter = ArrayAdapter(this, R.layout.spinner_item, labels)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        threshold.adapter = adapter
        val current = THRESHOLDS.indexOf(prefs.cacheThresholdKb)
        threshold.setSelection(if (current >= 0) current else 0)
        threshold.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                prefs.cacheThresholdKb = THRESHOLDS[pos]
            }

            override fun onNothingSelected(p: AdapterView<*>?) = Unit
        }

        findViewById<Button>(R.id.btnCacheClear).setOnClickListener {
            Toast.makeText(this, R.string.not_implemented_yet, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Режим сна. В боевом секция скрывается целиком, когда HDMI-CEC
     * недоступен, и понять это со стороны невозможно — полдня уходит
     * на уверенность, что функции нет вовсе. Мы показываем её всегда:
     * при недоступном CEC поля станут неактивны, а рядом встанет строка
     * с причиной.
     */
    private fun wireSleep() {
        findViewById<TextView>(R.id.sleepNote).setText(R.string.sleep_unavailable)
        sleepSwitch.isChecked = prefs.sleepEnabled
        sleepSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.sleepEnabled = checked
            applyChainState()
            Toast.makeText(this, R.string.not_implemented_yet, Toast.LENGTH_SHORT).show()
        }
        findViewById<EditText>(R.id.inputWakeAt).setText(prefs.wakeAt)
        findViewById<EditText>(R.id.inputSleepAt).setText(prefs.sleepAt)
    }

    private fun savePreferences() {
        prefs.setCode(Hosts.Kind.SLIDESHOW, slideshow.text.toString())
        prefs.setCode(Hosts.Kind.STREAM, stream.text.toString())
        prefs.setCode(Hosts.Kind.SET, set.text.toString())
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

        /**
         * Доступность режима сна в боевом определяется по HDMI-CEC
         * (canStandby, standby_not_available). Свою проверку заведём
         * на Э6 вместе с расписанием; до тех пор считаем доступным,
         * иначе ветку нечем проверять.
         */
        private const val SLEEP_AVAILABLE = true

        /** Значения порога из локализации боевого приложения. */
        private val THRESHOLDS = listOf(0, 100, 500, 1024)
    }
}
