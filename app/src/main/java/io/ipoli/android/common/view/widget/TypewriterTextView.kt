package io.ipoli.android.common.view.widget

import android.content.Context
import android.os.Handler
import android.support.v4.content.ContextCompat
import android.support.v7.widget.AppCompatTextView
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import io.ipoli.android.R

class TypewriterTextView : AppCompatTextView {

    private var stringBuilder: SpannableStringBuilder? = null
    private var visibleSpan: ForegroundColorSpan? = null
    private var hiddenSpan: ForegroundColorSpan? = null
    private var index: Int = 0
    private var delay: Long = 30
    var animationCompleteCallback: () -> Unit = {}

    private val textHandler = Handler()
    private val characterAdder = object : Runnable {
        override fun run() {
            stringBuilder?.setSpan(visibleSpan, 0, index++, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
            text = stringBuilder
            if (index <= stringBuilder?.length ?: 0) {
                textHandler.postDelayed(this, delay)
            } else {
                animationCompleteCallback()
            }
        }
    }

    val isAnimating: Boolean
        get() = index < stringBuilder?.length ?: 0


    constructor(context: Context) : super(context) {
        initUI(context)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        initUI(context)
    }

    private fun initUI(context: Context) {
        setOnClickListener {
            stopTextAnimation()
            animationCompleteCallback()
        }
        setupTextColors(context)
    }

    private fun setupTextColors(context: Context) {
        visibleSpan = ForegroundColorSpan(ContextCompat.getColor(context, R.color.md_dark_text_87))
        hiddenSpan =
            ForegroundColorSpan(ContextCompat.getColor(context, android.R.color.transparent))
    }

    fun animateText(text: CharSequence) {
        stringBuilder = SpannableStringBuilder(text)
        stringBuilder?.setSpan(
            hiddenSpan,
            0,
            stringBuilder?.length ?: 0,
            Spannable.SPAN_INCLUSIVE_EXCLUSIVE
        )
        index = 0

        setText(stringBuilder)
        textHandler.removeCallbacks(characterAdder)
        textHandler.postDelayed(characterAdder, delay)
    }

    override fun onDetachedFromWindow() {
        textHandler.removeCallbacks(characterAdder)
        super.onDetachedFromWindow()
    }

    fun setCharacterDelay(millis: Long) {
        delay = millis
    }

    fun stopTextAnimation() {
        index = stringBuilder?.length ?: 0
    }
}