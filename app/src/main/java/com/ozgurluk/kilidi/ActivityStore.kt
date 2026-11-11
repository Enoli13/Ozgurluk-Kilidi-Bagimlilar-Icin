package com.ozgurluk.kilidi

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Process
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/**
 * Günlük etkinlik paneli (kitap, telefon, ders, gitar, spor ve kullanıcının eklediği diğerleri).
 *
 * - "Telefon" çubuğu sistemden otomatik gelir ve elle değiştirilemez.
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
            // Telefon çubuğu her zaman bulunur.
            if (list.none { it.isPhone }) DEFAULTS.filter { it.isPhone } + list else list
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

    fun deleteCategory(context: Context, id: String) {
        if (id == PHONE_ID) return
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

    fun setMinutes(context: Context, id: String, minutes: Long) {
        if (id == PHONE_ID) return // telefon süresi sistemden gelir
        val values = values(context)
        values.put(id, minutes.coerceIn(0L, 24 * 60L))
        saveValues(context, values)
    }

    fun minutes(context: Context, id: String): Long =
        if (id == PHONE_ID) phoneMinutesToday(context) else values(context).optLong(id, 0L)

    fun entries(context: Context): List<Entry> {
        val values = values(context)
        val phone = phoneMinutesToday(context)
        return categories(context).map {
            Entry(it, if (it.isPhone) phone else values.optLong(it.id, 0L))
        }
    }

    // ---- Telefon süresi (sistemden) ---------------------------------------------------------

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
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
        (if (hasUsageAccess(context)) systemScreenSeconds(context) else null)
            ?.div(60)
            ?: (fallbackScreenSeconds(context) / 60)

    @Suppress("DEPRECATION")
    private fun systemScreenSeconds(context: Context): Long? = try {
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        val launchers = launcherPackages(context)
        val events = usm.queryEvents(start, now)
        val event = UsageEvents.Event()
        var current: String? = null
        var currentStart = 0L
        var total = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    if (current != null) total += event.timeStamp - currentStart
                    if (pkg in launchers) {
                        current = null
                    } else {
                        current = pkg
                        currentStart = event.timeStamp
                    }
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> if (pkg == current) {
                    total += event.timeStamp - currentStart
                    current = null
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.KEYGUARD_SHOWN -> {
                    if (current != null) total += event.timeStamp - currentStart
                    current = null
                }
            }
        }
        if (current != null) total += now - currentStart
        total / 1000
    } catch (e: Exception) {
        null
    }

    @Suppress("DEPRECATION")
    private fun launcherPackages(context: Context): Set<String> = try {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }
            .toSet()
    } catch (e: Exception) {
        emptySet()
    }

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
