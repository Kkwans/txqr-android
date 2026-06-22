package com.txqr.reader

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.google.mlkit.vision.barcode.common.Barcode

class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var barcodes: List<Barcode> = emptyList()
    private var imageWidth = 0
    private var imageHeight = 0
    private var viewWidth = 0
    private var viewHeight = 0
    private var rotation = 0

    // 焦点动画
    private var focusX = 0f
    private var focusY = 0f
    private var focusStartTime = 0L
    private val FOCUS_ANIM_MS = 800L

    // 识别框持续显示
    private var lastDetectTime = 0L
    private val DETECT_HOLD_MS = 300L

    // 脉冲动画
    private var pulseAnimator: ValueAnimator? = null
    private var pulseProgress = 0f

    // 画笔 - 识别框（绿色半透明填充 + 绿色边框）
    private val fillPaint = Paint().apply {
        color = Color.parseColor("#334CAF50")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val borderPaint = Paint().apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    // 角标画笔（橙色加粗）
    private val cornerPaint = Paint().apply {
        color = Color.parseColor("#FF9800")
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }
    // 焦点画笔
    private val focusPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }
    // 扫描线画笔
    private val scanLinePaint = Paint().apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.FILL
        isAntiAlias = true
        alpha = 80
    }

    fun updateBarcodes(
        detected: List<Barcode>,
        imgW: Int, imgH: Int,
        vW: Int, vH: Int,
        imgRotation: Int
    ) {
        barcodes = detected
        imageWidth = imgW
        imageHeight = imgH
        viewWidth = vW
        viewHeight = vH
        rotation = imgRotation
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

        // 绘制识别到的二维码精确框
        if (barcodes.isNotEmpty() || now - lastDetectTime < DETECT_HOLD_MS) {
            for (barcode in barcodes) {
                drawBarcodeOutline(canvas, barcode)
            }
        }

        // 绘制焦点动画
        if (focusStartTime > 0 && now - focusStartTime < FOCUS_ANIM_MS) {
            drawFocusAnim(canvas, now)
            postInvalidateDelayed(16)
        }

        // 持续重绘（如果有识别结果）
        if (barcodes.isNotEmpty()) {
            postInvalidateDelayed(50)
        }
    }

    /** 绘制屏幕中央的扫描区域参考线 */
    private fun drawScanArea(canvas: Canvas) {
        val cw = width.toFloat() / 2f
        val ch = height.toFloat() / 2f
        val size = minOf(width, height).toFloat() * 0.65f
        val l = cw - size / 2f
        val t = ch - size / 2f
        val r = l + size
        val b = t + size

        // 暗色遮罩（扫描区域外）
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

    /** 绘制二维码精确边界（使用角点坐标） */
    private fun drawBarcodeOutline(canvas: Canvas, barcode: Barcode) {
        val points = barcode.cornerPoints
        if (points == null || points.size < 4) return

        // 将图像坐标映射到视图坐标
        val mappedPoints = points.map { p ->
            mapPoint(p.x.toFloat(), p.y.toFloat())
        }

        // 绘制填充
        val path = Path().apply {
            moveTo(mappedPoints[0].x, mappedPoints[0].y)
            for (i in 1 until mappedPoints.size) {
                lineTo(mappedPoints[i].x, mappedPoints[i].y)
            }
            close()
        }
        canvas.drawPath(path, fillPaint)

        // 绘制边框
        canvas.drawPath(path, borderPaint)

        // 绘制四角加粗标记
        val cornerLen = 16f
        for (i in mappedPoints.indices) {
            val curr = mappedPoints[i]
            val next = mappedPoints[(i + 1) % mappedPoints.size]
            val dx = next.x - curr.x
            val dy = next.y - curr.y
            val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            if (dist == 0f) continue
            val ux = dx / dist
            val uy = dy / dist
            canvas.drawLine(curr.x, curr.y, curr.x + ux * cornerLen, curr.y + uy * cornerLen, cornerPaint)
            val prev = mappedPoints[(i - 1 + mappedPoints.size) % mappedPoints.size]
            val pdx = prev.x - curr.x
            val pdy = prev.y - curr.y
            val pdist = Math.sqrt((pdx * pdx + pdy * pdy).toDouble()).toFloat()
            if (pdist > 0f) {
                canvas.drawLine(curr.x, curr.y, curr.x + pdx / pdist * cornerLen, curr.y + pdy / pdist * cornerLen, cornerPaint)
            }
        }

        // 中心脉冲效果
        val cx = mappedPoints.map { it.x }.average().toFloat()
        val cy = mappedPoints.map { it.y }.average().toFloat()
        val elapsed = System.currentTimeMillis() % 1000
        val progress = elapsed / 1000f
        val pulseAlpha = (60 * (1 - progress)).toInt()
        val pulseRadius = 8f + progress * 20f
        val pulsePaint = Paint().apply {
            color = Color.parseColor("#4CAF50")
            style = Paint.Style.FILL
            isAntiAlias = true
            alpha = pulseAlpha
        }
        canvas.drawCircle(cx, cy, pulseRadius, pulsePaint)
    }

    /** 将图像坐标映射到视图坐标（考虑旋转和缩放） */
    private fun mapPoint(imgX: Float, imgY: Float): PointF {
        val vw = viewWidth.toFloat()
        val vh = viewHeight.toFloat()
        val iw = imageWidth.toFloat()
        val ih = imageHeight.toFloat()

        // 根据旋转调整坐标
        val (rx, ry) = when (rotation) {
            90 -> Pair(ih - imgY, imgX)
            180 -> Pair(iw - imgX, ih - imgY)
            270 -> Pair(imgY, iw - imgX)
            else -> Pair(imgX, imgY)
        }

        // 计算缩放和偏移
        val rotatedW = if (rotation == 90 || rotation == 270) ih else iw
        val rotatedH = if (rotation == 90 || rotation == 270) iw else ih
        val scale = maxOf(vw / rotatedW, vh / rotatedH)
        val offsetX = (vw - rotatedW * scale) / 2f
        val offsetY = (vh - rotatedH * scale) / 2f

        return PointF(rx * scale + offsetX, ry * scale + offsetY)
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

        // 十字线
        val crossPaint = Paint(focusPaint).apply { this.alpha = (a * 0.5f).toInt() }
        canvas.drawLine(focusX - 16f, focusY, focusX - 5f, focusY, crossPaint)
        canvas.drawLine(focusX + 5f, focusY, focusX + 16f, focusY, crossPaint)
        canvas.drawLine(focusX, focusY - 16f, focusX, focusY - 5f, crossPaint)
        canvas.drawLine(focusX, focusY + 5f, focusX, focusY + 16f, crossPaint)
    }
}
