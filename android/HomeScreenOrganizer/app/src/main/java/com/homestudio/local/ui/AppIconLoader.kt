package com.homestudio.local.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.concurrent.ConcurrentHashMap

private object IconBitmapCache {
    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    fun getOrLoad(context: Context, packageName: String, sizePx: Int = 72): ImageBitmap? {
        cache[packageName]?.let { return it }
        val loaded = runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            drawableToBitmap(drawable, sizePx).asImageBitmap()
        }.getOrNull()
        if (loaded != null) {
            cache[packageName] = loaded
        }
        return loaded
    }

    private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        return bmp
    }
}

@Composable
fun InstalledAppIcon(
    packageName: String?,
    fallbackLabel: String,
    size: Dp = 32.dp,
) {
    val context = LocalContext.current
    val icon = remember(packageName) {
        if (packageName.isNullOrBlank()) null else IconBitmapCache.getOrLoad(context, packageName)
    }
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = fallbackLabel,
            modifier = Modifier.size(size),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .background(Color(0xFF2B3246), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = fallbackLabel.take(1).uppercase(),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
