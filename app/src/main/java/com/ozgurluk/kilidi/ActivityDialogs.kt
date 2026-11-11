package com.ozgurluk.kilidi

import android.content.Context
import android.content.DialogInterface
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Etkinlik panelinin düzenleme ve ekleme pencereleri. Kilit ekranı ([MainActivity]) ve ana ekran
 * widget'ı ([EditActivityActivity]) aynı pencereleri kullanır.
 */
object ActivityDialogs {

    private const val MINUTE_STEP = 5

    /** Bir çubuğun süresini (ve adını/ikonunu) düzenler. Telefon süresi düzenlenemez. */
    fun showEdit(
        context: Context,
        categoryId: String,
        onChanged: () -> Unit,
        onDismiss: (() -> Unit)? = null
    ) {
        val category = ActivityStore.categories(context).firstOrNull { it.id == categoryId }
        if (category == null || category.isPhone) {
            if (category?.isPhone == true) {
                Toast.makeText(
                    context,
                    "Telefon süresi sistemden otomatik gelir, düzenlenemez.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            onDismiss?.invoke()
            return
        }

        val current = ActivityStore.minutes(context, categoryId)
        val dp = context.resources.displayMetrics.density
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * dp).toInt(), (8 * dp).toInt(), (24 * dp).toInt(), 0)
        }
        val nameRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val emojiInput = emojiField(context, category.emoji)
        val nameInput = nameField(context, category.name)
        nameRow.addView(emojiInput, LinearLayout.LayoutParams((64 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT))
        nameRow.addView(nameInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(nameRow)

        root.addView(TextView(context).apply {
            text = "Bugün ne kadar?"
            setPadding(0, (16 * dp).toInt(), 0, 0)
        })

        val hourPicker = NumberPicker(context).apply {
            minValue = 0; maxValue = 23; value = (current / 60).toInt()
        }
        val minuteValues = (0 until 60 step MINUTE_STEP).map { "%02d".format(it) }.toTypedArray()
        val minutePicker = NumberPicker(context).apply {
            minValue = 0; maxValue = minuteValues.size - 1; displayedValues = minuteValues
            value = ((current % 60) / MINUTE_STEP).toInt()
        }
        val pickers = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(hourPicker)
            addView(TextView(context).apply { text = " saat   " })
            addView(minutePicker)
            addView(TextView(context).apply { text = " dakika" })
        }
        root.addView(pickers)

        MaterialAlertDialogBuilder(context)
            .setTitle("${category.emoji} ${category.name}")
            .setView(root)
            .setPositiveButton("Kaydet") { _, _ ->
                val name = nameInput.text.toString().trim().ifEmpty { category.name }
                val emoji = emojiInput.text.toString().trim().ifEmpty { category.emoji }
                ActivityStore.updateCategory(context, categoryId, name, emoji)
                ActivityStore.setMinutes(
                    context, categoryId,
                    hourPicker.value * 60L + minutePicker.value * MINUTE_STEP
                )
                onChanged()
            }
            .setNeutralButton("Sil", null) // tıklama aşağıda: onay penceresine geçer
            .setNegativeButton("İptal", null)
            .create()
            .apply {
                var deleting = false
                setOnShowListener {
                    getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
                        deleting = true
                        dismiss()
                        confirmDelete(context, category, onChanged, onDismiss)
                    }
                }
                setOnDismissListener { if (!deleting) onDismiss?.invoke() }
            }
            .show()
    }

    private fun confirmDelete(
        context: Context,
        category: ActivityStore.Category,
        onChanged: () -> Unit,
        onDismiss: (() -> Unit)?
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle("\"${category.name}\" silinsin mi?")
            .setMessage("Bu etkinlik panelden kaldırılır.")
            .setPositiveButton("Sil") { _, _ ->
                ActivityStore.deleteCategory(context, category.id)
                onChanged()
            }
            .setNegativeButton("Vazgeç", null)
            .setOnDismissListener { onDismiss?.invoke() }
            .show()
    }

    /** Panele yeni bir etkinlik ekler. */
    fun showAdd(context: Context, onChanged: () -> Unit, onDismiss: (() -> Unit)? = null) {
        val dp = context.resources.displayMetrics.density
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * dp).toInt(), (8 * dp).toInt(), (24 * dp).toInt(), 0)
        }
        val nameRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val emojiInput = emojiField(context, "⭐")
        val nameInput = nameField(context, "").apply { hint = "Etkinlik adı (örn. Yürüyüş)" }
        nameRow.addView(emojiInput, LinearLayout.LayoutParams((64 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT))
        nameRow.addView(nameInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(nameRow)

        root.addView(TextView(context).apply {
            text = "Renk"
            setPadding(0, (16 * dp).toInt(), 0, (8 * dp).toInt())
        })
        val used = ActivityStore.categories(context).map { it.color }.toSet()
        var selected = ActivityStore.PALETTE.firstOrNull { it !in used } ?: ActivityStore.PALETTE[0]
        val swatches = mutableListOf<Pair<Int, View>>()
        val colorRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        fun refresh() = swatches.forEach { (c, v) ->
            (v.background as GradientDrawable).setStroke(
                if (c == selected) (3 * dp).toInt() else 0, 0xFFFFFFFF.toInt()
            )
        }
        ActivityStore.PALETTE.forEach { c ->
            val v = View(context).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c) }
                setOnClickListener { selected = c; refresh() }
            }
            swatches += c to v
            colorRow.addView(v, LinearLayout.LayoutParams(0, (30 * dp).toInt(), 1f).apply {
                setMargins((3 * dp).toInt(), 0, (3 * dp).toInt(), 0)
            })
        }
        refresh()
        root.addView(colorRow)

        MaterialAlertDialogBuilder(context)
            .setTitle("Panele yeni etkinlik ekle")
            .setView(root)
            .setPositiveButton("Ekle") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(context, "Bir ad yazmalısın.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val emoji = emojiInput.text.toString().trim().ifEmpty { "⭐" }
                ActivityStore.addCategory(context, name, emoji, selected)
                onChanged()
            }
            .setNegativeButton("İptal", null)
            .setOnDismissListener { onDismiss?.invoke() }
            .show()
    }

    private fun emojiField(context: Context, value: String) = EditText(context).apply {
        setText(value)
        gravity = Gravity.CENTER
        textSize = 22f
        filters = arrayOf(InputFilter.LengthFilter(8))
        hint = "🙂"
        isSingleLine = true
    }

    private fun nameField(context: Context, value: String) = EditText(context).apply {
        setText(value)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        filters = arrayOf(InputFilter.LengthFilter(24))
        isSingleLine = true
    }
}
