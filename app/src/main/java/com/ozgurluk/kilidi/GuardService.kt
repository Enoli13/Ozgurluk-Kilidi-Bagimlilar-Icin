package com.ozgurluk.kilidi

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Kilidin yedek bekçisi (kalıcı bildirimli ön plan servisi).
 *
 * Neden var: HyperOS / MIUI, uygulama son uygulamalardan kaydırılıp kapatılınca süreci öldürür ve
 * "Otomatik başlatma" izni yoksa Erişilebilirlik servisinin yeniden bağlanmasına izin vermez.
 * Bu durumda servis ayarlarda "açık" görünür ama hiçbir şey kilitlenmez; uygulama elle açılınca
 * tekrar çalışır. Bu servis:
 *
 * 1. Ön planda (bildirimle) çalışarak sürecin öldürülmesini zorlaştırır; öldürülürse START_STICKY
 *    ve [onTaskRemoved] alarmı ile kendini yeniden başlatır.
 * 2. Erişilebilirlik servisi çalışmıyorsa ([AppBlockerService.isAlive] false), öndeki uygulamayı
 *    Android'in kullanım kayıtlarından bulur ve kilit gerekiyorsa kilit ekranını kendisi açar.
 */
class GuardService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    /** Kullanım kayıtlarının en son okunduğu an ve o ana kadar öndeki uygulama. */
    private var eventsReadUntil = 0L
    private var topPkg: String? = null
    private var lastBounceAt = 0L

    private val loop = object : Runnable {
        override fun run() {
            try {
                AddictGuard.expireIfDone(this@GuardService)
                if (!isNeeded(this@GuardService)) {
                    stopSelf()
                    return
                }
                enforce()
            } catch (e: Exception) {
                Log.w(TAG, "Bekçi döngüsünde hata", e)
            } finally {
                if (isNeeded(this@GuardService)) handler.postDelayed(this, LOOP_MS)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Ön plan servisi başlatılamadı", e)
        }
        handler.removeCallbacks(loop)
        handler.post(loop)
        return START_STICKY
    }

    /** Son uygulamalardan kaydırıldı: süreç öldürülebilir, kısa süre sonra yeniden başla. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        scheduleRestart(this)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (isNeeded(this)) scheduleRestart(this)
        super.onDestroy()
    }

    // ---- Yedek kilit ----------------------------------------------------------------------------

    private fun enforce() {
        val top = readTopPackage() // erişilebilirlik çalışsa da kayıtlar güncel tutulsun
        if (AppBlockerService.isAlive()) return
        if (!LockSettings.shouldLockNow(this) || !isUserLooking(this)) return
        if (top == null || top == packageName) return
        if (LinkedApps.isLinked(top)) return
        val launchers = SystemUsage.launcherPackages(this)
        // Ana ekran da kilit sırasında yasaktır; diğer hariç tutulanlar (serbest liste, arama,
        // ayarlar, klavye...) serbesttir.
        if (top !in launchers && top in LockSettings.limitExcludedPackages(this)) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastBounceAt < 1_500L) return
        lastBounceAt = now
        Log.d(TAG, "Yedek bekçi engelledi: $top")
        try {
            startActivity(
                Intent(this, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Kilit ekranı açılamadı", e)
        }
    }

    /** Kullanım kayıtlarındaki son "öne geldi" olayından öndeki uygulamayı bulur. */
    private fun readTopPackage(): String? {
        if (!ActivityStore.hasUsageAccess(this)) return null
        val usm = getSystemService(UsageStatsManager::class.java) ?: return null
        val now = System.currentTimeMillis()
        val from = if (eventsReadUntil == 0L || now - eventsReadUntil > 6 * 3_600_000L) {
            now - 30 * 60_000L
        } else eventsReadUntil
        try {
            val events = usm.queryEvents(from, now)
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                when (e.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> topPkg = e.packageName
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                    UsageEvents.Event.KEYGUARD_SHOWN -> topPkg = null
                }
            }
            eventsReadUntil = now
        } catch (e: Exception) {
            return topPkg
        }
        return topPkg
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Kilit koruması", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Kilidin arka planda kapatılmaması için gereken kalıcı bildirim"
                    setShowBadge(false)
                }
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = if (LockSettings.isAddictModeOn(this)) {
            "Bağımlılar İçin modu açık · günlük 2 saat takip ediliyor"
        } else {
            "Zamanlayıcı açık · kilit saatleri takip ediliyor"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Özgürlük Kilidi çalışıyor")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val TAG = "OzgurlukKilidi"
        private const val CHANNEL_ID = "koruma"
        private const val NOTIFICATION_ID = 41
        private const val LOOP_MS = 2_000L

        /** Bekçi yalnızca bir koruma kuruluyken çalışır. */
        fun isNeeded(context: Context): Boolean =
            LockSettings.isAddictModeOn(context) || LockSettings.isActive(context)

        /** Gerekliyse servisi başlatır; Android izin vermezse sessizce geçer. */
        fun ensureRunning(context: Context) {
            if (!isNeeded(context)) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, GuardService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Bekçi başlatılamadı", e)
            }
        }

        fun scheduleRestart(context: Context) {
            try {
                val pi = PendingIntent.getBroadcast(
                    context, 7, Intent(context, GuardRestartReceiver::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                context.getSystemService(AlarmManager::class.java)?.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 2_000L,
                    pi
                )
            } catch (e: Exception) {
                Log.w(TAG, "Yeniden başlatma alarmı kurulamadı", e)
            }
        }

        /** Ekran açık ve telefonun kendi kilidi açılmış mı? */
        fun isUserLooking(context: Context): Boolean {
            val interactive = context.getSystemService(PowerManager::class.java)?.isInteractive ?: true
            val keyguard = context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked ?: false
            return interactive && !keyguard
        }
    }
}

/** Açılışta, güncellemeden sonra ve yeniden başlatma alarmında bekçiyi ayağa kaldırır. */
class GuardRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        GuardService.ensureRunning(context)
    }
}
