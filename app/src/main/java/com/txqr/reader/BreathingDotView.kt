package com.txqr.reader

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * 涟漪呼吸动画圆点
 * 原始圆点不变，外圈浅色环缩放呼吸
 */
class BreathingDotView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
    }

    private var dotColor = Color.parseColor("#FFC107")
    private var ringScale = 1f
    private var animator: ValueAnimator? = null

    fun setColor(hex: String) {
        dotColor = Color.parseColor(hex)
        ringPaint.color = dotColor and 0x44FFFFFF.toInt() // 浅色半透明
        dotPaint.color = dotColor
        invalidate()
    }

    fun startBreathing() {
        stopBreathing()
        animator = ValueAnimator.ofFloat(1f, 2.2f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener {
                ringScale = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stopBreathing() {
        animator?.cancel()
        animator = null
        ringScale = 1f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val dotR = cx.coerceAtMost(cy) * 0.6f

        // 画外圈涟漪环
        ringPaint.strokeWidth = dotR * 0.3f
        ringPaint.color = dotColor and 0x33FFFFFF.toInt()
        canvas.drawCircle(cx, cy, dotR * ringScale, ringPaint)

        // 画原始圆点
        dotPaint.color = dotColor
        canvas.drawCircle(cx, cy, dotR, dotPaint)
    }
}
