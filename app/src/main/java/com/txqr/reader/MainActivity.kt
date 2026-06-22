package com.txqr.reader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.util.Size
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import mobile.Mobile
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var decoder: mobile.Decoder
    private lateinit var statusText: TextView
    private lateinit var frameCountText: TextView
    private lateinit var previewView: PreviewView

    private val isProcessing = AtomicBoolean(false)
    private var fileCount = 0

    companion object {
        private const val TAG = "TxqrReader"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val SAVE_DIR = "TXQR"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        statusText = findViewById(R.id.statusText)
        frameCountText = findViewById(R.id.frameCountText)

        decoder = Mobile.newDecoder()
        cameraExecutor = Executors.newSingleThreadExecutor()

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
                    val savedPath = saveFile(data, fileCount)
                    statusText.text = "✅ 解码完成！已保存 $savedPath"
                    frameCountText.text = "唯一帧数: ${decoder.uniqueFrames()} | 大小: ${formatSize(data.size.toLong())}"
                } else {
                    val progress = decoder.progress()
                    statusText.text = "⏳ 解码中: $progress% (已接收 ${decoder.uniqueFrames()} 帧)"
                    frameCountText.text = "原始大小: ${formatSize(decoder.totalSize())}"
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
            // PNG
            b[0] == 0x89.toByte() && b[1] == 0x50.toByte() && b[2] == 0x4E.toByte() && b[3] == 0x47.toByte() -> ".png"
            // JPEG
            b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte() -> ".jpg"
            // GIF
            b[0] == 0x47.toByte() && b[1] == 0x49.toByte() && b[2] == 0x46.toByte() -> ".gif"
            // PDF
            b[0] == 0x25.toByte() && b[1] == 0x50.toByte() && b[2] == 0x44.toByte() && b[3] == 0x46.toByte() -> ".pdf"
            // ZIP / APK / DOCX / XLSX
            b[0] == 0x50.toByte() && b[1] == 0x4B.toByte() && b[2] == 0x03.toByte() && b[3] == 0x04.toByte() -> ".zip"
            // RAR
            b[0] == 0x52.toByte() && b[1] == 0x61.toByte() && b[2] == 0x72.toByte() && b[3] == 0x21.toByte() -> ".rar"
            // 7Z
            b[0] == 0x37.toByte() && b[1] == 0x7A.toByte() && b[2] == 0xBC.toByte() && b[3] == 0xAF.toByte() -> ".7z"
            // GZIP (tar.gz)
            b[0] == 0x1F.toByte() && b[1] == 0x8B.toByte() -> ".tar.gz"
            // TAR (ustar magic)
            data.size > 263 && b[257] == 0x75.toByte() && b[258] == 0x73.toByte() && b[259] == 0x74.toByte() && b[260] == 0x61.toByte() -> ".tar"
            // BMP
            b[0] == 0x42.toByte() && b[1] == 0x4D.toByte() -> ".bmp"
            // WEBP
            data.size > 12 && b[8] == 0x57.toByte() && b[9] == 0x45.toByte() && b[10] == 0x42.toByte() && b[11] == 0x50.toByte() -> ".webp"
            // MP4/MOV (ftyp box)
            data.size > 4 && b[4] == 0x66.toByte() && b[5] == 0x74.toByte() && b[6] == 0x79.toByte() && b[7] == 0x70.toByte() -> ".mp4"
            // ELF (Linux binary)
            b[0] == 0x7F.toByte() && b[1] == 0x45.toByte() && b[2] == 0x4C.toByte() && b[3] == 0x46.toByte() -> ""
            // HTML
            data.size > 10 -> {
                val head = String(b, 0, minOf(100, b.size)).lowercase()
                if (head.contains("<!doctype") || head.contains("<html")) ".html" else ""
            }
            else -> ""
        }
    }

    /**
     * 保存文件到 /Download/TXQR/ 目录
     * 通过魔数推断文件类型，使用正确扩展名
     */
    private fun saveFile(data: ByteArray, count: Int): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val ext = detectExtension(data)
        val fileName = "txqr_${count}_${timestamp}$ext"

        val dirPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val txqrDir = File(dirPath, SAVE_DIR)

        if (!txqrDir.exists()) {
            txqrDir.mkdirs()
        }

        val file = File(txqrDir, fileName)
        try {
            FileOutputStream(file).use { it.write(data) }
            Toast.makeText(this, "已保存: ${file.name}", Toast.LENGTH_LONG).show()
            Log.d(TAG, "文件已保存: ${file.absolutePath} (${data.size} 字节, 类型: $ext)")
            return file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "保存失败: ${e.message}")
            Toast.makeText(this, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
            return "保存失败"
        }
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
