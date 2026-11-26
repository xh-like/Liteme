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
 * 自绘 QWERTY 键盘，视觉风格参考 FlorisBoard：
 * 圆角按键 + 阴影 + 绿色回车键 + 按下弹出预览气泡。
 * onKeyEvent(code, label) 回调；code 为可见字符或 KEY_* 特殊键码。
 */
class KeyboardView(
    context: Context,
    private val theme: ImeTheme,
    private val onKeyEvent: (code: Int, label: String) -> Unit,
) : View(context) {

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
    private var pressedRect: RectF? = null
    private var rowHeight = 0f

    private fun cjkPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textLocale = Locale.CHINESE
    }

    private val paintBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.background }
    private val paintShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.shadow }
    private val paintKey = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.keyBg }
    private val paintKeySpecial = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.keySpecialBg }
    private val paintPressed = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.keyPressedBg }
    private val paintAccent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.keyAccentBg }
    private val paintAccentPressed = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.keyAccentPressedBg }
    private val paintText = cjkPaint(theme.text)
    private val paintTextSpecial = cjkPaint(theme.textSpecial)
    private val paintTextAccent = cjkPaint(theme.accentText)
    private val paintPopup = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.popupBg }
    private val paintPopupText = cjkPaint(theme.popupText)

    private val radius = dp(8).toFloat()
    private val shadowOffset = dp(2).toFloat()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        keyRects.clear()
        val gap = (w * 0.012f).roundToInt().toFloat()
        rowHeight = (h - gap * (rows.size + 1)) / rows.size
        // 基准字号按「行高」与「普通键宽」的较小者计算，避免键盘被拉伸时字体溢出键位
        val firstRowUnit = (w - gap * (rows[0].size + 1)) / rows[0].size.toFloat()
        val baseSize = minOf(rowHeight, firstRowUnit) * 0.38f
        paintText.textSize = baseSize
        paintTextSpecial.textSize = baseSize
        paintTextAccent.textSize = baseSize
        paintPopupText.textSize = minOf(rowHeight, firstRowUnit) * 0.62f
        for ((ri, row) in rows.withIndex()) {
            val top = gap + ri * (rowHeight + gap)
            // 第三行首尾键更宽
            val weights = row.map { key ->
                when {
                    key.code == KEY_LANG || key.code == KEY_DELETE -> 1.35f
                    key.code == KEY_SPACE -> 3.2f
                    key.special -> 1.1f
                    else -> 1f
                }
            }
            val totalW = w - gap * (row.size + 1)
            val unit = totalW / weights.sum()
            var left = gap
            for ((ki, key) in row.withIndex()) {
                val kw = unit * weights[ki]
                val rect = RectF(left, top, left + kw, top + rowHeight)
                keyRects.add(rect to key)
                left += kw + gap
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paintBg)
        for ((rect, key) in keyRects) {
            val isPressed = pressedKey === key
            // FlorisBoard 风格：按键下方轻微阴影
            canvas.drawRoundRect(
                rect.left, rect.top + shadowOffset, rect.right, rect.bottom + shadowOffset,
                radius, radius, paintShadow,
            )
            val bg = when {
                isPressed && key.code == KEY_ENTER -> paintAccentPressed
                isPressed -> paintPressed
                key.code == KEY_ENTER -> paintAccent
                key.special -> paintKeySpecial
                else -> paintKey
            }
            canvas.drawRoundRect(rect, radius, radius, bg)
            // 按标签实际宽度自适应缩放，长标签（如"中/英"）自动缩小
            val paint = when {
                key.code == KEY_ENTER -> paintTextAccent
                key.special -> paintTextSpecial
                else -> paintText
            }
            val base = paint.textSize
            val maxW = rect.width() - dp(6)
            val tw = paint.measureText(key.label)
            paint.textSize = if (tw > maxW && tw > 0f) base * maxW / tw else base
            val baseline = rect.centerY() - (paint.descent() + paint.ascent()) / 2
            canvas.drawText(key.label, rect.centerX(), baseline, paint)
            paint.textSize = base
        }
        // 按下时在按键上方绘制放大预览气泡（FlorisBoard KeyPopupBox）
        val pk = pressedKey
        val pr = pressedRect
        if (pk != null && pr != null) drawPopup(canvas, pr, pk.label)
    }

    private fun drawPopup(canvas: Canvas, rect: RectF, label: String) {
        val w = maxOf(rect.width() * 1.2f, paintPopupText.measureText(label) + dp(20))
        val h = rowHeight * 1.05f
        val left = (rect.centerX() - w / 2).coerceIn(dp(4).toFloat(), width - w - dp(4))
        // 第一行键上方空间不足时，气泡改画在按键下方
        val top = if (rect.top - h - dp(6) >= 0f) rect.top - h - dp(6) else rect.bottom + dp(6)
        val box = RectF(left, top, left + w, top + h)
        canvas.drawRoundRect(box, radius, radius, paintPopup)
        val base = paintPopupText.textSize
        val tw = paintPopupText.measureText(label)
        if (tw > w - dp(12) && tw > 0f) paintPopupText.textSize = base * (w - dp(12)) / tw
        val baseline = box.centerY() - (paintPopupText.descent() + paintPopupText.ascent()) / 2
        canvas.drawText(label, box.centerX(), baseline, paintPopupText)
        paintPopupText.textSize = base
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val pair = findKey(event.x, event.y)
                val key = pair?.second
                if (key != pressedKey) {
                    pressedKey = key
                    pressedRect = pair?.first
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                pressedKey?.let { onKeyEvent(it.code, it.label) }
                pressedKey = null
                pressedRect = null
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedKey = null
                pressedRect = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun findKey(x: Float, y: Float): Pair<RectF, Key>? {
        for (pair in keyRects) {
            if (pair.first.contains(x, y)) return pair
        }
        return null
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()
}
