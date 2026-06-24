package com.txqr.reader

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * 呼吸动画圆点
 * 参考 Heimdall 的 box-shadow 脉冲效果：
 * 中心实心圆不变，外圈同色半透明实心圆从中心向外扩散并渐隐，无间隙
 */
class BreathingDotView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var dotColor = Color.parseColor("#FFC107")
    private var glowRadius = 0f      // 当前光晕半径
    private var glowAlpha = 0f       // 当前光晕透明度 (0~1)
    private var animator: ValueAnimator? = null

    fun setColor(hex: String) {
        dotColor = Color.parseColor(hex)
        dotPaint.color = dotColor
        invalidate()
    }

    fun startBreathing() {
        stopBreathing()
        // 0→1 周期：光晕从无到最大再消失
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                // 光晕半径：0 → 最大（中心点直径的 2.5 倍）
                glowRadius = t * 2.5f
                // 光晕透明度：前半段渐显，后半段渐隐
                glowAlpha = if (t < 0.5f) {
                    t * 2f * 0.4f  // 0 → 0.4
                } else {
                    (1f - t) * 2f * 0.4f  // 0.4 → 0
                }
                invalidate()
            }
            start()
        }
    }

    fun stopBreathing() {
        animator?.cancel()
        animator = null
        glowRadius = 0f
        glowAlpha = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val dotR = cx.coerceAtMost(cy) * 0.45f

        // 画呼吸光晕（实心圆，从中心扩散，渐隐，无间隙）
        if (glowAlpha > 0.01f) {
            val alpha = (glowAlpha * 255).toInt().coerceIn(0, 255)
            val glowColor = Color.argb(alpha, Color.red(dotColor), Color.green(dotColor), Color.blue(dotColor))
            glowPaint.color = glowColor
            canvas.drawCircle(cx, cy, dotR * glowRadius, glowPaint)
        }

        // 画中心实心圆点（不变）
        dotPaint.color = dotColor
        canvas.drawCircle(cx, cy, dotR, dotPaint)
    }
}
