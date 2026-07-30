package com.fanqie.xfqdeobf2

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import de.robv.android.xposed.callbacks.XC_LoadPackage
import kotlin.math.sqrt

class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "xfqdeobf2_prefs"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_DEOBF_KEY = "deobf_key"
        val DEFAULT_KEY: Double = (sqrt(5.0) - 1.0) / 2.0 // 黄金比例共轭 ~0.618

        private var prefs: SharedPreferences? = null

        /**
         * Called from XpEntry when QQ process starts.
         * Must use module's own package's SharedPreferences (not QQ's)
         * so the settings are shared between the standalone SettingsActivity
         * and the XpEntry running inside QQ.
         */
        fun initAppContext(lpparam: XC_LoadPackage.LoadPackageParam) {
            if (prefs != null) return
            try {
                // Get the system context first
                val atClass = Class.forName("android.app.ActivityThread")
                val currentAt = atClass.getMethod("currentActivityThread").invoke(null)
                val app = atClass.getMethod("getApplication").invoke(currentAt) as? Context ?: return
                // Create a context for OUR module's package to share prefs
                // between the standalone SettingsActivity and the hook inside QQ
                val modCtx = app.createPackageContext(
                    "com.fanqie.xfqdeobf2",
                    Context.CONTEXT_IGNORE_SECURITY
                )
                prefs = modCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            } catch (e: Throwable) {
                // Fallback: use QQ's own prefs (settings will be QQ-process-local)
                try {
                    val atClass = Class.forName("android.app.ActivityThread")
                    val currentAt = atClass.getMethod("currentActivityThread").invoke(null)
                    val app = atClass.getMethod("getApplication").invoke(currentAt) as? Context
                    if (app != null) {
                        prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    }
                } catch (_: Throwable) {}
            }
        }

        /** For the standalone activity, init from its own context. */
        private fun initLocal(ctx: Context) {
            if (prefs == null) {
                prefs = ctx.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }
        }

        var enabled: Boolean
            get() = prefs?.getBoolean(KEY_ENABLED, true) ?: true
            set(v) { prefs?.edit()?.putBoolean(KEY_ENABLED, v)?.apply() }

        var deobfKey: Double
            get() {
                val raw = prefs?.getString(KEY_DEOBF_KEY, null) ?: return DEFAULT_KEY
                return raw.toDoubleOrNull()?.takeIf { it > 0.0 && it <= 1.618 } ?: DEFAULT_KEY
            }
            set(v) { prefs?.edit()?.putString(KEY_DEOBF_KEY, v.toString())?.apply() }
    }

    // ---- UI ----

    private lateinit var switchEnabled: Switch
    private lateinit var tvKeyStatus: TextView
    private lateinit var etKey: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initLocal(this)
        buildUI()
    }

    override fun onResume() {
        super.onResume()
        refreshUI()
    }

    private fun buildUI() {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(20)
            setPadding(pad, pad, pad, pad)
        }

        // Title
        root.addView(TextView(this).apply {
            text = "小番茄解混淆"
            textSize = 22f
            setTextColor(Color.parseColor("#212121"))
            setPadding(0, dp(8), 0, dp(16))
        })

        // Description
        root.addView(TextView(this).apply {
            text = "长按QQ聊天图片，点击\"解混淆\"进行小番茄(Gilbert曲线)图片解混淆并保存到相册。"
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            setPadding(0, 0, 0, dp(20))
        })

        // Enable Switch
        val enableRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        enableRow.addView(TextView(this).apply {
            text = "功能开关"
            textSize = 16f
            setTextColor(Color.parseColor("#212121"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        switchEnabled = Switch(this).apply {
            setOnCheckedChangeListener { _, isChecked ->
                enabled = isChecked
                Toast.makeText(context, if (isChecked) "已启用解混淆功能" else "已禁用解混淆功能", Toast.LENGTH_SHORT).show()
                refreshKeySection()
            }
        }
        enableRow.addView(switchEnabled)
        root.addView(enableRow)
        root.addView(divider())

        // Key Section
        val keyLabel = TextView(this).apply {
            text = "解混淆 Key 配置"
            textSize = 16f
            setTextColor(Color.parseColor("#212121"))
            setPadding(0, dp(16), 0, dp(4))
        }
        root.addView(keyLabel)
        root.addView(TextView(this).apply {
            text = "范围 (0, 1.618]，默认 ${"%.4f".format(DEFAULT_KEY)} (黄金比例共轭)"
            textSize = 13f
            setTextColor(Color.parseColor("#999999"))
            setPadding(0, 0, 0, dp(8))
        })

        etKey = EditText(this).apply {
            setText("%.6f".format(deobfKey))
            setTextColor(Color.parseColor("#212121"))
            textSize = 16f
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }
        root.addView(etKey)

        tvKeyStatus = TextView(this).apply {
            textSize = 13f
            setPadding(0, dp(8), 0, dp(12))
        }
        root.addView(tvKeyStatus)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val padBtn = dp(8)
            setPadding(0, padBtn, 0, padBtn)
        }
        btnRow.addView(Button(this).apply {
            text = "保存"
            setOnClickListener { saveKey() }
        })
        btnRow.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(16), 0)
        })
        btnRow.addView(Button(this).apply {
            text = "恢复默认"
            setOnClickListener { resetKey() }
        })
        root.addView(btnRow)
        root.addView(divider())

        // Info section
        root.addView(TextView(this).apply {
            text = "如何使用"
            textSize = 16f
            setTextColor(Color.parseColor("#212121"))
            setPadding(0, dp(16), 0, dp(8))
        })
        root.addView(TextView(this).apply {
            text = "1. 在 LSPosed 管理器中启用本模块，作用域勾选 QQ\n" +
                  "2. 重新启动 QQ\n" +
                  "3. 在 QQ 聊天中长按小番茄混淆的图片\n" +
                  "4. 点击弹出菜单中的「解混淆」\n" +
                  "5. 图片将自动解混淆并保存到 Pictures/FanqieDeobf"
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            setLineSpacing(dp(4).toFloat(), 1f)
        })

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun divider(): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            )
            setBackgroundColor(Color.parseColor("#E0E0E0"))
            val margin = dp(12)
            (layoutParams as LinearLayout.LayoutParams).setMargins(0, margin, 0, margin)
        }
    }

    private fun saveKey() {
        val raw = etKey.text.toString().trim()
        val value = raw.toDoubleOrNull()
        if (value == null || value <= 0.0 || value > 1.618) {
            AlertDialog.Builder(this)
                .setTitle("输入错误")
                .setMessage("请输入 (0, 1.618] 范围内的数字")
                .setPositiveButton("确定", null)
                .show()
            return
        }
        deobfKey = value
        refreshKeySection()
        Toast.makeText(this, "Key 已保存: ${"%.4f".format(value)}", Toast.LENGTH_SHORT).show()
    }

    private fun resetKey() {
        deobfKey = DEFAULT_KEY
        etKey.setText("%.6f".format(DEFAULT_KEY))
        refreshKeySection()
        Toast.makeText(this, "已恢复默认 Key", Toast.LENGTH_SHORT).show()
    }

    private fun refreshUI() {
        switchEnabled.isChecked = enabled
        etKey.setText("%.6f".format(deobfKey))
        refreshKeySection()
    }

    private fun refreshKeySection() {
        val current = deobfKey
        if (current == DEFAULT_KEY) {
            tvKeyStatus.text = "当前使用默认 Key: ${"%.4f".format(DEFAULT_KEY)}"
        } else {
            tvKeyStatus.text = "当前 Key: ${"%.4f".format(current)} (默认: ${"%.4f".format(DEFAULT_KEY)})"
        }
        tvKeyStatus.setTextColor(Color.parseColor("#4CAF50"))
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
