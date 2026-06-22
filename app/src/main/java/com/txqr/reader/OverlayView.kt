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
 * 在识别到二维码时绘制高亮边框、角标、脉冲动画、对焦点
 */
class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 边框颜色：检测中黄色，检测完成绿色
    private val scanningColor = Color.parseColor("#FFD740")      // 扫描中 - 黄色
    private val detectedColor = Color.parseColor("#4CAF50")      // 已检测 - 绿色
    private val cornerColor = Color.parseColor("#FFD740")        // 角标 - 亮黄色

    private val borderPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    private val fillPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val cornerPaint = Paint().apply {
        color = cornerColor
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }

    private val gridPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        isAntiAlias = true
    }

    private val pulsePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    // 对焦点
    private val focusPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    private var boxes: List<RectF> = emptyList()
    private var lastDetectedTime = 0L
    private var focusPoint: Pair<Float, Float>? = null
    private var focusAnimStart = 0L

    companion object {
        private const val BOX_PERSIST_MS = 500L   // 识别框持续显示时间
        private const val FOCUS_ANIM_MS = 1200L    // 对焦点动画时长
        private const val PULSE_FRAME = 16L         // 60fps
    }

    fun updateBarcodes(
        barcodes: List<Barcode>,
        imageWidth: Int, imageHeight: Int,
        viewWidth: Int, viewHeight: Int,
        rotationDegrees: Int
    ) {
        val newBoxes = barcodes.mapNotNull { barcode ->
            barcode.boundingBox?.let { rect ->
                mapToViewRect(rect, imageWidth, imageHeight, viewWidth, viewHeight, rotationDegrees)
            }
        }
        if (newBoxes.isNotEmpty()) {
            boxes = newBoxes
            lastDetectedTime = System.currentTimeMillis()
            invalidate()
        } else if (boxes.isNotEmpty()) {
            // 没有新检测到时，保持旧框一小段时间再消失
            if (System.currentTimeMillis() - lastDetectedTime > BOX_PERSIST_MS) {
                boxes = emptyList()
                invalidate()
            } else {
                // 还在持续时间内，继续显示 + 动画
                postInvalidateDelayed(PULSE_FRAME)
            }
        }
    }

    fun showFocusPoint(x: Float, y: Float) {
        focusPoint = Pair(x, y)
        focusAnimStart = System.currentTimeMillis()
        invalidate()
    }

    fun clear() {
        boxes = emptyList()
        lastDetectedTime = 0L
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 画暗角
        canvas.drawColor(Color.parseColor("#08000000"))

        // 绘制扫描区域框（始终显示）
        drawScanArea(canvas)

        // 绘制二维码识别框
        if (boxes.isNotEmpty()) {
            val elapsed = System.currentTimeMillis() - lastDetectedTime
            val isScanning = elapsed < 200

            for (box in boxes) {
                // 填充
                fillPaint.color = if (isScanning) Color.parseColor("#33FFD740") else Color.parseColor("#334CAF50")
                canvas.drawRect(box, fillPaint)

                // 边框
                borderPaint.color = if (isScanning) scanningColor else detectedColor
                canvas.drawRect(box, borderPaint)

                // 角标
                drawCorners(canvas, box)

                // 网格（扫描中显示）
                if (isScanning) {
                    gridPaint.color = scanningColor
                    gridPaint.alpha = 60
                    drawGrid(canvas, box)
                }

                // 脉冲
                drawPulse(canvas, box, scanningColor)
            }

            // 继续动画
            postInvalidateDelayed(PULSE_FRAME)
        }

        // 绘制对焦点
        focusPoint?.let { (x, y) ->
            drawFocusPoint(canvas, x, y)
        }
    }

    private fun drawScanArea(canvas: Canvas) {
        val cw = width.toFloat() / 2f
        val ch = height.toFloat() / 2f
        val size = minOf(width, height).toFloat() * 0.7f
        val l = cw - size / 2f
        val t = ch - size / 2f
        val r = l + size
        val b = t + size

        val scanPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2f
            alpha = 50
            isAntiAlias = true
        }
        canvas.drawRect(l, t, r, b, scanPaint)

        val cornerLen = 30f
        val cornerScanPaint = Paint().apply {
            color = Color.parseColor("#4CAF50")
            style = Paint.Style.STROKE
            strokeWidth = 3f
            alpha = 100
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
        }

        canvas.drawLine(l, t + cornerLen, l, t, cornerScanPaint)
        canvas.drawLine(l, t, l + cornerLen, t, cornerScanPaint)

        canvas.drawLine(r - cornerLen, t, r, t, cornerScanPaint)
        canvas.drawLine(r, t, r, t + cornerLen, cornerScanPaint)

        canvas.drawLine(r, b - cornerLen, r, b, cornerScanPaint)
        canvas.drawLine(r, b, r - cornerLen, b, cornerScanPaint)

        canvas.drawLine(l, b - cornerLen, l, b, cornerScanPaint)
        canvas.drawLine(l, b, l + cornerLen, b, cornerScanPaint)
    }

    private fun drawCorners(canvas: Canvas, rect: RectF) {
        val len = 40f
        val path = Path()
        path.moveTo(rect.left, rect.top + len)
        path.lineTo(rect.left, rect.top)
        path.lineTo(rect.left + len, rect.top)

        path.moveTo(rect.right - len, rect.top)
        path.lineTo(rect.right, rect.top)
        path.lineTo(rect.right, rect.top + len)

        path.moveTo(rect.right, rect.bottom - len)
        path.lineTo(rect.right, rect.bottom)
        path.lineTo(rect.right - len, rect.bottom)

        path.moveTo(rect.left + len, rect.bottom)
        path.lineTo(rect.left, rect.bottom)
        path.lineTo(rect.left, rect.bottom - len)

        canvas.drawPath(path, cornerPaint)
    }

    private fun drawGrid(canvas: Canvas, rect: RectF) {
        val step = 20f
        var x = rect.left
        while (x < rect.right) {
            canvas.drawLine(x, rect.top, x, rect.bottom, gridPaint)
            x += step
        }
        var y = rect.top
        while (y < rect.bottom) {
            canvas.drawLine(rect.left, y, rect.right, y, gridPaint)
            y += step
        }
    }

    private fun drawPulse(canvas: Canvas, rect: RectF, color: Int) {
        val elapsed = System.currentTimeMillis() % 1500
        val progress = elapsed / 1500f
        val expand = 8f + progress * 20f
        val alpha = (155 * (1 - progress)).toInt()

        pulsePaint.color = color
        pulsePaint.alpha = alpha

        val expand2 = expand * 0.6f
        val alpha2 = (100 * (1 - progress)).toInt()
        pulsePaint.alpha = alpha2

        canvas.drawRect(
            rect.left - expand, rect.top - expand,
            rect.right + expand, rect.bottom + expand,
            pulsePaint
        )
        pulsePaint.alpha = alpha
        canvas.drawRect(
            rect.left - expand2, rect.top - expand2,
            rect.right + expand2, rect.bottom + expand2,
            pulsePaint
        )
    }

    private fun drawFocusPoint(canvas: Canvas, x: Float, y: Float) {
        val elapsed = System.currentTimeMillis() - focusAnimStart
        if (elapsed >= FOCUS_ANIM_MS) {
            focusPoint = null
            return
        }

        val progress = elapsed / FOCUS_ANIM_MS.toFloat()
        val alpha = (255 * (1 - progress)).toInt()
        val radius = 24f + progress * 40f

        focusPaint.alpha = alpha
        val fill = Paint(focusPaint).apply { style = Paint.Style.FILL; alpha = alpha / 3 }
        canvas.drawCircle(x, y, radius, fill)
        canvas.drawCircle(x, y, radius, focusPaint)

        // 十字线
        val crossPaint = Paint(focusPaint).apply { alpha = alpha / 2 }
        canvas.drawLine(x - 20f, y, x - 6f, y, crossPaint)
        canvas.drawLine(x + 6f, y, x + 20f, y, crossPaint)
        canvas.drawLine(x, y - 20f, x, y - 6f, crossPaint)
        canvas.drawLine(x, y + 6f, x, y + 20f, crossPaint)

        // 对勾（成功指示，前500ms）
        if (elapsed < 500) {
            val checkPaint = Paint(focusPaint).apply {
                color = Color.parseColor("#4CAF50")
                alpha = alpha
                strokeWidth = 4f
            }
            canvas.drawLine(x - 10f, y, x - 3f, y + 7f, checkPaint)
            canvas.drawLine(x - 3f, y + 7f, x + 10f, y - 7f, checkPaint)
        }

        postInvalidateDelayed(PULSE_FRAME)
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
}
