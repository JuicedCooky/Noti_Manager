package com.juicedcooky.notimanager.util

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.asImageBitmap
import com.juicedcooky.notimanager.AppSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun getInstalledAppsWithUi(context: Context): List<AppSetting> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    pm.getInstalledPackages(PackageManager.GET_META_DATA)
        .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
        .map { pkg ->
            AppSetting(
                name = pkg.applicationInfo?.loadLabel(pm).toString(),
                packageName = pkg.packageName,
                iconBitmap = pkg.applicationInfo?.loadIcon(pm)?.toBitmap()?.asImageBitmap()
            )
        }
        .sortedBy { it.name }
}

private fun Drawable.toBitmap(): Bitmap {
    val w = if (intrinsicWidth > 0) intrinsicWidth else 96
    val h = if (intrinsicHeight > 0) intrinsicHeight else 96
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bmp
}
