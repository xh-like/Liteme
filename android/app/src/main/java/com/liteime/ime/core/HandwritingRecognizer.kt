package com.liteime.ime.core

import kotlin.math.acos
import kotlin.math.hypot

/** 手写识别：笔画方向分类 + 加权编辑距离。与 Python core/handwriting.py 等价。 */
object Handwriting {

    const val H = 'h'; const val S = 's'; const val P = 'p'; const val N = 'n'; const val Z = 'z'

    private val SUB_COST = mapOf(
        "hs" to 0.75, "hp" to 1.0, "hn" to 1.0, "hz" to 0.85,
        "sp" to 1.0, "sn" to 1.0, "sz" to 0.85,
        "pn" to 0.55, "pz" to 0.8,
        "nz" to 0.85,
    )
    private const val INS_COST = 0.62
    private const val DEL_COST = 0.62

    private fun sub(a: Char, b: Char): Double {
        if (a == b) return 0.0
        val k1 = "$a$b"; val k2 = "$b$a"
        return SUB_COST[k1] ?: SUB_COST[k2] ?: 1.0
    }

    /**
     * 把一条笔迹归类为 h/s/p/n/z。
     * @param yUp true = y 轴向上（makemeahanzi 数据）；false = 屏幕画布（y 向下）
     */
    fun classifyStroke(
        points: List<Pair<Double, Double>>,
        yUp: Boolean = false,
        dotThresh: Double = 14.0,
    ): Char {
        if (points.size < 2) return N
        val pts = ArrayList<Pair<Double, Double>>()
        pts.add(points[0])
        for (p in points.drop(1)) {
            if (Math.abs(p.first - pts.last().first) > 0.5 || Math.abs(p.second - pts.last().second) > 0.5) {
                pts.add(p)
            }
        }
        if (pts.size < 2) return N

        val (x0, y0) = pts.first()
        val (x1, y1) = pts.last()
        val dx = x1 - x0
        var dy = y1 - y0
        if (yUp) dy = -dy
        val chord = hypot(dx, dy)

        // 净转角（折笔判定）
        val mid = pts.size / 2
        val v1x = pts[mid].first - pts[0].first
        val v1y = pts[mid].second - pts[0].second
        val v2x = pts.last().first - pts[mid].first
        val v2y = pts.last().second - pts[mid].second
        val l1 = hypot(v1x, v1y)
        val l2 = hypot(v2x, v2y)
        val netTurn = if (l1 < 1e-6 || l2 < 1e-6) 0.0
        else {
            val cosv = ((v1x * v2x + v1y * v2y) / (l1 * l2)).coerceIn(-1.0, 1.0)
            acos(cosv)
        }

        var arc = 0.0
        for (i in 1 until pts.size) {
            arc += hypot(pts[i].first - pts[i - 1].first, pts[i].second - pts[i - 1].second)
        }
        val curl = if (chord > 1e-6) arc / chord else 3.0

        if (netTurn > 1.05 || curl > 1.9) return Z
        if (chord < dotThresh && arc < dotThresh * 1.9) return N
        if (Math.abs(dx) >= 1.8 * Math.abs(dy)) return if (dx >= 0) H else P
        if (Math.abs(dy) >= 1.8 * Math.abs(dx)) return S
        return if (dy > 0) (if (dx > 0) N else P) else (if (dx > 0) H else P)
    }
}

class HandwritingRecognizer(private val dict: Dictionary) {

    companion object {
        private const val INS_COST = 0.62
        private const val DEL_COST = 0.62
    }

    private val byLen = HashMap<Int, ArrayList<Pair<String, String>>>()
    init {
        for ((ch, seq) in dict.strokes) {
            byLen.getOrPut(seq.length) { ArrayList() }.add(ch to seq)
        }
    }

    fun recognize(strokes: List<List<Pair<Double, Double>>>, topN: Int = 10): List<String> {
        if (strokes.isEmpty()) return emptyList()
        val seq = strokes.joinToString("") { Handwriting.classifyStroke(it).toString() }
        val n = seq.length

        val scored = ArrayList<Pair<Double, String>>()
        for (length in maxOf(1, n - 2)..(n + 2)) {
            for ((ch, target) in byLen[length] ?: emptyList()) {
                var d = distance(seq, target)
                d += 0.08 * Math.abs(target.length - n)
                scored.add(d to ch)
            }
        }
        scored.sortWith(compareBy({ it.first }, { -(dict.charFreq[it.second] ?: 0) }))
        val out = ArrayList<String>()
        for ((_, ch) in scored) {
            if (ch !in out) out.add(ch)
            if (out.size >= topN) break
        }
        return out
    }

    private fun distance(a: String, b: String): Double {
        val m = a.length
        val n = b.length
        var prev = DoubleArray(n + 1) { it * INS_COST }
        for (i in 1..m) {
            val cur = DoubleArray(n + 1)
            cur[0] = i * DEL_COST
            for (j in 1..n) {
                cur[j] = minOf(
                    prev[j - 1] + sub(a[i - 1], b[j - 1]),
                    prev[j] + DEL_COST,
                    cur[j - 1] + INS_COST,
                )
            }
            prev = cur
        }
        return prev[n]
    }

    private fun sub(a: Char, b: Char): Double {
        if (a == b) return 0.0
        val table = mapOf(
            "hs" to 0.75, "hp" to 1.0, "hn" to 1.0, "hz" to 0.85,
            "sp" to 1.0, "sn" to 1.0, "sz" to 0.85,
            "pn" to 0.55, "pz" to 0.8, "nz" to 0.85,
        )
        return table["$a$b"] ?: table["$b$a"] ?: 1.0
    }
}
