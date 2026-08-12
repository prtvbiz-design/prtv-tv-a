package pro.prtv.tva
import android.content.Context
import android.content.SharedPreferences
import java.util.UUID
/**
 * Хранилище настроек и идентификатор устройства.
 *
 * Идентификатор генерируется при первом запуске и больше не меняется.
 * Никуда не отправляется — лежит локально, виден на экране диагностики.
 * Заводится сразу: добавить его задним числом стоит физического визита
 * к каждому устройству.
 */
class Prefs(ctx: Context) {
    private val sp: SharedPreferences =
        ctx.getSharedPreferences("prtv_tv_a", Context.MODE_PRIVATE)
    companion object {
        private const val K_DEVICE_ID = "device_id"
        private const val K_HOST = "host"
        private const val K_SLIDESHOW = "code_slideshow"
        private const val K_SET = "code_set"
        private const val K_STREAM = "code_stream"
        private const val K_LAST_KIND = "last_kind"
        private const val K_LANG = "lang"
        private const val K_CACHE_ON = "cache_enabled"
        private const val K_CACHE_KB = "cache_threshold_kb"
        private const val K_SLEEP_ON = "sleep_enabled"
        private const val K_WAKE_AT = "wake_at"
        private const val K_SLEEP_AT = "sleep_at"
    }
    /** Создаётся один раз, лениво, при первом обращении. */
    val deviceId: String
        get() {
            sp.getString(K_DEVICE_ID, null)?.let { return it }
            val generated = "A-" + UUID.randomUUID().toString().substring(0, 13).uppercase()
            sp.edit().putString(K_DEVICE_ID, generated).apply()
            return generated
        }
    var host: String
        get() = sp.getString(K_HOST, Hosts.PRO) ?: Hosts.PRO
        set(v) = sp.edit().putString(K_HOST, v).apply()
    /** Последний введённый код каждого типа. */
    fun code(kind: Hosts.Kind): String = sp.getString(keyFor(kind), "") ?: ""
    fun setCode(kind: Hosts.Kind, value: String) {
        sp.edit().putString(keyFor(kind), value.trim()).apply()
    }
    /**
     * Какой тип запускали последним. Восстанавливается именно он, иначе
     * поток невозможно оставить включённым между перезапусками.
     */
    var lastKind: Hosts.Kind
        get() = runCatching { Hosts.Kind.valueOf(sp.getString(K_LAST_KIND, "") ?: "") }
            .getOrDefault(Hosts.Kind.SLIDESHOW)
        set(v) = sp.edit().putString(K_LAST_KIND, v.name).apply()
    /** "ru" или "en". Пустая строка — язык системы. */
    var language: String
        get() = sp.getString(K_LANG, "") ?: ""
        set(v) = sp.edit().putString(K_LANG, v).apply()
    /**
     * Настройки кэша сохраняются уже сейчас, хотя сам кэш появится
     * на следующем этапе: так значения переживут обновление и не
     * придётся вводить их заново.
     */
    var cacheEnabled: Boolean
        get() = sp.getBoolean(K_CACHE_ON, false)
        set(v) = sp.edit().putBoolean(K_CACHE_ON, v).apply()
    var cacheThresholdKb: Int
        get() = sp.getInt(K_CACHE_KB, 0)
        set(v) = sp.edit().putInt(K_CACHE_KB, v).apply()
    var sleepEnabled: Boolean
        get() = sp.getBoolean(K_SLEEP_ON, false)
        set(v) = sp.edit().putBoolean(K_SLEEP_ON, v).apply()
    var wakeAt: String
        get() = sp.getString(K_WAKE_AT, "") ?: ""
        set(v) = sp.edit().putString(K_WAKE_AT, v.trim()).apply()
    var sleepAt: String
        get() = sp.getString(K_SLEEP_AT, "") ?: ""
        set(v) = sp.edit().putString(K_SLEEP_AT, v.trim()).apply()
    private fun keyFor(kind: Hosts.Kind) = when (kind) {
        Hosts.Kind.SLIDESHOW -> K_SLIDESHOW
        Hosts.Kind.SET -> K_SET
        Hosts.Kind.STREAM -> K_STREAM
    }
}
