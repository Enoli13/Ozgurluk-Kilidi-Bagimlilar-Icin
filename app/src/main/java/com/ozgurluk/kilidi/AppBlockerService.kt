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

    /** Kullanım sayacı: son ölçüm anı ve henüz kaydedilmemiş milisaniyeler. */
    private var lastMeasureAt = 0L
    private var pendingUsageMs = 0L
    private var warnedDate: String? = null
    private var launcherPkgs: Set<String> = emptySet()

    /**
     * Pencere olayları yalnızca uygulama değişince gelir. Kilit saati başladığında yasaklı bir
     * uygulama zaten açıksa hiçbir olay gelmez; bu denetleyici o durumu da yakalar.
     */
    private val watchdog = object : Runnable {
        override fun run() {
            AddictGuard.expireIfDone(this@AppBlockerService)
            measureUsage()
            foregroundPkg?.let { pkg ->
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
        lastMeasureAt = SystemClock.elapsedRealtime()
        if (LockSettings.isAddictModeOn(this)) AddictGuard.harden(this)
        handler.removeCallbacks(watchdog)
        handler.postDelayed(watchdog, WATCHDOG_INTERVAL_MS)
    }

    override fun onDestroy() {
        flushUsage(force = true)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val className = event.className?.toString()

        // Klavye, izin diyalogları, bildirim paneli gibi pencereler altta açık olan uygulamanın
        // "parçası" sayılır: ne engellenir ne de ön plandaki uygulama bilgisini değiştirir.
        if (isHelperWindow(pkg, className)) return

        // Uygulama değişmeden önce o ana kadarki süreyi eski uygulamaya yaz.
        measureUsage()
        foregroundPkg = pkg
        if (shouldBlock(pkg)) {
            Log.d(TAG, "Engellendi: $pkg ($className)")
            if (pkg in TAMPER_PACKAGES && !LockSettings.shouldLockNow(this)) {
                val msg = if (LockSettings.isAddictModeOn(this)) {
                    "\"Bağımlılar İçin\" modu sürerken Ayarlar açılamaz."
                } else {
                    "Kilit kapatılamaz günlerde Ayarlar açılamaz."
                }
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
            bringLockScreenToFront()
        }
    }

    private fun shouldBlock(pkg: String): Boolean {
        if (pkg == packageName) return false // kendi ekranımız
        // Takvimle kapatılamaz günlerde ve "Bağımlılar İçin" modu boyunca kilit saatleri dışında
        // da Ayarlar ve uygulama kaldırma ekranı açılamaz; aksi halde Erişilebilirlik servisi ya
        // da cihaz yöneticiliği kapatılarak kilit aşılabilirdi.
        if (pkg in TAMPER_PACKAGES && LockSettings.isTamperProtected(this)) return true
        if (!LockSettings.shouldLockNow(this)) return false
        return pkg !in LockSettings.allowedApps(this) // seçilen serbest uygulamalar
    }

    // ---- Bağımlılar İçin: kullanım sayacı -------------------------------------------------------

    /** Son ölçümden bu yana geçen süreyi, sayılması gerekiyorsa sayaca ekler. */
    private fun measureUsage() {
        val now = SystemClock.elapsedRealtime()
        // Telefon uykudayken watchdog çalışmaz; aradaki uzun boşluk kullanım sayılmasın.
        val delta = (now - lastMeasureAt).coerceIn(0L, MAX_MEASURE_GAP_MS)
        lastMeasureAt = now
        val pkg = foregroundPkg
        if (pkg != null && isUserLooking() && countsTowardLimit(pkg)) {
            pendingUsageMs += delta
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
        if (pkg in ALWAYS_ALLOWED || pkg in TAMPER_PACKAGES) return false
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

    private companion object {
        const val TAG = "OzgurlukKilidi"
        const val BOUNCE_DEBOUNCE_MS = 400L
        const val WATCHDOG_INTERVAL_MS = 2_000L
        const val MAX_MEASURE_GAP_MS = 10_000L
        const val FLUSH_EVERY_MS = 15_000L
        const val WARN_BEFORE_SECONDS = 10 * 60L

        /** Kilidi kapatmaya / uygulamayı kaldırmaya yarayan ekranlar. */
        val TAMPER_PACKAGES = setOf(
            "com.android.settings",
            "com.miui.securitycenter",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller"
        )

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
