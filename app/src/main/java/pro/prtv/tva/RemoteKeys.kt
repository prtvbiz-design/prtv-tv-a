package pro.prtv.tva
import android.view.KeyEvent
/**
 * Цветные кнопки пульта.
 *
 * Коды не входят в KeyEvent как именованные константы на всех сборках,
 * поэтому заданы численно. Соответствие подписям на главном экране:
 * зелёная — слайд-шоу, жёлтая — поток, красная — подборка.
 */
object RemoteKeys {
    const val RED = 183
    const val GREEN = 184
    const val YELLOW = 185
    const val BLUE = 186
    fun kindFor(keyCode: Int): Hosts.Kind? = when (keyCode) {
        GREEN, KeyEvent.KEYCODE_PROG_GREEN -> Hosts.Kind.SLIDESHOW
        YELLOW, KeyEvent.KEYCODE_PROG_YELLOW -> Hosts.Kind.STREAM
        RED, KeyEvent.KEYCODE_PROG_RED -> Hosts.Kind.SET
        else -> null
    }
}
