package com.liteime.ime.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 键盘顶部工具栏，参考语燕输入法：
 * 左侧品牌圆形图标，右侧一排圆形功能按钮（手写/表情/剪贴板/收起）。
 */
class ToolbarView(
    context: Context,
    private val theme: ImeTheme,
    private val onAction: (String) -> Unit,
) : View(context) {

    companion object {
        const val ACTION_HANDWRITE = "handwrite"
        const val ACTION_EMOJI = "emoji"
        const val ACTION_CLIPBOARD = "clipboard"
        const val ACTION_HIDE = "hide"
        const val ACTION_BRAND = "brand"
    }

    private data class Button(val action: String, val glyph: String)

    private val buttons = listOf(
        Button(ACTION_HANDWRITE, "✎"),
        Button(ACTION_EMOJI, "☺"),
        Button(ACTION_CLIPBOARD, "▤"),
        Button(ACTION_HIDE, "⌄"),
    )

    private val hitAreas = ArrayList<Pair<RectF, Button>>()
    private var pressed: Button? = null
    private var brandRect = RectF()

    private val paintIconBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.toolbarIconBg }
    private val paintIcon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = theme.toolbarIcon
        textAlign = Paint.Align.CENTER
        textSize = dp(17).toFloat()
    }
    private val paintBrand = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.functionKeyBg }
    private val paintBrandMark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = theme.toolbarIconBg
        style = Paint.Style.FILL
    }
    private val paintPressed = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.pressedOverlay }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        hitAreas.clear()
        val pad = dp(12).toFloat()
        val size = min(h.toFloat() - dp(10), dp(44).toFloat())
        val brand = RectF(pad, (h - size) / 2, pad + size, (h + size) / 2)
        brandRect = brand
        val gap = dp(12).toFloat()
        var right = w - pad - size
        for (b in buttons.reversed()) {
            hitAreas.add(RectF(right, (h - size) / 2, right + size, (h + size) / 2) to b)
            right -= size + gap
        }
    }

    override fun onDraw(canvas: Canvas) {
        // 左侧品牌圆 + 燕子形标记
        canvas.drawRoundRect(brandRect, brandRect.height() / 2, brandRect.height() / 2, paintBrand)
        val cx = brandRect.centerX()
        val cy = brandRect.centerY()
        val s = brandRect.width() * 0.28f
        val path = Path()
        path.moveTo(cx - s, cy + s * 0.35f)
        path.quadTo(cx - s * 0.2f, cy - s * 0.8f, cx + s, cy - s * 0.45f)
        path.quadTo(cx + s * 0.1f, cy - s * 0.1f, cx - s * 0.6f, cy + s * 0.15f)
        path.close()
        canvas.drawPath(path, paintBrandMark)

        for ((rect, button) in hitAreas) {
            canvas.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, paintIconBg)
            if (pressed === button) {
                canvas.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, paintPressed)
            }
            val baseline = rect.centerY() - (paintIcon.descent() + paintIcon.ascent()) / 2
            canvas.drawText(button.glyph, rect.centerX(), baseline, paintIcon)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val b = findButton(event.x, event.y)
                if (b != pressed) {
                    pressed = b
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (brandRect.contains(event.x, event.y)) {
                    onAction(ACTION_BRAND)
                } else {
                    pressed?.let { onAction(it.action) }
                }
                pressed = null
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressed = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun findButton(x: Float, y: Float): Button? {
        for ((rect, button) in hitAreas) if (rect.contains(x, y)) return button
        return null
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()
}
