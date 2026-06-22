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
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var tvSaveDir: TextView

    companion object {
        const val PREFS_NAME = "txqr"
        const val KEY_SAVE_DIR = "save_dir"
        const val KEY_SAVE_DIR_URI = "save_dir_uri"
        const val KEY_QR_ONLY = "qr_only"
        const val KEY_SHOW_OVERLAY = "show_overlay"
        const val KEY_AUTO_FOCUS = "auto_focus"
        const val REQUEST_CODE_OPEN_DOCUMENT_TREE = 1001
        const val REQUEST_CODE_OPEN_DIR = 1002
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

        // 显示当前保存路径
        updateDirDisplay()

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

        // 查看保存目录 — 直接打开到文件所在位置
        btnOpenSaveDir.setOnClickListener { openSavedDir() }

        // 设置保存目录 — SAF 系统文件选择器
        findViewById<LinearLayout>(R.id.saveDirRow).setOnClickListener { chooseDirectory() }

        btnBack.setOnClickListener { finish() }
    }

    private fun updateDirDisplay() {
        val savedUri = prefs.getString(KEY_SAVE_DIR_URI, "") ?: ""
        val savedPath = prefs.getString(KEY_SAVE_DIR, "") ?: ""
        tvSaveDir.text = when {
            savedPath.isNotEmpty() -> savedPath
            savedUri.isNotEmpty() -> "已选择目录"
            else -> "/Download/TXQR"
        }
    }

    private fun chooseDirectory() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
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

            // 持久化 URI 权限
            try {
                contentResolver.takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            } catch (_: Exception) {}

            // 保存 SAF URI
            prefs.edit().putString(KEY_SAVE_DIR_URI, uri.toString()).apply()

            // 尝试提取真实路径用于显示
            val path = getPathFromTreeUri(uri)
            if (path != null) {
                prefs.edit().putString(KEY_SAVE_DIR, path).apply()
                tvSaveDir.text = path
            } else {
                // 即使无法提取路径，也保存 URI
                val docId = DocumentsContract.getTreeDocumentId(uri)
                val displayName = docId.split(":").lastOrNull() ?: "已选择目录"
                tvSaveDir.text = displayName
                prefs.edit().putString(KEY_SAVE_DIR, "").apply()
            }

            Toast.makeText(this, "✅ 保存目录已修改", Toast.LENGTH_SHORT).show()
        }
    }

    /** 打开保存目录到具体位置 */
    private fun openSavedDir() {
        val savedUri = prefs.getString(KEY_SAVE_DIR_URI, "") ?: ""
        val savedPath = prefs.getString(KEY_SAVE_DIR, "") ?: ""

        if (savedUri.isNotEmpty()) {
            // 有 SAF URI — 用 ACTION_VIEW 打开到该目录
            try {
                val treeUri = Uri.parse(savedUri)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(treeUri, "vnd.android.document/directory")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                return
            } catch (_: Exception) {}

            // 回退: 用 ACTION_OPEN_DOCUMENT_TREE 打开到该位置
            try {
                val treeUri = Uri.parse(savedUri)
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, treeUri)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                return
            } catch (_: Exception) {}
        }

        if (savedPath.isNotEmpty()) {
            // 有真实路径 — 尝试各种方式打开
            val dir = File(savedPath)
            if (!dir.exists()) dir.mkdirs()
            openDirByPath(dir)
            return
        }

        // 默认目录
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "TXQR")
        if (!dir.exists()) dir.mkdirs()
        openDirByPath(dir)
    }

    private fun openDirByPath(dir: File) {
        // 策略1: 小米文件管理器 (直接跳转到目录)
        try {
            val intent = Intent().apply {
                setClassName("com.android.fileexplorer", "com.android.fileexplorer.FileExplorerTabActivity")
                data = Uri.fromFile(dir)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            return
        } catch (_: Exception) {}

        // 策略2: DocumentsUI
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.fromFile(dir), "resource/folder")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            return
        } catch (_: Exception) {}

        // 策略3: file:// URI
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.fromFile(dir)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            return
        } catch (_: Exception) {}

        // 策略4: 系统文档选择器
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "请在文件管理器查看: ${dir.absolutePath}", Toast.LENGTH_LONG).show()
        }
    }

    /** 从 SAF tree URI 提取真实路径 */
    private fun getPathFromTreeUri(uri: Uri): String? {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val split = docId.split(":")
            when {
                split.size >= 2 && split[0].equals("primary", ignoreCase = true) ->
                    "/storage/emulated/0/${split[1]}"
                split.size == 1 && split[0].equals("primary", ignoreCase = true) ->
                    "/storage/emulated/0"
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
