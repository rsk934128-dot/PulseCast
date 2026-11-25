package com.example.ui.adapters

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.Base64
import android.util.LruCache
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.dispose
import coil.load
import coil.transform.CircleCropTransformation
import com.example.R
import com.example.databinding.ViewUserAvatarBinding
import com.example.ui.theme.EmeraldMint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Resolved ARGB palette extracted from Jetpack Compose [MaterialTheme.colorScheme]
 * so RecyclerView ViewBinding holders adapt seamlessly to light, dark, and dynamic themes.
 */
data class RecyclerThemePalette(
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val secondaryContainer: Int,
    val onSecondaryContainer: Int,
    val tertiary: Int,
    val tertiaryContainer: Int,
    val onTertiaryContainer: Int,
    val surface: Int,
    val onSurface: Int,
    val surfaceVariant: Int,
    val onSurfaceVariant: Int,
    val outline: Int,
    val emeraldMint: Int
)

@Composable
fun rememberRecyclerThemePalette(): RecyclerThemePalette {
    val cs = MaterialTheme.colorScheme
    return remember(cs) {
        RecyclerThemePalette(
            primary = cs.primary.toArgb(),
            onPrimary = cs.onPrimary.toArgb(),
            primaryContainer = cs.primaryContainer.toArgb(),
            onPrimaryContainer = cs.onPrimaryContainer.toArgb(),
            secondaryContainer = cs.secondaryContainer.toArgb(),
            onSecondaryContainer = cs.onSecondaryContainer.toArgb(),
            tertiary = cs.tertiary.toArgb(),
            tertiaryContainer = cs.tertiaryContainer.toArgb(),
            onTertiaryContainer = cs.onTertiaryContainer.toArgb(),
            surface = cs.surface.toArgb(),
            onSurface = cs.onSurface.toArgb(),
            surfaceVariant = cs.surfaceVariant.toArgb(),
            onSurfaceVariant = cs.onSurfaceVariant.toArgb(),
            outline = cs.outline.toArgb(),
            emeraldMint = EmeraldMint.toArgb()
        )
    }
}

internal fun Context.dpToPx(dp: Float): Int =
    (dp * resources.displayMetrics.density + 0.5f).toInt()

internal fun Context.dpToPxF(dp: Float): Float =
    dp * resources.displayMetrics.density

internal fun applyAlpha(color: Int, alpha: Float): Int =
    ColorUtils.setAlphaComponent(color, (alpha.coerceIn(0f, 1f) * 255f).toInt())

internal fun roundedRippleBackground(
    fillColor: Int,
    rippleBaseColor: Int,
    radiusPx: Float,
    strokeWidthPx: Int = 0,
    strokeColor: Int = 0
): RippleDrawable {
    val content = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radiusPx
        setColor(fillColor)
        if (strokeWidthPx > 0) {
            setStroke(strokeWidthPx, strokeColor)
        }
    }
    val mask = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radiusPx
        setColor(android.graphics.Color.WHITE)
    }
    return RippleDrawable(
        ColorStateList.valueOf(applyAlpha(rippleBaseColor, 0.14f)),
        content,
        mask
    )
}

internal fun roundedSolidDrawable(
    fillColor: Int,
    radiusPx: Float
): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.RECTANGLE
    cornerRadius = radiusPx
    setColor(fillColor)
}

internal fun circleSolidDrawable(
    fillColor: Int,
    strokeWidthPx: Int = 0,
    strokeColor: Int = 0
): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(fillColor)
    if (strokeWidthPx > 0) {
        setStroke(strokeWidthPx, strokeColor)
    }
}

internal fun View.applyRoundedCornerOutline(radiusPx: Float) {
    clipToOutline = true
    outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
        }
    }
}

internal fun View.applyTopRoundedCornerOutline(radiusPx: Float) {
    clipToOutline = true
    outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height + radiusPx.toInt(), radiusPx)
        }
    }
}

internal fun View.applyCircularOutline() {
    clipToOutline = true
    outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setOval(0, 0, view.width, view.height)
        }
    }
}

/**
 * Memory-bounded Base64 bitmap LRU cache + off-UI-thread downsampling decoder.
 * Prevents frame drops and OOM pressure on low-end devices during RecyclerView flings.
 */
object Base64BitmapCache {
    private val decodeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val maxCacheKb: Int = run {
        val maxKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
        (maxKb / 16).coerceIn(2048, 8192)
    }

    private val cache = object : LruCache<String, Bitmap>(maxCacheKb) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

    fun decodeInto(
        imageView: ImageView,
        base64: String?,
        targetMaxPx: Int,
        preferRgb565: Boolean = true,
        onDecoded: ((Boolean) -> Unit)? = null
    ) {
        cancelPendingDecode(imageView)
        if (base64.isNullOrBlank()) {
            imageView.setTag(R.id.tag_bitmap_request_key, null)
            imageView.setImageDrawable(null)
            onDecoded?.invoke(false)
            return
        }

        val cacheKey = "${targetMaxPx}_${base64.length}_${base64.hashCode()}_${base64.take(16)}"
        imageView.setTag(R.id.tag_bitmap_request_key, cacheKey)

        cache.get(cacheKey)?.let { cached ->
            if (!cached.isRecycled) {
                imageView.setImageBitmap(cached)
                onDecoded?.invoke(true)
                return
            } else {
                cache.remove(cacheKey)
            }
        }

        imageView.setImageDrawable(null)
        val job = decodeScope.launch {
            val bitmap = decodeSampledBase64(base64, targetMaxPx, preferRgb565)
            if (bitmap != null) {
                cache.put(cacheKey, bitmap)
            }
            withContext(Dispatchers.Main.immediate) {
                if (imageView.getTag(R.id.tag_bitmap_request_key) == cacheKey) {
                    if (bitmap != null && !bitmap.isRecycled) {
                        imageView.setImageBitmap(bitmap)
                        onDecoded?.invoke(true)
                    } else {
                        onDecoded?.invoke(false)
                    }
                }
            }
        }
        imageView.setTag(R.id.tag_bitmap_decode_job, job)
    }

    fun cancelPendingDecode(imageView: ImageView) {
        (imageView.getTag(R.id.tag_bitmap_decode_job) as? Job)?.cancel()
        imageView.setTag(R.id.tag_bitmap_decode_job, null)
        imageView.setTag(R.id.tag_bitmap_request_key, null)
    }

    private fun decodeSampledBase64(
        base64: String,
        targetMaxPx: Int,
        preferRgb565: Boolean
    ): Bitmap? {
        return try {
            val cleaned = base64.trim().let { raw ->
                val commaIdx = raw.indexOf(',')
                if (raw.startsWith("data:", ignoreCase = true) && commaIdx > 0) {
                    raw.substring(commaIdx + 1)
                } else {
                    raw
                }
            }
            val bytes = Base64.decode(cleaned, Base64.DEFAULT)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, targetMaxPx)
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = if (preferRgb565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
        } catch (_: Throwable) {
            null
        }
    }

    private fun calculateInSampleSize(rawWidth: Int, rawHeight: Int, targetMaxPx: Int): Int {
        if (rawWidth <= 0 || rawHeight <= 0 || targetMaxPx <= 0) return 1
        var inSampleSize = 1
        val maxDim = maxOf(rawWidth, rawHeight)
        while ((maxDim / inSampleSize) > targetMaxPx * 2) {
            inSampleSize *= 2
        }
        return inSampleSize
    }
}

object AvatarViewBinder {
    fun bindAvatar(
        binding: ViewUserAvatarBinding,
        palette: RecyclerThemePalette,
        displayName: String,
        isOnline: Boolean,
        avatarSizeDp: Int,
        isGroup: Boolean = false,
        photoUrl: String? = null,
        avatarBase64: String? = null,
        indicatorTag: String? = null,
        onClick: (() -> Unit)? = null
    ) {
        val ctx = binding.root.context
        val safeName = displayName.trim().ifBlank { "Creator" }
        val avatarPx = ctx.dpToPx(avatarSizeDp.toFloat())
        val dotDp = (avatarSizeDp * 0.30f).coerceIn(10f, 16f)
        val dotPx = ctx.dpToPx(dotDp)

        binding.root.layoutParams = binding.root.layoutParams?.apply {
            width = avatarPx
            height = avatarPx
        }
        if (onClick != null) {
            binding.root.isClickable = true
            binding.root.isFocusable = true
            binding.root.setOnClickListener { onClick() }
        } else {
            binding.root.isClickable = false
            binding.root.isFocusable = false
            binding.root.setOnClickListener(null)
        }

        binding.avatarCircle.applyCircularOutline()
        binding.avatarCircle.background = circleSolidDrawable(palette.primaryContainer)

        binding.avatarOnlineDot.layoutParams = binding.avatarOnlineDot.layoutParams?.apply {
            width = dotPx
            height = dotPx
        }
        val dotColor = if (isOnline) palette.emeraldMint else applyAlpha(palette.outline, 0.75f)
        binding.avatarOnlineDot.background = circleSolidDrawable(
            fillColor = dotColor,
            strokeWidthPx = ctx.dpToPx(2f),
            strokeColor = palette.surface
        )
        binding.avatarOnlineDot.tag = indicatorTag
        binding.avatarOnlineDot.contentDescription = if (isOnline) {
            ctx.getString(R.string.avatar_status_online, safeName)
        } else {
            ctx.getString(R.string.avatar_status_offline, safeName)
        }

        binding.avatarImage.dispose()
        Base64BitmapCache.cancelPendingDecode(binding.avatarImage)

        if (isGroup) {
            binding.avatarInitial.visibility = View.GONE
            binding.avatarImage.visibility = View.GONE
            binding.avatarGroupIcon.visibility = View.VISIBLE
            binding.avatarGroupIcon.setColorFilter(palette.onPrimaryContainer)
            binding.avatarGroupIcon.contentDescription = safeName
            return
        }

        binding.avatarGroupIcon.visibility = View.GONE
        binding.avatarInitial.visibility = View.VISIBLE
        binding.avatarInitial.text = safeName.take(1).uppercase()
        binding.avatarInitial.textSize = if (avatarSizeDp >= 44) 16f else 13f
        binding.avatarInitial.setTextColor(palette.onPrimaryContainer)

        when {
            !avatarBase64.isNullOrBlank() -> {
                binding.avatarImage.visibility = View.INVISIBLE
                binding.avatarImage.contentDescription = "$safeName profile photo"
                Base64BitmapCache.decodeInto(
                    imageView = binding.avatarImage,
                    base64 = avatarBase64,
                    targetMaxPx = avatarPx * 2,
                    preferRgb565 = true
                ) { loaded ->
                    binding.avatarImage.visibility = if (loaded) View.VISIBLE else View.GONE
                }
            }
            !photoUrl.isNullOrBlank() -> {
                binding.avatarImage.visibility = View.VISIBLE
                binding.avatarImage.contentDescription = "$safeName profile photo"
                binding.avatarImage.load(photoUrl) {
                    size(avatarPx * 2, avatarPx * 2)
                    transformations(CircleCropTransformation())
                    crossfade(false)
                    listener(
                        onError = { _, _ -> binding.avatarImage.visibility = View.GONE }
                    )
                }
            }
            else -> {
                binding.avatarImage.visibility = View.GONE
                binding.avatarImage.setImageDrawable(null)
            }
        }
    }

    fun clearAvatar(binding: ViewUserAvatarBinding) {
        binding.avatarImage.dispose()
        Base64BitmapCache.cancelPendingDecode(binding.avatarImage)
        binding.avatarImage.setImageDrawable(null)
        binding.root.setOnClickListener(null)
    }
}

class SpacingItemDecoration(
    private val horizontalEdgePx: Int = 0,
    private val topEdgePx: Int = 0,
    private val bottomEdgePx: Int = 0,
    private val itemGapPx: Int = 0,
    private val orientation: Int = RecyclerView.VERTICAL
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val pos = parent.getChildAdapterPosition(view)
        if (pos == RecyclerView.NO_POSITION) return
        val count = state.itemCount

        if (orientation == RecyclerView.VERTICAL) {
            outRect.left = horizontalEdgePx
            outRect.right = horizontalEdgePx
            outRect.top = if (pos == 0) topEdgePx else itemGapPx
            outRect.bottom = if (pos == count - 1) bottomEdgePx else 0
        } else {
            outRect.top = topEdgePx
            outRect.bottom = bottomEdgePx
            outRect.left = if (pos == 0) horizontalEdgePx else itemGapPx
            outRect.right = if (pos == count - 1) horizontalEdgePx else 0
        }
    }
}

fun configureOptimizedRecyclerView(
    recyclerView: RecyclerView,
    orientation: Int = RecyclerView.VERTICAL,
    viewCacheSize: Int = 8,
    prefetchCount: Int = 4,
    recycledViewPool: RecyclerView.RecycledViewPool? = null
) {
    val lm = LinearLayoutManager(recyclerView.context, orientation, false).apply {
        initialPrefetchItemCount = prefetchCount
    }
    recyclerView.layoutManager = lm
    recyclerView.setHasFixedSize(true)
    recyclerView.setItemViewCacheSize(viewCacheSize)
    if (recycledViewPool != null) {
        recyclerView.setRecycledViewPool(recycledViewPool)
    }
    (recyclerView.itemAnimator as? DefaultItemAnimator)?.supportsChangeAnimations = false
}
