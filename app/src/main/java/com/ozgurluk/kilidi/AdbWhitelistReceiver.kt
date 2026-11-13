package com.ozgurluk.kilidi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

/**
 * Serbest listeye YALNIZCA BİLGİSAYARDAN (adb) uygulama ekleme / çıkarma.
 *
 * Mod sürerken liste uygulama içinden değiştirilemez. Unutulan bir uygulamayı eklemek için bu
 * alıcı kullanılır. Manifest'te `android.permission.DUMP` izniyle korunur: bu izin yalnızca adb
 * kabuğunda ve sistemde bulunur, telefondaki hiçbir uygulama bu yayını gönderemez. Böylece kilit
 * telefondan anlık bir dürtüyle aşılamaz; USB ile bağlı bir bilgisayar gerekir.
 *
 * Kullanım:
 *   adb shell am broadcast -a com.ozgurluk.kilidi.bagimli.SERBEST_EKLE \
 *       -n com.ozgurluk.kilidi.bagimli/com.ozgurluk.kilidi.AdbWhitelistReceiver \
 *       --es paketler "paket1,paket2"
 *   (çıkarmak için eylem: com.ozgurluk.kilidi.bagimli.SERBEST_CIKAR)
 */
class AdbWhitelistReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val requested = intent.getStringExtra(EXTRA_PACKAGES)
            ?.split(',', ' ', ';')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        if (requested.isEmpty()) {
            setResultData("HATA: --es paketler \"paket1,paket2\" verilmedi")
            return
        }

        val current = LockSettings.allowedApps(context).toMutableSet()
        val report = mutableListOf<String>()
        when (intent.action) {
            ACTION_ADD -> requested.forEach { pkg ->
                when {
                    !isInstalled(context, pkg) -> report += "$pkg: yüklü değil, eklenmedi"
                    !LockSettings.isWhitelistable(pkg) -> report += "$pkg: sosyal medya, serbest yapılamaz"
                    current.add(pkg) -> report += "$pkg: eklendi"
                    else -> report += "$pkg: zaten serbest"
                }
            }
            ACTION_REMOVE -> requested.forEach { pkg ->
                report += if (current.remove(pkg)) "$pkg: çıkarıldı" else "$pkg: listede yoktu"
            }
            else -> {
                setResultData("HATA: bilinmeyen eylem ${intent.action}")
                return
            }
        }
        LockSettings.setAllowedApps(context, current)
        Log.i(TAG, "adb serbest liste: $report")
        setResultData(report.joinToString(" | "))
    }

    private fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getApplicationInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    companion object {
        private const val TAG = "OzgurlukKilidi"
        const val ACTION_ADD = "com.ozgurluk.kilidi.bagimli.SERBEST_EKLE"
        const val ACTION_REMOVE = "com.ozgurluk.kilidi.bagimli.SERBEST_CIKAR"
        const val EXTRA_PACKAGES = "paketler"
    }
}
