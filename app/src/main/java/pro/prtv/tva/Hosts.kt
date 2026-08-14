package pro.prtv.tva

/**
 * Разрешение хоста и построение пути показа.
 *
 * Правило модуля: хост — параметр. Имя домена влияет ровно на одно —
 * на форму пути показа. Никаких других поведенческих развилок по имени
 * домена в проекте быть не должно.
 *
 * Цена нарушения этого правила видна в боевом приложении: там переменная
 * isNewTestHost включала особую доставку клавиш пульта на страницах
 * подборок и потоков, и при переезде на prtv.pro её пришлось срочно
 * дополнять вторым доменом, иначе пульт откатился бы к старому поведению
 * (A-7 §5.2). Имя переменной при этом осталось прежним и теперь врёт.
 *
 * new-test.prtv.su убран по ТЗ A-8 §0.2. На логику схем это не влияет:
 * семейство определяется по составу коллекций модели, а не по домену,
 * поэтому стенд и опознавался как pro-семейство без единой правки кода.
 */
object Hosts {

    const val SU = "prtv.su"
    const val PRO = "prtv.pro"

    /** Порядок = порядок кнопок на экране ввода. */
    val ALL = listOf(SU, PRO)

    enum class Kind { SLIDESHOW, SET, STREAM }

    /**
     * su-семейство отдаёт показ с корня, pro-семейство — из /playback/.
     * Оба пути подтверждены живым прогоном на телевизоре 12.08 (A-4 §3).
     */
    fun isLegacySchema(host: String): Boolean =
        host == SU

    fun baseUrl(host: String): String = "https://$host/"

    fun playbackPath(host: String, kind: Kind, id: String): String {
        val code = id.trim()
        return if (isLegacySchema(host)) {
            when (kind) {
                Kind.SLIDESHOW -> code
                Kind.SET -> "set/$code"
                Kind.STREAM -> "stream/$code"
            }
        } else {
            when (kind) {
                Kind.SLIDESHOW -> "playback/$code"
                Kind.SET -> "playback/set/$code"
                Kind.STREAM -> "playback/stream/$code"
            }
        }
    }

    fun playbackUrl(host: String, kind: Kind, id: String): String =
        baseUrl(host) + playbackPath(host, kind, id)

    /** Человекочитаемое имя схемы — для экрана диагностики и отчётов. */
    fun schemaName(host: String): String =
        if (isLegacySchema(host)) "su-family" else "pro-family"
}
