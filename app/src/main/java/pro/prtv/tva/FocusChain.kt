package pro.prtv.tva

/**
 * Порядок обхода фокуса на главном экране.
 *
 * Навигация в приложении PRTV не пространственная, а линейная: элементы
 * выстроены в одну цепочку, вправо — вперёд, влево — назад. Ряды на экране
 * существуют только визуально, на порядок обхода они не влияют.
 *
 * Воспроизводим цепочку буквально (ТЗ A-8 §3.1). Причина не в красоте:
 * если ощущение от пульта у нашего приложения будет отличаться от боевого,
 * сравнение двух приложений потеряет смысл — клиент будет сравнивать
 * привычность, а не архитектуру.
 *
 * Асимметрия «вправо заворачивает на первый, влево с первого не делает
 * ничего» — сознательная, из оригинала. Это не забытая ветка.
 *
 * Отключённые элементы выпадают из цепочки целиком, а не пропускаются
 * визуально: переход отдаёт следующий доступный, фокус не теряется.
 */
object FocusChain {

    /**
     * Тринадцать элементов боевого приложения, порядок дословный:
     * slideshowInput → streamInput → setInput → playFlash →
     * langRu → langEn → imageCacheThresholdInput →
     * enableVideoCache → clearVideoCache →
     * timerToggle → timerOn → timerOff → systemData
     */
    private val CORE = intArrayOf(
        R.id.inputSlideshow,
        R.id.inputStream,
        R.id.inputSet,
        R.id.btnUsbSlideshow,
        R.id.btnLangRu,
        R.id.btnLangEn,
        R.id.inputCacheThreshold,
        R.id.switchCache,
        R.id.btnCacheClear,
        R.id.switchSleep,
        R.id.inputWakeAt,
        R.id.inputSleepAt,
        R.id.btnSystemData,
    )

    /**
     * Ничего сверх боевого набора здесь нет. Выбор хоста и пресеты
     * сравнения убраны с главного экрана: хост определяется по коду,
     * а пресеты — инструмент разработчика, им не место под пультом
     * у человека, который просто хочет включить показ.
     */
    val ORDER: IntArray = CORE

    /**
     * Пять особых переходов влево. В оригинале они прописаны отдельными
     * правилами и не выводятся из порядка цепочки — переносим как есть.
     *
     * Если цель перехода отключена, правило не срабатывает и переход идёт
     * обычным путём назад по цепочке.
     */
    private val LEFT_OVERRIDE = mapOf(
        R.id.inputSleepAt to R.id.inputWakeAt,
        R.id.inputWakeAt to R.id.btnCacheClear,
        R.id.switchSleep to R.id.switchCache,
        R.id.btnCacheClear to R.id.inputCacheThreshold,
        R.id.switchCache to R.id.inputCacheThreshold,
    )

    /** Вперёд по цепочке. С последнего элемента — заворот на первый. */
    fun next(currentId: Int, enabled: (Int) -> Boolean): Int {
        val start = ORDER.indexOf(currentId)
        if (start < 0) return firstEnabled(enabled) ?: currentId
        var k = start
        repeat(ORDER.size) {
            k = (k + 1) % ORDER.size
            if (enabled(ORDER[k])) return ORDER[k]
        }
        return currentId
    }

    /** Назад по цепочке. С первого элемента — ничего не происходит. */
    fun previous(currentId: Int, enabled: (Int) -> Boolean): Int {
        LEFT_OVERRIDE[currentId]?.let { target ->
            if (enabled(target)) return target
        }
        var k = ORDER.indexOf(currentId)
        if (k <= 0) return currentId
        while (k > 0) {
            k--
            if (enabled(ORDER[k])) return ORDER[k]
        }
        return currentId
    }

    /** Первый доступный элемент. Нужен, когда фокус оказался вне цепочки. */
    fun firstEnabled(enabled: (Int) -> Boolean): Int? =
        ORDER.firstOrNull { enabled(it) }

    fun contains(id: Int): Boolean = ORDER.contains(id)
}
