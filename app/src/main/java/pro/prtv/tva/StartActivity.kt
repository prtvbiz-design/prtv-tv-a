package pro.prtv.tva
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
/**
 * Экран ввода. Три сущности — слайд-шоу, подборка, поток (R-IN-01),
 * три хоста, последний код каждого типа сохраняется (R-IN-02).
 *
 * Экрана подборки со своей навигацией здесь ещё нет — это Э2.
 * Код подборки пока открывается тем же трактом показа.
 */
class StartActivity : Activity() {
    private lateinit var prefs: Prefs
    /** Гард от повторного запуска: 2500 мс. */
    private var lastLaunchAt = 0L
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)
        prefs = Prefs(this)
        EventLog.add("start", "device=${prefs.deviceId} build=${BuildConfig.VERSION_NAME}")
        val slideshow = findViewById<EditText>(R.id.inputSlideshow)
        val set = findViewById<EditText>(R.id.inputSet)
        val stream = findViewById<EditText>(R.id.inputStream)
        slideshow.setText(prefs.code(Hosts.Kind.SLIDESHOW))
        set.setText(prefs.code(Hosts.Kind.SET))
        stream.setText(prefs.code(Hosts.Kind.STREAM))
        val hostSu = findViewById<RadioButton>(R.id.hostSu)
        val hostPro = findViewById<RadioButton>(R.id.hostPro)
        val hostNewTest = findViewById<RadioButton>(R.id.hostNewTest)
        when (prefs.host) {
            Hosts.SU -> hostSu.isChecked = true
            Hosts.NEW_TEST -> hostNewTest.isChecked = true
            else -> hostPro.isChecked = true
        }
        findViewById<TextView>(R.id.deviceLine).text =
            getString(R.string.device_line, prefs.deviceId, BuildConfig.VERSION_NAME)
        val selectedHost = {
            when {
                hostSu.isChecked -> Hosts.SU
                hostNewTest.isChecked -> Hosts.NEW_TEST
                else -> Hosts.PRO
            }
        }
        val launch = { kind: Hosts.Kind, field: EditText ->
            val code = field.text.toString().trim()
            if (code.isEmpty()) {
                Toast.makeText(this, R.string.enter_code, Toast.LENGTH_SHORT).show()
            } else if (SystemClock.elapsedRealtime() - lastLaunchAt < 2500) {
                // молча: повторное нажатие пульта в пределах гарда
            } else {
                lastLaunchAt = SystemClock.elapsedRealtime()
                val host = selectedHost()
                prefs.host = host
                prefs.setCode(kind, code)
                prefs.lastKind = kind
                startActivity(
                    Intent(this, PlayerActivity::class.java)
                        .putExtra(PlayerActivity.EXTRA_HOST, host)
                        .putExtra(PlayerActivity.EXTRA_KIND, kind.name)
                        .putExtra(PlayerActivity.EXTRA_CODE, code)
                )
            }
        }
        findViewById<Button>(R.id.btnSlideshow).setOnClickListener {
            launch(Hosts.Kind.SLIDESHOW, slideshow)
        }
        findViewById<Button>(R.id.btnSet).setOnClickListener {
            launch(Hosts.Kind.SET, set)
        }
        findViewById<Button>(R.id.btnStream).setOnClickListener {
            launch(Hosts.Kind.STREAM, stream)
        }
        findViewById<Button>(R.id.btnDiag).setOnClickListener {
            startActivity(Intent(this, DiagActivity::class.java))
        }
        wirePresets()
    }
    /**
     * Пресеты из B3 — три пары «то же СШ, разный хост», по которым уже есть
     * базовые цифры Playwright. Нужны для сравнения, не для показа клиенту.
     */
    private fun wirePresets() {
        val row = findViewById<View>(R.id.presetsRow)
        val slideshow = findViewById<EditText>(R.id.inputSlideshow)
        val hostSu = findViewById<RadioButton>(R.id.hostSu)
        val hostPro = findViewById<RadioButton>(R.id.hostPro)
        val apply = { su: String, pro: String ->
            if (hostSu.isChecked) slideshow.setText(su) else {
                hostPro.isChecked = true
                slideshow.setText(pro)
            }
        }
        findViewById<Button>(R.id.presetCalendar).setOnClickListener { apply("11211", "0012276") }
        findViewById<Button>(R.id.presetPizza).setOnClickListener { apply("11472", "0012277") }
        findViewById<Button>(R.id.presetOlympic).setOnClickListener { apply("32024", "0012278") }
        row.visibility = View.VISIBLE
    }
}
