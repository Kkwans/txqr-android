package com.txqr.reader

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
        const val KEY_AUTO_FOCUS = "auto_focus"
        const val KEY_SHOW_OVERLAY = "show_overlay"
        const val KEY_SHOW_PROGRESS = "show_progress"
        const val KEY_RESOLUTION = "resolution"
        const val REQUEST_CODE_OPEN_DOCUMENT_TREE = 1001

        // 分辨率选项: 值 -> 目标分辨率
        val RESOLUTIONS = arrayOf("640×480", "1280×720", "1920×1080")
        val RESOLUTION_VALUES = arrayOf("640x480", "1280x720", "1920x1080")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        tvSaveDir = findViewById(R.id.tvSaveDir)
        val btnOpenSaveDir = findViewById<Button>(R.id.btnOpenSaveDir)
        val switchQrOnly = findViewById<Switch>(R.id.switchQrOnly)
        val switchAutoFocus = findViewById<Switch>(R.id.switchAutoFocus)
        val switchShowOverlay = findViewById<Switch>(R.id.switchShowOverlay)
        val switchShowProgress = findViewById<Switch>(R.id.switchShowProgress)
        val spinnerResolution = findViewById<Spinner>(R.id.spinnerResolution)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)

        updateDirDisplay()

        // 初始化开关状态
        switchQrOnly.isChecked = prefs.getBoolean(KEY_QR_ONLY, true)
        switchAutoFocus.isChecked = prefs.getBoolean(KEY_AUTO_FOCUS, true)
        switchShowOverlay.isChecked = prefs.getBoolean(KEY_SHOW_OVERLAY, true)
        switchShowProgress.isChecked = prefs.getBoolean(KEY_SHOW_PROGRESS, true)

        // 开关监听
        switchQrOnly.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_QR_ONLY, isChecked).apply()
        }
        switchAutoFocus.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_AUTO_FOCUS, isChecked).apply()
        }
        switchShowOverlay.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_SHOW_OVERLAY, isChecked).apply()
        }
        switchShowProgress.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_SHOW_PROGRESS, isChecked).apply()
        }

        // 分辨率选择器
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, RESOLUTIONS)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerResolution.adapter = adapter
        val currentRes = prefs.getString(KEY_RESOLUTION, "640x480") ?: "640x480"
        val resIndex = RESOLUTION_VALUES.indexOf(currentRes).coerceAtLeast(0)
        spinnerResolution.setSelection(resIndex)
        spinnerResolution.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                prefs.edit().putString(KEY_RESOLUTION, RESOLUTION_VALUES[position]).apply()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })

        // 帮助弹窗
        findViewById<ImageButton>(R.id.btnHelpQrOnly).setOnClickListener {
            showHelp("仅扫描 QR 码", "开启后只识别 QR 码格式，忽略其他条形码。\n\n建议保持开启，可提升识别速度。")
        }
        findViewById<ImageButton>(R.id.btnHelpAutoFocus).setOnClickListener {
            showHelp("连续自动对焦", "相机持续自动对焦，无需点击屏幕。\n\n建议保持开启，确保二维码始终清晰。")
        }

        // 文件保存
        btnOpenSaveDir.setOnClickListener { openSavedDir() }
        findViewById<LinearLayout>(R.id.saveDirRow).setOnClickListener { chooseDirectory() }
        btnBack.setOnClickListener { finish() }

        // GitHub 链接
        findViewById<TextView>(R.id.tvGithub).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Kkwans/txqr-android"))
            startActivity(intent)
        }
    }

    private fun showHelp(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("知道了", null)
            .show()
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

            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}

            prefs.edit().putString(KEY_SAVE_DIR_URI, uri.toString()).apply()

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

    private fun openSavedDir() {
        val savedPath = prefs.getString(KEY_SAVE_DIR, "") ?: ""
        val savedUri = prefs.getString(KEY_SAVE_DIR_URI, "") ?: ""

        if (savedPath.isNotEmpty()) {
            val dir = File(savedPath)
            if (!dir.exists()) dir.mkdirs()
            openDirWithFileManager(dir)
            return
        }

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

        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "TXQR")
        if (!dir.exists()) dir.mkdirs()
        openDirWithFileManager(dir)
    }

    private fun openDirWithFileManager(dir: File) {
        try {
            val intent = Intent().apply {
                setClassName("com.android.fileexplorer", "com.android.fileexplorer.FileExplorerTabActivity")
                data = Uri.fromFile(dir)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            return
        } catch (_: Exception) {}

        try {
            val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_FILES)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            return
        } catch (_: Exception) {}

        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.fromFile(dir)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            return
        } catch (_: Exception) {}

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
