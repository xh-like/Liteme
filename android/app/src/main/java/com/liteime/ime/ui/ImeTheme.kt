package com.liteime.ime.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.ColorInt

/**
 * 键盘主题。配色参考 FlorisBoard（Apache-2.0）的 Material 风格：
 * 圆角按键、阴影、绿色主色回车键、按下态高亮、按键弹出预览。
 */
data class ImeTheme(
    val id: String,
    @ColorInt val background: Int,
    @ColorInt val keyBg: Int,
    @ColorInt val keySpecialBg: Int,
    @ColorInt val keyPressedBg: Int,
    @ColorInt val keyAccentBg: Int,
    @ColorInt val keyAccentPressedBg: Int,
    @ColorInt val text: Int,
    @ColorInt val textSpecial: Int,
    @ColorInt val accentText: Int,
    @ColorInt val candidateBg: Int,
    @ColorInt val candidateFirstBg: Int,
    @ColorInt val candidateText: Int,
    @ColorInt val preeditText: Int,
    @ColorInt val pageText: Int,
    @ColorInt val popupBg: Int,
    @ColorInt val popupText: Int,
    @ColorInt val shadow: Int,
    @ColorInt val handwritingBg: Int,
    @ColorInt val handwritingInk: Int,
) {
    companion object {
        const val LIGHT = "light"
        const val DARK = "dark"

        val LIGHT_THEME = ImeTheme(
            id = LIGHT,
            background = 0xFFF2F3F7.toInt(),
            keyBg = 0xFFFFFFFF.toInt(),
            keySpecialBg = 0xFFE8EAED.toInt(),
            keyPressedBg = 0xFFD7E3FC.toInt(),
            keyAccentBg = 0xFF4CAF50.toInt(),
            keyAccentPressedBg = 0xFF388E3C.toInt(),
            text = 0xFF1F2329.toInt(),
            textSpecial = 0xFF3C4043.toInt(),
            accentText = 0xFFFFFFFF.toInt(),
            candidateBg = 0xFFFFFFFF.toInt(),
            candidateFirstBg = 0xFFE8F0FE.toInt(),
            candidateText = 0xFF1F2329.toInt(),
            preeditText = 0xFF3B6FF6.toInt(),
            pageText = 0xFF6B7280.toInt(),
            popupBg = 0xFF757575.toInt(),
            popupText = 0xFFFFFFFF.toInt(),
            shadow = 0x14000000,
            handwritingBg = 0xFFFFFFFF.toInt(),
            handwritingInk = 0xFF1F2329.toInt(),
        )

        val DARK_THEME = ImeTheme(
            id = DARK,
            background = 0xFF212121.toInt(),
            keyBg = 0xFF424242.toInt(),
            keySpecialBg = 0xFF303134.toInt(),
            keyPressedBg = 0xFF616161.toInt(),
            keyAccentBg = 0xFF4CAF50.toInt(),
            keyAccentPressedBg = 0xFF388E3C.toInt(),
            text = 0xFFFFFFFF.toInt(),
            textSpecial = 0xFFE8EAED.toInt(),
            accentText = 0xFFFFFFFF.toInt(),
            candidateBg = 0xFF2C2C2C.toInt(),
            candidateFirstBg = 0xFF3C4043.toInt(),
            candidateText = 0xFFFFFFFF.toInt(),
            preeditText = 0xFF8AB4F8.toInt(),
            pageText = 0xFF9AA0A6.toInt(),
            popupBg = 0xFF9AA0A6.toInt(),
            popupText = 0xFF202124.toInt(),
            shadow = 0x33000000,
            handwritingBg = 0xFF303134.toInt(),
            handwritingInk = 0xFFFFFFFF.toInt(),
        )

        fun load(context: Context): ImeTheme {
            val prefs: SharedPreferences = context.getSharedPreferences("liteime", Context.MODE_PRIVATE)
            return when (prefs.getString("theme", LIGHT)) {
                DARK -> DARK_THEME
                else -> LIGHT_THEME
            }
        }
    }
}
