package com.ozgurluk.kilidi

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.util.Log

/**
 * "Bağımlılar İçin" modunun silinmeye karşı korunması.
 *
 * - **Cihaz yöneticisi (normal kurulum):** Etkin cihaz yöneticisi olan bir uygulama, yöneticiliği
 *   Ayarlar'dan kaldırılmadan silinemez. Mod sürerken Ayarlar ve kaldırma ekranları
 *   [AppBlockerService] tarafından açtırılmadığı için bu kaldırma da yapılamaz.
 * - **Cihaz sahibi (isteğe bağlı, adb ile):** Uygulama cihaz sahibi yapıldıysa kaldırma sistem
 *   düzeyinde engellenir ve güvenli mod kapatılır. Mod bitince bu kısıtlamalar otomatik kalkar.
 *
 * Seçilen tarih geçince [release] çağrılır; yöneticilik bırakılır ve uygulama normal şekilde
 * silinebilir hale gelir.
 */
object AddictGuard {

    private const val TAG = "OzgurlukKilidi"

    fun admin(context: Context) = ComponentName(context, AddictAdminReceiver::class.java)

    private fun dpm(context: Context): DevicePolicyManager? =
        context.getSystemService(DevicePolicyManager::class.java)

    fun isAdminActive(context: Context): Boolean =
        dpm(context)?.isAdminActive(admin(context)) ?: false

    fun isDeviceOwner(context: Context): Boolean =
        dpm(context)?.isDeviceOwnerApp(context.packageName) ?: false

    /** Cihaz yöneticisi etkinleştirme ekranını açan Intent. */
    fun activationIntent(context: Context): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin(context))
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "\"Bağımlılar İçin\" modu seçtiğin tarihe kadar silinemesin diye gerekli. " +
                    "Tarih dolunca yöneticilik kendiliğinden kalkar."
            )

    /** Mod kurulduğunda ve servis her bağlandığında çağrılır; cihaz sahibiyse sıkılaştırır. */
    fun harden(context: Context) {
        if (!isDeviceOwner(context)) return
        val dpm = dpm(context) ?: return
        val admin = admin(context)
        try {
            dpm.setUninstallBlocked(admin, context.packageName, true)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
        } catch (e: SecurityException) {
            Log.w(TAG, "Cihaz sahibi kısıtlamaları uygulanamadı", e)
        }
    }

    /** Mod bitti: tüm kısıtlamaları kaldır, yöneticiliği bırak. */
    @Suppress("DEPRECATION")
    fun release(context: Context) {
        val dpm = dpm(context) ?: return
        val admin = admin(context)
        try {
            if (isDeviceOwner(context)) {
                dpm.setUninstallBlocked(admin, context.packageName, false)
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
                dpm.clearDeviceOwnerApp(context.packageName)
            }
            if (dpm.isAdminActive(admin)) dpm.removeActiveAdmin(admin)
        } catch (e: SecurityException) {
            Log.w(TAG, "Yöneticilik bırakılamadı", e)
        }
    }

    /** Tarihi geçmiş modu kapatır. Etkinlikten ve servisten düzenli olarak çağrılır. */
    fun expireIfDone(context: Context): Boolean {
        if (!LockSettings.isAddictModeExpired(context)) return false
        LockSettings.setAddictArmed(context, false)
        release(context)
        return true
    }
}

class AddictAdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        if (LockSettings.isAddictModeOn(context)) {
            "\"Bağımlılar İçin\" modu sürüyor. Bu kararı kendin verdin, sonuna kadar götür!"
        } else {
            "Özgürlük Kilidi'nin silinme koruması kaldırılacak."
        }
}
