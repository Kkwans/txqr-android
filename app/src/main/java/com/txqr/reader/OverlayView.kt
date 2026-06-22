package com.txqr.reader

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.google.mlkit.vision.barcode.common.Barcode

class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var barcodes: List<Barcode> = emptyList()
    private var focusX = 0f
    private var focusY = 0f
    private var focusStartTime = 0L
    private val FOCUS_ANIM_MS = 800L
    private var lastDetectTime = 0L
    private val DETECT_HOLD_MS = 300L

    private val focusPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    fun updateBarcodes(
        detected: List<Barcode>,
        imgW: Int, imgH: Int,
        vW: Int, vH: Int,
        imgRotation: Int
    ) {
        barcodes = detected
        lastDetectTime = System.currentTimeMillis()
        invalidate()
    }

    fun showFocusPoint(x: Float, y: Float) {
        focusX = x
        focusY = y
        focusStartTime = System.currentTimeMillis()
        invalidate()
    }

    fun clear() {
        barcodes = emptyList()
        lastDetectTime = 0L
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val now = System.currentTimeMillis()

        // 绘制扫描区域参考框
        drawScanArea(canvas)

        // 焦点动画
        if (focusStartTime > 0 && now - focusStartTime < FOCUS_ANIM_MS) {
            drawFocusAnim(canvas, now)
            postInvalidateDelayed(16)
        }

        // 有识别结果时持续重绘
        if (barcodes.isNotEmpty() || now - lastDetectTime < DETECT_HOLD_MS) {
            postInvalidateDelayed(50)
        }
    }

    private fun drawScanArea(canvas: Canvas) {
        val cw = width.toFloat() / 2f
        val ch = height.toFloat() / 2f
        val size = minOf(width, height).toFloat() * 0.65f
        val l = cw - size / 2f
        val t = ch - size / 2f
        val r = l + size
        val b = t + size

        // 暗色遮罩
        val maskPaint = Paint().apply {
            color = Color.parseColor("#33000000")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), t, maskPaint)
        canvas.drawRect(0f, t, l, b, maskPaint)
        canvas.drawRect(r, t, width.toFloat(), b, maskPaint)
        canvas.drawRect(0f, b, width.toFloat(), height.toFloat(), maskPaint)

        // 虚线边框
        val dashPaint = Paint().apply {
            color = Color.parseColor("#80FFFFFF")
            style = Paint.Style.STROKE
            strokeWidth = 1f
            isAntiAlias = true
            pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
        }
        canvas.drawRect(l, t, r, b, dashPaint)

        // 四角短标
        val cornerLen = 24f
        val cPaint = Paint().apply {
            color = Color.parseColor("#FFFFFF")
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(l, t + cornerLen, l, t, cPaint)
        canvas.drawLine(l, t, l + cornerLen, t, cPaint)
        canvas.drawLine(r - cornerLen, t, r, t, cPaint)
        canvas.drawLine(r, t, r, t + cornerLen, cPaint)
        canvas.drawLine(r, b - cornerLen, r, b, cPaint)
        canvas.drawLine(r, b, r - cornerLen, b, cPaint)
        canvas.drawLine(l, b - cornerLen, l, b, cPaint)
        canvas.drawLine(l, b, l + cornerLen, b, cPaint)
    }

    private fun drawFocusAnim(canvas: Canvas, now: Long) {
        val elapsed = now - focusStartTime
        if (elapsed > FOCUS_ANIM_MS) return
        val progress = elapsed.toFloat() / FOCUS_ANIM_MS
        val alpha = (255 * (1 - progress)).toInt()
        val radius = 20f + progress * 35f

        val a = alpha.toFloat()
        focusPaint.alpha = a.toInt()
        canvas.drawCircle(focusX, focusY, radius, focusPaint)

        val crossPaint = Paint(focusPaint).apply { this.alpha = (a * 0.5f).toInt() }
        canvas.drawLine(focusX - 16f, focusY, focusX - 5f, focusY, crossPaint)
        canvas.drawLine(focusX + 5f, focusY, focusX + 16f, focusY, crossPaint)
        canvas.drawLine(focusX, focusY - 16f, focusX, focusY - 5f, crossPaint)
        canvas.drawLine(focusX, focusY + 5f, focusX, focusY + 16f, crossPaint)
    }
}
