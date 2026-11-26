package com.liteime.ime.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View

/**
 * 手写画布：收集笔迹点序列。
 * onStrokesChanged(strokes) 在每笔抬起时回调。
 */
class HandwritingView(
    context: Context,
    theme: ImeTheme = ImeTheme.LIGHT_THEME,
    private val onStrokesChanged: (List<List<Pair<Double, Double>>>) -> Unit,
) : View(context) {

    val strokes = ArrayList<ArrayList<Pair<Double, Double>>>()
    private var current = ArrayList<Pair<Double, Double>>()

    private val paintStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = theme.handwritingInk
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val paintBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.handwritingBg }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 16f, 16f, paintBg)
        for (stroke in strokes + listOf(current)) {
            if (stroke.size < 2) continue
            val path = Path()
            path.moveTo(stroke[0].first.toFloat(), stroke[0].second.toFloat())
            for (p in stroke.drop(1)) path.lineTo(p.first.toFloat(), p.second.toFloat())
            canvas.drawPath(path, paintStroke)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                current = ArrayList()
                current.add(event.x.toDouble() to event.y.toDouble())
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                current.add(event.x.toDouble() to event.y.toDouble())
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                current.add(event.x.toDouble() to event.y.toDouble())
                if (current.size >= 2) {
                    strokes.add(current)
                }
                current = ArrayList()
                invalidate()
                onStrokesChanged(strokes.map { it.toList() })
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun undo() {
        if (strokes.isNotEmpty()) strokes.removeAt(strokes.size - 1)
        invalidate()
        onStrokesChanged(strokes.map { it.toList() })
    }

    fun clear() {
        strokes.clear()
        current = ArrayList()
        invalidate()
        onStrokesChanged(emptyList())
    }
}
