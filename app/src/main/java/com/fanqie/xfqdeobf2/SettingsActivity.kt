package com.fanqie.xfqdeobf2

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.sqrt

/**
 * 简单设置页：开关 and Key 配置
 */
class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "xfqdeobf2_prefs"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_DEOBF_KEY = "deobf_key"
        const val DEFAULT_KEY = (sqrt(5.0) - 1.0) / 2.0 // 黄金比例共轭

        private var prefs: SharedPreferences? = null

        fun init(ctx: Context) {
            if (prefs == null) {
                prefs = ctx.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }
        }

        var enabled: Boolean
            get() = prefs?.getBoolean(KEY_ENABLED, true) ?: true
            set(v) = prefs?.edit()?.putBoolean(KEY_ENABLED, v)?.apply()

        var deobfKey: Double
            get() {
                val raw = prefs?.getString(KEY_DEOBF_KEY, null) ?: return DEFAULT_KEY
                return raw.toDoubleOrNull() ?: DEFAULT_KEY
            }
            set(v) = prefs?.edit()?.putString(KEY_DEOBF_KEY, v.toString())?.apply()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        init(this)
        showMainDialog()
    }

    private fun showMainDialog() {
        val items = arrayOf(
            if (enabled) "功能状态: 已启用 (点击禁用)" else "功能状态: 已禁用 (点击启用)",
            "当前 Key: ${"%.4f".format(deobfKey)} (默认 ${"%.4f".format(DEFAULT_KEY)})",
            "修改 Key 值"
        )
        AlertDialog.Builder(this)
            .setTitle("小番茄解混淆")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        enabled = !enabled
                        showMainDialog()
                    }
                    1 -> showKeyDialog()
                    2 -> showKeyDialog()
                }
            }
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun showKeyDialog() {
        val edit = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText("%.6f".format(deobfKey))
            setSelection(text.length)
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), 0)
            addView(edit)
        }
        AlertDialog.Builder(this)
            .setTitle("设置解混淆 Key")
            .setMessage("范围 (0, 1.618], 默认 ${"%.4f".format(DEFAULT_KEY)} (黄金比例共轭)")
            .setView(layout)
            .setPositiveButton("保存") { _, _ ->
                val v = edit.text.toString().trim().toDoubleOrNull()
                if (v == null || v <= 0.0 || v > 1.618) {
                    AlertDialog.Builder(this)
                        .setMessage("请输入 (0, 1.618] 范围内的数字")
                        .setPositiveButton("确定", null)
                        .show()
                } else {
                    deobfKey = v
                    showMainDialog()
                }
            }
            .setNeutralButton("恢复默认") { _, _ ->
                deobfKey = DEFAULT_KEY
                showMainDialog()
            }
            .setNegativeButton("取消") { _, _ -> showMainDialog() }
            .show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
