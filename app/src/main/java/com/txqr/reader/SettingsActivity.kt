package com.txqr.reader

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
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

        // 查看保存目录
        btnOpenSaveDir.setOnClickListener {
            val dir = if (saveDir.isEmpty()) {
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "TXQR")
            } else {
                File(saveDir)
            }
            if (!dir.exists()) dir.mkdirs()
            openFileManager(dir)
        }

        // 设置保存目录 - SAF 系统文件选择器
        findViewById<LinearLayout>(R.id.saveDirRow).setOnClickListener {
            chooseDirectory()
        }

        btnBack.setOnClickListener { finish() }
    }

    private fun chooseDirectory() {
        try {
            // 使用 SAF ACTION_OPEN_DOCUMENT_TREE 让用户选择目录
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivityForResult(intent, REQUEST_CODE_OPEN_DOCUMENT_TREE)
        } catch (e: Exception) {
            Toast.makeText(this, "系统文件选择器不可用", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_OPEN_DOCUMENT_TREE && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            val path = getPathFromTreeUri(uri)
            if (path != null) {
                prefs.edit().putString(KEY_SAVE_DIR, path).apply()
                tvSaveDir.text = path
                Toast.makeText(this, "保存目录已设置: $path", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "目录已选择，但无法获取路径", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 从 SAF tree URI 提取真实路径 */
    private fun getPathFromTreeUri(uri: Uri): String? {
        return try {
            val docId = DocumentsContract.getDocumentId(uri)
            val split = docId.split(":")
            if (split.size >= 2 && "primary".equals(split[0], ignoreCase = true)) {
                "/storage/emulated/0/${split[1]}"
            } else if (split.size == 1 && "primary".equals(split[0], ignoreCase = true)) {
                "/storage/emulated/0"
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun openFileManager(dir: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_FILES)
                startActivity(intent)
                return
            }
        } catch (_: Exception) {}

        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(android.net.Uri.fromFile(dir), "*/*")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            return
        } catch (_: Exception) {}

        try {
            val intent = packageManager.getLaunchIntentForPackage("com.android.fileexplorer")
            if (intent != null) { startActivity(intent); return }
        } catch (_: Exception) {}

        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "请在文件管理器查看: ${dir.absolutePath}", Toast.LENGTH_LONG).show()
        }
    }
}
