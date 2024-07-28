package com.company.chamberly.activities

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import com.company.chamberly.R

class DottedCircularProgressBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = 0xFF7A7AFF.toInt() // Black color for the dots
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private var dotCount = 12
    private var dotRadius = 10f
    private var dotSpacing = 20f // Spacing between dots
    private var progress = 0f
    private var animating = false

    init {
        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.DottedCircularProgressBar)
            dotCount = typedArray.getInt(R.styleable.DottedCircularProgressBar_dotCount, 12)
            dotRadius = typedArray.getDimension(R.styleable.DottedCircularProgressBar_dotRadius, 10f)
            dotSpacing = typedArray.getDimension(R.styleable.DottedCircularProgressBar_dotSpacing, 20f)
            typedArray.recycle()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(centerX, centerY) - (dotRadius + dotSpacing) * 2

        for (i in 0 until dotCount) {
            val angle = Math.PI * 2 * i / dotCount + progress
            val x = centerX + (radius * cos(angle)).toFloat()
            val y = centerY + (radius * sin(angle)).toFloat()
            canvas.drawCircle(x, y, dotRadius, paint)
        }
    }

    fun startAnimation() {
        animating = true
        animateProgress()
    }

    fun stopAnimation() {
        animating = false
    }

    private fun animateProgress() {
        if (!animating) return

        progress += 0.1f
        if (progress >= Math.PI * 2) {
            progress = 0f
        }
        invalidate()
        postDelayed({ animateProgress() }, 16) // Approximately 60 FPS
    }
}
