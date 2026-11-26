package com.liteime.ime.core

/** 联想预测。与 Python core/predictor.py 等价。 */
class Predictor(private val dict: Dictionary) {

    fun predict(committed: String, n: Int = 8): List<String> {
        if (committed.isEmpty()) return emptyList()
        val out = ArrayList<String>()
        val seen = HashSet<String>()

        fun push(w: String) {
            if (w.isNotEmpty() && seen.add(w) && w != committed) out.add(w)
        }

        for (k in intArrayOf(2, 1)) {
            if (committed.length < k) continue
            val prefix = committed.takeLast(k)
            dict.words.asSequence()
                .filter { it.word.startsWith(prefix) && it.word.length > prefix.length }
                .sortedByDescending { it.freq }
                .take(n * 2)
                .forEach { push(it.word) }
        }

        val last = committed.last().toString()
        dict.words.asSequence()
            .filter { it.word.startsWith(last) && it.word.length > 1 }
            .sortedByDescending { it.freq }
            .take(n * 2)
            .forEach { push(it.word) }

        return out.take(n)
    }
}
