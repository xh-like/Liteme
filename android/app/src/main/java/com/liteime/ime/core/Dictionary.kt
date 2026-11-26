package com.liteime.ime.core

/** 词典加载与检索。与 Python core/dictionary.py 等价。 */
class Dictionary private constructor() {

    data class WordEntry(val word: String, val syls: List<String>, val freq: Int)

    val chars = HashMap<String, List<String>>()       // 字 -> [读音]
    val charFreq = HashMap<String, Int>()             // 字 -> 词频
    val words = ArrayList<WordEntry>()
    val strokes = HashMap<String, String>()           // 字 -> 笔画序列

    private val byFirst = HashMap<String, ArrayList<WordEntry>>()
    private var firstSorted: List<String> = emptyList()
    private val charsBySyl = HashMap<String, ArrayList<Pair<String, Int>>>()
    private var charsSylSorted: List<String> = emptyList()

    companion object {
        /** 从三份数据文件的行序列构建（便于从 assets 加载）。 */
        fun load(charLines: Sequence<String>, wordLines: Sequence<String>, strokeLines: Sequence<String>): Dictionary {
            val d = Dictionary()
            d.loadChars(charLines)
            d.loadWords(wordLines)
            d.loadStrokes(strokeLines)
            d.buildIndex()
            return d
        }
    }

    private fun loadChars(lines: Sequence<String>) {
        for (line in lines) {
            if (line.startsWith("#") || line.isBlank()) continue
            val parts = line.split("\t")
            if (parts.size < 2) continue
            val ch = parts[0]
            val readings = parts[1].split(",").filter { it.isNotEmpty() }
            val freq = parts.getOrNull(2)?.toIntOrNull() ?: 0
            chars[ch] = readings
            charFreq[ch] = freq
        }
    }

    private fun loadWords(lines: Sequence<String>) {
        for (line in lines) {
            if (line.startsWith("#") || line.isBlank()) continue
            val parts = line.split("\t")
            if (parts.size < 2) continue
            val word = parts[0]
            val syls = parts[1].split(" ").filter { it.isNotEmpty() }
            if (syls.size != word.length) continue
            val freq = parts.getOrNull(2)?.toIntOrNull() ?: 1
            words.add(WordEntry(word, syls, freq))
        }
    }

    private fun loadStrokes(lines: Sequence<String>) {
        for (line in lines) {
            if (line.startsWith("#") || line.isBlank()) continue
            val parts = line.split("\t")
            if (parts.size >= 2) strokes[parts[0]] = parts[1]
        }
    }

    private fun buildIndex() {
        byFirst.clear()
        for (e in words) {
            byFirst.getOrPut(e.syls[0]) { ArrayList() }.add(e)
        }
        for (lst in byFirst.values) lst.sortByDescending { it.freq }
        firstSorted = byFirst.keys.sorted()
        charsBySyl.clear()
        for ((ch, readings) in chars) {
            val fr = charFreq[ch] ?: 0
            for (r in readings) {
                charsBySyl.getOrPut(r) { ArrayList() }.add(ch to fr)
            }
        }
        for (lst in charsBySyl.values) lst.sortByDescending { it.second }
        charsSylSorted = charsBySyl.keys.sorted()
    }

    // -------------------------------------------------------- 检索
    private fun prefixRange(keys: List<String>, prefix: String): IntRange {
        val lo = keys.binarySearch(prefix).let { if (it >= 0) it else -it - 1 }
        var hi = lo
        while (hi < keys.size && keys[hi].startsWith(prefix)) hi++
        return lo until hi
    }

    private fun firstBucket(first: String, prefix: Boolean, fuzzy: Boolean): List<WordEntry> {
        if (fuzzy) {
            val ff = Pinyin.foldFuzzy(first)
            val out = ArrayList<WordEntry>()
            for ((key, lst) in byFirst) {
                val fk = Pinyin.foldFuzzy(key)
                if (fk == ff || (prefix && fk.startsWith(ff))) out.addAll(lst)
            }
            return out
        }
        if (!prefix) return byFirst[first] ?: emptyList()
        val out = ArrayList<WordEntry>()
        for (i in prefixRange(firstSorted, first)) out.addAll(byFirst[firstSorted[i]]!!)
        return out
    }

    /**
     * 查找与音节序列匹配的词。
     * rank: 0=长度完全匹配 1=输入是词前缀(补全) 2=简拼
     */
    fun findWords(
        syls: List<String>,
        lastPrefix: Boolean = false,
        allPrefix: Boolean = false,
        fuzzy: Boolean = false,
        allowLonger: Boolean = true,
        limit: Int = 200,
    ): List<Pair<WordEntry, Int>> {
        if (syls.isEmpty()) return emptyList()
        val n = syls.size
        val results = ArrayList<Pair<WordEntry, Int>>()
        val bucket = firstBucket(syls[0], allPrefix || syls[0].length < 2, fuzzy)
        for (e in bucket) {
            val m = e.syls.size
            if (m < n || (m > n && !allowLonger)) continue
            var ok = true
            for (i in 0 until minOf(n, m)) {
                val q = syls[i]
                val t = e.syls[i]
                val isLast = i == n - 1
                val matched = when {
                    allPrefix -> Pinyin.sylHasPrefix(t, q, fuzzy)
                    isLast && lastPrefix -> Pinyin.sylHasPrefix(t, q, fuzzy) || Pinyin.sylEq(q, t, fuzzy)
                    else -> Pinyin.sylEq(q, t, fuzzy)
                }
                if (!matched) {
                    ok = false
                    break
                }
            }
            if (!ok) continue
            val rank = when {
                m == n -> if (allPrefix && syls.indices.any { syls[it] != e.syls[it] }) 2 else 0
                else -> 1
            }
            results.add(e to rank)
        }
        return results.sortedWith(compareBy({ it.second }, { -it.first.freq })).take(limit)
    }

    fun findChars(
        syl: String,
        lastPrefix: Boolean = false,
        fuzzy: Boolean = false,
        limit: Int = 100,
    ): List<Pair<String, Int>> {
        val out = ArrayList<Pair<String, Int>>()
        if (!fuzzy) {
            out.addAll(charsBySyl[syl] ?: emptyList())
            if (lastPrefix && syl.isNotEmpty()) {
                for (i in prefixRange(charsSylSorted, syl)) {
                    val k = charsSylSorted[i]
                    if (k != syl) out.addAll(charsBySyl[k]!!)
                }
            }
        } else {
            val fs = Pinyin.foldFuzzy(syl)
            for ((k, lst) in charsBySyl) {
                val fk = Pinyin.foldFuzzy(k)
                if (fk == fs || (lastPrefix && fk.startsWith(fs))) out.addAll(lst)
            }
        }
        val best = HashMap<String, Int>()
        for ((ch, fr) in out) {
            if (best[ch] == null || fr > best[ch]!!) best[ch] = fr
        }
        return best.entries.sortedByDescending { it.value }.take(limit).map { it.key to it.value }
    }
}
