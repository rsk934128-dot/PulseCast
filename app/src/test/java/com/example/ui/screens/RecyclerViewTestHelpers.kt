package com.example.ui.screens

import android.app.Activity
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.robolectric.Shadows

internal fun findViewByTagRaw(root: View, tag: String): View? {
    if (tag == root.tag) return root
    if (root is ViewGroup) {
        for (i in 0 until root.childCount) {
            val found = findViewByTagRaw(root.getChildAt(i), tag)
            if (found != null) return found
        }
    }
    return null
}

internal inline fun <reified T : View> findViewByTag(root: View, tag: String): T? =
    findViewByTagRaw(root, tag) as? T

internal fun findTextInViewTree(root: View, expectedText: String): Boolean {
    if (root is android.widget.TextView && root.visibility == View.VISIBLE && root.text?.toString() == expectedText) {
        return true
    }
    if (root is ViewGroup) {
        for (i in 0 until root.childCount) {
            if (findTextInViewTree(root.getChildAt(i), expectedText)) return true
        }
    }
    return false
}

internal fun settleRecyclerUi(composeRule: AndroidComposeTestRule<*, *>) {
    composeRule.waitForIdle()
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    composeRule.waitForIdle()
}

internal inline fun <reified T : View> waitForViewByTag(
    composeRule: AndroidComposeTestRule<*, *>,
    tag: String,
    timeoutMs: Long = 3_000L
): T {
    composeRule.waitUntil(timeoutMillis = timeoutMs) {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        val view = findViewByTag<T>(composeRule.activity.window.decorView, tag)
        view != null && view.isShown
    }
    val view = findViewByTag<T>(composeRule.activity.window.decorView, tag)
    assertNotNull("Expected view with tag '$tag' to exist", view)
    return view!!
}

internal fun waitUntilViewAbsentOrHidden(
    composeRule: AndroidComposeTestRule<*, *>,
    tag: String,
    timeoutMs: Long = 3_000L
) {
    composeRule.waitUntil(timeoutMillis = timeoutMs) {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        val view = findViewByTag<View>(composeRule.activity.window.decorView, tag)
        view == null || !view.isShown
    }
    val view = findViewByTag<View>(composeRule.activity.window.decorView, tag)
    assertTrue(
        "Expected view with tag '$tag' to not exist or be hidden",
        view == null || !view.isShown
    )
}

internal fun waitUntilTextVisibleInRecycler(
    composeRule: AndroidComposeTestRule<*, *>,
    recyclerTag: String,
    expectedText: String,
    timeoutMs: Long = 3_000L
) {
    composeRule.waitUntil(timeoutMillis = timeoutMs) {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        val rv = findViewByTag<View>(composeRule.activity.window.decorView, recyclerTag) ?: return@waitUntil false
        findTextInViewTree(rv, expectedText)
    }
    val rv = findViewByTag<View>(composeRule.activity.window.decorView, recyclerTag)
    assertNotNull(rv)
    assertTrue(
        "Expected '$expectedText' inside RecyclerView '$recyclerTag'",
        findTextInViewTree(rv!!, expectedText)
    )
}

internal fun scrollRecyclerToViewTag(
    composeRule: AndroidComposeTestRule<*, *>,
    recyclerTag: String,
    itemTag: String
) {
    val rv = waitForViewByTag<RecyclerView>(composeRule, recyclerTag)
    if (findViewByTag<View>(rv, itemTag)?.isShown == true) return
    val count = rv.adapter?.itemCount ?: 0
    for (pos in 0 until count) {
        composeRule.runOnUiThread {
            rv.scrollToPosition(pos)
            rv.requestLayout()
            val width = rv.width.takeIf { it > 0 } ?: 1080
            val height = rv.height.takeIf { it > 0 } ?: 1920
            rv.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            rv.layout(rv.left, rv.top, rv.left + width, rv.top + height)
        }
        settleRecyclerUi(composeRule)
        if (findViewByTag<View>(rv, itemTag)?.isShown == true) {
            return
        }
    }
    assertNotNull("Failed to scroll RecyclerView '$recyclerTag' to view '$itemTag'", findViewByTag<View>(rv, itemTag))
}
