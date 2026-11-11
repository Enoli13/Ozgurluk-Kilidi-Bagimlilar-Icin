package com.ozgurluk.kilidi

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.telecom.TelecomManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast

/**
 * Kilit penceresi içindeyken ya da "Bağımlılar İçin" modunda günlük sınır dolduğunda, izin
 * verilmeyen bir uygulamanın penceresi açılırsa kullanıcıyı [MainActivity] (grid ekranı) üzerine
 * geri gönderir. "Bağımlılar İçin" modunda serbest olmayan uygulamalarda geçen süreyi de sayar.
 *
 * Tüm ayarlar [LockSettings] üzerinden (kalıcı) okunur; süreç öldürülse ya da telefon yeniden
 * başlasa bile kilit ayarları kaybolmaz.
 */
class AppBlockerService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastBounceAt = 0L

    /** Ekranda en son öne gelen "gerçek" uygulama (klavye, bildirim paneli vb. hariç). */
    private var foregroundPkg: String? = null

    /** Paket yükleyicide hangi ekranın açık olduğu (kurulum serbest, kaldırma engelli). */
    private var foregroundClass: String? = null

    /** Kullanım sayacı: son ölçüm anı ve henüz kaydedilmemiş milisaniyeler. */
    private var lastMeasureAt = 0L
    private var pendingUsageMs = 0L
    private var warnedDate: String? = null
    private var launcherPkgs: Set<String> = emptySet()

    /** Telefon süresi yedeği (kullanım erişimi yoksa) ve widget yenileme zamanlaması. */
    private var pendingScreenMs = 0L
    private var lastWidgetUpdateAt = 0L

    /** Ayarlar ekranı taramasının sıklık sınırı. */
    private var lastSettingsScanAt = 0L

    /** Yüzen pencere / PiP kalkanı ve tarama sıklık sınırı. */
    private var floatingGuard: FloatingGuard? = null
    private var lastFloatingScanAt = 0L

    /**
     * Pencere olayları yalnızca uygulama değişince gelir. Kilit saati başladığında yasaklı bir
     * uygulama zaten açıksa hiçbir olay gelmez; bu denetleyici o durumu da yakalar.
     */
    private val watchdog = object : Runnable {
        override fun run() {
            AddictGuard.expireIfDone(this@AppBlockerService)
            measureUsage()
            refreshWidgetIfDue()
            updateFloating(force = true)
            foregroundPkg?.let { pkg ->
                if (pkg in SETTINGS_PACKAGES && isUserLooking()) guardSettings(force = true)
                if (isUserLooking() && shouldBlock(pkg)) {
                    Log.d(TAG, "Engellendi (zaten açıktı): $pkg")
                    bringLockScreenToFront()
                }
            }
            handler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        launcherPkgs = queryLauncherPackages()
        floatingGuard = FloatingGuard(this)
        lastMeasureAt = SystemClock.elapsedRealtime()
        if (LockSettings.isAddictModeOn(this)) AddictGuard.harden(this)
        handler.removeCallbacks(watchdog)
        handler.postDelayed(watchdog, WATCHDOG_INTERVAL_MS)
    }

    override fun onDestroy() {
        flushUsage(force = true)
        floatingGuard?.clear()
        floatingGuard = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        // Yüzen pencere / PiP açıldı, taşındı ya da kapandı
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            updateFloating(force = false)
            return
        }
        val pkg = event.packageName?.toString() ?: return

        // Ayarlar içindeki gezinti (alt sayfalar) çoğu zaman yalnızca içerik değişimi olarak gelir.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (pkg in SETTINGS_PACKAGES && pkg == foregroundPkg) guardSettings(force = false)
            return
        }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val className = event.className?.toString()

        // Klavye, izin diyalogları, bildirim paneli gibi pencereler altta açık olan uygulamanın
        // "parçası" sayılır: ne engellenir ne de ön plandaki uygulama bilgisini değiştirir.
        if (isHelperWindow(pkg, className)) return

        // Uygulama değişmeden önce o ana kadarki süreyi eski uygulamaya yaz.
        measureUsage()
        foregroundPkg = pkg
        foregroundClass = className
        if (pkg in SETTINGS_PACKAGES) {
            guardSettings(force = true)
            return
        }
        if (shouldBlock(pkg)) {
            Log.d(TAG, "Engellendi: $pkg ($className)")
            if (pkg in INSTALLER_PACKAGES && !LockSettings.shouldLockNow(this)) {
                Toast.makeText(this, "Koruma sürerken uygulama kaldırılamaz.", Toast.LENGTH_SHORT).show()
            }
            bringLockScreenToFront()
        }
    }

    private fun shouldBlock(pkg: String): Boolean {
        if (pkg == packageName) return false // kendi ekranımız
        // Ayarlar her zaman açılabilir; içindeki tehlikeli ekranları guardSettings() engeller.
        if (pkg in SETTINGS_PACKAGES) return false
        // Paket yükleyici: güncelleme ve kurulum her zaman serbest (uygulamanın yeni sürümü
        // kurulabilsin); koruma sürerken (takvimli gün ya da Bağımlılar İçin) yalnızca kaldırma
        // ekranı açılamaz.
        if (pkg in INSTALLER_PACKAGES) {
            return LockSettings.isTamperProtected(this) && isUninstallScreen(foregroundClass)
        }
        if (!LockSettings.shouldLockNow(this)) return false
        return pkg !in LockSettings.allowedApps(this) // seçilen serbest uygulamalar
    }

    /**
     * Kilit sürerken serbest olmayan uygulamaların yüzen pencerelerini ve PiP videolarını kapatır.
     * Pencere olayları çok sık gelebildiği için en fazla 250 ms'de bir tarar; watchdog her iki
     * saniyede bir son durumu yeniden uygular.
     */
    private fun updateFloating(force: Boolean) {
        val guard = floatingGuard ?: return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastFloatingScanAt < FLOATING_SCAN_MIN_MS) {
            handler.removeCallbacks(floatingRetry)
            handler.postDelayed(floatingRetry, FLOATING_SCAN_MIN_MS)
            return
        }
        lastFloatingScanAt = now
        val locked = LockSettings.shouldLockNow(this) && isUserLooking()
        guard.update(locked, if (locked) LockSettings.limitExcludedPackages(this) else emptySet())
    }

    private val floatingRetry = Runnable { updateFloating(force = true) }

    private fun isUninstallScreen(className: String?): Boolean =
        className?.contains("uninstall", ignoreCase = true) == true

    // ---- Ayarlar: yalnızca kilidi kapatmaya yarayan ekranlar engellenir ----------------------

    /**
     * Ayarlar açıkken ekrandaki yazılara bakar. Koruma sürüyorsa ve ekran bu uygulamanın
     * Erişilebilirlik / uygulama bilgisi / izin sayfası, cihaz yöneticisi, tarih-saat, fabrika
     * ayarlarına sıfırlama ya da USB hata ayıklama ekranıysa bir önceki sayfaya geri döner.
     */
    private fun guardSettings(force: Boolean) {
        if (!LockSettings.isTamperProtected(this)) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastSettingsScanAt < SETTINGS_SCAN_MIN_MS) return
        lastSettingsScanAt = now

        val root = try { rootInActiveWindow } catch (e: Exception) { null } ?: return
        if (root.packageName?.toString() !in SETTINGS_PACKAGES) return
        val text = StringBuilder()
        collectText(root, text, intArrayOf(0))
        val screen = normalize(text.toString())
        val reason = dangerReason(screen) ?: return

        Log.d(TAG, "Ayarlar ekranı engellendi: $reason")
        performGlobalAction(GLOBAL_ACTION_BACK)
        if (now - lastBounceAt > 1500L) {
            lastBounceAt = now
            Toast.makeText(this, "Koruma sürerken bu ayar açılamaz ($reason).", Toast.LENGTH_SHORT).show()
        }
    }

    private fun collectText(node: AccessibilityNodeInfo?, out: StringBuilder, count: IntArray) {
        if (node == null || count[0] > MAX_SCAN_NODES) return
        count[0]++
        node.text?.let { out.append(it).append('\n') }
        node.contentDescription?.let { out.append(it).append('\n') }
        for (i in 0 until node.childCount) collectText(node.getChild(i), out, count)
    }

    private fun dangerReason(screen: String): String? {
        val myLabel = normalize(applicationInfo.loadLabel(packageManager).toString())
        val mentionsMe = screen.contains(myLabel) || screen.contains("bagimlilar icin") ||
            screen.contains(normalize("Bağımlılar İçin"))
        if (mentionsMe) {
            // Kullanım erişimi henüz verilmediyse o sayfa açık kalsın (telefon süresi için).
            val usagePage = USAGE_ACCESS_WORDS.any { screen.contains(it) }
            if (usagePage && !ActivityStore.hasUsageAccess(this)) return null
            if (DEVICE_ADMIN_WORDS.any { screen.contains(it) }) return "cihaz yöneticisi"
            if (APP_PAGE_WORDS.any { screen.contains(it) }) return "uygulama ayarları"
        }
        if (DATE_TIME_WORDS.any { screen.contains(it) }) return "tarih ve saat"
        if (RESET_WORDS.any { screen.contains(it) }) return "sıfırlama"
        if (DEBUG_WORDS.any { screen.contains(it) }) return "geliştirici seçenekleri"
        return null
    }

    // ---- Etkinlik paneli: widget ve telefon süresi yedeği --------------------------------------

    private fun refreshWidgetIfDue() {
        val now = SystemClock.elapsedRealtime()
        if (!isUserLooking() || now - lastWidgetUpdateAt < WIDGET_REFRESH_MS) return
        lastWidgetUpdateAt = now
        try {
            ActivityWidget.updateAll(this)
        } catch (e: Exception) {
            Log.w(TAG, "Widget güncellenemedi", e)
        }
    }

    // ---- Bağımlılar İçin: kullanım sayacı -------------------------------------------------------

    /** Son ölçümden bu yana geçen süreyi, sayılması gerekiyorsa sayaca ekler. */
    private fun measureUsage() {
        val now = SystemClock.elapsedRealtime()
        // Telefon uykudayken watchdog çalışmaz; aradaki uzun boşluk kullanım sayılmasın.
        val delta = (now - lastMeasureAt).coerceIn(0L, MAX_MEASURE_GAP_MS)
        lastMeasureAt = now
        val pkg = foregroundPkg
        val looking = isUserLooking()
        if (pkg != null && looking && countsTowardLimit(pkg)) {
            pendingUsageMs += delta
        }
        if (looking && pkg != null && pkg !in launcherPkgs) pendingScreenMs += delta
        if (pendingScreenMs >= FLUSH_EVERY_MS) {
            val s = pendingScreenMs / 1000L
            pendingScreenMs -= s * 1000L
            ActivityStore.addFallbackScreenSeconds(this, s)
        }
        flushUsage(force = false)
    }

    private fun flushUsage(force: Boolean) {
        if (pendingUsageMs < 1000L) return
        if (!force && pendingUsageMs < FLUSH_EVERY_MS &&
            LockSettings.remainingUsageSeconds(this) * 1000L > pendingUsageMs + FLUSH_EVERY_MS
        ) return
        val seconds = pendingUsageMs / 1000L
        pendingUsageMs -= seconds * 1000L
        LockSettings.addUsageSeconds(this, seconds)
        maybeWarn()
    }

    /** Sınıra 10 dakika kala bir kez uyarır. */
    private fun maybeWarn() {
        val today = java.time.LocalDate.now().toString()
        if (warnedDate == today) return
        val remaining = LockSettings.remainingUsageSeconds(this)
        if (remaining in 1..WARN_BEFORE_SECONDS) {
            warnedDate = today
            Toast.makeText(
                this,
                "Bugünkü 2 saatten ${(remaining + 59) / 60} dakika kaldı.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /** Bu uygulamada geçen süre günlük sınırdan düşer mi? */
    private fun countsTowardLimit(pkg: String): Boolean {
        if (!LockSettings.isAddictModeOn(this)) return false
        if (LockSettings.isInBreak(this)) return false // mola saatleri sayılmaz
        if (pkg == packageName) return false
        if (pkg in ALWAYS_ALLOWED || pkg in SETTINGS_PACKAGES || pkg in INSTALLER_PACKAGES) return false
        if (pkg in launcherPkgs) return false // ana ekran
        if (pkg in inputMethodPackages() || pkg == defaultDialerPackage()) return false
        return pkg !in LockSettings.allowedApps(this)
    }

    @Suppress("DEPRECATION")
    private fun queryLauncherPackages(): Set<String> = try {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }
            .toSet()
    } catch (e: Exception) {
        emptySet()
    }

    /** Ekran kapalıyken ya da telefon kilitliyken kilit ekranını açmaya çalışma. */
    private fun isUserLooking(): Boolean {
        val interactive = getSystemService(PowerManager::class.java)?.isInteractive ?: true
        val keyguardLocked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked ?: false
        return interactive && !keyguardLocked
    }

    /**
     * Serbest bir uygulamanın "parçası" sayılan pencereler. Bunlar yüzünden kilit ekranına
     * geri atılmamalı; aksi halde örneğin WhatsApp'ta klavye açılınca grid ekranı geri gelir.
     */
    private fun isHelperWindow(pkg: String, className: String?): Boolean {
        if (pkg in ALWAYS_ALLOWED) return true
        // Klavye (IME) penceresi: sınıf adı ya da yüklü klavye paketlerinden biri
        if (className != null && className.startsWith("android.inputmethodservice.")) return true
        if (pkg in inputMethodPackages()) return true
        // Gelen/giden arama ekranı
        return pkg == defaultDialerPackage()
    }

    private fun inputMethodPackages(): Set<String> = try {
        getSystemService(InputMethodManager::class.java)
            ?.enabledInputMethodList
            ?.map { it.packageName }
            ?.toSet()
            ?: emptySet()
    } catch (e: Exception) {
        emptySet()
    }

    private fun defaultDialerPackage(): String? = try {
        getSystemService(TelecomManager::class.java)?.defaultDialerPackage
    } catch (e: Exception) {
        null
    }

    private fun bringLockScreenToFront() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBounceAt < BOUNCE_DEBOUNCE_MS) return
        lastBounceAt = now

        val lockIntent = Intent(this, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        try {
            startActivity(lockIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Kilit ekranı açılamadı", e)
        }
    }

    override fun onInterrupt() {}

    companion object {
        const val TAG = "OzgurlukKilidi"
        const val BOUNCE_DEBOUNCE_MS = 400L
        const val WATCHDOG_INTERVAL_MS = 2_000L
        const val MAX_MEASURE_GAP_MS = 10_000L
        const val FLUSH_EVERY_MS = 15_000L
        const val WARN_BEFORE_SECONDS = 10 * 60L

        /** Türkçe/İngilizce büyük-küçük harf farklarını yok sayarak karşılaştırma için. */
        fun normalize(t: String): String =
            t.replace('İ', 'i').replace('I', 'i').lowercase(java.util.Locale.ROOT)
                .replace("i\u0307", "i").replace('ı', 'i')

        const val SETTINGS_SCAN_MIN_MS = 600L
        const val FLOATING_SCAN_MIN_MS = 250L
        const val MAX_SCAN_NODES = 600
        const val WIDGET_REFRESH_MS = 60_000L

        /** Ayarlar uygulamaları: açılabilir, yalnızca tehlikeli ekranları engellenir. */
        val SETTINGS_PACKAGES = setOf(
            "com.android.settings",
            "com.miui.securitycenter",
            "com.samsung.android.lool"
        )

        /** Uygulama kaldırma ekranları: koruma sürerken hiç açılamaz. */
        val INSTALLER_PACKAGES = setOf(
            "com.android.packageinstaller",
            "com.google.android.packageinstaller"
        )

        // Ekran yazılarında aranan ifadeler; karşılaştırma büyük-küçük harf ve ı/i farkını yok sayar.
        /** Bu uygulamanın adıyla birlikte görünürse: uygulama bilgisi, erişilebilirlik, izinler. */
        val APP_PAGE_WORDS = listOf(
            "kaldır", "uninstall", "durmaya zorla", "zorla durdur", "force stop",
            "devre dışı", "disable", "kullan", "use ", "kısayol", "shortcut",
            "izin", "permission", "verileri temizle", "depolama", "storage", "clear data",
            "usage access", "display over", "üstünde göster", "üzerinde göster"
        ).map { normalize(it) }
        val USAGE_ACCESS_WORDS = listOf("kullanım erişimi", "kullanım verilerine erişim", "usage access", "usage data access").map { normalize(it) }
        val DEVICE_ADMIN_WORDS = listOf(
            "cihaz yönetici", "device admin", "yönetici uygulama"
        ).map { normalize(it) }
        val DATE_TIME_WORDS = listOf(
            "otomatik tarih", "tarihi ayarla", "saati ayarla", "saati otomatik", "ağ tarafından sağlanan",
            "set time automatically", "automatic date", "set date", "use network-provided time"
        ).map { normalize(it) }
        val RESET_WORDS = listOf(
            "fabrika verilerine", "fabrika ayarlarına", "tüm verileri sil",
            "factory data reset", "factory reset", "erase all data"
        ).map { normalize(it) }
        val DEBUG_WORDS = listOf("usb hata ayıklama", "usb debugging", "kablosuz hata ayıklama", "wireless debugging").map { normalize(it) }

        /**
         * Kilit sırasında da çalışması gereken sistem yardımcıları.
         *
         * Bir ekranda hâlâ grid'e geri atılıyorsan: `adb logcat -s OzgurlukKilidi` çalıştır,
         * "Engellendi: <paket>" satırındaki paket adını buraya ekle.
         */
        val ALWAYS_ALLOWED = setOf(
            // Bildirim çubuğu, hızlı ayarlar, ses/güç menüsü, ekran görüntüsü
            "com.android.systemui",
            // Sistem diyalogları ve paylaşım menüsü
            "android",
            "com.android.intentresolver",
            // İzin pencereleri (kamera, mikrofon, konum, bildirim...)
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
            "com.lbe.security.miui",
            // Dosya / medya seçiciler (WhatsApp'ta belge, fotoğraf eklemek için)
            "com.google.android.documentsui",
            "com.android.documentsui",
            "com.android.providers.media.module",
            "com.google.android.providers.media.module",
            "com.sec.android.app.myfiles",
            // Google Play hizmetleri diyalogları (konum doğruluğu, hesap seçici vb.)
            "com.google.android.gms",
            // Arama ekranı: gelen aramalar kilit yüzünden engellenmemeli
            "com.android.incallui",
            "com.android.server.telecom",
            "com.android.phone",
            "com.google.android.dialer",
            "com.android.dialer",
            "com.samsung.android.incallui",
            "com.samsung.android.dialer",
            // Çalan alarm ekranı
            "com.google.android.deskclock",
            "com.android.deskclock",
            "com.sec.android.app.clockpackage"
        )
    }
}
