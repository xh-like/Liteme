package com.liteime.ime.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 自绘 QWERTY 键盘。
 * onKeyEvent(code, label) 回调；code 为可见字符或 KEY_* 特殊键码。
 */
class KeyboardView(context: Context, private val onKeyEvent: (code: Int, label: String) -> Unit) :
    View(context) {

    companion object {
        const val KEY_SHIFT = -1
        const val KEY_DELETE = -2
        const val KEY_ENTER = -3
        const val KEY_SPACE = -4
        const val KEY_SYMBOL = -5
        const val KEY_HANDWRITE = -6
        const val KEY_LANG = -7
        const val KEY_COMMA = -8
        const val KEY_PERIOD = -9
    }

    private data class Key(val code: Int, val label: String, val special: Boolean = false)

    private val rows = listOf(
        listOf(
            Key('q'.code, "q"), Key('w'.code, "w"), Key('e'.code, "e"), Key('r'.code, "r"),
            Key('t'.code, "t"), Key('y'.code, "y"), Key('u'.code, "u"), Key('i'.code, "i"),
            Key('o'.code, "o"), Key('p'.code, "p"),
        ),
        listOf(
            Key('a'.code, "a"), Key('s'.code, "s"), Key('d'.code, "d"), Key('f'.code, "f"),
            Key('g'.code, "g"), Key('h'.code, "h"), Key('j'.code, "j"), Key('k'.code, "k"),
            Key('l'.code, "l"),
        ),
        listOf(
            Key(KEY_LANG, "中/英", true),
            Key('z'.code, "z"), Key('x'.code, "x"), Key('c'.code, "c"), Key('v'.code, "v"),
            Key('b'.code, "b"), Key('n'.code, "n"), Key('m'.code, "m"),
            Key(KEY_DELETE, "删除", true),
        ),
        listOf(
            Key(KEY_SYMBOL, "符", true),
            Key(KEY_HANDWRITE, "手写", true),
            Key(KEY_COMMA, "，", true),
            Key(KEY_SPACE, "空格", true),
            Key(KEY_PERIOD, "。", true),
            Key(KEY_ENTER, "回车", true),
        ),
    )

    private val keyRects = ArrayList<Pair<RectF, Key>>()
    private var pressedKey: Key? = null

    private val paintBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF2F3F7.toInt() }
    private val paintKey = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val paintKeySpecial = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE4E7EE.toInt() }
    private val paintPressed = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFDCE6FD.toInt() }
    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1F2329.toInt()
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        // 关键：显式指定中文字体与 locale，避免自绘 Canvas 在部分设备上
        // 不 fallback 到中文字形而显示成方块/乱码
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textLocale = Locale.CHINESE
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        keyRects.clear()
        val gap = (w * 0.012f).roundToInt().toFloat()
        val rowH = (h - gap * (rows.size + 1)) / rows.size
        // 基准字号按「行高」与「普通键宽」的较小者计算，避免键盘被拉伸时字体溢出键位
        val firstRowUnit = (w - gap * (rows[0].size + 1)) / rows[0].size.toFloat()
        paintText.textSize = minOf(rowH, firstRowUnit) * 0.38f
        for ((ri, row) in rows.withIndex()) {
            val top = gap + ri * (rowH + gap)
            val specWeight = if (ri >= 2) 1.25f else 1f
            // 第三行首尾键更宽
            val weights = row.map { key ->
                when {
                    key.code == KEY_LANG || key.code == KEY_DELETE -> 1.35f
                    key.code == KEY_SPACE -> 3.2f
                    key.special -> specWeight
                    else -> 1f
                }
            }
            val totalW = w - gap * (row.size + 1)
            val unit = totalW / weights.sum()
            var left = gap
            for ((ki, key) in row.withIndex()) {
                val kw = unit * weights[ki]
                keyRects.add(RectF(left, top, left + kw, top + rowH) to key)
                left += kw + gap
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 0f, 0f, paintBg)
        for ((rect, key) in keyRects) {
            val paint = when {
                pressedKey === key -> paintPressed
                key.special -> paintKeySpecial
                else -> paintKey
            }
            canvas.drawRoundRect(rect, 12f, 12f, paint)
            // 按标签实际宽度自适应缩放，长标签（如"中/英"）自动缩小
            val base = paintText.textSize
            val maxW = rect.width() - 10f
            val tw = paintText.measureText(key.label)
            paintText.textSize = if (tw > maxW && tw > 0f) base * maxW / tw else base
            val baseline = rect.centerY() - (paintText.descent() + paintText.ascent()) / 2
            canvas.drawText(key.label, rect.centerX(), baseline, paintText)
            paintText.textSize = base
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val key = findKey(event.x, event.y)
                if (key != pressedKey) {
                    pressedKey = key
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                pressedKey?.let { onKeyEvent(it.code, it.label) }
                pressedKey = null
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedKey = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun findKey(x: Float, y: Float): Key? {
        for ((rect, key) in keyRects) {
            if (rect.contains(x, y)) return key
        }
        return null
    }
}
