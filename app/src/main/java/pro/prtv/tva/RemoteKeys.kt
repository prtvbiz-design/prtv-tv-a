package pro.prtv.tva
import android.view.KeyEvent
/**
 * Цветные кнопки пульта — переход к соответствующему полю ввода.
 *
 * Между полями можно перемещаться и стрелками, и цветными кнопками.
 * Стрелки обрабатывает сама система: раскладка нативная, фокус ходит
 * по элементам без нашего участия. Цветные кнопки нужно принимать явно.
 *
 * Показ эти кнопки не запускают. Запуск делает действие «Готово»
 * экранной клавиатуры после набора номера — как в приложении PRTV.
 *
 * ── Коды по платформам ──
 *
 * Android TV: 183–186. Именованные константы KEYCODE_PROG_* существуют
 * не на всех сборках, поэтому продублированы числами.
 *
 * LG webOS и Samsung Tizen: 403–406. Совпадают между собой, подтверждено
 * реализациями PRTV для обеих платформ. Нативному Android-приложению они
 * не приходят, но приняты на случай нестандартных пультов и оставлены
 * здесь как перенос знания в будущие версии под эти платформы.
 *
 * Соответствие подписям главного экрана:
 *   зелёная  — Слайд-шоу
 *   жёлтая   — Запуск по расписанию
 *   красная  — Меню слайд-шоу
 */
object RemoteKeys {
    // Android TV
    private const val RED_ANDROID = 183
    private const val GREEN_ANDROID = 184
    private const val YELLOW_ANDROID = 185
    private const val BLUE_ANDROID = 186
    // webOS и Tizen
    private const val RED_WEB = 403
    private const val GREEN_WEB = 404
    private const val YELLOW_WEB = 405
    private const val BLUE_WEB = 406
    private val RED = setOf(RED_ANDROID, RED_WEB, KeyEvent.KEYCODE_PROG_RED)
    private val GREEN = setOf(GREEN_ANDROID, GREEN_WEB, KeyEvent.KEYCODE_PROG_GREEN)
    private val YELLOW = setOf(YELLOW_ANDROID, YELLOW_WEB, KeyEvent.KEYCODE_PROG_YELLOW)
    /** Синяя не используется, но перечислена, чтобы не искать код заново. */
    private val BLUE = setOf(BLUE_ANDROID, BLUE_WEB, KeyEvent.KEYCODE_PROG_BLUE)
    fun kindFor(keyCode: Int): Hosts.Kind? = when (keyCode) {
        in GREEN -> Hosts.Kind.SLIDESHOW
        in YELLOW -> Hosts.Kind.STREAM
        in RED -> Hosts.Kind.SET
        else -> null
    }
    fun isBlue(keyCode: Int): Boolean = keyCode in BLUE
}
