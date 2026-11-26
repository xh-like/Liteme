package com.liteime.ime.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.ColorInt

/**
 * 键盘主题。配色与布局语言参考语燕输入法（gurecn/YuyanIme, BSD-3-Clause）
 * 的 MaterialLight / MaterialDark 内置主题：
 * 蓝灰键盘底、近白圆角按键、深蓝灰文字、青绿色回车键、半透明按下高亮。
 */
data class ImeTheme(
    val id: String,
    val label: String,
    val isDark: Boolean,
    @ColorInt val keyboardBg: Int,
    @ColorInt val barBg: Int,
    @ColorInt val keyBg: Int,
    @ColorInt val keyText: Int,
    @ColorInt val functionKeyBg: Int,
    @ColorInt val functionKeyText: Int,
    @ColorInt val accentKeyBg: Int,
    @ColorInt val accentKeyText: Int,
    @ColorInt val pressedOverlay: Int,
    @ColorInt val popupBg: Int,
    @ColorInt val popupText: Int,
    @ColorInt val toolbarIconBg: Int,
    @ColorInt val toolbarIcon: Int,
    @ColorInt val candidateBg: Int,
    @ColorInt val candidateText: Int,
    @ColorInt val candidateFirstBg: Int,
    @ColorInt val preeditText: Int,
    @ColorInt val pageText: Int,
    @ColorInt val handwritingBg: Int,
    @ColorInt val handwritingInk: Int,
) {
    companion object {
        const val MATERIAL_LIGHT = "material_light"
        const val MATERIAL_DARK = "material_dark"

        /** 语燕 MaterialLight 精确色值 */
        val LIGHT_THEME = ImeTheme(
            id = MATERIAL_LIGHT,
            label = "语燕浅色",
            isDark = false,
            keyboardBg = 0xFFECEFF1.toInt(),
            barBg = 0xFFE4E7E9.toInt(),
            keyBg = 0xFFFBFCFC.toInt(),
            keyText = 0xFF37474F.toInt(),
            functionKeyBg = 0xFFC9CED1.toInt(),
            functionKeyText = 0xFF37474F.toInt(),
            accentKeyBg = 0xFF5CB5AB.toInt(),
            accentKeyText = 0xFFFFFFFF.toInt(),
            pressedOverlay = 0x1F000000.toInt(),
            popupBg = 0xFFD9DBDD.toInt(),
            popupText = 0xFF37474F.toInt(),
            toolbarIconBg = 0xFFFFFFFF.toInt(),
            toolbarIcon = 0xFF37474F.toInt(),
            candidateBg = 0xFFE4E7E9.toInt(),
            candidateText = 0xFF37474F.toInt(),
            candidateFirstBg = 0xFFD2DADD.toInt(),
            preeditText = 0xFF2E7D74.toInt(),
            pageText = 0xFF78909C.toInt(),
            handwritingBg = 0xFFFBFCFC.toInt(),
            handwritingInk = 0xFF37474F.toInt(),
        )

        /** 语燕 MaterialDark 精确色值 */
        val DARK_THEME = ImeTheme(
            id = MATERIAL_DARK,
            label = "语燕深色",
            isDark = true,
            keyboardBg = 0xFF263238.toInt(),
            barBg = 0xFF21272B.toInt(),
            keyBg = 0xFF404A50.toInt(),
            keyText = 0xFFD9DBDC.toInt(),
            functionKeyBg = 0xFF3B464C.toInt(),
            functionKeyText = 0xFFD9DBDC.toInt(),
            accentKeyBg = 0xFF6EACA8.toInt(),
            accentKeyText = 0xFFFFFFFF.toInt(),
            pressedOverlay = 0x33FFFFFF.toInt(),
            popupBg = 0xFF3C474C.toInt(),
            popupText = 0xFFECEFF1.toInt(),
            toolbarIconBg = 0xFF3B464C.toInt(),
            toolbarIcon = 0xFFD9DBDC.toInt(),
            candidateBg = 0xFF21272B.toInt(),
            candidateText = 0xFFD9DBDC.toInt(),
            candidateFirstBg = 0xFF3B464C.toInt(),
            preeditText = 0xFF80CBC4.toInt(),
            pageText = 0xFF78909C.toInt(),
            handwritingBg = 0xFF404A50.toInt(),
            handwritingInk = 0xFFECEFF1.toInt(),
        )

        val ALL = listOf(LIGHT_THEME, DARK_THEME)

        fun load(context: Context): ImeTheme {
            val prefs: SharedPreferences = context.getSharedPreferences("liteime", Context.MODE_PRIVATE)
            return when (prefs.getString("theme", MATERIAL_LIGHT)) {
                MATERIAL_DARK -> DARK_THEME
                else -> LIGHT_THEME
            }
        }
    }
}
