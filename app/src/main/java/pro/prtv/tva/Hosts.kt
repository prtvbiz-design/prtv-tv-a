package pro.prtv.tva
/**
 * Разрешение хоста и построение пути показа.
 *
 * Правило модуля: хост — параметр. Имя домена влияет ровно на одно —
 * на форму пути показа. Никаких других поведенческих развилок по имени
 * домена в проекте быть не должно.
 */
object Hosts {
    const val SU = "prtv.su"
    const val PRO = "prtv.pro"
    const val NEW_TEST = "new-test.prtv.su"
    /** Порядок = порядок кнопок на экране ввода. */
    val ALL = listOf(SU, PRO, NEW_TEST)
    enum class Kind { SLIDESHOW, SET, STREAM }
    /**
     * su-семейство отдаёт показ с корня, pro-семейство — из /playback/.
     * new-test подтверждён живым прогоном 29.07 как pro-семейство,
     * несмотря на то, что домен лежит внутри prtv.su.
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
