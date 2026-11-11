package com.ozgurluk.kilidi

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import java.time.LocalDate
import java.time.ZoneId

/**
 * Android'in kendi kullanım kayıtlarından (Dijital Denge'nin de kullandığı UsageStats olayları)
 * bugünkü ekran sürelerini hesaplar. Uygulama kurulmadan / açılmadan önceki kullanım da dahildir.
 *
 * Hesap: her uygulamanın öne geldiği (RESUMED) ve arka plana gittiği (PAUSED/STOPPED) anlardan
 * kullanım aralıkları çıkarılır; ekran kapanınca ya da kilit ekranı gelince açık aralıklar kapanır.
 * Aynı anda birden fazla uygulama görünürse (bölünmüş ekran) süre iki kez sayılmaz.
 */
object SystemUsage {

    private data class Interval(val pkg: String, val start: Long, val end: Long)

    private const val CACHE_MS = 5_000L

    // Artımlı okuma durumu: her yenilemede yalnızca son okumadan bu yana gelen olaylar okunur.
    // (Önceden her seferinde günün tüm kayıtları baştan okunuyordu; akşam saatlerinde bu
    // saniyeler sürüyor ve kilidin geç devreye girmesine yol açıyordu.)
    private var stateDate: LocalDate? = null
    private var lastEnd = 0L
    private val resumed = HashMap<String, MutableSet<String>>() // paket -> öndeki etkinlikler
    private val openSince = HashMap<String, Long>()
    private val closed = ArrayList<Interval>()

    private var cacheAt = 0L
    private var cache: List<Interval> = emptyList()

    /** Önbelleği temizler (örn. mola saatleri değişince). */
    @Synchronized
    fun invalidate() {
        cacheAt = 0L
    }

    @Synchronized
    private fun intervals(context: Context): List<Interval>? {
        val nowElapsed = SystemClock.elapsedRealtime()
        if (stateDate == LocalDate.now() && cacheAt != 0L && nowElapsed - cacheAt < CACHE_MS) return cache
        if (!refresh(context)) return null
        val now = System.currentTimeMillis()
        val snapshot = ArrayList<Interval>(closed.size + openSince.size)
        snapshot.addAll(closed)
        openSince.forEach { (pkg, s) -> if (now > s) snapshot += Interval(pkg, s, now) }
        cache = snapshot
        cacheAt = nowElapsed
        return snapshot
    }

    /** Son okumadan bu yana gelen kullanım olaylarını işler. */
    @Suppress("DEPRECATION")
    private fun refresh(context: Context): Boolean = try {
        val today = LocalDate.now()
        if (stateDate != today) {
            stateDate = today
            lastEnd = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            resumed.clear(); openSince.clear(); closed.clear()
        }
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        if (now > lastEnd) {
            val events = usm.queryEvents(lastEnd, now)
            val event = UsageEvents.Event()

            fun close(pkg: String, at: Long) {
                val s = openSince.remove(pkg) ?: return
                if (at > s) closed += Interval(pkg, s, at)
            }

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                val cls = event.className ?: ""
                val t = event.timeStamp
                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        resumed.getOrPut(pkg) { HashSet() }.add(cls)
                        if (pkg !in openSince) openSince[pkg] = t
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                        val set = resumed[pkg]
                        set?.remove(cls)
                        if (set.isNullOrEmpty()) close(pkg, t)
                    }
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.KEYGUARD_SHOWN,
                    UsageEvents.Event.DEVICE_SHUTDOWN -> {
                        openSince.keys.toList().forEach { close(it, t) }
                        resumed.clear()
                    }
                }
            }
            lastEnd = now
        }
        true
    } catch (e: Exception) {
        false
    }

    /** Aralıkların birleşimi (çakışan süreler bir kez sayılır), [cut] aralıkları çıkarılarak. */
    private fun unionMillis(list: List<Interval>, cut: List<LongRange>): Long {
        if (list.isEmpty()) return 0L
        val sorted = list.sortedBy { it.start }
        var total = 0L
        var curS = sorted[0].start
        var curE = sorted[0].end
        fun add(s: Long, e: Long) {
            var len = e - s
            cut.forEach { c ->
                val os = maxOf(s, c.first)
                val oe = minOf(e, c.last)
                if (oe > os) len -= (oe - os)
            }
            total += len.coerceAtLeast(0L)
        }
        for (i in 1 until sorted.size) {
            val iv = sorted[i]
            if (iv.start <= curE) {
                curE = maxOf(curE, iv.end)
            } else {
                add(curS, curE)
                curS = iv.start; curE = iv.end
            }
        }
        add(curS, curE)
        return total
    }

    /** Bugün ekranda geçen toplam süre (saniye); ana ekran hariç. Erişim yoksa null. */
    fun screenSecondsToday(context: Context): Long? {
        if (!ActivityStore.hasUsageAccess(context)) return null
        val list = intervals(context) ?: return null
        val launchers = launcherPackages(context)
        return unionMillis(list.filter { it.pkg !in launchers }, emptyList()) / 1000
    }

    /**
     * "Bağımlılar İçin" sayacı: serbest listede olmayan uygulamaların bugünkü toplam süresi
     * (saniye), mola saat aralığında geçen süre hariç. Erişim yoksa null.
     */
    fun limitedSecondsToday(context: Context, excluded: Set<String>): Long? {
        if (!ActivityStore.hasUsageAccess(context)) return null
        val list = intervals(context) ?: return null
        val counted = list.filter { it.pkg !in excluded }
        return unionMillis(counted, breakRangesToday(context)) / 1000
    }

    /** Bugünün mola aralıkları (epoch ms). Gece yarısını aşan mola iki parçaya bölünür. */
    private fun breakRangesToday(context: Context): List<LongRange> {
        val start = LockSettings.breakStartMinutes(context)
        val end = LockSettings.breakEndMinutes(context)
        if (start == LockSettings.NOT_SET || end == LockSettings.NOT_SET || start == end) return emptyList()
        val day0 = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val min = 60_000L
        val dayEnd = day0 + 24 * 60 * min
        return if (start < end) {
            listOf(day0 + start * min..day0 + end * min)
        } else {
            listOf(day0..day0 + end * min, day0 + start * min..dayEnd)
        }
    }

    @Suppress("DEPRECATION")
    fun launcherPackages(context: Context): Set<String> = try {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }
            .toSet()
    } catch (e: Exception) {
        emptySet()
    }
}
