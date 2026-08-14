package pro.prtv.tva

import android.os.SystemClock
import android.view.KeyEvent

/**
 * Единая точка обработки клавиш главного экрана.
 *
 * Лестница приоритетов из A-6, восемь уровней, строго сверху вниз.
 * Уровни, которых на этом этапе ещё нет, присутствуют явными заглушками
 * и возвращают false. Так сделано намеренно: правило серии гласит, что
 * пропущенную механику потом додумывают, и додуманное попадает в сборку
 * незамеченным. Пустой уровень в списке виден, отсутствующий — нет.
 *
 * Главное свойство лестницы: направления никогда не проваливаются в
 * обработчик цветных кнопок. На Android TV цветные подписи привязаны
 * к стрелкам, коды совпадают, и без этого правила навигация превращалась
 * бы в прыжки между полями.
 */
class KeyRouter(private val screen: Screen) {

    /**
     * Экран, которому принадлежит лестница. Все уровни имеют реализацию
     * по умолчанию, поэтому экран переопределяет только то, что у него есть.
     */
    interface Screen {
        /** 1. Гард «назад» после возврата с показа. */
        fun onBackGuard(keyCode: Int): Boolean = false

        /** 2. Модальное окно порога кэша: стрелки меняют значение, OK подтверждает. */
        fun onCacheThresholdModal(keyCode: Int): Boolean = false

        /** 3. Модальное окно подтверждения: влево-вправо выбирают, OK применяет. */
        fun onConfirmModal(keyCode: Int): Boolean = false

        /** 4. Модальное окно системных данных: любая клавиша фокусирует «закрыть». */
        fun onSystemDataModal(keyCode: Int): Boolean = false

        /** 5. Показ ошибки: OK, «назад» и «выход» скрывают. */
        fun onErrorBanner(keyCode: Int): Boolean = false

        /** 6. Диалог пароля. */
        fun onPasswordDialog(keyCode: Int): Boolean = false

        /** 7. Направления. Если обработаны, дальше не идут. */
        fun onDirection(keyCode: Int): Boolean = false

        /** 8. Цветные кнопки. Только когда нет диалога пароля и закрыта клавиатура. */
        fun onColorKey(keyCode: Int): Boolean = false

        /**
         * Глушит обработку целиком: открыта экранная клавиатура или
         * модальное окно, которое забирает все клавиши себе.
         */
        fun isInputBlocked(): Boolean = false

        fun logKey(keyCode: Int, verdict: String) {}
    }

    /**
     * Дедупликация нажатий (A-IN-05).
     *
     * Величина окна наша, не унаследованная: в боевом приложении факт
     * дедупликации подтверждён, а значение — нет. 400 мс подобраны так,
     * чтобы гасить дребезг и двойные посылки пультов, но не мешать
     * человеку нажать кнопку дважды осмысленно.
     *
     * Действует НЕ на все клавиши, а только на цветные и «назад».
     * Список узкий намеренно. Направлениям нужен автоповтор, иначе
     * навигация по длинной цепочке станет вязкой. Цифрам он нужен ещё
     * сильнее: в коде 11211 подряд идут две единицы, и дедупликация
     * «по любой клавише» съела бы вторую, превратив номер в 1211.
     */
    private var lastCode = Int.MIN_VALUE
    private var lastAt = 0L

    private fun isDedupable(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_BACK ||
            RemoteKeys.kindFor(keyCode) != null ||
            RemoteKeys.isBlue(keyCode)

    private fun isDuplicate(keyCode: Int): Boolean {
        if (!isDedupable(keyCode)) return false
        val now = SystemClock.elapsedRealtime()
        val duplicate = keyCode == lastCode && now - lastAt < DEDUP_MS
        lastCode = keyCode
        lastAt = now
        return duplicate
    }

    /**
     * Возвращает true, если событие обработано и дальше идти не должно.
     * Вызывается только для ACTION_DOWN.
     */
    fun dispatch(keyCode: Int): Boolean {
        if (screen.onBackGuard(keyCode)) {
            screen.logKey(keyCode, "back-guard")
            return true
        }
        if (isDuplicate(keyCode)) {
            screen.logKey(keyCode, "dup")
            return true
        }
        if (screen.isInputBlocked()) {
            screen.logKey(keyCode, "blocked")
            return false
        }
        if (screen.onCacheThresholdModal(keyCode)) return true
        if (screen.onConfirmModal(keyCode)) return true
        if (screen.onSystemDataModal(keyCode)) return true
        if (screen.onErrorBanner(keyCode)) return true
        if (screen.onPasswordDialog(keyCode)) return true
        if (screen.onDirection(keyCode)) {
            screen.logKey(keyCode, "direction")
            return true
        }
        if (screen.onColorKey(keyCode)) {
            screen.logKey(keyCode, "color")
            return true
        }
        screen.logKey(keyCode, "pass")
        return false
    }

    companion object {
        const val DEDUP_MS = 400L

        fun isDirection(keyCode: Int): Boolean = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN -> true
            else -> false
        }
    }
}
