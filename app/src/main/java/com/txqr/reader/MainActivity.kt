package com.txqr.reader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.Button
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
    private lateinit var resultPanel: LinearLayout
    private lateinit var btnOpenDir: Button
    private lateinit var btnNextFile: Button

    private val isProcessing = AtomicBoolean(false)
    private var fileCount = 0
    private var lastSavedFile: File? = null
    private var lastSavedDir: File? = null

    private val saveDir: String
        get() = getSharedPreferences("txqr", MODE_PRIVATE)
            .getString("save_dir", "") ?: ""

    private val txqrDir: File
        get() {
            val custom = saveDir
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
        statusText = findViewById(R.id.statusText)
        frameCountText = findViewById(R.id.frameCountText)
        resultPanel = findViewById(R.id.resultPanel)
        btnOpenDir = findViewById(R.id.btnOpenDir)
        btnNextFile = findViewById(R.id.btnNextFile)

        decoder = Mobile.newDecoder()
        cameraExecutor = Executors.newSingleThreadExecutor()

        btnOpenDir.setOnClickListener { openFileManager() }
        btnNextFile.setOnClickListener { resetForNext() }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1920, 1080))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, QRCodeAnalyzer { qrContent ->
                        onQRCodeDetected(qrContent)
                    })
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
            } catch (exc: Exception) {
                Log.e(TAG, "相机绑定失败", exc)
            }

        }, ContextCompat.getMainExecutor(this))
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

    /**
     * 根据文件头魔数推断文件扩展名
     */
    private fun detectExtension(data: ByteArray): String {
        if (data.size < 4) return ""
        val b = data

        return when {
            // 图片
            b[0] == 0x89.toByte() && b[1] == 0x50.toByte() -> ".png"
            b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() -> ".jpg"
            b[0] == 0x47.toByte() && b[1] == 0x49.toByte() -> ".gif"
            b[0] == 0x42.toByte() && b[1] == 0x4D.toByte() -> ".bmp"
            data.size > 12 && b[8] == 0x57.toByte() && b[9] == 0x45.toByte() -> ".webp"
            data.size > 4 && b[4] == 0x66.toByte() && b[5] == 0x74.toByte() -> ".mp4"
            // 文档
            b[0] == 0x25.toByte() && b[1] == 0x50.toByte() -> ".pdf"
            b[0] == 0xD0.toByte() && b[1] == 0xCF.toByte() -> ".doc"
            b[0] == 0x50.toByte() && b[1] == 0x4B.toByte() -> ".zip"
            // 压缩
            b[0] == 0x1F.toByte() && b[1] == 0x8B.toByte() -> ".tar.gz"
            b[0] == 0x52.toByte() && b[1] == 0x61.toByte() && b[2] == 0x72.toByte() -> ".rar"
            b[0] == 0x37.toByte() && b[1] == 0x7A.toByte() -> ".7z"
            data.size > 257 && b[257] == 0x75.toByte() && b[258] == 0x73.toByte() -> ".tar"
            // 可执行
            b[0] == 0x7F.toByte() && b[1] == 0x45.toByte() && b[2] == 0x4C.toByte() -> ".elf"
            b[0] == 0x4D.toByte() && b[1] == 0x5A.toByte() -> ".exe"
            // 文本检测：尝试判断是否为 UTF-8 文本
            isLikelyText(data) -> detectTextType(data)
            else -> ""
        }
    }

    /**
     * 检测数据是否可能是文本文件
     */
    private fun isLikelyText(data: ByteArray): Boolean {
        val sampleSize = minOf(512, data.size)
        var nonTextBytes = 0
        for (i in 0 until sampleSize) {
            val v = data[i].toInt() and 0xFF
            // 允许：可打印ASCII、换行、制表、以及UTF-8多字节序列
            if (v > 127) continue // UTF-8 多字节，先跳过
            if (v < 32 && v != 10 && v != 13 && v != 9) {
                nonTextBytes++
            }
        }
        // 非文本字节占比 < 5% 认为是文本
        return nonTextBytes * 100 / sampleSize < 5
    }

    /**
     * 检测文本文件的具体类型
     */
    private fun detectTextType(data: ByteArray): String {
        val head = try {
            String(data, 0, minOf(500, data.size), Charsets.UTF_8).lowercase()
        } catch (e: Exception) {
            return ".txt"
        }

        return when {
            // JSON
            head.trimStart().startsWith("{") || head.trimStart().startsWith("[") -> ".json"
            // XML / HTML
            head.contains("<?xml") -> ".xml"
            head.contains("<!doctype") || head.contains("<html") -> ".html"
            head.contains("<svg") -> ".svg"
            // Markdown
            head.contains("---\n") || head.contains("# ") || head.contains("## ") -> ".md"
            // YAML
            head.trimStart().startsWith("---") && head.contains(": ") -> ".yaml"
            // Shell
            head.startsWith("#!/bin/") || head.startsWith("#!/usr/bin/") -> ".sh"
            // CSS
            head.contains("{") && head.contains("}") && (head.contains(":") && head.contains(";")) -> ".css"
            // JavaScript / TypeScript
            head.contains("function ") || head.contains("const ") || head.contains("import ") -> ".js"
            // Java / Kotlin
            head.contains("public class ") || head.contains("fun ") || head.contains("package ") -> {
                if (head.contains("fun ") && head.contains("val ")) ".kt" else ".java"
            }
            // Python
            head.contains("def ") || head.contains("import ") || head.startsWith("# ") -> ".py"
            // SQL
            head.contains("select ") || head.contains("create table") || head.contains("insert into") -> ".sql"
            // CSV
            head.contains(",") && head.count { it == '\n' } > 0 -> ".csv"
            // Log
            head.contains("[INFO]") || head.contains("[ERROR]") || head.contains("[WARN]") -> ".log"
            // Config
            head.contains("[") && head.contains("]") && head.contains("=") -> ".ini"
            // 默认纯文本
            else -> ".txt"
        }
    }

    /**
     * 保存文件
     */
    private fun saveFile(data: ByteArray, count: Int): File {
        val ext = detectExtension(data)
        val dir = txqrDir
        if (!dir.exists()) dir.mkdirs()

        // 简洁命名：序号 + 扩展名
        val baseName = "文件${count}"
        var file = File(dir, "$baseName$ext")
        // 避免覆盖
        var n = 1
        while (file.exists()) {
            file = File(dir, "${baseName}_$n$ext")
            n++
        }

        FileOutputStream(file).use { it.write(data) }
        Toast.makeText(this, "已保存: ${file.name}", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "文件已保存: ${file.absolutePath} (${data.size} 字节)")
        return file
    }

    /**
     * 打开文件所在目录
     */
    private fun openFileManager() {
        val dir = lastSavedDir ?: txqrDir
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", dir)
            intent.setDataAndType(uri, "resource/folder")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        } catch (e: Exception) {
            // 如果文件管理器不支持打开目录，尝试打开文件
            try {
                val file = lastSavedFile ?: return
                val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(uri, getMimeType(file))
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(intent)
            } catch (e2: Exception) {
                Toast.makeText(this, "无法打开文件管理器", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getMimeType(file: File): String {
        val name = file.name.lowercase()
        return when {
            name.endsWith(".png") -> "image/png"
            name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
            name.endsWith(".gif") -> "image/gif"
            name.endsWith(".pdf") -> "application/pdf"
            name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".json") || name.endsWith(".xml") -> "text/plain"
            name.endsWith(".html") -> "text/html"
            name.endsWith(".mp4") -> "video/mp4"
            else -> "*/*"
        }
    }

    /**
     * 重置解码器，准备接收下一个文件
     */
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
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "需要摄像头权限才能扫描二维码", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
