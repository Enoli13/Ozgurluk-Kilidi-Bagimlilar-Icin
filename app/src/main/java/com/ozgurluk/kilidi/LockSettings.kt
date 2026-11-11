package com.ozgurluk.kilidi

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Calendar

/**
 * Tüm kilit ayarları burada kalıcı olarak (SharedPreferences) saklanır.
 * Eskiden bunlar `companion object` içindeki static değişkenlerdi; uygulama süreci
 * Android tarafından sonlandırıldığında (çok sık olur) kilit ve saatler kayboluyordu.
 */
object LockSettings {

    const val NOT_SET = -1

    /** Takvim bağlılığı türleri. */
    const val COMMIT_NONE = 0
    const val COMMIT_UNTIL = 1 // "Şu güne kadar"
    const val COMMIT_DATES = 2 // "Şu günlerde"

    private const val PREFS_NAME = "OzgurlukKilidiPrefs"
    private const val KEY_ACTIVE = "scheduler_active"
    private const val KEY_START = "start_minutes"
    private const val KEY_END = "end_minutes"
    private const val KEY_DAYS = "selected_days"
    private const val KEY_APPS = "whitelist_apps"
    private const val KEY_COMMIT_MODE = "commit_mode"
    private const val KEY_COMMIT_UNTIL = "commit_until"
    private const val KEY_COMMIT_DATES = "commit_dates"

    /**
     * Serbest listeye eklenemeyen uygulamalar: sosyal medya (WhatsApp hariç) ve kilidi kapatmaya
     * yarayan sistem Ayarları.
     */
    val NOT_WHITELISTABLE = setOf(
        // Instagram / Threads
        "com.instagram.android", "com.instagram.lite", "com.instagram.barcelona",
        // Facebook / Messenger
        "com.facebook.katana", "com.facebook.lite", "com.facebook.orca", "com.facebook.mlite",
        // TikTok
        "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.zhiliaoapp.musically.go",
        "com.ss.android.ugc.tiktok.lite",
        // X (Twitter)
        "com.twitter.android", "com.twitter.android.lite",
        // Snapchat
        "com.snapchat.android",
        // YouTube
        "com.google.android.youtube",
        // Reddit, Pinterest, Tumblr, LinkedIn
        "com.reddit.frontpage", "com.pinterest", "com.tumblr", "com.linkedin.android",
        // Telegram, Discord
        "org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram",
        "com.discord",
        // Twitch, Bluesky, Mastodon, BeReal, VK, Likee, Lemon8
        "tv.twitch.android.app", "xyz.blueskyweb.app", "org.joinmastodon.android",
        "com.bereal.ft", "com.vkontakte.android", "video.like", "com.bd.nproject",
        // Ayarlar: serbest olursa Erişilebilirlik servisi kapatılıp kilit aşılabilir
        "com.android.settings"
    )

    fun isWhitelistable(pkg: String): Boolean = pkg !in NOT_WHITELISTABLE

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ---- Ayarlar ----------------------------------------------------------------------------

    fun isActive(context: Context): Boolean = prefs(context).getBoolean(KEY_ACTIVE, false)

    fun setActive(context: Context, active: Boolean) =
        prefs(context).edit { putBoolean(KEY_ACTIVE, active) }

    /** Gece yarısından itibaren dakika (0..1439) ya da [NOT_SET]. */
    fun startMinutes(context: Context): Int = prefs(context).getInt(KEY_START, NOT_SET)

    fun endMinutes(context: Context): Int = prefs(context).getInt(KEY_END, NOT_SET)

    fun setStartMinutes(context: Context, minutes: Int) =
        prefs(context).edit { putInt(KEY_START, minutes) }

    fun setEndMinutes(context: Context, minutes: Int) =
        prefs(context).edit { putInt(KEY_END, minutes) }

    /** Calendar.MONDAY vb. değerleri. Kurulum için en az bir gün seçilmesi zorunludur. */
    fun days(context: Context): Set<Int> =
        prefs(context).getStringSet(KEY_DAYS, emptySet())
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet()
            ?: emptySet()

    fun setDays(context: Context, days: Set<Int>) =
        prefs(context).edit { putStringSet(KEY_DAYS, days.map { it.toString() }.toSet()) }

    /** Serbest uygulamalar. Eski sürümlerde seçilmiş sosyal medya uygulamaları elenir. */
    fun allowedApps(context: Context): Set<String> =
        (prefs(context).getStringSet(KEY_APPS, emptySet()) ?: emptySet())
            .filter { isWhitelistable(it) }
            .toSet()

    fun setAllowedApps(context: Context, apps: Set<String>) {
        prefs(context).edit { putStringSet(KEY_APPS, apps.filter { isWhitelistable(it) }.toSet()) }
        invalidateUsage()
    }

    /** Eski sürümlerin şifre verilerini cihazdan siler. */
    fun clearLegacyPasswordData(context: Context) = prefs(context).edit {
        remove("password_hash")
        remove("password_salt")
        remove("failed_attempts")
        remove("lockout_until")
    }

    // ---- Takvim bağlılığı -------------------------------------------------------------------

    fun commitMode(context: Context): Int = prefs(context).getInt(KEY_COMMIT_MODE, COMMIT_NONE)

    fun setCommitMode(context: Context, mode: Int) =
        prefs(context).edit { putInt(KEY_COMMIT_MODE, mode) }

    /** "Şu güne kadar" tarihi (o gün dahil). */
    fun commitUntil(context: Context): LocalDate? =
        prefs(context).getString(KEY_COMMIT_UNTIL, null)?.let { parseDate(it) }

    fun setCommitUntil(context: Context, date: LocalDate?) =
        prefs(context).edit { putString(KEY_COMMIT_UNTIL, date?.toString()) }

    /** "Şu günlerde" seçilen tarihler. */
    fun commitDates(context: Context): Set<LocalDate> =
        (prefs(context).getStringSet(KEY_COMMIT_DATES, emptySet()) ?: emptySet())
            .mapNotNull { parseDate(it) }
            .toSet()

    fun setCommitDates(context: Context, dates: Set<LocalDate>) =
        prefs(context).edit { putStringSet(KEY_COMMIT_DATES, dates.map { it.toString() }.toSet()) }

    /**
     * Zamanlayıcı şu an kapatılamaz durumda mı? Kurulu zamanlayıcı, "şu güne kadar" seçildiyse
     * o tarih bitene kadar, "şu günlerde" seçildiyse o tarihlerin her birinde durdurulamaz.
     */
    fun isCommittedNow(context: Context, today: LocalDate = LocalDate.now()): Boolean =
        when (commitMode(context)) {
            COMMIT_UNTIL -> commitUntil(context)?.let { !today.isAfter(it) } ?: false
            COMMIT_DATES -> today in commitDates(context)
            else -> false
        }

    private fun parseDate(text: String): LocalDate? = try {
        LocalDate.parse(text)
    } catch (e: DateTimeParseException) {
        null
    }

    // ---- Zaman hesapları --------------------------------------------------------------------

    /** Şu an kilit penceresinin (başlangıç–bitiş, seçili günler) içinde miyiz? */
    fun isInLockWindow(context: Context, now: Calendar = Calendar.getInstance()): Boolean {
        val start = startMinutes(context)
        val end = endMinutes(context)
        if (start == NOT_SET || end == NOT_SET || start == end) return false

        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val today = now.get(Calendar.DAY_OF_WEEK)

        // Kilidin hangi güne "ait" olduğu: gece yarısını aşan pencerelerde (örn. 22:00–02:00)
        // gece yarısından sonraki kısım bir önceki günün oturumudur.
        val ownerDay: Int = if (start < end) {
            if (nowMinutes in start until end) today else return false
        } else {
            when {
                nowMinutes >= start -> today
                nowMinutes < end -> (today + 5) % 7 + 1 // dünün gün numarası
                else -> return false
            }
        }

        // Eski sürümlerden kalan "gün seçilmedi = her gün" kurulumları çalışmaya devam etsin.
        val days = days(context)
        return days.isEmpty() || ownerDay in days
    }

    /** Bir kilit oturumunun toplam uzunluğu (saniye). */
    fun windowLengthSeconds(context: Context): Long {
        val start = startMinutes(context)
        val end = endMinutes(context)
        if (start == NOT_SET || end == NOT_SET || start == end) return 0L
        return ((end - start + 1440) % 1440) * 60L
    }

    /** Kilit penceresi içindeyken bitişe kalan saniye. */
    fun secondsUntilEnd(context: Context, now: Calendar = Calendar.getInstance()): Long {
        val end = endMinutes(context)
        val target = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, end / 60)
            set(Calendar.MINUTE, end % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!target.after(now)) target.add(Calendar.DAY_OF_YEAR, 1)
        return (target.timeInMillis - now.timeInMillis) / 1000
    }

    /** Kilit penceresi dışındayken bir sonraki başlangıca kalan saniye (yoksa null). */
    fun secondsUntilNextStart(context: Context, now: Calendar = Calendar.getInstance()): Long? {
        val start = startMinutes(context)
        if (start == NOT_SET) return null
        val days = days(context)

        for (offset in 0..7) {
            val candidate = (now.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, start / 60)
                set(Calendar.MINUTE, start % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val dayMatches = days.isEmpty() || candidate.get(Calendar.DAY_OF_WEEK) in days
            if (candidate.after(now) && dayMatches) {
                return (candidate.timeInMillis - now.timeInMillis) / 1000
            }
        }
        return null
    }

    // ---- Bağımlılar İçin ----------------------------------------------------------------------
    //
    // Seçilen tarih bitene kadar çalışır, kapatılamaz ve (cihaz yöneticisi sayesinde) silinemez.
    // Serbest listedeki uygulamalar sayılmaz. Geri kalan uygulamaların o günkü toplam kullanımı
    // [DAILY_LIMIT_SECONDS]'ı geçince telefon, mola saat aralığı dışında kilitlenir (yalnızca
    // serbest uygulamalar, arama ve alarm açık kalır). Sayaç her gece yarısı sıfırlanır.
    // Mola saatlerinde her uygulama serbesttir ve bu süre sayaca eklenmez.

    /** Serbest olmayan uygulamalar için günlük toplam süre: 2 saat. */
    const val DAILY_LIMIT_SECONDS = 2 * 60 * 60L

    private const val KEY_ADDICT_ACTIVE = "addict_active"
    private const val KEY_ADDICT_UNTIL = "addict_until"
    private const val KEY_BREAK_START = "break_start_minutes"
    private const val KEY_BREAK_END = "break_end_minutes"
    private const val KEY_USAGE_DATE = "usage_date"
    private const val KEY_USAGE_SECONDS = "usage_seconds"

    fun isAddictArmed(context: Context): Boolean = prefs(context).getBoolean(KEY_ADDICT_ACTIVE, false)

    fun setAddictArmed(context: Context, armed: Boolean) =
        prefs(context).edit { putBoolean(KEY_ADDICT_ACTIVE, armed) }

    /** Modun sona ereceği tarih (o gün dahil). */
    fun addictUntil(context: Context): LocalDate? =
        prefs(context).getString(KEY_ADDICT_UNTIL, null)?.let { parseDate(it) }

    fun setAddictUntil(context: Context, date: LocalDate?) =
        prefs(context).edit { putString(KEY_ADDICT_UNTIL, date?.toString()) }

    /** "Bağımlılar İçin" modu şu an yürürlükte mi? (kurulmuş ve tarih henüz bitmemiş) */
    fun isAddictModeOn(context: Context, today: LocalDate = LocalDate.now()): Boolean {
        if (!isAddictArmed(context)) return false
        val until = addictUntil(context) ?: return false
        return !today.isAfter(until)
    }

    /** Mod kurulu ama tarihi geçmiş: kendini kapatıp cihaz yöneticisini bırakması gerekir. */
    fun isAddictModeExpired(context: Context, today: LocalDate = LocalDate.now()): Boolean =
        isAddictArmed(context) && !isAddictModeOn(context, today)

    fun breakStartMinutes(context: Context): Int = prefs(context).getInt(KEY_BREAK_START, NOT_SET)

    fun breakEndMinutes(context: Context): Int = prefs(context).getInt(KEY_BREAK_END, NOT_SET)

    fun setBreakStartMinutes(context: Context, minutes: Int) {
        prefs(context).edit { putInt(KEY_BREAK_START, minutes) }
        invalidateUsage()
    }

    fun setBreakEndMinutes(context: Context, minutes: Int) {
        prefs(context).edit { putInt(KEY_BREAK_END, minutes) }
        invalidateUsage()
    }

    /** Şu an mola saat aralığında mıyız? (her gün geçerli, gece yarısını aşabilir) */
    fun isInBreak(context: Context, now: Calendar = Calendar.getInstance()): Boolean {
        val start = breakStartMinutes(context)
        val end = breakEndMinutes(context)
        if (start == NOT_SET || end == NOT_SET || start == end) return false
        val m = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return if (start < end) m in start until end else (m >= start || m < end)
    }

    /**
     * Bugün serbest olmayan uygulamalarda geçen süre (saniye), mola saatleri hariç.
     *
     * Kullanım erişimi verildiyse Android'in kendi kayıtlarından (Dijital Denge ile aynı kaynak)
     * hesaplanır; böylece uygulama açılmadan / kurulmadan önceki kullanım da sayılır. Erişim
     * yoksa Erişilebilirlik servisinin kendi ölçümüne düşer.
     */
    fun usageSecondsToday(context: Context, today: LocalDate = LocalDate.now()): Long {
        if (today != LocalDate.now()) return fallbackUsageSeconds(context, today)
        val latched = if (isLimitReachedToday(context)) DAILY_LIMIT_SECONDS else 0L
        if (!ActivityStore.hasUsageAccess(context)) {
            return maxOf(latched, fallbackUsageSeconds(context, today))
        }
        // Kilit kararı bekletilmesin: son hesaplanan değer hemen döner, sistem kayıtları arka
        // planda okunur.
        val now = android.os.SystemClock.elapsedRealtime()
        val fresh = usageCacheDate == today && now - usageCacheAt < USAGE_CACHE_MS
        if (!fresh) refreshUsageAsync(context.applicationContext)
        val value = if (usageCacheDate == today) usageCache else fallbackUsageSeconds(context, today)
        return maxOf(latched, value)
    }

    private fun refreshUsageAsync(app: Context) {
        if (usageRefreshing) return
        usageRefreshing = true
        usageWorker.execute {
            try {
                val today = LocalDate.now()
                SystemUsage.limitedSecondsToday(app, limitExcludedPackages(app))?.let {
                    usageCache = it
                    usageCacheDate = today
                    usageCacheAt = android.os.SystemClock.elapsedRealtime()
                    if (it >= DAILY_LIMIT_SECONDS) markLimitReachedToday(app)
                }
            } finally {
                usageRefreshing = false
            }
        }
    }

    // Servis bu değeri 2 saniyede bir sorar; sistem kayıtları arka planda birkaç saniyede bir okunur.
    private const val USAGE_CACHE_MS = 4_000L
    private val usageWorker = java.util.concurrent.Executors.newSingleThreadExecutor()
    @Volatile private var usageRefreshing = false
    @Volatile private var usageCache = 0L
    @Volatile private var usageCacheAt = 0L
    @Volatile private var usageCacheDate: LocalDate? = null

    // Sınır o gün bir kez dolduysa bu kaydedilir; kilit kararı artık hesap beklemez ve
    // uygulama yeniden başlasa da kilit hemen geçerli olur.
    private const val KEY_LIMIT_REACHED_DATE = "limit_reached_date"

    fun isLimitReachedToday(context: Context): Boolean =
        prefs(context).getString(KEY_LIMIT_REACHED_DATE, null) == LocalDate.now().toString()

    private fun markLimitReachedToday(context: Context) {
        if (!isLimitReachedToday(context)) {
            prefs(context).edit { putString(KEY_LIMIT_REACHED_DATE, LocalDate.now().toString()) }
        }
    }

    /** Ayar değişince (serbest liste, mola) sayaç hemen yeniden hesaplansın. */
    fun invalidateUsage() {
        usageCacheAt = 0L
        excludedCacheAt = 0L
        SystemUsage.invalidate()
    }

    /** 2 saat sayacına hiç girmeyen paketler: serbest liste, ana ekran, sistem ekranları. */
    fun limitExcludedPackages(context: Context): Set<String> {
        val now = android.os.SystemClock.elapsedRealtime()
        excludedCache?.let { if (now - excludedCacheAt < EXCLUDED_CACHE_MS) return it }
        val set = HashSet<String>()
        set += allowedApps(context)
        set += context.packageName
        set += SystemUsage.launcherPackages(context)
        set += AppBlockerService.ALWAYS_ALLOWED
        set += AppBlockerService.SETTINGS_PACKAGES
        set += AppBlockerService.INSTALLER_PACKAGES
        try {
            context.getSystemService(android.telecom.TelecomManager::class.java)
                ?.defaultDialerPackage?.let { set += it }
            context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                ?.enabledInputMethodList?.forEach { set += it.packageName }
        } catch (e: Exception) {
            // izin/servis yoksa yalnızca yukarıdakiler hariç tutulur
        }
        excludedCache = set
        excludedCacheAt = now
        return set
    }

    private const val EXCLUDED_CACHE_MS = 30_000L
    @Volatile private var excludedCache: Set<String>? = null
    @Volatile private var excludedCacheAt = 0L

    /** Erişilebilirlik servisinin kendi ölçtüğü süre (kullanım erişimi yoksa yedek). */
    private fun fallbackUsageSeconds(context: Context, today: LocalDate): Long {
        val p = prefs(context)
        return if (p.getString(KEY_USAGE_DATE, null) == today.toString()) {
            p.getLong(KEY_USAGE_SECONDS, 0L)
        } else 0L
    }

    /** Sayaca süre ekler; gün değiştiyse sayaç önce sıfırlanır. */
    fun addUsageSeconds(context: Context, seconds: Long, today: LocalDate = LocalDate.now()) {
        if (seconds <= 0) return
        val total = fallbackUsageSeconds(context, today) + seconds
        prefs(context).edit {
            putString(KEY_USAGE_DATE, today.toString())
            putLong(KEY_USAGE_SECONDS, total)
        }
        if (total >= DAILY_LIMIT_SECONDS && !ActivityStore.hasUsageAccess(context)) {
            markLimitReachedToday(context)
        }
    }

    fun remainingUsageSeconds(context: Context): Long =
        (DAILY_LIMIT_SECONDS - usageSecondsToday(context)).coerceAtLeast(0L)

    /** Günlük sınır doldu ve mola saatinde değiliz: telefon kilitli olmalı. */
    fun isAddictLocked(context: Context): Boolean =
        isAddictModeOn(context) &&
            !isInBreak(context) &&
            (isLimitReachedToday(context) || usageSecondsToday(context) >= DAILY_LIMIT_SECONDS)

    /** Zamanlayıcı penceresi ya da günlük sınır yüzünden şu an kilit ekranı mı gösterilmeli? */
    fun shouldLockNow(context: Context): Boolean =
        (isActive(context) && isInLockWindow(context)) || isAddictLocked(context)

    /** Ayarlar ve kaldırma ekranı açılamaz mı? (takvimli gün ya da Bağımlılar İçin modu) */
    fun isTamperProtected(context: Context): Boolean =
        (isActive(context) && isCommittedNow(context)) || isAddictModeOn(context)

    /**
     * Günlük sınır kilidinin kalkmasına kalan saniye: bir sonraki mola başlangıcı ya da sayacın
     * sıfırlandığı gece yarısı (hangisi önce gelirse).
     */
    fun secondsUntilAddictUnlock(context: Context, now: Calendar = Calendar.getInstance()): Long {
        val midnight = (now.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        var best = (midnight.timeInMillis - now.timeInMillis) / 1000
        val start = breakStartMinutes(context)
        if (start != NOT_SET) {
            val breakAt = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, start / 60)
                set(Calendar.MINUTE, start % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (!breakAt.after(now)) breakAt.add(Calendar.DAY_OF_YEAR, 1)
            best = minOf(best, (breakAt.timeInMillis - now.timeInMillis) / 1000)
        }
        return best
    }
}
