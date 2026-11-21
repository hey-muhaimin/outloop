package com.example.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

data class LaunchableAppInfo(
    val packageName: String,
    val displayName: String
)

object InstalledAppDetector {
    private val iconCache = LruCache<String, ImageBitmap>(64)

    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        val pm = context.packageManager ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    fun getInstalledPackages(context: Context, packageNames: Iterable<String>): Set<String> {
        return packageNames.filterTo(LinkedHashSet()) { pkg ->
            isPackageInstalled(context, pkg)
        }
    }

    fun getLaunchableApps(context: Context): List<LaunchableAppInfo> {
        val pm = context.packageManager ?: return emptyList()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveList = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }
        } catch (_: Exception) {
            emptyList()
        }

        val seen = HashSet<String>()
        return resolveList.mapNotNull { info ->
            val activityInfo = info.activityInfo ?: return@mapNotNull null
            val pkg = activityInfo.packageName ?: return@mapNotNull null
            if (pkg == context.packageName || !seen.add(pkg)) return@mapNotNull null
            val label = runCatching { info.loadLabel(pm).toString() }
                .getOrDefault("")
                .trim()
                .ifEmpty { pkg }
            LaunchableAppInfo(packageName = pkg, displayName = label)
        }.sortedBy { it.displayName.lowercase() }
    }

    fun loadCachedAppIcon(context: Context, packageName: String): ImageBitmap? {
        if (packageName.isBlank()) return null
        iconCache.get(packageName)?.let { return it }
        val pm = context.packageManager ?: return null
        return try {
            val drawable = pm.getApplicationIcon(packageName)
            val bitmap = drawableToBitmap(drawable, sizePx = 128)
            bitmap.asImageBitmap().also { iconCache.put(packageName, it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            val src = drawable.bitmap
            if (src.width <= sizePx && src.height <= sizePx) {
                return src
            }
        }
        val width = drawable.intrinsicWidth.takeIf { it > 0 }?.coerceAtMost(sizePx) ?: sizePx
        val height = drawable.intrinsicHeight.takeIf { it > 0 }?.coerceAtMost(sizePx) ?: sizePx
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
