package pro.prtv.tva
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
/**
 * Кольцевой буфер событий в памяти.
 *
 * Существует, чтобы отказ читался с экрана телевизора, а не из adb logcat.
 *
 * Query-строки в журнал не попадают ни при каких условиях — см. redact().
 */
object EventLog {
    private const val CAPACITY = 200
    private const val TAG = "PRTV-A"
    private val buffer = ArrayDeque<String>(CAPACITY)
    private val stamp = SimpleDateFormat("HH:mm:ss", Locale.US)
    @Synchronized
    fun add(tag: String, message: String) {
        val line = "${stamp.format(Date())} $tag ${redact(message)}"
        if (buffer.size >= CAPACITY) buffer.removeFirst()
        buffer.addLast(line)
        Log.i(TAG, line)
    }
    @Synchronized
    fun snapshot(): List<String> = buffer.toList().asReversed()
    @Synchronized
    fun clear() = buffer.clear()
    /**
     * Срезает query-строку целиком. Правило безусловное: в журнал попадает
     * только то, что положено туда намеренно.
     */
    private fun redact(raw: String): String {
        val q = raw.indexOf('?')
        return if (q >= 0) raw.substring(0, q) + "?…" else raw
    }
}
