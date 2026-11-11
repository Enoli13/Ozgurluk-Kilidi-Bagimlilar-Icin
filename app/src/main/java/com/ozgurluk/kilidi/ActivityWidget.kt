package com.ozgurluk.kilidi

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import androidx.appcompat.app.AppCompatActivity

/**
 * Ana ekran widget'ı: etkinlik grafiği. Bir çubuğa dokununca süresi düzenlenir, "+" ile yeni
 * etkinlik eklenir. Telefon çubuğu sistemden gelir, dokunulunca değiştirilemez.
 */
class ActivityWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle
    ) {
        update(context, manager, id)
    }

    companion object {
        private const val HEADER_DP = 40
        private const val MAX_PIXELS = 1_100_000

        /** Tüm widget'ları yeniden çizer (süre değişince, servis dakikada bir çağırır). */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, ActivityWidget::class.java))
            ids.forEach { update(context, manager, it) }
        }

        private fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val entries = ActivityStore.entries(context)
            val views = RemoteViews(context.packageName, R.layout.widget_activity)

            // Widget boyutuna uygun grafik bitmap'i
            val options = manager.getAppWidgetOptions(id)
            val wDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: 300
            val hDp = (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: 220) - HEADER_DP
            var density = context.resources.displayMetrics.density
            var w = (wDp * density).toInt()
            var h = (hDp.coerceAtLeast(120) * density).toInt()
            if (w * h > MAX_PIXELS) { // widget bitmap bellek sınırı
                val scale = Math.sqrt(MAX_PIXELS.toDouble() / (w * h)).toFloat()
                w = (w * scale).toInt(); h = (h * scale).toInt(); density *= scale
            }
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            ChartRenderer.draw(Canvas(bitmap), w, h, density, entries, ChartRenderer.LIGHT)
            views.setImageViewBitmap(R.id.widgetChart, bitmap)

            // Her çubuk için tıklama alanı
            views.removeAllViews(R.id.widgetColumns)
            entries.forEachIndexed { i, e ->
                val col = RemoteViews(context.packageName, R.layout.widget_column)
                col.setOnClickPendingIntent(R.id.widgetColumn, editIntent(context, e.category.id, i))
                views.addView(R.id.widgetColumns, col)
            }

            views.setOnClickPendingIntent(R.id.widgetAdd, editIntent(context, null, 9999))
            views.setOnClickPendingIntent(R.id.widgetTitle, PendingIntent.getActivity(
                context, 10000, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            ))
            manager.updateAppWidget(id, views)
        }

        private fun editIntent(context: Context, categoryId: String?, requestCode: Int): PendingIntent {
            val intent = Intent(context, EditActivityActivity::class.java)
                .setData(Uri.parse("ozgurluk://etkinlik/${categoryId ?: "yeni"}"))
                .putExtra(EditActivityActivity.EXTRA_ID, categoryId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            return PendingIntent.getActivity(
                context, requestCode, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }
}

/** Widget'tan açılan şeffaf pencere: düzenleme ya da ekleme diyalogunu gösterip kapanır. */
class EditActivityActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) { finish(); return }
        val id = intent.getStringExtra(EXTRA_ID)
        val changed = { ActivityWidget.updateAll(this) }
        val done = { if (!isFinishing) finish() }
        if (id == null) {
            ActivityDialogs.showAdd(this, changed, done)
        } else {
            ActivityDialogs.showEdit(this, id, changed, done)
        }
    }

    companion object {
        const val EXTRA_ID = "category_id"
    }
}
