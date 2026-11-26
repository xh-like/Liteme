package com.liteime.ime.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 自绘 QWERTY 键盘，UI 视觉语言参考语燕输入法（gurecn/YuyanIme, BSD-3-Clause）：
 * 圆角近白按键、深蓝灰大写字母、青绿色回车、数字行、图标化功能键。
 * onKeyEvent(code, label) 回调；code 为可见字符或 KEY_* 特殊键码。
 */
class KeyboardView(
    context: Context,
    private val theme: ImeTheme,
    private val onKeyEvent: (code: Int, label: String) -> Unit,
) : View(context) {

    companion object {
        const val KEY_DELETE = -2
        const val KEY_ENTER = -3
        const val KEY_SPACE = -4
        const val KEY_SYMBOL = -5
        const val KEY_HANDWRITE = -6
        const val KEY_LANG = -7
        const val KEY_COMMA = -8
        const val KEY_PERIOD = -9
        const val KEY_NUMBER = -10
    }

    private enum class Style { NORMAL, FUNCTION, ACCENT, NUMBER }
    private data class Key(val code: Int, val label: String, val style: Style)

    /** 顶部数字行（语燕的「键盘数字行」） */
    private val numberRow = listOf(
        Key('1'.code, "1", Style.NUMBER), Key('2'.code, "2", Style.NUMBER),
        Key('3'.code, "3", Style.NUMBER), Key('4'.code, "4", Style.NUMBER),
        Key('5'.code, "5", Style.NUMBER), Key('6'.code, "6", Style.NUMBER),
        Key('7'.code, "7", Style.NUMBER), Key('8'.code, "8", Style.NUMBER),
        Key('9'.code, "9", Style.NUMBER), Key('0'.code, "0", Style.NUMBER),
    )

    private val rows = listOf(
        listOf(
            Key('q'.code, "Q", Style.NORMAL), Key('w'.code, "W", Style.NORMAL),
            Key('e'.code, "E", Style.NORMAL), Key('r'.code, "R", Style.NORMAL),
            Key('t'.code, "T", Style.NORMAL), Key('y'.code, "Y", Style.NORMAL),
            Key('u'.code, "U", Style.NORMAL), Key('i'.code, "I", Style.NORMAL),
            Key('o'.code, "O", Style.NORMAL), Key('p'.code, "P", Style.NORMAL),
        ),
        listOf(
            Key('a'.code, "A", Style.NORMAL), Key('s'.code, "S", Style.NORMAL),
            Key('d'.code, "D", Style.NORMAL), Key('f'.code, "F", Style.NORMAL),
            Key('g'.code, "G", Style.NORMAL), Key('h'.code, "H", Style.NORMAL),
            Key('j'.code, "J", Style.NORMAL), Key('k'.code, "K", Style.NORMAL),
            Key('l'.code, "L", Style.NORMAL),
        ),
        listOf(
            Key(KEY_LANG, "中/英", Style.FUNCTION),
            Key('z'.code, "Z", Style.NORMAL), Key('x'.code, "X", Style.NORMAL),
            Key('c'.code, "C", Style.NORMAL), Key('v'.code, "V", Style.NORMAL),
            Key('b'.code, "B", Style.NORMAL), Key('n'.code, "N", Style.NORMAL),
            Key('m'.code, "M", Style.NORMAL),
            Key(KEY_DELETE, "", Style.FUNCTION),
        ),
        listOf(
            Key(KEY_SYMBOL, "符", Style.FUNCTION),
            Key(KEY_NUMBER, "123", Style.FUNCTION),
            Key(KEY_COMMA, "，", Style.FUNCTION),
            Key(KEY_SPACE, "", Style.FUNCTION),
            Key(KEY_PERIOD, "。", Style.FUNCTION),
            Key(KEY_LANG, "中/英", Style.FUNCTION),
            Key(KEY_ENTER, "", Style.ACCENT),
        ),
    )

    private val keyRects = ArrayList<Pair<RectF, Key>>()
    private var pressedKey: Key? = null
    private var pressedRect: RectF? = null
    private var rowHeight = 0f

    private fun textPaint(color: Int, size: Float, bold: Boolean = false) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
            textLocale = Locale.CHINESE
            textSize = size
        }

    private val defaultSize = dp(22).toFloat()
    private val paintBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.keyboardBg }
    private val paintKey = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.keyBg }
    private val paintFunction = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.functionKeyBg }
    private val paintAccent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.accentKeyBg }
    private val paintPressed = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.pressedOverlay }
    private val paintText = textPaint(theme.keyText, defaultSize)
    private val paintFunctionText = textPaint(theme.functionKeyText, dp(14).toFloat())
    private val paintAccentText = textPaint(theme.accentKeyText, defaultSize)
    private val paintIcon = strokePaint(theme.keyText)
    private val paintAccentIcon = strokePaint(theme.accentKeyText)
    private val paintPopup = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.popupBg }
    private val paintPopupText = textPaint(theme.popupText, dp(26).toFloat(), bold = true)

    private fun strokePaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = dp(2).toFloat()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val radius = dp(10).toFloat()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        keyRects.clear()
        // 布局：数字行(约10%) + 字母4行
        val gap = (w * 0.014f).roundToInt().toFloat()
        val numberH = h * 0.10f
        val letterH = (h - numberH - gap * 6) / 4
        var top = gap

        // 数字行
        val nKeyW = (w - gap * (numberRow.size + 1)) / numberRow.size
        var left = gap
        for (key in numberRow) {
            keyRects.add(RectF(left, top, left + nKeyW, top + numberH) to key)
            left += nKeyW + gap
        }
        top += numberH + gap

        for (row in rows) {
            val weights = row.map { key ->
                when {
                    key.code == KEY_SPACE -> 3.0f
                    key.code == KEY_LANG || key.code == KEY_DELETE -> 1.3f
                    key.style == Style.FUNCTION -> 1.05f
                    else -> 1f
                }
            }
            val totalW = w - gap * (row.size + 1)
            val unit = totalW / weights.sum()
            left = gap
            for ((ki, key) in row.withIndex()) {
                val kw = unit * weights[ki]
                keyRects.add(RectF(left, top, left + kw, top + letterH) to key)
                left += kw + gap
            }
            top += letterH + gap
            rowHeight = letterH
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paintBg)
        for ((rect, key) in keyRects) {
            val bg = when (key.style) {
                Style.NORMAL, Style.NUMBER -> paintKey
                Style.FUNCTION -> paintFunction
                Style.ACCENT -> paintAccent
            }
            canvas.drawRoundRect(rect, radius, radius, bg)
            // 语燕：按下用半透明叠加高亮，而非替换底色
            if (pressedKey === key) canvas.drawRoundRect(rect, radius, radius, paintPressed)
            drawKeyContent(canvas, rect, key)
        }
        val pk = pressedKey
        val pr = pressedRect
        if (pk != null && pr != null && pk.label.isNotEmpty()) drawPopup(canvas, pr, pk.label)
    }

    private fun drawKeyContent(canvas: Canvas, rect: RectF, key: Key) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        when (key.code) {
            KEY_DELETE -> drawDeleteIcon(canvas, cx, cy, rect)
            KEY_ENTER -> drawEnterIcon(canvas, cx, cy, rect)
            KEY_SPACE -> drawSpaceIcon(canvas, cx, cy, rect)
            else -> {
                val paint = when (key.style) {
                    Style.ACCENT -> paintAccentText
                    Style.FUNCTION -> paintFunctionText
                    else -> paintText
                }
                val base = paint.textSize
                val size = if (key.style == Style.NUMBER) minOf(base, rect.height() * 0.5f) else base
                paint.textSize = size
                val maxW = rect.width() - dp(8)
                val tw = paint.measureText(key.label)
                if (tw > maxW && tw > 0f) paint.textSize = size * maxW / tw
                val baseline = rect.centerY() - (paint.descent() + paint.ascent()) / 2
                canvas.drawText(key.label, cx, baseline, paint)
                paint.textSize = base
            }
        }
    }

    private fun drawDeleteIcon(canvas: Canvas, cx: Float, cy: Float, rect: RectF) {
        val icon = paintIcon
        val s = minOf(rect.width(), rect.height()) * 0.22f
        val path = Path()
        path.moveTo(cx + s, cy - s * 0.7f)
        path.lineTo(cx - s, cy - s * 0.7f)
        path.lineTo(cx - s, cy + s * 0.7f)
        path.lineTo(cx + s, cy + s * 0.7f)
        path.close()
        canvas.drawPath(path, icon)
        canvas.drawLine(cx - s * 0.35f, cy - s * 0.35f, cx + s * 0.35f, cy + s * 0.35f, icon)
        canvas.drawLine(cx + s * 0.35f, cy - s * 0.35f, cx - s * 0.35f, cy + s * 0.35f, icon)
    }

    private fun drawEnterIcon(canvas: Canvas, cx: Float, cy: Float, rect: RectF) {
        val icon = paintAccentIcon
        val s = minOf(rect.width(), rect.height()) * 0.24f
        canvas.drawLine(cx + s * 0.8f, cy + s * 0.5f, cx - s * 0.8f, cy + s * 0.5f, icon)
        canvas.drawLine(cx - s * 0.8f, cy + s * 0.5f, cx - s * 0.8f, cy - s * 0.6f, icon)
        canvas.drawLine(cx - s * 0.8f, cy - s * 0.6f, cx - s * 0.3f, cy - s * 0.1f, icon)
        canvas.drawLine(cx - s * 0.8f, cy - s * 0.6f, cx - s * 1.3f, cy - s * 0.1f, icon)
    }

    private fun drawSpaceIcon(canvas: Canvas, cx: Float, cy: Float, rect: RectF) {
        val s = rect.width() * 0.10f
        canvas.drawLine(cx - s, cy - s * 0.4f, cx - s, cy + s * 0.4f, paintIcon)
        canvas.drawLine(cx - s, cy + s * 0.4f, cx + s, cy + s * 0.4f, paintIcon)
        canvas.drawLine(cx + s, cy + s * 0.4f, cx + s, cy - s * 0.4f, paintIcon)
    }

    private fun drawPopup(canvas: Canvas, rect: RectF, label: String) {
        val w = maxOf(rect.width() * 1.15f, paintPopupText.measureText(label) + dp(24))
        val h = rowHeight * 1.05f
        val left = (rect.centerX() - w / 2).coerceIn(dp(4).toFloat(), width - w - dp(4))
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
        for (pair in keyRects) if (pair.first.contains(x, y)) return pair
        return null
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()
}
