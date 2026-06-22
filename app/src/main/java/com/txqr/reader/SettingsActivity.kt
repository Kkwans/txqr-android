package com.txqr.reader

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Environment
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

    companion object {
        const val PREFS_NAME = "txqr"
        const val KEY_SAVE_DIR = "save_dir"
        const val KEY_QR_ONLY = "qr_only"
        const val KEY_SHOW_OVERLAY = "show_overlay"
        const val KEY_AUTO_FOCUS = "auto_focus"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        val tvSaveDir = findViewById<TextView>(R.id.tvSaveDir)
        val btnOpenSaveDir = findViewById<Button>(R.id.btnOpenSaveDir)
        val switchQrOnly = findViewById<Switch>(R.id.switchQrOnly)
        val switchShowOverlay = findViewById<Switch>(R.id.switchShowOverlay)
        val switchAutoFocus = findViewById<Switch>(R.id.switchAutoFocus)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)

        // 显示当前保存目录
        val saveDir = prefs.getString(KEY_SAVE_DIR, "") ?: ""
        tvSaveDir.text = if (saveDir.isEmpty()) "/Download/TXQR" else saveDir

        // 加载设置
        switchQrOnly.isChecked = prefs.getBoolean(KEY_QR_ONLY, true)
        switchShowOverlay.isChecked = prefs.getBoolean(KEY_SHOW_OVERLAY, true)
        switchAutoFocus.isChecked = prefs.getBoolean(KEY_AUTO_FOCUS, true)

        // 保存设置
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

        // 设置保存目录
        findViewById<LinearLayout>(R.id.saveDirRow).setOnClickListener {
            // TODO: 弹出目录选择器，暂时使用自定义输入
            val input = android.widget.EditText(this).apply {
                hint = "输入自定义目录路径"
                setText(saveDir)
                setSelection(saveDir.length)
            }
            android.app.AlertDialog.Builder(this)
                .setTitle("设置保存目录")
                .setView(input)
                .setPositiveButton("确定") { _, _ ->
                    val path = input.text.toString().trim()
                    prefs.edit().putString(KEY_SAVE_DIR, path).apply()
                    tvSaveDir.text = if (path.isEmpty()) "/Download/TXQR" else path
                }
                .setNegativeButton("恢复默认") { _, _ ->
                    prefs.edit().remove(KEY_SAVE_DIR).apply()
                    tvSaveDir.text = "/Download/TXQR"
                }
                .show()
        }

        btnBack.setOnClickListener { finish() }
    }
}
