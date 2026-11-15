package com.ozgurluk.kilidi

import android.app.AppOpsManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Process
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Günlük etkinlik paneli (kitap, telefon, ders, gitar, spor ve kullanıcının eklediği diğerleri).
 *
 * - "Telefon" çubuğu sistemden otomatik gelir ve elle değiştirilemez.
 * - "Ders" AnkiDroid'de, "Elektro gitar" Perde'de geçen süreyi otomatik alır ([LinkedApps]);
 *   kullanıcı bu çubuklara yalnızca otomatik sürenin üstüne ekleme yapabilir.
 * - Diğer süreleri kullanıcı kilit ekranından ya da ana ekran widget'ından düzenler.
 * - Süreler her gece yarısı sıfırlanır.
 */
object ActivityStore {

    const val PHONE_ID = "phone"

    data class Category(
        val id: String,
        val name: String,
        val emoji: String,
        val color: Int
    ) {
        val isPhone: Boolean get() = id == PHONE_ID
    }

    data class Entry(val category: Category, val minutes: Long)

    /** Yeni kategori eklerken seçilebilen renkler. */
    val PALETTE = intArrayOf(
        0xFFFF3333.toInt(), 0xFF0DC0DF.toInt(), 0xFFFDDE5A.toInt(), 0xFF7FD957.toInt(),
        0xFF8C52FF.toInt(), 0xFFFF8A3D.toInt(), 0xFFFF5CA8.toInt(), 0xFF3D7BFF.toInt()
    )

    private val DEFAULTS = listOf(
        Category("book", "Kitap", "📖", 0xFFFF3333.toInt()),
        Category(PHONE_ID, "Telefon", "📱", 0xFF0DC0DF.toInt()),
        Category("study", "Ders", "🎓", 0xFFFDDE5A.toInt()),
        Category("guitar", "Elektro gitar", "🎸", 0xFF7FD957.toInt()),
        Category("sport", "Spor", "🏋️", 0xFF8C52FF.toInt())
    )

    private const val PREFS = "EtkinlikPaneli"
    private const val KEY_CATEGORIES = "categories"
    private const val KEY_DATE = "values_date"
    private const val KEY_VALUES = "values"
    private const val KEY_SCREEN_DATE = "screen_date"
    private const val KEY_SCREEN_SECONDS = "screen_seconds"
    private const val KEY_LINKED_DATE = "linked_date"
    private const val KEY_LINKED_PREFIX = "linked_seconds_"

    /** Bağlı çubuklarda elle eklenen fazlalık, değerler içinde bu önekle saklanır. */
    private const val EXTRA_PREFIX = "extra_"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---- Kategoriler ------------------------------------------------------------------------

    fun categories(context: Context): List<Category> {
        val raw = prefs(context).getString(KEY_CATEGORIES, null) ?: return DEFAULTS
        return try {
            val arr = JSONArray(raw)
            val list = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Category(o.getString("id"), o.getString("name"), o.getString("emoji"), o.getInt("color"))
            }
            // Telefon, Ders ve Gitar çubukları her zaman bulunur (süreleri otomatik gelir).
            val missing = DEFAULTS.filter { d ->
                (d.isPhone || LinkedApps.isLinkedCategory(d.id)) && list.none { it.id == d.id }
            }
            if (missing.isEmpty()) list else missing + list
        } catch (e: Exception) {
            DEFAULTS
        }
    }

    private fun saveCategories(context: Context, list: List<Category>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("emoji", it.emoji).put("color", it.color))
        }
        prefs(context).edit { putString(KEY_CATEGORIES, arr.toString()) }
    }

    fun addCategory(context: Context, name: String, emoji: String, color: Int) {
        val id = "c" + System.currentTimeMillis()
        saveCategories(context, categories(context) + Category(id, name, emoji, color))
    }

    fun updateCategory(context: Context, id: String, name: String, emoji: String) {
        if (id == PHONE_ID) return
        saveCategories(context, categories(context).map {
            if (it.id == id) it.copy(name = name, emoji = emoji) else it
        })
    }

    /** Telefon, Ders ve Gitar çubukları silinemez. */
    fun isDeletable(id: String): Boolean = id != PHONE_ID && !LinkedApps.isLinkedCategory(id)

    fun deleteCategory(context: Context, id: String) {
        if (!isDeletable(id)) return
        saveCategories(context, categories(context).filter { it.id != id })
        val values = values(context)
        values.remove(id)
        saveValues(context, values)
    }

    // ---- Günlük süreler ----------------------------------------------------------------------

    private fun values(context: Context): JSONObject {
        val p = prefs(context)
        if (p.getString(KEY_DATE, null) != LocalDate.now().toString()) return JSONObject()
        return try {
            JSONObject(p.getString(KEY_VALUES, "{}") ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun saveValues(context: Context, values: JSONObject) = prefs(context).edit {
        putString(KEY_DATE, LocalDate.now().toString())
        putString(KEY_VALUES, values.toString())
    }

    /**
     * Çubuğun bugünkü süresini ayarlar. Ders/Gitar çubuklarında [minutes] toplam süredir;
     * Anki/Perde'den gelen süreden az olamaz, aradaki fark "elle eklenen" olarak saklanır.
     */
    fun setMinutes(context: Context, id: String, minutes: Long) {
        if (id == PHONE_ID) return // telefon süresi sistemden gelir
        val values = values(context)
        val total = minutes.coerceIn(0L, 24 * 60L)
        if (LinkedApps.isLinkedCategory(id)) {
            val auto = autoMinutes(context, id)
            values.put(EXTRA_PREFIX + id, (total - auto).coerceAtLeast(0L))
            values.remove(id) // eski sürümün elle girilen toplamı artık kullanılmaz
        } else {
            values.put(id, total)
        }
        saveValues(context, values)
    }

    fun minutes(context: Context, id: String): Long = when {
        id == PHONE_ID -> phoneMinutesToday(context)
        LinkedApps.isLinkedCategory(id) -> {
            val auto = autoMinutes(context, id)
            auto + extraMinutes(values(context), id, auto)
        }
        else -> values(context).optLong(id, 0L)
    }

    fun entries(context: Context): List<Entry> {
        val values = values(context)
        val phone = phoneMinutesToday(context)
        return categories(context).map {
            val m = when {
                it.isPhone -> phone
                LinkedApps.isLinkedCategory(it.id) -> {
                    val auto = autoMinutes(context, it.id)
                    auto + extraMinutes(values, it.id, auto)
                }
                else -> values.optLong(it.id, 0L)
            }
            Entry(it, m)
        }
    }

    /**
     * Bağlı çubukta elle eklenen fazlalık. Bu sürümden önce bugün elle bir toplam girildiyse
     * o toplamın otomatik süreyi aşan kısmı fazlalık sayılır (çift sayılmasın).
     */
    private fun extraMinutes(values: JSONObject, id: String, auto: Long): Long =
        if (values.has(EXTRA_PREFIX + id)) values.optLong(EXTRA_PREFIX + id, 0L)
        else (values.optLong(id, 0L) - auto).coerceAtLeast(0L)

    // ---- Anki / Perde süresi (sistemden) ----------------------------------------------------

    /** Ders/Gitar çubuğunun bağlı uygulamadan bugün gelen süresi (dakika). */
    fun autoMinutes(context: Context, id: String): Long {
        val matcher = LinkedApps.matcherFor(id) ?: return 0L
        val seconds = SystemUsage.packageSecondsToday(context, matcher)
            ?: fallbackLinkedSeconds(context, id)
        return seconds / 60
    }

    private fun fallbackLinkedSeconds(context: Context, id: String): Long {
        val p = prefs(context)
        return if (p.getString(KEY_LINKED_DATE, null) == LocalDate.now().toString()) {
            p.getLong(KEY_LINKED_PREFIX + id, 0L)
        } else 0L
    }

    /** Kullanım erişimi yoksa Erişilebilirlik servisi Anki/Perde süresini buraya ekler. */
    fun addFallbackLinkedSeconds(context: Context, id: String, seconds: Long) {
        if (seconds <= 0) return
        val p = prefs(context)
        val today = LocalDate.now().toString()
        val fresh = p.getString(KEY_LINKED_DATE, null) != today
        p.edit {
            if (fresh) {
                putString(KEY_LINKED_DATE, today)
                p.all.keys.filter { it.startsWith(KEY_LINKED_PREFIX) }.forEach { remove(it) }
            }
            putLong(KEY_LINKED_PREFIX + id, (if (fresh) 0L else p.getLong(KEY_LINKED_PREFIX + id, 0L)) + seconds)
        }
    }

    // ---- Telefon süresi (sistemden) ---------------------------------------------------------

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Bugün ekranda geçen süre (dakika). Kullanım erişimi verildiyse Android'in kendi kullanım
     * kayıtlarından (Dijital Denge ile aynı kaynak), verilmediyse Erişilebilirlik servisinin
     * kendi ölçümünden hesaplanır.
     */
    fun phoneMinutesToday(context: Context): Long =
        SystemUsage.screenSecondsToday(context)
            ?.div(60)
            ?: (fallbackScreenSeconds(context) / 60)

    private fun fallbackScreenSeconds(context: Context): Long {
        val p = prefs(context)
        return if (p.getString(KEY_SCREEN_DATE, null) == LocalDate.now().toString()) {
            p.getLong(KEY_SCREEN_SECONDS, 0L)
        } else 0L
    }

    /** Erişilebilirlik servisi ekran açık ve kilitsizken geçen süreyi buraya ekler. */
    fun addFallbackScreenSeconds(context: Context, seconds: Long) {
        if (seconds <= 0) return
        val total = fallbackScreenSeconds(context) + seconds
        prefs(context).edit {
            putString(KEY_SCREEN_DATE, LocalDate.now().toString())
            putLong(KEY_SCREEN_SECONDS, total)
        }
    }

    fun formatMinutes(minutes: Long): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0L -> "${m}dk"
            m == 0L -> "${h}s"
            else -> "${h}s ${m}dk"
        }
    }
}
