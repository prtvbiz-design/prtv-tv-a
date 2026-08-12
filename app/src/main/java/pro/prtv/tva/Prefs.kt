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
    private fun keyFor(kind: Hosts.Kind) = when (kind) {
        Hosts.Kind.SLIDESHOW -> K_SLIDESHOW
        Hosts.Kind.SET -> K_SET
        Hosts.Kind.STREAM -> K_STREAM
    }
}
