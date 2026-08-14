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

    /**
     * Хост не выбран вручную — определяется по коду. Значение по умолчанию.
     */
    const val AUTO = "auto"

    /** Порядок = порядок кнопок на экране ввода. */
    val ALL = listOf(AUTO, SU, PRO)

    /**
     * Граница длины кода между семействами.
     *
     * Наблюдаемые коды: su — 11211, 11472, 32024 (пять знаков), подсказка
     * на экране приложения обещает «11111/111111», то есть пять или шесть.
     * pro — 0012276, 0012277, 0012278: семь знаков с ведущими нулями.
     *
     * Величина помечена как предположение (П): в бандле боевого приложения
     * функция resolveBaseUrlForCode существует, но само число лежит в
     * байткоде и строковым разбором не достаётся. Проверяется вводом
     * реальных кодов; решение пишется в журнал, поэтому ошибка видна сразу.
     */
    const val PRO_CODE_MIN_LENGTH = 7

    /**
     * Хост для конкретного кода.
     *
     * Так устроено боевое приложение: адрес выводится из кода, а не берётся
     * из настройки. Ручной выбор остаётся перекрытием — в бандле это
     * отражено соседством resolveBaseUrlForCode и shouldPreferConfiguredBaseUrl.
     *
     * Нам перекрытие нужно ещё и для сравнительных прогонов: одно и то же
     * слайд-шоу заведено на обоих хостах под разными номерами, и иногда
     * надо принудительно отправить короткий код на pro, чтобы увидеть отказ.
     */
    fun resolveForCode(code: String, configured: String): String {
        if (configured == SU || configured == PRO) return configured
        return if (code.trim().length >= PRO_CODE_MIN_LENGTH) PRO else SU
    }

    /** Подпись хоста для экранов и журнала. */
    fun label(host: String): String =
        if (host == AUTO) "авто" else host

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
