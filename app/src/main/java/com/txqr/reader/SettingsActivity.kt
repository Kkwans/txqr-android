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

        btnOpenSaveDir.setOnClickListener { openSavedDir() }
        findViewById<LinearLayout>(R.id.saveDirRow).setOnClickListener { chooseDirectory() }
        btnBack.setOnClickListener { finish() }

        // GitHub 链接
        findViewById<TextView>(R.id.tvGithub).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Kkwans/txqr-android"))
            startActivity(intent)
        }
    }

    private fun updateDirDisplay() {
        val savedPath = prefs.getString(KEY_SAVE_DIR, "") ?: ""
        val savedUri = prefs.getString(KEY_SAVE_DIR_URI, "") ?: ""
        tvSaveDir.text = when {
            savedPath.isNotEmpty() -> cleanPath(savedPath)
            savedUri.isNotEmpty() -> "已选择目录"
            else -> "/Download/TXQR"
        }
    }

    /** 去掉 /storage/emulated/0 前缀，保持统一格式 */
    private fun cleanPath(path: String): String {
        return path
            .removePrefix("/storage/emulated/0")
            .let { if (it.startsWith("/")) it else "/$it" }
    }

    private fun chooseDirectory() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                )
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
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}

            // 保存 SAF URI
            prefs.edit().putString(KEY_SAVE_DIR_URI, uri.toString()).apply()

            // 提取真实路径并去掉 /storage/emulated/0 前缀
            val path = getPathFromTreeUri(uri)
            if (path != null) {
                prefs.edit().putString(KEY_SAVE_DIR, path).apply()
                tvSaveDir.text = cleanPath(path)
            } else {
                val docId = DocumentsContract.getTreeDocumentId(uri)
                val displayName = docId.split(":").lastOrNull() ?: "已选择目录"
                tvSaveDir.text = "/$displayName"
                prefs.edit().putString(KEY_SAVE_DIR, "").apply()
            }

            Toast.makeText(this, "✅ 保存目录已修改", Toast.LENGTH_SHORT).show()
        }
    }

    /** 打开保存目录 — 直接跳转到文件管理器的对应目录 */
    private fun openSavedDir() {
        val savedPath = prefs.getString(KEY_SAVE_DIR, "") ?: ""
        val savedUri = prefs.getString(KEY_SAVE_DIR_URI, "") ?: ""

        // 有真实路径时，直接用 file:// URI 打开
        if (savedPath.isNotEmpty()) {
            val dir = File(savedPath)
            if (!dir.exists()) dir.mkdirs()
            openDirWithFileManager(dir)
            return
        }

        // 有 SAF URI 时，用 ACTION_OPEN_DOCUMENT_TREE 打开到该位置
        if (savedUri.isNotEmpty()) {
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

        // 默认目录
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "TXQR")
        if (!dir.exists()) dir.mkdirs()
        openDirWithFileManager(dir)
    }

    /** 尝试用各种方式打开目录到具体位置 */
    private fun openDirWithFileManager(dir: File) {
        // 策略1: 小米文件管理器 (com.android.fileexplorer)
        try {
            val intent = Intent().apply {
                setClassName("com.android.fileexplorer", "com.android.fileexplorer.FileExplorerTabActivity")
                data = Uri.fromFile(dir)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            return
        } catch (_: Exception) {}

        // 策略2: 系统文件管理器 (API 30+)
        try {
            val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_FILES)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            return
        } catch (_: Exception) {}

        // 策略3: file:// URI + 通用 intent
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
