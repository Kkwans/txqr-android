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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var tvSaveDir: TextView
    private lateinit var tvResolution: TextView
    private lateinit var switchShowProgress: Switch
    private lateinit var switchAlwaysShow: Switch

    companion object {
        const val PREFS_NAME = "txqr"
        const val KEY_SAVE_DIR = "save_dir"
        const val KEY_SAVE_DIR_URI = "save_dir_uri"
        const val KEY_QR_ONLY = "qr_only"
        const val KEY_AUTO_FOCUS = "auto_focus"
        const val KEY_SHOW_OVERLAY = "show_overlay"
        const val KEY_SHOW_PROGRESS = "show_progress"
        const val KEY_ALWAYS_SHOW_PROGRESS = "always_show_progress"
        const val KEY_RESOLUTION = "resolution"
        const val REQUEST_CODE_OPEN_DOCUMENT_TREE = 1001
        val RESOLUTION_LABELS = arrayOf("480p (最快)", "720p (均衡)", "1080p (最准)", "1440p (超清)")
        val RESOLUTION_VALUES = arrayOf("640x480", "1280x720", "1920x1080", "2560x1440")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        tvSaveDir = findViewById(R.id.tvSaveDir)
        tvResolution = findViewById(R.id.tvResolution)

        val switchQrOnly = findViewById<Switch>(R.id.switchQrOnly)
        val switchAutoFocus = findViewById<Switch>(R.id.switchAutoFocus)
        val switchShowOverlay = findViewById<Switch>(R.id.switchShowOverlay)
        switchShowProgress = findViewById(R.id.switchShowProgress)
        switchAlwaysShow = findViewById(R.id.switchAlwaysShowProgress)

        updateDirDisplay(); updateResolutionDisplay()

        switchQrOnly.isChecked = prefs.getBoolean(KEY_QR_ONLY, true)
        switchAutoFocus.isChecked = prefs.getBoolean(KEY_AUTO_FOCUS, true)
        switchShowOverlay.isChecked = prefs.getBoolean(KEY_SHOW_OVERLAY, true)
        switchAlwaysShow.isChecked = prefs.getBoolean(KEY_ALWAYS_SHOW_PROGRESS, true)
        updateShowProgressState()

        switchQrOnly.setOnCheckedChangeListener { _, c -> prefs.edit().putBoolean(KEY_QR_ONLY, c).apply() }
        switchAutoFocus.setOnCheckedChangeListener { _, c -> prefs.edit().putBoolean(KEY_AUTO_FOCUS, c).apply() }
        switchShowOverlay.setOnCheckedChangeListener { _, c -> prefs.edit().putBoolean(KEY_SHOW_OVERLAY, c).apply() }

        // 始终显示 → 联动解码时显示
        switchAlwaysShow.setOnCheckedChangeListener { _, c ->
            prefs.edit().putBoolean(KEY_ALWAYS_SHOW_PROGRESS, c).apply()
            updateShowProgressState()
        }

        // 解码时显示（仅在始终显示关闭时可操作）
        switchShowProgress.setOnCheckedChangeListener { _, c ->
            if (switchAlwaysShow.isChecked && !c) {
                // 始终显示开启时，不允许关闭解码时显示
                switchShowProgress.isChecked = true
                return@setOnCheckedChangeListener
            }
            prefs.edit().putBoolean(KEY_SHOW_PROGRESS, c).apply()
        }

        findViewById<TextView>(R.id.btnHelpQrOnly).setOnClickListener { showHelp("仅扫描 QR 码", "开启后只识别 QR 码格式，忽略其他条形码。建议保持开启。") }
        findViewById<TextView>(R.id.btnHelpAutoFocus).setOnClickListener { showHelp("连续自动对焦", "相机持续自动对焦，无需点击屏幕。建议保持开启。") }
        findViewById<TextView>(R.id.btnHelpOverlay).setOnClickListener { showHelp("扫描区域提示", "在画面中央显示扫描参考框和暗色遮罩。关闭后不再显示。") }
        findViewById<TextView>(R.id.btnHelpProgress).setOnClickListener { showHelp("解码时显示进度卡片", "解码过程中在底部显示详细进度信息。开启「始终显示」时自动启用。") }
        findViewById<TextView>(R.id.btnHelpAlwaysShow).setOnClickListener { showHelp("始终显示进度卡片", "进入软件即显示进度卡片，点击「开始扫描」按钮才开始扫描。\n\n开启后「解码时显示进度卡片」将自动启用。") }
        findViewById<TextView>(R.id.btnHelpResolution).setOnClickListener { showHelp("分析分辨率", "相机分析二维码的分辨率。\n\n• 480p: 最快，适合近距离\n• 720p: 均衡\n• 1080p: 最准，适合远距离\n• 1440p: 超清，耗电更多") }

        // 设置项名称也能点击触发帮助弹窗
        findViewById<TextView>(R.id.tvQrOnly).setOnClickListener { showHelp("仅扫描 QR 码", "开启后只识别 QR 码格式，忽略其他条形码。建议保持开启。") }
        findViewById<TextView>(R.id.tvAutoFocus).setOnClickListener { showHelp("连续自动对焦", "相机持续自动对焦，无需点击屏幕。建议保持开启。") }
        findViewById<TextView>(R.id.tvOverlay).setOnClickListener { showHelp("扫描区域提示", "在画面中央显示扫描参考框和暗色遮罩。关闭后不再显示。") }
        findViewById<TextView>(R.id.tvProgressLabel).setOnClickListener { showHelp("解码时显示进度卡片", "解码过程中在底部显示详细进度信息。开启「始终显示」时自动启用。") }
        findViewById<TextView>(R.id.tvAlwaysShowLabel).setOnClickListener { showHelp("始终显示进度卡片", "进入软件即显示进度卡片，点击「开始扫描」按钮才开始扫描。\n\n开启后「解码时显示进度卡片」将自动启用。") }
        findViewById<TextView>(R.id.tvResolutionLabel).setOnClickListener { showHelp("分析分辨率", "相机分析二维码的分辨率。\n\n• 480p: 最快，适合近距离\n• 720p: 均衡\n• 1080p: 最准，适合远距离\n• 1440p: 超清，耗电更多") }

        findViewById<LinearLayout>(R.id.resolutionRow).setOnClickListener { showResolutionPicker() }
        findViewById<Button>(R.id.btnOpenSaveDir).setOnClickListener { openSavedDir() }
        findViewById<LinearLayout>(R.id.saveDirRow).setOnClickListener { chooseDirectory() }
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvGithub).setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Kkwans/txqr-android"))) }
    }

    private fun updateShowProgressState() {
        val alwaysOn = prefs.getBoolean(KEY_ALWAYS_SHOW_PROGRESS, true)
        if (alwaysOn) {
            // 始终显示开启 → 解码时显示强制开启且不可关闭
            prefs.edit().putBoolean(KEY_SHOW_PROGRESS, true).apply()
            switchShowProgress.isChecked = true
            switchShowProgress.isEnabled = false
            switchShowProgress.alpha = 0.5f
        } else {
            switchShowProgress.isChecked = prefs.getBoolean(KEY_SHOW_PROGRESS, true)
            switchShowProgress.isEnabled = true
            switchShowProgress.alpha = 1.0f
        }
    }

    private fun showHelp(title: String, message: String) {
        AlertDialog.Builder(this, R.style.RoundedDialog)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun showResolutionPicker() {
        val idx = RESOLUTION_VALUES.indexOf(prefs.getString(KEY_RESOLUTION, "640x480") ?: "640x480").coerceAtLeast(0)
        AlertDialog.Builder(this, R.style.RoundedDialog)
            .setTitle("分析分辨率")
            .setSingleChoiceItems(RESOLUTION_LABELS, idx) { d, w ->
                prefs.edit().putString(KEY_RESOLUTION, RESOLUTION_VALUES[w]).apply()
                updateResolutionDisplay()
                d.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updateResolutionDisplay() {
        val idx = RESOLUTION_VALUES.indexOf(prefs.getString(KEY_RESOLUTION, "640x480") ?: "640x480").coerceAtLeast(0)
        tvResolution.text = RESOLUTION_LABELS[idx]
    }

    private fun updateDirDisplay() {
        val p = prefs.getString(KEY_SAVE_DIR, "") ?: ""
        val u = prefs.getString(KEY_SAVE_DIR_URI, "") ?: ""
        tvSaveDir.text = when {
            p.isNotEmpty() -> p.removePrefix("/storage/emulated/0").let { if (it.startsWith("/")) it else "/$it" }
            u.isNotEmpty() -> "已选择目录"
            else -> "/Download/TXQR"
        }
    }

    private fun chooseDirectory() {
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }, REQUEST_CODE_OPEN_DOCUMENT_TREE)
        } catch (_: Exception) { Toast.makeText(this, "系统文件选择器不可用", Toast.LENGTH_SHORT).show() }
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == REQUEST_CODE_OPEN_DOCUMENT_TREE && res == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Exception) {}
            prefs.edit().putString(KEY_SAVE_DIR_URI, uri.toString()).apply()
            try {
                val docId = DocumentsContract.getTreeDocumentId(uri); val split = docId.split(":")
                val path = if (split.size >= 2 && split[0].equals("primary", ignoreCase = true)) "/storage/emulated/0/${split[1]}" else null
                if (path != null) { prefs.edit().putString(KEY_SAVE_DIR, path).apply(); tvSaveDir.text = path.removePrefix("/storage/emulated/0") }
                else { tvSaveDir.text = "/${split.lastOrNull() ?: "已选择目录"}"; prefs.edit().putString(KEY_SAVE_DIR, "").apply() }
            } catch (_: Exception) { tvSaveDir.text = "已选择目录" }
            Toast.makeText(this, "✅ 保存目录已修改", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openSavedDir() {
        val p = prefs.getString(KEY_SAVE_DIR, "") ?: ""; val u = prefs.getString(KEY_SAVE_DIR_URI, "") ?: ""
        if (p.isNotEmpty()) { val d = File(p); if (!d.exists()) d.mkdirs(); openDir(d); return }
        if (u.isNotEmpty()) { try { startActivity(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply { putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(u)); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }); return } catch (_: Exception) {} }
        val d = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "TXQR"); if (!d.exists()) d.mkdirs(); openDir(d)
    }

    private fun openDir(d: File) {
        try { startActivity(Intent().apply { setClassName("com.android.fileexplorer", "com.android.fileexplorer.FileExplorerTabActivity"); data = Uri.fromFile(d); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }); return } catch (_: Exception) {}
        try { startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_FILES).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }); return } catch (_: Exception) {}
        try { startActivity(Intent(Intent.ACTION_VIEW).apply { data = Uri.fromFile(d); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }); return } catch (_: Exception) {}
        try { startActivity(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) } catch (_: Exception) {}
    }
}
