package com.liteime.ime.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 候选条：预编辑区 + 横向候选列表。
 * onPick(index) 点击候选；onPage(delta) 翻页。
 */
class CandidateView(
    context: Context,
    private val theme: ImeTheme,
    private val onPick: (Int) -> Unit,
) : HorizontalScrollView(context) {

    private val container = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(theme.candidateBg)
    }
    private val preedit = TextView(context).apply {
        setTextColor(theme.preeditText)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(10), 0, dp(6), 0)
        gravity = Gravity.CENTER_VERTICAL
    }
    private val pageLabel = TextView(context).apply {
        setTextColor(theme.pageText)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        setPadding(dp(6), 0, dp(10), 0)
        gravity = Gravity.CENTER_VERTICAL
    }
    private var candidates: List<String> = emptyList()

    init {
        isHorizontalScrollBarEnabled = false
        container.addView(preedit)
        addView(container)
    }

    fun show(preeditText: String, cands: List<String>, page: Int, total: Int) {
        candidates = cands
        preedit.text = preeditText
        // 移除旧候选（保留 preedit 与 pageLabel）
        while (container.childCount > 1) container.removeViewAt(1)
        for ((i, c) in cands.withIndex()) {
            val tv = TextView(context).apply {
                text = "${i + 1}.$c"
                setTextColor(theme.candidateText)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setPadding(dp(10), dp(8), dp(10), dp(8))
                setOnClickListener { onPick(i) }
                setBackgroundColor(if (i == 0) theme.candidateFirstBg else theme.candidateBg)
            }
            container.addView(tv)
        }
        val pages = maxOf(1, (total + 8) / 9)
        pageLabel.text = if (total > 0) "${page + 1}/$pages" else ""
        container.addView(pageLabel)
    }

    fun hide() {
        preedit.text = ""
        while (container.childCount > 1) container.removeViewAt(1)
        pageLabel.text = ""
    }

    @SuppressLint("SetTextI18n")
    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
}
