package com.txqr.reader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.google.mlkit.vision.barcode.common.Barcode

/**
 * 二维码识别框叠加层
 * 在识别到二维码时绘制高亮边框、角标、对焦点
 */
class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val boxPaint = Paint().apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val cornerPaint = Paint().apply {
        color = Color.parseColor("#FFEB3B")
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }

    private val fillPaint = Paint().apply {
        color = Color.parseColor("#334CAF50")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val focusPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    private val focusFillPaint = Paint().apply {
        color = Color.parseColor("#33FFFFFF")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private var boxes: List<RectF> = emptyList()
    private var focusPoint: Pair<Float, Float>? = null
    private var focusAnimStart = 0L
    private var hasDetected = false

    fun updateBarcodes(
        barcodes: List<Barcode>,
        imageWidth: Int, imageHeight: Int,
        viewWidth: Int, viewHeight: Int,
        rotationDegrees: Int
    ) {
        boxes = barcodes.mapNotNull { barcode ->
            barcode.boundingBox?.let { rect ->
                mapToViewRect(rect, imageWidth, imageHeight, viewWidth, viewHeight, rotationDegrees)
            }
        }
        hasDetected = boxes.isNotEmpty()
        invalidate()
    }

    fun showFocusPoint(x: Float, y: Float) {
        focusPoint = Pair(x, y)
        focusAnimStart = System.currentTimeMillis()
        invalidate()
    }

    fun clear() {
        boxes = emptyList()
        hasDetected = false
        invalidate()
    }

    private fun mapToViewRect(
        rect: android.graphics.Rect,
        imageWidth: Int, imageHeight: Int,
        viewWidth: Int, viewHeight: Int,
        rotationDegrees: Int
    ): RectF {
        val (w, h) = when (rotationDegrees) {
            90, 270 -> Pair(imageHeight, imageWidth)
            else -> Pair(imageWidth, imageHeight)
        }
        val scaleX = viewWidth.toFloat() / w
        val scaleY = viewHeight.toFloat() / h
        val scale = maxOf(scaleX, scaleY)
        val offsetX = (viewWidth - w * scale) / 2f
        val offsetY = (viewHeight - h * scale) / 2f
        return RectF(
            rect.left * scale + offsetX,
            rect.top * scale + offsetY,
            rect.right * scale + offsetX,
            rect.bottom * scale + offsetY
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 绘制二维码识别框
        if (hasDetected) {
            for (box in boxes) {
                canvas.drawRect(box, fillPaint)
                canvas.drawRect(box, boxPaint)
                drawCorners(canvas, box)
                drawPulse(canvas, box)
            }
        }

        // 绘制对焦点
        focusPoint?.let { (x, y) ->
            val elapsed = System.currentTimeMillis() - focusAnimStart
            if (elapsed < 1000) {
                val progress = elapsed / 1000f
                val radius = 30f + progress * 30f
                val alpha = (255 * (1 - progress)).toInt()

                focusPaint.alpha = alpha
                focusFillPaint.alpha = alpha / 3

                canvas.drawCircle(x, y, radius, focusFillPaint)
                canvas.drawCircle(x, y, radius, focusPaint)

                // 对焦成功指示
                if (elapsed < 500) {
                    val checkPaint = Paint(focusPaint).apply {
                        color = Color.parseColor("#4CAF50")
                        this.alpha = alpha
                        strokeWidth = 4f
                    }
                    canvas.drawLine(x - 12f, y, x - 4f, y + 8f, checkPaint)
                    canvas.drawLine(x - 4f, y + 8f, x + 12f, y - 8f, checkPaint)
                }

                postInvalidateDelayed(16)
            } else {
                focusPoint = null
            }
        }
    }

    private fun drawCorners(canvas: Canvas, rect: RectF) {
        val len = 40f
        val path = Path()
        path.moveTo(rect.left, rect.top + len); path.lineTo(rect.left, rect.top); path.lineTo(rect.left + len, rect.top)
        path.moveTo(rect.right - len, rect.top); path.lineTo(rect.right, rect.top); path.lineTo(rect.right, rect.top + len)
        path.moveTo(rect.right, rect.bottom - len); path.lineTo(rect.right, rect.bottom); path.lineTo(rect.right - len, rect.bottom)
        path.moveTo(rect.left + len, rect.bottom); path.lineTo(rect.left, rect.bottom); path.lineTo(rect.left, rect.bottom - len)
        canvas.drawPath(path, cornerPaint)
    }

    private fun drawPulse(canvas: Canvas, rect: RectF) {
        val pulsePaint = Paint(boxPaint).apply {
            strokeWidth = 2f
            alpha = (128 + 127 * Math.sin((System.currentTimeMillis() % 1000) / 1000.0 * 2 * Math.PI)).toInt()
        }
        val expand = 12f
        canvas.drawRect(rect.left - expand, rect.top - expand, rect.right + expand, rect.bottom + expand, pulsePaint)
        postInvalidateDelayed(16)
    }
}
