package pro.prtv.tva

import android.os.Handler
import android.os.Looper
import android.widget.ImageView

/**
 * Смена изображений на правой панели главного экрана.
 *
 * Перекрёстное затухание между двумя наложенными картинками. Никаких
 * сдвигов, наездов и параллакса: рядом стоит поле ввода кода, и движение
 * возле него мешает набирать номер. По той же причине смена редкая —
 * панель должна оживлять экран, а не тянуть внимание на себя.
 *
 * Картинки лежат в drawable-nodpi намеренно. В обычной drawable Android
 * считает их mdpi и на телевизоре с плотностью xhdpi разворачивает вдвое
 * в памяти: кадр 700×1080 превращается примерно в 12 МБ вместо трёх.
 * На приставке с гигабайтом это заметно.
 *
 * В памяти одновременно живут две картинки, а не четыре: декодируется
 * только та, что въезжает.
 */
class PanelRotator(
    private val back: ImageView,
    private val front: ImageView,
) {

    private val handler = Handler(Looper.getMainLooper())
    private val tick = Runnable { step() }

    private var index = 0
    private var running = false

    /** Какая из двух картинок сейчас наверху. Стартуем с нижней. */
    private var frontOnTop = false

    fun start() {
        if (running || PANELS.size < 2) return
        running = true
        handler.postDelayed(tick, HOLD_MS)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tick)
    }

    private fun step() {
        if (!running) return
        index = (index + 1) % PANELS.size

        val incoming = if (frontOnTop) back else front
        val outgoing = if (frontOnTop) front else back

        incoming.setImageResource(PANELS[index])
        incoming.alpha = 0f
        incoming.animate().alpha(1f).setDuration(FADE_MS).start()
        outgoing.animate().alpha(0f).setDuration(FADE_MS).start()

        frontOnTop = !frontOnTop
        handler.postDelayed(tick, HOLD_MS)
    }

    companion object {
        /**
         * Три сцены применения: ресторан, пекарня, отель.
         * Полный круг — тридцать секунд.
         */
        private val PANELS = intArrayOf(
            R.drawable.panel_1,
            R.drawable.panel_2,
            R.drawable.panel_3,
        )

        private const val HOLD_MS = 10_000L
        private const val FADE_MS = 800L
    }
}
