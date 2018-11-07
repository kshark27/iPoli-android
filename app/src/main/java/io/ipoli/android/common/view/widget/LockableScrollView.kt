package io.ipoli.android.common.view.widget

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.ScrollView

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 9/17/17.
 */
class LockableScrollView : ScrollView {
    var isLocked = false
    var scrollChangedListener: (Int) -> Unit = { _ -> }
    var singleTapListener: () -> Unit = {}

    private val gestureDetector =
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                singleTapListener()
                return super.onSingleTapUp(e)
            }
        })

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet)
        : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int)
        : super(context, attrs, defStyleAttr)

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        return if (isLocked) false else super.onInterceptTouchEvent(ev)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent) =
        if (isLocked)
            false
        else
            super.onTouchEvent(ev)


    override fun onScrollChanged(x: Int, y: Int, oldX: Int, oldY: Int) {
        if (!isLocked) {
            scrollChangedListener(y - oldY)
        }
        super.onScrollChanged(x, y, oldX, oldY)
    }
}