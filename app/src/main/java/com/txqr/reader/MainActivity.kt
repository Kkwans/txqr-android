package com.txqr.reader

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.util.Size
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
    private lateinit var resultPanel: LinearLayout
    private lateinit var btnOpenFile: Button
    private lateinit var btnOpenDir: Button
    private lateinit var btnNextFile: Button
    private lateinit var btnSettings: ImageButton

    private val isProcessing = AtomicBoolean(false)
    private var fileCount = 0
    private var lastSavedFile: File? = null
    private var lastSavedDir: File? = null

    private var currentCamera: Camera? = null
    private lateinit var scaleDetector: ScaleGestureDetector
    private lateinit var gestureDetector: GestureDetector

    private val prefs by lazy { getSharedPreferences("txqr", MODE_PRIVATE) }

    private val txqrDir: File
        get() {
            val custom = prefs.getString("save_dir", "") ?: ""
            return if (custom.isNotEmpty()) {
                File(custom)
            } else {
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "TXQR")
            }
        }

    companion object {
        private const val TAG = "TxqrReader"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.overlayView)
        statusText = findViewById(R.id.statusText)
        frameCountText = findViewById(R.id.frameCountText)
        resultPanel = findViewById(R.id.resultPanel)
        btnOpenFile = findViewById(R.id.btnOpenFile)
        btnOpenDir = findViewById(R.id.btnOpenDir)
        btnNextFile = findViewById(R.id.btnNextFile)
        btnSettings = findViewById(R.id.btnSettings)

        decoder = Mobile.newDecoder()
        cameraExecutor = Executors.newSingleThreadExecutor()

        btnOpenFile.setOnClickListener { openFile() }
        btnOpenDir.setOnClickListener { openDir() }
        btnNextFile.setOnClickListener { resetForNext() }
        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        setupGestures()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun setupGestures() {
        scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val camera = currentCamera ?: return false
                val currentZoom = camera.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                val newZoom = (currentZoom * detector.scaleFactor).coerceIn(1f, camera.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f)
                camera.cameraControl.setZoomRatio(newZoom)
                return true
            }
        })

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val camera = currentCamera ?: return false
                val factory = previewView.meteringPointFactory
                val point = factory.createPoint(e.x, e.y)
                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                    .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                camera.cameraControl.startFocusAndMetering(action)
                overlayView.showFocusPoint(e.x, e.y)
                return true
            }
        })

        previewView.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val qrOnly = prefs.getBoolean("qr_only", true)
            val analyzer = if (qrOnly) {
                val options = BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
                createAnalyzer(BarcodeScanning.getClient(options))
            } else {
                createAnalyzer(BarcodeScanning.getClient())
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1920, 1080))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor, analyzer) }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                currentCamera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)

                // 自动对焦
                if (prefs.getBoolean("auto_focus", true)) {
                    val factory = previewView.meteringPointFactory
                    val point = factory.createPoint(0.5f, 0.5f)
                    val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                        .build()
                    currentCamera?.cameraControl?.startFocusAndMetering(action)
                }
            } catch (exc: Exception) {
                Log.e(TAG, "相机绑定失败", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun createAnalyzer(
        scanner: com.google.mlkit.vision.barcode.BarcodeScanner
    ): ImageAnalysis.Analyzer {
        return ImageAnalysis.Analyzer { imageProxy ->
            @SuppressLint("UnsafeOptInUsageError")
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        val showOverlay = prefs.getBoolean("show_overlay", true)
                        if (showOverlay && barcodes.isNotEmpty()) {
                            runOnUiThread {
                                overlayView.updateBarcodes(
                                    barcodes,
                                    mediaImage.width, mediaImage.height,
                                    previewView.width, previewView.height,
                                    imageProxy.imageInfo.rotationDegrees
                                )
                            }
                        }

                        for (barcode in barcodes) {
                            val content = barcode.rawValue ?: continue
                            if (content.contains("|")) {
                                onQRCodeDetected(content)
                            }
                        }
                    }
                    .addOnFailureListener { }
                    .addOnCompleteListener {
                        if (!isProcessing.get()) {
                            runOnUiThread { overlayView.clear() }
                        }
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }

    private fun onQRCodeDetected(content: String) {
        if (decoder.isCompleted()) return
        if (!isProcessing.compareAndSet(false, true)) return

        try {
            decoder.decodeChunk(content)

            runOnUiThread {
                if (decoder.isCompleted()) {
                    val data = decoder.dataBytes()
                    fileCount++
                    val savedFile = saveFile(data, fileCount)
                    lastSavedFile = savedFile
                    lastSavedDir = savedFile?.parentFile

                    statusText.text = "✅ 解码完成！"
                    frameCountText.text = "${savedFile?.name} (${formatSize(data.size.toLong())})"
                    resultPanel.visibility = View.VISIBLE
                    overlayView.clear()
                } else {
                    val progress = decoder.progress()
                    statusText.text = "⏳ $progress% (帧: ${decoder.uniqueFrames()})"
                    frameCountText.text = "大小: ${formatSize(decoder.totalSize())}"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解码错误: ${e.message}")
        } finally {
            isProcessing.set(false)
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
            bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
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
        val sampleSize = minOf(512, data.size)
        var nonTextBytes = 0
        for (i in 0 until sampleSize) {
            val v = data[i].toInt() and 0xFF
            if (v > 127) continue
            if (v < 32 && v != 10 && v != 13 && v != 9) nonTextBytes++
        }
        return nonTextBytes * 100 / sampleSize < 5
    }

    private fun detectTextType(data: ByteArray): String {
        val head = try {
            String(data, 0, minOf(500, data.size), Charsets.UTF_8).lowercase()
        } catch (e: Exception) {
            return ".txt"
        }
        return when {
            head.trimStart().startsWith("{") || head.trimStart().startsWith("[") -> ".json"
            head.contains("<?xml") -> ".xml"
            head.contains("<!doctype") || head.contains("<html") -> ".html"
            head.contains("<svg") -> ".svg"
            head.startsWith("---\n") || head.contains("# ") || head.contains("## ") -> ".md"
            head.trimStart().startsWith("---") && head.contains(": ") -> ".yaml"
            head.startsWith("#!/bin/") || head.startsWith("#!/usr/bin/") -> ".sh"
            head.contains("function ") || head.contains("const ") || head.contains("import ") -> ".js"
            head.contains("public class ") || head.contains("package ") -> ".java"
            head.contains("fun ") && head.contains("val ") -> ".kt"
            head.contains("def ") -> ".py"
            head.contains("select ") || head.contains("create table") -> ".sql"
            head.contains(",") && head.count { it == '\n' } > 0 -> ".csv"
            head.contains("[INFO]") || head.contains("[ERROR]") -> ".log"
            else -> ".txt"
        }
    }

    private fun saveFile(data: ByteArray, count: Int): File {
        val ext = detectExtension(data)
        val dir = txqrDir
        if (!dir.exists()) dir.mkdirs()
        val baseName = "文件${count}"
        var file = File(dir, "$baseName$ext")
        var n = 1
        while (file.exists()) {
            file = File(dir, "${baseName}_$n$ext")
            n++
        }
        FileOutputStream(file).use { it.write(data) }
        Toast.makeText(this, "已保存: ${file.name}", Toast.LENGTH_SHORT).show()
        return file
    }

    private fun openFile() {
        val file = lastSavedFile ?: return
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, getMimeType(file))
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开文件: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openDir() {
        val dir = lastSavedDir ?: txqrDir
        if (!dir.exists()) dir.mkdirs()
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", dir)
            intent.setDataAndType(uri, "resource/folder")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开目录: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getMimeType(file: File): String {
        val name = file.name.lowercase()
        return when {
            name.endsWith(".png") -> "image/png"
            name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
            name.endsWith(".gif") -> "image/gif"
            name.endsWith(".pdf") -> "application/pdf"
            name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".json") -> "text/plain"
            name.endsWith(".html") -> "text/html"
            name.endsWith(".mp4") -> "video/mp4"
            else -> "*/*"
        }
    }

    private fun resetForNext() {
        decoder.reset()
        resultPanel.visibility = View.GONE
        statusText.text = "将摄像头对准二维码动画"
        frameCountText.text = ""
        lastSavedFile = null
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) startCamera()
            else { Toast.makeText(this, "需要摄像头权限", Toast.LENGTH_SHORT).show(); finish() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
