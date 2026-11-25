package com.homestudio.local.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class CuratedWallpaperPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val colors: IntArray,
    val accentHex: Int,
)

object WallpaperEngine {

    private const val ACTIVE_WALLPAPER_FILE = "home_wallpaper_active.png"
    private const val DEVICE_SYNCED_WALLPAPER_FILE = "home_wallpaper_device.png"

    val CURATED_PRESETS: List<CuratedWallpaperPreset> = listOf(
        CuratedWallpaperPreset(
            id = "obsidian-aura",
            title = "Obsidian Aura",
            subtitle = "Deep midnight with emerald halo",
            colors = intArrayOf(0xFF070B14.toInt(), 0xFF0F1F2E.toInt(), 0xFF08141E.toInt()),
            accentHex = 0x552EE59D,
        ),
        CuratedWallpaperPreset(
            id = "oneui-bloom",
            title = "One UI Velvet Bloom",
            subtitle = "Sapphire & indigo silk wave",
            colors = intArrayOf(0xFF0B1028.toInt(), 0xFF1F2B6B.toInt(), 0xFF0D1426.toInt()),
            accentHex = 0x667C9BFF,
        ),
        CuratedWallpaperPreset(
            id = "warm-dusk",
            title = "Warm Dusk",
            subtitle = "Charcoal plum & sunset amber",
            colors = intArrayOf(0xFF140E1A.toInt(), 0xFF2D1E2F.toInt(), 0xFF10131C.toInt()),
            accentHex = 0x55F59E42,
        ),
        CuratedWallpaperPreset(
            id = "forest-canopy",
            title = "Forest Canopy",
            subtitle = "Calm pine & soft teal mist",
            colors = intArrayOf(0xFF071410.toInt(), 0xFF11332A.toInt(), 0xFF0A1716.toInt()),
            accentHex = 0x5534D399,
        ),
        CuratedWallpaperPreset(
            id = "nordic-slate",
            title = "Nordic Slate",
            subtitle = "Minimal cool slate studio",
            colors = intArrayOf(0xFF1A2232.toInt(), 0xFF2D3A52.toInt(), 0xFF141B29.toInt()),
            accentHex = 0x44A5B4FC,
        ),
        CuratedWallpaperPreset(
            id = "pure-amoled",
            title = "AMOLED Onyx",
            subtitle = "Pure deep contrast for crisp icons",
            colors = intArrayOf(0xFF050608.toInt(), 0xFF0B0E14.toInt(), 0xFF040507.toInt()),
            accentHex = 0x2264748B,
        ),
    )

    fun activeWallpaperFile(context: Context): File = File(context.filesDir, ACTIVE_WALLPAPER_FILE)

    fun syncedDeviceWallpaperFile(context: Context): File = File(context.filesDir, DEVICE_SYNCED_WALLPAPER_FILE)

    fun externalDeviceWallpaperFile(context: Context): File? =
        context.getExternalFilesDir(null)?.let { File(it, DEVICE_SYNCED_WALLPAPER_FILE) }

    fun loadActiveWallpaperBitmap(
        context: Context,
        activeSource: String = "device",
        maxDimPx: Int = 900,
    ): Bitmap {
        val activeFile = activeWallpaperFile(context)
        if (activeSource != "device" && activeSource.startsWith("preset:")) {
            val presetId = activeSource.removePrefix("preset:")
            return renderPresetBitmap(presetId, widthPx = 540, heightPx = 1170, dimFraction = 0.0f)
        }
        if (activeFile.exists() && activeSource != "device") {
            decodeScaledFile(activeFile, maxDimPx)?.let { return it }
        }
        val syncedCandidates = listOfNotNull(externalDeviceWallpaperFile(context), syncedDeviceWallpaperFile(context))
            .filter { it.exists() && it.length() > 0 }
            .sortedByDescending { it.lastModified() }
        for (syncedDevice in syncedCandidates) {
            decodeScaledFile(syncedDevice, maxDimPx)?.let { return it }
        }
        if (activeFile.exists()) {
            decodeScaledFile(activeFile, maxDimPx)?.let { return it }
        }
        // Best-effort read of system wallpaper (Android 13+ may restrict without storage permission)
        runCatching {
            val wm = WallpaperManager.getInstance(context)
            val drawable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wm.peekDrawable()
            } else {
                @Suppress("DEPRECATION")
                wm.drawable
            }
            if (drawable != null) {
                val rawW = drawable.intrinsicWidth.takeIf { it > 0 } ?: 540
                val rawH = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1170
                val scale = min(1f, maxDimPx.toFloat() / max(rawW, rawH).toFloat())
                val w = max(180, (rawW * scale).roundToInt())
                val h = max(360, (rawH * scale).roundToInt())
                return drawable.toBitmap(w, h, Bitmap.Config.ARGB_8888)
            }
        }
        return renderPresetBitmap("obsidian-aura", widthPx = 540, heightPx = 1170, dimFraction = 0.0f)
    }

    fun decodeUriToBitmap(context: Context, uri: Uri, maxDimPx: Int = 1440): Bitmap? {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            val sampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDimPx)
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        }.getOrNull()
    }

    fun renderPresetBitmap(
        presetId: String,
        widthPx: Int = 540,
        heightPx: Int = 1170,
        dimFraction: Float = 0f,
    ): Bitmap {
        val preset = CURATED_PRESETS.firstOrNull { it.id == presetId } ?: CURATED_PRESETS.first()
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, widthPx.toFloat(), heightPx.toFloat(),
                preset.colors,
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, widthPx.toFloat(), heightPx.toFloat(), bgPaint)

        val glow1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                widthPx * 0.78f,
                heightPx * 0.24f,
                widthPx * 0.85f,
                preset.accentHex,
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, widthPx.toFloat(), heightPx.toFloat(), glow1)

        val glow2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                widthPx * 0.2f,
                heightPx * 0.78f,
                widthPx * 0.75f,
                (preset.accentHex and 0x00FFFFFF) or 0x33000000,
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, widthPx.toFloat(), heightPx.toFloat(), glow2)

        if (dimFraction > 0.01f) {
            canvas.drawColor(Color.argb((dimFraction.coerceIn(0f, 0.75f) * 255).roundToInt(), 0, 0, 0))
        }
        return bmp
    }

    fun applyDimOverlay(source: Bitmap, dimFraction: Float): Bitmap {
        if (dimFraction <= 0.01f) return source
        val copy = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(copy)
        canvas.drawColor(Color.argb((dimFraction.coerceIn(0f, 0.75f) * 255).roundToInt(), 0, 0, 0))
        return copy
    }

    fun savePreviewBitmap(context: Context, bitmap: Bitmap): File {
        val file = activeWallpaperFile(context)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 92, out)
        }
        return file
    }

    /**
     * Applies wallpaper ONLY to the unlocked Home Screen (WallpaperManager.FLAG_SYSTEM).
     * Lock screen (WallpaperManager.FLAG_LOCK) is NEVER modified.
     */
    fun applyUnlockedHomeWallpaper(context: Context, bitmap: Bitmap): Result<Unit> = runCatching {
        savePreviewBitmap(context, bitmap)
        val wm = WallpaperManager.getInstance(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
        } else {
            wm.setBitmap(bitmap)
        }
    }

    private fun decodeScaledFile(file: File, maxDimPx: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDimPx)
        }
        BitmapFactory.decodeFile(file.absolutePath, opts)
    }.getOrNull()

    private fun computeInSampleSize(w: Int, h: Int, maxDimPx: Int): Int {
        var inSample = 1
        val maxSide = max(w, h)
        while (maxSide / inSample > maxDimPx * 2) {
            inSample *= 2
        }
        return inSample
    }
}
