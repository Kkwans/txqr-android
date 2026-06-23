package com.txqr.reader

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import android.util.Size
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import mobile.Mobile
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var decoder: mobile.Decoder
    private lateinit var statusText: TextView
    private lateinit var frameCountText: TextView
    private lateinit var previewView: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var hintText: TextView
    private lateinit var resultPanel: LinearLayout
    private lateinit var progressCard: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var progressPercent: TextView
    private lateinit var decoderStatus: TextView
    private lateinit var diagnosticInfo: TextView
    private lateinit var fileInfo: TextView
    private lateinit var progressTitle: TextView
    private lateinit var btnStartScan: Button
    private lateinit var scanButtons: LinearLayout
    private lateinit var btnOpenFile: Button
    private lateinit var btnOpenDir: Button
    private lateinit var btnNextFile: Button
    private lateinit var btnRestart: Button
    private lateinit var btnStop: Button
    private lateinit var btnSettings: ImageButton
    private lateinit var dotIndicator: BreathingDotView

    private val isProcessing = AtomicBoolean(false)
    private var fileCount = 0
    private var lastSavedFile: File? = null
    private var isStopped = false
    private var isPaused = false
    private var isScanning = false
    private var isDecoding = false  // 是否已开始解码（扫描到二维码）

    private var totalFramesProcessed = 0
    private var newFrames = 0
    private var duplicateFrames = 0
    private var uniqueFrames = 0

    private var currentCamera: Camera? = null
    private lateinit var scaleDetector: ScaleGestureDetector
    private lateinit var gestureDetector: GestureDetector

    private val prefs by lazy { getSharedPreferences("txqr", MODE_PRIVATE) }

    private val txqrDir: File
        get() {
            val custom = prefs.getString("save_dir", "") ?: ""
            return if (custom.isNotEmpty()) File(custom)
            else File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "TXQR")
        }

    private fun getSaveDirUri(): Uri? {
        val uriStr = prefs.getString("save_dir_uri", "") ?: ""
        return if (uriStr.isNotEmpty()) Uri.parse(uriStr) else null
    }

    companion object {
        private const val TAG = "TxqrReader"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private const val KEY_FILE_COUNT = "file_count"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupImmersiveMode()
        setContentView(R.layout.activity_main)

        val topBar = findViewById<LinearLayout>(R.id.topBar)
        val statusBarHeight = getStatusBarHeight()
        topBar.setPadding(topBar.paddingLeft, statusBarHeight + 8, topBar.paddingRight, topBar.paddingBottom)

        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.overlayView)
        statusText = findViewById(R.id.statusText)
        frameCountText = findViewById(R.id.frameCountText)
        hintText = findViewById(R.id.hintText)
        resultPanel = findViewById(R.id.resultPanel)
        progressCard = findViewById(R.id.progressCard)
        progressBar = findViewById(R.id.progressBar)
        progressPercent = findViewById(R.id.progressPercent)
        decoderStatus = findViewById(R.id.decoderStatus)
        diagnosticInfo = findViewById(R.id.diagnosticInfo)
        fileInfo = findViewById(R.id.fileInfo)
        progressTitle = findViewById(R.id.progressTitle)
        btnStartScan = findViewById(R.id.btnStartScan)
        scanButtons = findViewById(R.id.scanButtons)
        btnOpenFile = findViewById(R.id.btnOpenFile)
        btnOpenDir = findViewById(R.id.btnOpenDir)
        btnNextFile = findViewById(R.id.btnNextFile)
        btnRestart = findViewById(R.id.btnRestart)
        btnStop = findViewById(R.id.btnStop)
        btnSettings = findViewById(R.id.btnSettings)

        dotIndicator = findViewById(R.id.dotIndicator)

        decoder = Mobile.newDecoder()
        cameraExecutor = Executors.newSingleThreadExecutor()
        fileCount = prefs.getInt(KEY_FILE_COUNT, 0)

        btnOpenFile.setOnClickListener { openFile() }
        btnOpenDir.setOnClickListener { openDir() }
        btnNextFile.setOnClickListener { resetForNext() }
        btnRestart.setOnClickListener { resetForNext() }
        btnStop.setOnClickListener { togglePause() }
        btnStartScan.setOnClickListener { startScanningFromButton() }
        btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        setupGestures()
        applySettings()

        if (allPermissionsGranted()) startCamera()
        else ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
    }

    override fun onResume() {
        super.onResume()
        applySettings()
    }

    private fun applySettings() {
        overlayView.setScanAreaVisible(prefs.getBoolean("show_overlay", true))

        val alwaysShow = prefs.getBoolean("always_show_progress", true)
        val showProgress = prefs.getBoolean("show_progress", true)

        if (!isDecoding) {
            // 非解码中状态（等待扫描、正在扫描但未扫到） → 是否展示取决于“始终显示进度卡片”
            if (alwaysShow && !decoder.isCompleted()) {
                showProgressCardWaiting()
            } else {
                progressCard.visibility = View.GONE
            }
        } else {
            // 解码中状态（已扫描到二维码） → 是否展示取决于“解码时显示进度卡片”
            if (!showProgress) {
                progressCard.visibility = View.GONE
            } else {
                progressCard.visibility = View.VISIBLE
            }
        }

        updateScanAreaOffset()
    }

    private fun updateScanAreaOffset() {
        val showP = prefs.getBoolean("show_progress", true)
        val always = prefs.getBoolean("always_show_progress", true)
        val cardVisible = (showP || always) && progressCard.visibility == View.VISIBLE
        val offsetPx = if (cardVisible) 80f * resources.displayMetrics.density else 0f
        overlayView.setScanAreaOffset(offsetPx)
    }

    private fun setupImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun getStatusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    private fun showProgressCardWaiting() {
        progressCard.visibility = View.VISIBLE
        progressTitle.text = "  等待扫描"
        progressPercent.text = "点击下方按钮开始扫描"
        progressBar.progress = 0
        decoderStatus.text = "解码器: 就绪 | 摄像头: 就绪"
        diagnosticInfo.text = "诊断: 0 新帧 | 0 重复"
        fileInfo.text = ""
        btnStartScan.visibility = View.VISIBLE
        scanButtons.visibility = View.GONE
        stopBreathingAnimation()
        setDotColor("#FFC107")
        updateScanAreaOffset()
    }

    private fun startScanningFromButton() {
        isScanning = true
        isStopped = false
        isPaused = false
        btnStartScan.visibility = View.GONE
        scanButtons.visibility = View.VISIBLE
        progressTitle.text = "  正在扫描"
        progressPercent.text = "等待二维码..."
        progressBar.progress = 0
        decoderStatus.text = "解码器: 就绪 | 摄像头: 正常"
        diagnosticInfo.text = "诊断: 0 新帧 | 0 重复"
        fileInfo.text = ""
        statusText.text = "扫描中..."
        startBreathingAnimation("#4CAF50")
        updateScanAreaOffset()
    }

    private fun togglePause() {
        isPaused = !isPaused
        if (isPaused) {
            isStopped = true
            btnStop.text = "继续扫描"
            btnStop.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#26C6DA"))
            statusText.text = "已暂停"
            progressTitle.text = "  已暂停"
            stopBreathingAnimation()
            setDotColor("#EF5350")
        } else {
            isStopped = false
            btnStop.text = "暂停扫描"
            btnStop.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#EF5350"))
            statusText.text = "扫描中..."
            if (isDecoding) {
                progressTitle.text = "  正在解码"
                startBreathingAnimation("#26C6DA")
            } else {
                progressTitle.text = "  正在扫描"
                startBreathingAnimation("#4CAF50")
            }
        }
    }

    private fun setDotColor(hex: String) {
        dotIndicator.setColor(hex)
    }

    private fun startBreathingAnimation(hex: String) {
        stopBreathingAnimation()
        dotIndicator.setColor(hex)
        dotIndicator.startBreathing()
    }

    private fun stopBreathingAnimation() {
        dotIndicator.stopBreathing()
    }

    private fun setupGestures() {
        scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val cam = currentCamera ?: return false
                val cur = cam.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                cam.cameraControl.setZoomRatio((cur * detector.scaleFactor).coerceIn(1f, cam.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f))
                return true
            }
        })
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val cam = currentCamera ?: return false
                val point = previewView.meteringPointFactory.createPoint(e.x, e.y)
                cam.cameraControl.startFocusAndMetering(
                    FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                        .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS).build()
                )
                overlayView.showFocusPoint(e.x, e.y)
                return true
            }
        })
        previewView.setOnTouchListener { _, event -> scaleDetector.onTouchEvent(event); gestureDetector.onTouchEvent(event); true }
    }

    private fun getResolutionFromPrefs(): Size {
        return when (prefs.getString("resolution", "640x480")) {
            "1280x720" -> Size(1280, 720)
            "1920x1080" -> Size(1920, 1080)
            "2560x1440" -> Size(2560, 1440)
            else -> Size(640, 480)
        }
    }

    private fun startCamera() {
        ProcessCameraProvider.getInstance(this).addListener({
            val cp = ProcessCameraProvider.getInstance(this).get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val qrOnly = prefs.getBoolean("qr_only", true)
            val scanner = if (qrOnly) BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
            else BarcodeScanning.getClient()
            val ia = ImageAnalysis.Builder()
                .setTargetResolution(getResolutionFromPrefs())
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build().also { it.setAnalyzer(cameraExecutor, createAnalyzer(scanner)) }
            try {
                cp.unbindAll()
                currentCamera = cp.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, ia)
                if (prefs.getBoolean("auto_focus", true)) {
                    currentCamera?.cameraControl?.startFocusAndMetering(
                        FocusMeteringAction.Builder(previewView.meteringPointFactory.createPoint(0.5f, 0.5f),
                            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                        ).setAutoCancelDuration(Long.MAX_VALUE, java.util.concurrent.TimeUnit.MILLISECONDS).build()
                    )
                }
            } catch (e: Exception) { Log.e(TAG, "相机绑定失败", e) }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun createAnalyzer(scanner: com.google.mlkit.vision.barcode.BarcodeScanner): ImageAnalysis.Analyzer {
        return ImageAnalysis.Analyzer { imageProxy ->
            val alwaysShow = prefs.getBoolean("always_show_progress", false)
            if ((alwaysShow && !isScanning) || isStopped) { imageProxy.close(); return@Analyzer }

            @SuppressLint("UnsafeOptInUsageError")
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                totalFramesProcessed++
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        val showOv = prefs.getBoolean("show_overlay", true)
                        if (showOv && barcodes.isNotEmpty()) {
                            runOnUiThread { overlayView.updateBarcodes(barcodes, mediaImage.width, mediaImage.height, previewView.width, previewView.height, imageProxy.imageInfo.rotationDegrees) }
                        } else if (!showOv) {
                            runOnUiThread { overlayView.clear() }
                        }
                        if (!isProcessing.get() && !decoder.isCompleted() && barcodes.isNotEmpty()) {
                            for (barcode in barcodes) {
                                val content = barcode.rawValue ?: continue
                                if (content.contains("|")) { onQRCodeDetected(content); break }
                            }
                        }
                    }
                    .addOnFailureListener { Log.e(TAG, "ML Kit error", it) }
                    .addOnCompleteListener { imageProxy.close() }
            } else { imageProxy.close() }
        }
    }

    private fun onQRCodeDetected(content: String) {
        if (decoder.isCompleted() || isStopped) return
        if (!isProcessing.compareAndSet(false, true)) return
        isDecoding = true  // 标记已开始解码
        try {
            val prev = decoder.uniqueFrames().toInt()
            decoder.decodeChunk(content)
            val curr = decoder.uniqueFrames().toInt()
            if (curr > prev) { newFrames++; uniqueFrames = curr } else { duplicateFrames++ }

            runOnUiThread {
                updateProgressDisplay()
                if (decoder.isCompleted()) {
                    val data = decoder.dataBytes()
                    fileCount++; prefs.edit().putInt(KEY_FILE_COUNT, fileCount).apply()
                    val sf = saveFile(data, fileCount)
                    if (sf != null) fileInfo.text = "${sf.name} · ${formatSize(data.size.toLong())}"
                    statusText.text = "✅ 解码完成！"
                    progressTitle.text = "  解码完成"
                    progressBar.progress = 100
                    progressPercent.text = "100% | 解码完成"
                    frameCountText.text = sf?.name ?: ""
                    overlayView.clear()
                    progressCard.visibility = View.GONE
                    resultPanel.visibility = View.VISIBLE
                    lastSavedFile = sf
                }
            }
        } catch (e: Exception) { Log.e(TAG, "解码错误: ${e.message}") }
        finally { isProcessing.set(false) }
    }

    private fun updateProgressDisplay() {
        val showP = prefs.getBoolean("show_progress", true)
        val always = prefs.getBoolean("always_show_progress", false)
        val progress = decoder.progress().toInt()
        val unique = decoder.uniqueFrames().toInt()
        val total = decoder.totalFrames().toInt()

        if (!showP && !always) {
            statusText.text = "⏳ $progress% | $unique/$total 帧"
            return
        }
        progressCard.visibility = View.VISIBLE
        progressBar.progress = progress
        progressTitle.text = "  正在解码"
        progressPercent.text = "$progress% | $unique/$total 帧"
        fileInfo.text = if (decoder.totalSize().toInt() > 0) formatSize(decoder.totalSize().toLong()) else ""
        decoderStatus.text = "解码器: 工作中 | 摄像头: ${if (currentCamera != null) "正常" else "未连接"}"
        diagnosticInfo.text = "诊断: ${newFrames} 新帧 | ${duplicateFrames} 重复"
        statusText.text = "⏳ $progress% | $unique/$total 帧"

        // 确保呼吸动画在运行（解码中用青色）
        // 确保呼吸动画在运行（解码中用青色）
        if (breathingAnimator == null || !breathingAnimator!!.isRunning) {
            startBreathingAnimation("#26C6DA")
        }

        updateScanAreaOffset()
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
        bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun detectExtension(data: ByteArray): String {
        if (data.size < 4) return ""
        val b = data
        return when {
            b[0] == 0x89.toByte() && b[1] == 0x50.toByte() -> ".png"
            b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() -> ".jpg"
            b[0] == 0x47.toByte() && b[1] == 0x49.toByte() -> ".gif"
            b[0] == 0x42.toByte() && b[1] == 0x4D.toByte() -> ".bmp"
            data.size > 12 && b[8] == 0x57.toByte() && b[9] == 0x45.toByte() -> ".webp"
            data.size > 4 && b[4] == 0x66.toByte() && b[5] == 0x74.toByte() -> ".mp4"
            b[0] == 0x25.toByte() && b[1] == 0x50.toByte() -> ".pdf"
            b[0] == 0xD0.toByte() && b[1] == 0xCF.toByte() -> ".doc"
            b[0] == 0x50.toByte() && b[1] == 0x4B.toByte() -> ".zip"
            b[0] == 0x1F.toByte() && b[1] == 0x8B.toByte() -> ".tar.gz"
            b[0] == 0x52.toByte() && b[1] == 0x61.toByte() && b[2] == 0x72.toByte() -> ".rar"
            b[0] == 0x37.toByte() && b[1] == 0x7A.toByte() -> ".7z"
            data.size > 257 && b[257] == 0x75.toByte() && b[258] == 0x73.toByte() -> ".tar"
            isLikelyText(data) -> detectTextType(data)
            else -> ""
        }
    }

    private fun isLikelyText(data: ByteArray): Boolean {
        val s = minOf(512, data.size); var n = 0
        for (i in 0 until s) { val v = data[i].toInt() and 0xFF; if (v < 32 && v != 10 && v != 13 && v != 9) n++ }
        return n * 100 / s < 5
    }

    private fun detectTextType(data: ByteArray): String {
        val h = try { String(data, 0, minOf(500, data.size), Charsets.UTF_8).lowercase() } catch (_: Exception) { return ".txt" }
        return when {
            h.trimStart().startsWith("{") || h.trimStart().startsWith("[") -> ".json"
            h.contains("<?xml") -> ".xml"; h.contains("<!doctype") || h.contains("<html") -> ".html"
            h.contains("<svg") -> ".svg"; h.startsWith("---\n") || h.contains("# ") -> ".md"
            h.trimStart().startsWith("---") && h.contains(": ") -> ".yaml"
            h.startsWith("#!/bin/") -> ".sh"; h.contains("function ") || h.contains("import ") -> ".js"
            h.contains("public class ") -> ".java"; h.contains("fun ") -> ".kt"
            h.contains("def ") -> ".py"; h.contains("select ") -> ".sql"
            h.contains(",") && h.count { it == '\n' } > 0 -> ".csv"
            else -> ".txt"
        }
    }

    private fun saveFile(data: ByteArray, count: Int): File? {
        val ext = detectExtension(data)
        val baseName = "文件${count}"
        val dirUri = getSaveDirUri()
        if (dirUri != null) {
            try {
                val mt = getMimeTypeByName(baseName + ext)
                var fn = "$baseName$ext"; var uri = createFileInTree(dirUri, fn, mt)
                if (uri == null) { var n = 1; while (uri == null && n < 100) { fn = "${baseName}_$n$ext"; uri = createFileInTree(dirUri, fn, mt); n++ } }
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { it.write(data) }
                    Toast.makeText(this, "已保存: $fn", Toast.LENGTH_SHORT).show()
                    return File(txqrDir, fn).also { f -> if (!f.parentFile.exists()) f.parentFile.mkdirs() }
                }
            } catch (_: Exception) {}
        }
        return try {
            val dir = txqrDir; if (!dir.exists()) dir.mkdirs()
            var f = File(dir, "$baseName$ext"); var n = 1
            while (f.exists()) { f = File(dir, "${baseName}_$n$ext"); n++ }
            FileOutputStream(f).use { it.write(data) }
            Toast.makeText(this, "已保存: ${f.name}", Toast.LENGTH_SHORT).show()
            f
        } catch (e: Exception) { Toast.makeText(this, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show(); null }
    }

    private fun createFileInTree(treeUri: Uri, fn: String, mt: String): Uri? = try {
        DocumentsContract.createDocument(contentResolver,
            DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri)), mt, fn)
    } catch (_: Exception) { null }

    private fun getMimeTypeByName(fn: String): String = when {
        fn.endsWith(".png") -> "image/png"; fn.endsWith(".jpg") -> "image/jpeg"; fn.endsWith(".gif") -> "image/gif"
        fn.endsWith(".pdf") -> "application/pdf"; fn.endsWith(".txt") -> "text/plain"; fn.endsWith(".md") -> "text/markdown"
        fn.endsWith(".json") -> "application/json"; fn.endsWith(".html") -> "text/html"; fn.endsWith(".mp4") -> "video/mp4"
        fn.endsWith(".zip") -> "application/zip"; else -> "application/octet-stream"
    }

    private fun openFile() {
        val f = lastSavedFile ?: return
        try { startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(FileProvider.getUriForFile(this@MainActivity, "${packageName}.fileprovider", f), getMimeType(f)); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }) }
        catch (e: Exception) { Toast.makeText(this, "无法打开文件: ${e.message}", Toast.LENGTH_SHORT).show() }
    }

    private fun openDir() {
        val d = lastSavedFile?.parentFile ?: txqrDir; if (!d.exists()) d.mkdirs()
        try { startActivity(Intent().apply { setClassName("com.android.fileexplorer", "com.android.fileexplorer.FileExplorerTabActivity"); data = Uri.fromFile(d); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }); return } catch (_: Exception) {}
        try { startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_FILES).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }); return } catch (_: Exception) {}
        try { startActivity(Intent(Intent.ACTION_VIEW).apply { data = Uri.fromFile(d); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }); return } catch (_: Exception) {}
        try { startActivity(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) } catch (_: Exception) {}
    }

    private fun getMimeType(f: File): String = when {
        f.name.endsWith(".png") -> "image/png"; f.name.endsWith(".jpg") -> "image/jpeg"; f.name.endsWith(".gif") -> "image/gif"
        f.name.endsWith(".pdf") -> "application/pdf"; f.name.endsWith(".txt") || f.name.endsWith(".md") -> "text/plain"
        f.name.endsWith(".html") -> "text/html"; f.name.endsWith(".mp4") -> "video/mp4"; else -> "*/*"
    }

    private fun resetForNext() {
        decoder.reset(); isStopped = false; isPaused = false; isScanning = false; isDecoding = false
        totalFramesProcessed = 0; newFrames = 0; duplicateFrames = 0; uniqueFrames = 0
        resultPanel.visibility = View.GONE; overlayView.clear(); lastSavedFile = null
        stopBreathingAnimation()
        if (prefs.getBoolean("always_show_progress", true)) showProgressCardWaiting()
        else { progressCard.visibility = View.GONE; updateScanAreaOffset() }
        statusText.text = "将摄像头对准二维码动画"; frameCountText.text = ""
        btnStop.text = "暂停扫描"
        btnStop.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#EF5350"))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all { ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (code == REQUEST_CODE_PERMISSIONS) { if (allPermissionsGranted()) startCamera() else { Toast.makeText(this, "需要摄像头权限", Toast.LENGTH_SHORT).show(); finish() } }
    }

    override fun onDestroy() { super.onDestroy(); cameraExecutor.shutdown() }
}
