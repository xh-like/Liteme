package com.liteime.ime.core

/** 输入引擎：音节切分 + 候选生成 + 分页。与 Python core/engine.py 等价。 */
class PinyinEngine(
    val dict: Dictionary,
    var scheme: String = "quanpin",
    var fuzzy: Boolean = false,
    val pageSize: Int = 9,
) {

    data class QueryResult(
        val raw: String,
        val segments: List<String>,
        val candidates: List<String>,
        val total: Int,
        val page: Int,
    )

    /** (音节, 是否前缀/不完整) */
    private data class Seg(val syl: String, val isPrefix: Boolean)

    private val sylPrefixes: Set<String> = run {
        val set = HashSet<String>()
        for (s in Pinyin.SYLLABLES) for (i in 1..s.length) set.add(s.substring(0, i))
        for (a in Pinyin.ALIASES.values) for (i in 1..a.length) set.add(a.substring(0, i))
        set
    }

    // -------------------------------------------------------- 切分
    private fun segmentQuanpin(group: String): List<List<Seg>> {
        if (group.isEmpty()) return emptyList()
        val n = group.length
        val memo = HashMap<Int, List<List<Seg>>>()

        fun walk(i: Int): List<List<Seg>> {
            memo[i]?.let { return it }
            if (i == n) return listOf(emptyList())
            val paths = ArrayList<List<Seg>>()
            // 1) 完整音节（含别名）
            for (l in 6 downTo 1) {
                if (i + l > n) continue
                val piece = group.substring(i, i + l)
                val cands = ArrayList<String>()
                if (piece in Pinyin.SYLLABLES) cands.add(piece)
                val alias = Pinyin.ALIASES[piece]
                if (alias != null && alias !in cands) cands.add(alias)
                for (syl in cands) {
                    for (tail in walk(i + l)) {
                        paths.add(listOf(Seg(syl, false)) + tail)
                    }
                }
            }
            // 2) 尾部剩余作为不完整音节前缀
            val rest = group.substring(i)
            if (rest in sylPrefixes) paths.add(listOf(Seg(rest, true)))
            // 3) 简拼：单字母继续切
            if (i + 1 <= n) {
                for (tail in walk(i + 1)) {
                    val seg = listOf(Seg(group[i].toString(), true)) + tail
                    if (seg !in paths) paths.add(seg)
                }
            }
            val sorted = paths.sortedWith(
                compareBy({ it.size }, { it.count { s -> s.isPrefix } }, { -it[0].syl.length })
            ).take(16)
            memo[i] = sorted
            return sorted
        }
        return walk(0)
    }

    private fun allPaths(raw: String): List<List<Seg>> {
        val norm = Pinyin.normalizeInput(raw)
        if (norm.isEmpty()) return emptyList()
        if (scheme != "quanpin") {
            return Shuangpin.decode(norm, scheme).map { path -> path.map { Seg(it.syl, it.isPrefix) } }
        }
        val groups = norm.split("'").filter { it.isNotEmpty() }
        if (groups.isEmpty()) return emptyList()
        var paths: List<List<Seg>> = listOf(emptyList())
        for (g in groups) {
            val gp = segmentQuanpin(g)
            val next = ArrayList<List<Seg>>()
            for (base in paths) {
                for (seg in gp) {
                    next.add(base + seg)
                    if (next.size >= 16) break
                }
                if (next.size >= 16) break
            }
            paths = next
        }
        return paths
    }

    // -------------------------------------------------------- 查询
    fun query(rawIn: String, page: Int = 0): QueryResult {
        val raw = Pinyin.normalizeInput(rawIn)
        if (raw.isEmpty()) return QueryResult(raw, emptyList(), emptyList(), 0, page)
        val paths = allPaths(raw)
        val tiers = Array(5) { ArrayList<String>() }
        val seen = HashSet<String>()

        fun push(tier: Int, text: String) {
            if (text.isNotEmpty() && seen.add(text)) tiers[tier].add(text)
        }

        var bestSegments: List<String> = emptyList()
        val completions = ArrayList<Dictionary.WordEntry>()

        for ((pi, path) in paths.withIndex()) {
            if (path.isEmpty()) continue
            if (bestSegments.isEmpty()) bestSegments = path.map { it.syl }
            val syls = path.map { it.syl }
            val lastPrefix = path.last().isPrefix
            val allPrefix = path.all { it.isPrefix }

            val exact = dict.findWords(
                syls, lastPrefix = lastPrefix, allPrefix = false,
                fuzzy = fuzzy, allowLonger = true, limit = 120,
            )
            for ((e, rank) in exact) {
                if (rank == 0) push(if (pi == 0) 0 else 1, e.word)
                else if (rank == 1) completions.add(e)
            }

            if (path.size == 1) {
                val chars = dict.findChars(
                    path[0].syl, lastPrefix = path[0].isPrefix, fuzzy = fuzzy, limit = 60,
                )
                for ((i, c) in chars.withIndex()) {
                    push(if (i < 5) (if (pi == 0) 0 else 1) else 2, c.first)
                }
            } else if (!allPrefix && path.size == 2) {
                for (w in charChain(syls, lastPrefix)) push(1, w)
            }

            if (allPrefix && path.size >= 2) {
                val abbr = dict.findWords(
                    syls, lastPrefix = false, allPrefix = true,
                    fuzzy = fuzzy, allowLonger = false, limit = 120,
                )
                for ((e, _) in abbr) push(3, e.word)
            }
        }
        for (e in completions) push(2, e.word)

        val collected = ArrayList<String>()
        for (t in tiers) collected.addAll(t)
        val start = page * pageSize
        val view = if (start < collected.size) collected.subList(start, minOf(start + pageSize, collected.size)) else emptyList()
        return QueryResult(raw, bestSegments, view.toList(), collected.size, page)
    }

    private fun charChain(syls: List<String>, lastPrefix: Boolean): List<String> {
        val pools = ArrayList<List<String>>()
        for ((i, syl) in syls.withIndex()) {
            val isLast = i == syls.size - 1
            pools.add(dict.findChars(syl, lastPrefix = isLast && lastPrefix, fuzzy = fuzzy, limit = 8).map { it.first })
        }
        if (pools.isEmpty() || pools.any { it.isEmpty() }) return emptyList()
        val out = ArrayList<String>()
        for (a in pools[0].take(8)) {
            if (pools.size == 1) out.add(a)
            else for (b in pools[1].take(8)) out.add(a + b)
        }
        return out
    }

    fun segmentDisplay(rawIn: String): String {
        val paths = allPaths(rawIn)
        if (paths.isEmpty()) return Pinyin.normalizeInput(rawIn)
        return paths[0].joinToString(" ") { it.syl }
    }
}
