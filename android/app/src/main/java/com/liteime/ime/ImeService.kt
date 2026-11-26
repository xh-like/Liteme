package com.liteime.ime

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import com.liteime.ime.core.Dictionary
import com.liteime.ime.core.HandwritingRecognizer
import com.liteime.ime.core.PinyinEngine
import com.liteime.ime.core.Predictor
import com.liteime.ime.ui.CandidateView
import com.liteime.ime.ui.HandwritingView
import com.liteime.ime.ui.KeyboardView
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Executors

class ImeService : InputMethodService() {

    private var dictionary: Dictionary? = null
    private var engine: PinyinEngine? = null
    private var predictor: Predictor? = null
    private var recognizer: HandwritingRecognizer? = null

    private lateinit var rootLayout: LinearLayout
    private lateinit var candidateView: CandidateView
    private lateinit var keyboardView: KeyboardView
    private lateinit var container: FrameLayout

    private var handwritingView: HandwritingView? = null
    private var handwritingMode = false

    private var modeCn = true
    private var raw = ""
    private var page = 0
    private var candidates: List<String> = emptyList()

    private val prefs by lazy { getSharedPreferences("liteime", MODE_PRIVATE) }

    // -------------------------------------------------------- 生命周期
    override fun onCreate() {
        super.onCreate()
        Executors.newSingleThreadExecutor().execute {
            val dict = Dictionary.load(
                assetLines("data/chars_pinyin.txt").asSequence(),
                assetLines("data/words.txt").asSequence(),
                assetLines("data/strokes.txt").asSequence(),
            )
            engine = PinyinEngine(
                dict,
                scheme = prefs.getString("scheme", "quanpin") ?: "quanpin",
                fuzzy = prefs.getBoolean("fuzzy", false),
            )
            predictor = Predictor(dict)
            recognizer = HandwritingRecognizer(dict)
            dictionary = dict
        }
    }

    private fun assetLines(path: String): List<String> {
        assets.open(path).use { input ->
            return BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readLines()
        }
    }

    override fun onCreateInputView(): View {
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF2F3F7.toInt())
        }
        candidateView = CandidateView(this) { idx -> pickCandidate(idx) }
        container = FrameLayout(this)
        keyboardView = KeyboardView(this) { code, label -> onKey(code, label) }
        container.addView(
            keyboardView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        rootLayout.addView(
            candidateView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)),
        )
        rootLayout.addView(
            container,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(280)),
        )
        return rootLayout
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        resetComposing()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        resetComposing()
        super.onFinishInputView(finishingInput)
    }

    // -------------------------------------------------------- 按键
    private fun onKey(code: Int, label: String) {
        val eng = engine ?: run {
            Toast.makeText(this, "词典加载中…", Toast.LENGTH_SHORT).show()
            return
        }
        when (code) {
            KeyboardView.KEY_LANG -> {
                modeCn = !modeCn
                resetComposing()
            }
            KeyboardView.KEY_DELETE -> {
                if (raw.isNotEmpty()) {
                    raw = raw.dropLast(1)
                    page = 0
                    refreshCandidates()
                } else {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                }
            }
            KeyboardView.KEY_ENTER -> {
                if (raw.isNotEmpty()) {
                    commit(raw)
                } else {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                }
            }
            KeyboardView.KEY_SPACE -> {
                if (raw.isNotEmpty()) pickCandidate(0)
                else commitOrType(" ")
            }
            KeyboardView.KEY_COMMA -> commitOrType("，")
            KeyboardView.KEY_PERIOD -> commitOrType("。")
            KeyboardView.KEY_SYMBOL -> {
                // 简单符号层：直接上屏常用标点
                commitOrType("！？：；“”（）【】…—")
            }
            KeyboardView.KEY_HANDWRITE -> toggleHandwriting()
            else -> {
                if (!modeCn) {
                    currentInputConnection.commitText(label, 1)
                    return
                }
                raw += label
                page = 0
                refreshCandidates()
            }
        }
    }

    private fun commitOrType(text: String) {
        if (raw.isNotEmpty()) commit(raw)
        currentInputConnection.commitText(text, 1)
    }

    // -------------------------------------------------------- 候选
    private fun refreshCandidates() {
        val eng = engine ?: return
        if (raw.isEmpty()) {
            candidates = emptyList()
            candidateView.hide()
            currentInputConnection.finishComposingText()
            return
        }
        val r = eng.query(raw, page)
        candidates = r.candidates
        val seg = eng.segmentDisplay(raw)
        candidateView.show(seg, candidates, r.page, r.total)
        // 预编辑区显示正在输入的拼音
        currentInputConnection.setComposingText(seg, 1)
    }

    private fun pickCandidate(index: Int) {
        if (candidates.isEmpty()) {
            if (index == 0 && raw.isNotEmpty()) commit(raw)
            return
        }
        if (index in candidates.indices) {
            commit(candidates[index])
        } else if (page > 0) {
            page--
            refreshCandidates()
        }
    }

    private fun commit(text: String) {
        currentInputConnection.commitText(text, 1)
        val p = predictor
        raw = ""
        page = 0
        candidates = emptyList()
        candidateView.hide()
        if (p != null) {
            val sugg = p.predict(text)
            if (sugg.isNotEmpty()) {
                candidates = sugg
                candidateView.show("", sugg, 0, sugg.size)
            }
        }
    }

    private fun resetComposing() {
        raw = ""
        page = 0
        candidates = emptyList()
        if (::candidateView.isInitialized) candidateView.hide()
    }

    // -------------------------------------------------------- 手写
    private fun toggleHandwriting() {
        handwritingMode = !handwritingMode
        container.removeAllViews()
        if (handwritingMode) {
            val hw = HandwritingView(this) { strokes ->
                val rec = recognizer
                if (strokes.isEmpty() || rec == null) return@HandwritingView
                val result = rec.recognize(strokes, 10)
                candidates = result
                candidateView.show("手写", result, 0, result.size)
            }
            handwritingView = hw
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(0xFFF2F3F7.toInt())
                addView(
                    hw,
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
                )
                val bar = LinearLayout(this@ImeService).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(TextViewButton("退一笔") { hw.undo() })
                    addView(TextViewButton("清空") { hw.clear() })
                    addView(TextViewButton("关闭手写") { toggleHandwriting() })
                }
                addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)))
            }
            container.addView(
                panel,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        } else {
            handwritingView = null
            container.addView(
                keyboardView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }
    }

    private fun TextViewButton(text: String, onClick: () -> Unit): View =
        android.widget.Button(this).apply {
            this.text = text
            setOnClickListener { onClick() }
        }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()
}
