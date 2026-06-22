package com.txqr.reader

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
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
                    frameCountText.text = "唯一帧数: ${decoder.uniqueFrames()} | 文件大小: ${formatSize(data.size.toLong())}"
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
     * 保存文件到 /Download/TXQR/ 目录
     * 文件名：txqr_序号_时间戳.bin
     */
    private fun saveFile(data: ByteArray, count: Int): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "txqr_${count}_${timestamp}.bin"
        val dirPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val txqrDir = File(dirPath, SAVE_DIR)

        if (!txqrDir.exists()) {
            txqrDir.mkdirs()
        }

        val file = File(txqrDir, fileName)
        try {
            FileOutputStream(file).use { it.write(data) }
            Toast.makeText(this, "已保存: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            Log.d(TAG, "文件已保存: ${file.absolutePath} (${data.size} 字节)")
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
