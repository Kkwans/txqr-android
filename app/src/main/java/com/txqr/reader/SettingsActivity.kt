package com.txqr.reader

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var tvSaveDir: TextView

    companion object {
        const val PREFS_NAME = "txqr"
        const val KEY_SAVE_DIR = "save_dir"
        const val KEY_QR_ONLY = "qr_only"
        const val KEY_SHOW_OVERLAY = "show_overlay"
        const val KEY_AUTO_FOCUS = "auto_focus"
        const val REQUEST_CODE_OPEN_DOCUMENT_TREE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        tvSaveDir = findViewById(R.id.tvSaveDir)
        val btnOpenSaveDir = findViewById<Button>(R.id.btnOpenSaveDir)
        val switchQrOnly = findViewById<Switch>(R.id.switchQrOnly)
        val switchShowOverlay = findViewById<Switch>(R.id.switchShowOverlay)
        val switchAutoFocus = findViewById<Switch>(R.id.switchAutoFocus)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)

        val saveDir = prefs.getString(KEY_SAVE_DIR, "") ?: ""
        tvSaveDir.text = if (saveDir.isEmpty()) "/Download/TXQR" else saveDir

        switchQrOnly.isChecked = prefs.getBoolean(KEY_QR_ONLY, true)
        switchShowOverlay.isChecked = prefs.getBoolean(KEY_SHOW_OVERLAY, true)
        switchAutoFocus.isChecked = prefs.getBoolean(KEY_AUTO_FOCUS, true)

        switchQrOnly.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_QR_ONLY, isChecked).apply()
        }
        switchShowOverlay.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_SHOW_OVERLAY, isChecked).apply()
        }
        switchAutoFocus.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_AUTO_FOCUS, isChecked).apply()
        }

        // 打开保存目录
        btnOpenSaveDir.setOnClickListener {
            val dir = if (saveDir.isEmpty()) {
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "TXQR")
            } else {
                File(saveDir)
            }
            if (!dir.exists()) dir.mkdirs()
            openDirectory(dir)
        }

        // 设置保存目录 - 使用 SAF 目录选择器
        findViewById<LinearLayout>(R.id.saveDirRow).setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            startActivityForResult(intent, REQUEST_CODE_OPEN_DOCUMENT_TREE)
        }

        btnBack.setOnClickListener { finish() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_OPEN_DOCUMENT_TREE && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            // 获取真实路径
            val path = getRealPathFromUri(uri)
            if (path != null) {
                prefs.edit().putString(KEY_SAVE_DIR, path).apply()
                tvSaveDir.text = path
                Toast.makeText(this, "保存目录已设置: $path", Toast.LENGTH_SHORT).show()
            } else {
                // 如果无法获取真实路径，保存 URI
                prefs.edit().putString(KEY_SAVE_DIR, uri.toString()).apply()
                tvSaveDir.text = uri.lastPathSegment ?: "已选择目录"
                Toast.makeText(this, "保存目录已设置", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getRealPathFromUri(uri: Uri): String? {
        val docId = DocumentsContract.getDocumentId(uri)
        val split = docId.split(":")
        val type = split[0]
        if ("primary".equals(type, ignoreCase = true)) {
            return "/storage/emulated/0/${split[1]}"
        }
        return null
    }

    private fun openDirectory(dir: File) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(Uri.fromFile(dir), "resource/folder")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e1: Exception) {
            try {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } catch (e2: Exception) {
                Toast.makeText(this, "无法打开目录: ${e2.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
