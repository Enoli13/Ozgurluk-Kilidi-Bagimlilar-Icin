package com.ozgurluk.kilidi

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.Rect
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.TextView

/**
 * Yüzen pencereler ve resim içinde resim (PiP) için kilit.
 *
 * Kilit sürerken serbest olmayan bir uygulama tam ekran açılırsa [AppBlockerService] kilit
 * ekranını öne getirir. Ama HyperOS kenar çubuğundan açılan yüzen pencereler ve PiP videoları
 * kilit ekranının ÜSTÜNDE kalır. Bu sınıf ekrandaki tüm pencereleri tarar; serbest olmayan bir
 * uygulamanın penceresini, dokunmaları yutan opak bir kalkanla kapatır ve çalan medyayı durdurur.
 *
 * Yüzen pencerenin üst şeridi (taşıma/kapatma tutamacı) açık bırakılır ki pencere kapatılabilsin;
 * tam ekrana alınırsa normal kilit devreye girer.
 */
class FloatingGuard(private val service: AccessibilityService) {

    private val wm = service.getSystemService(WindowManager::class.java)
    private val density = service.resources.displayMetrics.density

    /** Pencere kimliği → üstüne konan kalkan ve konumu. */
    private val covers = HashMap<Int, Pair<View, Rect>>()
    private var lastPauseAt = 0L

    /** [locked] true ise yasak pencereleri kapatır, false ise tüm kalkanları kaldırır. */
    fun update(locked: Boolean, excluded: Set<String>) {
        if (!locked) {
            clear()
            return
        }
        val wanted = HashMap<Int, Rect>()
        val windows = try { service.windows } catch (e: Exception) { emptyList() }
        var media = false
        for (w in windows) {
            if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val pkg = try { w.root?.packageName?.toString() } catch (e: Exception) { null } ?: continue
            if (pkg in excluded) continue
            val bounds = Rect().also { w.getBoundsInScreen(it) }
            if (bounds.isEmpty) continue
            val pip = w.isInPictureInPictureMode
            if (!pip && isFullScreen(bounds)) continue // tam ekranı servis zaten kapatır
            if (!pip) {
                // Yüzen pencerenin tutamacını açık bırak (kapatabilmek / tam ekrana almak için)
                bounds.top = minOf(bounds.bottom, bounds.top + (HANDLE_DP * density).toInt())
            }
            wanted[w.id] = bounds
            media = true
        }

        // Artık olmayanları kaldır
        covers.keys.filter { it !in wanted }.forEach { id -> remove(id) }
        // Yenileri ekle / yer değiştirenleri taşı
        wanted.forEach { (id, rect) ->
            val existing = covers[id]
            when {
                existing == null -> add(id, rect)
                existing.second != rect -> move(id, existing.first, rect)
            }
        }
        if (media) pauseMedia()
    }

    fun clear() {
        covers.keys.toList().forEach { remove(it) }
    }

    private fun isFullScreen(r: Rect): Boolean {
        val dm = service.resources.displayMetrics
        return r.width() >= dm.widthPixels * 0.95f && r.height() >= dm.heightPixels * 0.85f
    }

    private fun params(r: Rect) = WindowManager.LayoutParams(
        r.width(), r.height(),
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.OPAQUE
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = r.left
        y = r.top
        title = "OzgurlukKilidiKalkan"
    }

    private fun add(id: Int, r: Rect) {
        val view = TextView(service).apply {
            text = if (r.height() > 140 * density) "🔒\nKilitli\nBu uygulama şu an kullanılamaz" else "🔒"
            gravity = Gravity.CENTER
            textSize = 15f
            setTextColor(0xFFECEFF4.toInt())
            setBackgroundColor(0xFF111318.toInt())
            // Tüm dokunmaları yut
            setOnTouchListener { _, _ -> true }
        }
        try {
            wm.addView(view, params(r))
            covers[id] = view to Rect(r)
        } catch (e: Exception) {
            Log.w(TAG, "Kalkan eklenemedi", e)
        }
    }

    private fun move(id: Int, view: View, r: Rect) {
        try {
            wm.updateViewLayout(view, params(r))
            covers[id] = view to Rect(r)
        } catch (e: Exception) {
            remove(id)
            add(id, r)
        }
    }

    private fun remove(id: Int) {
        val (view, _) = covers.remove(id) ?: return
        try {
            wm.removeViewImmediate(view)
        } catch (e: Exception) {
            // zaten kaldırılmış
        }
    }

    /** Yasak pencerede çalan videoyu / sesi durdurur (en fazla 3 saniyede bir). */
    private fun pauseMedia() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPauseAt < 3_000L) return
        lastPauseAt = now
        try {
            val audio = service.getSystemService(AudioManager::class.java) ?: return
            if (!audio.isMusicActive) return
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
        } catch (e: Exception) {
            Log.w(TAG, "Medya durdurulamadı", e)
        }
    }

    private companion object {
        const val TAG = "OzgurlukKilidi"
        const val HANDLE_DP = 44
    }
}
