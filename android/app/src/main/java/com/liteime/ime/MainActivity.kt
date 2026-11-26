package com.liteime.ime

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** 设置页：启用输入法 / 切换输入法 / 选择双拼方案 / 模糊音。 */
class MainActivity : Activity() {

    private val prefs by lazy { getSharedPreferences("liteime", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(0xFFF2F3F7.toInt())
        }

        root.addView(title("LiteIME 轻输入法"))
        root.addView(subtitle("全拼 · 双拼 · 手写 · 联想"))

        root.addView(button("第一步：启用 LiteIME 输入法") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        root.addView(button("第二步：切换到 LiteIME") {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        })

        root.addView(title("输入方案"))
        val group = RadioGroup(this)
        val schemes = listOf(
            "quanpin" to "全拼",
            "xiaohe" to "小鹤双拼",
            "mspy" to "微软双拼",
            "ziranma" to "自然码",
        )
        val current = prefs.getString("scheme", "quanpin")
        for ((key, label) in schemes) {
            val rb = RadioButton(this).apply {
                text = label
                id = IdGen.generateViewId()
                isChecked = key == current
                setOnClickListener {
                    prefs.edit().putString("scheme", key).apply()
                    Toast.makeText(context, "已切换到$label", Toast.LENGTH_SHORT).show()
                }
            }
            group.addView(rb)
        }
        root.addView(group)

        val fuzzy = Switch(this).apply {
            text = "模糊音（z/zh、c/ch、s/sh、n/l、前后鼻音）"
            isChecked = prefs.getBoolean("fuzzy", false)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("fuzzy", checked).apply()
            }
        }
        root.addView(fuzzy)

        root.addView(title("键盘主题"))
        val themeGroup = RadioGroup(this)
        val themes = listOf(
            "light" to "浅色（Floris 风格）",
            "dark" to "深色",
        )
        val currentTheme = prefs.getString("theme", "light")
        for ((key, label) in themes) {
            val rb = RadioButton(this).apply {
                text = label
                id = IdGen.generateViewId()
                isChecked = key == currentTheme
                setOnClickListener {
                    prefs.edit().putString("theme", key).apply()
                    Toast.makeText(context, "已切换到$label，重新弹出键盘生效", Toast.LENGTH_SHORT).show()
                }
            }
            themeGroup.addView(rb)
        }
        root.addView(themeGroup)

        root.addView(subtitle("提示：英文模式下直接输入字母；中文模式点「中/英」切换。"))

        setContentView(root)
    }

    private fun title(t: String) = TextView(this).apply {
        text = t
        textSize = 20f
        setPadding(0, dp(8), 0, dp(8))
    }

    private fun subtitle(t: String) = TextView(this).apply {
        text = t
        textSize = 13f
        setPadding(0, dp(6), 0, dp(12))
    }

    private fun button(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t
        setOnClickListener { onClick() }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

private object IdGen {
    private var next = 1000
    fun generateViewId(): Int = next++
}
