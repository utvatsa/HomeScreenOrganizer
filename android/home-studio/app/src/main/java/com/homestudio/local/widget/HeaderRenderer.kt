package com.homestudio.local.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.homestudio.local.data.HeaderTheme
import com.homestudio.local.data.WidgetInstanceConfig
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object HeaderRenderer {

    fun render(
        context: Context,
        widgetConfig: WidgetInstanceConfig,
        globalTheme: HeaderTheme,
        widthDp: Int,
        heightDp: Int,
    ): Bitmap {
        val theme = widgetConfig.resolveTheme(globalTheme)
        val dm = context.resources.displayMetrics
        val density = dm.density
        val scaledDensity = dm.scaledDensity

        // Clamp bitmap size so RemoteViews stays well under Android binder transaction limits
        val safeWdp = widthDp.coerceIn(120, 460)
        val safeHdp = heightDp.coerceIn(48, 220)
        val widthPx = max(120, (safeWdp * density).roundToInt())
        val heightPx = max(48, (safeHdp * density).roundToInt())

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgColor = theme.parsedBackgroundColor()
        if (Color.alpha(bgColor) > 0) {
            canvas.drawColor(bgColor)
        }

        val padPx = (theme.paddingDp * density).roundToInt().coerceIn(0, widthPx / 4)
        val usableWidth = max(40, widthPx - padPx * 2)

        val baseTypeface = when (theme.fontFamily) {
            "serif" -> Typeface.SERIF
            "monospace" -> Typeface.MONOSPACE
            else -> Typeface.SANS_SERIF
        }
        val typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(baseTypeface, theme.fontWeight.coerceIn(100, 900), false)
        } else {
            val style = if (theme.fontWeight >= 600) Typeface.BOLD else Typeface.NORMAL
            Typeface.create(baseTypeface, style)
        }

        val baseTextColor = theme.parsedTextColor()
        val finalAlpha = ((Color.alpha(baseTextColor) / 255f) * theme.opacity.coerceIn(0f, 1f) * 255f)
            .roundToInt()
            .coerceIn(0, 255)
        val titleColor = Color.argb(
            finalAlpha,
            Color.red(baseTextColor),
            Color.green(baseTextColor),
            Color.blue(baseTextColor),
        )

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.typeface = typeface
            this.textSize = theme.fontSizeSp * scaledDensity
            this.color = titleColor
            this.letterSpacing = theme.letterSpacing
            if (theme.shadowEnabled) {
                setShadowLayer(4f * density, 0f, 1.5f * density, 0x80000000.toInt())
            }
        }

        val alignment = when (theme.alignment) {
            "center" -> Layout.Alignment.ALIGN_CENTER
            "end" -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_NORMAL
        }

        val titleLayout = StaticLayout.Builder
            .obtain(widgetConfig.title, 0, widgetConfig.title.length, titlePaint, usableWidth)
            .setAlignment(alignment)
            .setIncludePad(false)
            .setMaxLines(2)
            .build()

        val subtitleText = widgetConfig.subtitle?.takeIf { it.isNotBlank() }
        val subtitleLayout = subtitleText?.let { sub ->
            val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                this.typeface = Typeface.create(baseTypeface, min(400, theme.fontWeight), false)
                this.textSize = (theme.fontSizeSp * 0.48f).coerceAtLeast(11f) * scaledDensity
                this.color = Color.argb(
                    (finalAlpha * 0.72f).roundToInt(),
                    Color.red(baseTextColor),
                    Color.green(baseTextColor),
                    Color.blue(baseTextColor),
                )
            }
            StaticLayout.Builder
                .obtain(sub, 0, sub.length, subPaint, usableWidth)
                .setAlignment(alignment)
                .setIncludePad(false)
                .setMaxLines(1)
                .build()
        }

        val gapPx = if (subtitleLayout != null) (4 * density).roundToInt() else 0
        val sepGapPx = if (theme.showSeparator) (6 * density).roundToInt() else 0
        val contentHeight = titleLayout.height + gapPx + (subtitleLayout?.height ?: 0) + sepGapPx

        val topY = when (theme.verticalAlignment) {
            "top" -> padPx.toFloat()
            "center" -> ((heightPx - contentHeight) / 2f).coerceAtLeast(0f)
            else -> (heightPx - contentHeight - padPx * 0.5f).coerceAtLeast(0f)
        }

        canvas.save()
        canvas.translate(padPx.toFloat(), topY)
        titleLayout.draw(canvas)
        if (subtitleLayout != null) {
            canvas.translate(0f, (titleLayout.height + gapPx).toFloat())
            subtitleLayout.draw(canvas)
        }
        if (theme.showSeparator) {
            val lineY = (if (subtitleLayout != null) subtitleLayout.height else titleLayout.height) + sepGapPx.toFloat()
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.parsedSeparatorColor()
                strokeWidth = max(1f, density)
            }
            canvas.drawLine(0f, lineY, usableWidth.toFloat(), lineY, linePaint)
        }
        canvas.restore()

        return bitmap
    }
}
