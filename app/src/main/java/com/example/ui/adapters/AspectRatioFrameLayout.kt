package com.example.ui.adapters

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * Lightweight 16:9 container for VideoFeedCard cover artwork that enforces
 * exact aspect ratio in a single measure pass without ConstraintLayout overhead.
 */
class AspectRatioFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var aspectRatio: Float = 16f / 9f
        set(value) {
            if (field != value && value > 0f) {
                field = value
                requestLayout()
            }
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        if (widthMode == MeasureSpec.EXACTLY || widthMode == MeasureSpec.AT_MOST) {
            val calculatedHeight = (widthSize / aspectRatio).toInt().coerceAtLeast(1)
            val exactHeightSpec = MeasureSpec.makeMeasureSpec(calculatedHeight, MeasureSpec.EXACTLY)
            super.onMeasure(widthMeasureSpec, exactHeightSpec)
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
}
