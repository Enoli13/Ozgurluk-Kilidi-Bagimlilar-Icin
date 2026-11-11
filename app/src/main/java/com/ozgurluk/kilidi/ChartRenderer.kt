package com.ozgurluk.kilidi

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.ceil
import kotlin.math.max

/**
 * Etkinlik grafiği: saat ekseni (1, 2, 3, 4…), kesikli yardımcı çizgiler, renkli çubuklar ve
 * altında ikon + ad. Kilit ekranındaki [ChartView] ile ana ekran widget'ı aynı çizimi kullanır.
 */
object ChartRenderer {

    /** Sütun hizası widget'taki tıklama alanlarıyla aynı olsun diye dp cinsinden sabitler. */
    const val AXIS_DP = 40f
    const val RIGHT_DP = 8f
    private const val TOP_DP = 26f
    private const val BOTTOM_DP = 58f

    data class Palette(val axis: Int, val text: Int, val grid: Int, val subText: Int)

    val LIGHT = Palette(0xFF111111.toInt(), 0xFF111111.toInt(), 0x55888888, 0xFF555555.toInt())
    val DARK = Palette(0xFFECEFF4.toInt(), 0xFFECEFF4.toInt(), 0x556B7280, 0xFF9AA3B2.toInt())

    fun columnIndex(x: Float, width: Int, density: Float, count: Int): Int? {
        if (count == 0) return null
        val left = AXIS_DP * density
        val plotW = width - left - RIGHT_DP * density
        if (x < left || plotW <= 0) return null
        val i = ((x - left) / (plotW / count)).toInt()
        return i.takeIf { it in 0 until count }
    }

    fun draw(canvas: Canvas, w: Int, h: Int, density: Float, entries: List<ActivityStore.Entry>, pal: Palette) {
        val left = AXIS_DP * density
        val right = w - RIGHT_DP * density
        val top = TOP_DP * density
        val bottom = h - BOTTOM_DP * density
        if (bottom <= top || right <= left) return

        val maxHours = max(4, ceil((entries.maxOfOrNull { it.minutes } ?: 0L) / 60.0).toInt())
        val plotH = bottom - top
        fun yFor(minutes: Long) = bottom - plotH * (minutes / 60f) / maxHours

        val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pal.axis; strokeWidth = 2f * density
        }
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pal.grid; strokeWidth = 1.2f * density; style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(6f * density, 4f * density), 0f)
        }
        val yLabel = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pal.text; textSize = 15f * density
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); textAlign = Paint.Align.RIGHT
        }

        // Yatay kesikli çizgiler ve saat etiketleri
        val step = if (maxHours > 8) 2 else 1
        for (hr in step..maxHours step step) {
            val y = yFor(hr * 60L)
            canvas.drawLine(left, y, right, y, gridPaint)
            canvas.drawText(hr.toString(), left - 8f * density, y + yLabel.textSize * 0.35f, yLabel)
        }

        val n = entries.size
        if (n > 0) {
            val colW = (right - left) / n
            val barW = colW * 0.58f
            val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = pal.subText; textSize = 10.5f * density; textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
            val emojiPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = minOf(26f * density, colW * 0.62f); textAlign = Paint.Align.CENTER
            }
            val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = pal.subText; textSize = 10f * density; textAlign = Paint.Align.CENTER
            }
            entries.forEachIndexed { i, e ->
                val cx = left + colW * i + colW / 2
                val yTop = yFor(e.minutes).coerceAtMost(bottom - if (e.minutes > 0) 2f * density else 0f)
                barPaint.color = e.category.color
                canvas.drawRect(RectF(cx - barW / 2, yTop, cx + barW / 2, bottom), barPaint)
                canvas.drawText(ActivityStore.formatMinutes(e.minutes), cx, yTop - 5f * density, valuePaint)

                canvas.drawText(e.category.emoji, cx, bottom + 8f * density + emojiPaint.textSize, emojiPaint)
                val name = TextUtils.ellipsize(e.category.name, namePaint, colW - 4f * density, TextUtils.TruncateAt.END)
                canvas.drawText(name.toString(), cx, h - 6f * density, namePaint)
            }
        }

        // Eksenler
        canvas.drawLine(left, top - 12f * density, left, bottom, axisPaint)
        canvas.drawLine(left - 6f * density, bottom, w.toFloat(), bottom, axisPaint)
    }
}

/** Kilit ekranındaki grafik. Bir çubuğa dokunulunca [onColumnClick] çağrılır. */
class ChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var entries: List<ActivityStore.Entry> = emptyList()
        set(value) {
            field = value; invalidate()
        }
    var palette: ChartRenderer.Palette = ChartRenderer.DARK
    var onColumnClick: ((ActivityStore.Entry) -> Unit)? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        ChartRenderer.draw(canvas, width, height, resources.displayMetrics.density, entries, palette)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                val i = ChartRenderer.columnIndex(event.x, width, resources.displayMetrics.density, entries.size)
                if (i != null) {
                    performClick()
                    onColumnClick?.invoke(entries[i])
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()
}
