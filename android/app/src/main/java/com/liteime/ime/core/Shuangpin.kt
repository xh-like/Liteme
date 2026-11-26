package com.liteime.ime.core

/** 双拼方案解码。与 Python core/shuangpin.py 等价。 */
object Shuangpin {

    data class Seg(val syl: String, val isPrefix: Boolean)

    private val SPECIAL_INITIALS = mapOf("v" to "zh", "i" to "ch", "u" to "sh")

    private val XIAOHE = mapOf(
        "a" to listOf("a"), "b" to listOf("in"), "c" to listOf("ao"), "d" to listOf("ai"),
        "e" to listOf("e"), "f" to listOf("en"), "g" to listOf("eng"), "h" to listOf("ang"),
        "i" to listOf("i"), "j" to listOf("an"), "k" to listOf("ing", "uai"),
        "l" to listOf("iang", "uang"), "m" to listOf("ian"), "n" to listOf("iao"),
        "o" to listOf("o", "uo"), "p" to listOf("ie"), "q" to listOf("iu"),
        "r" to listOf("uan", "van"), "s" to listOf("ong", "iong"), "t" to listOf("ue", "ve"),
        "u" to listOf("u"), "v" to listOf("ui", "v"), "w" to listOf("ei"),
        "x" to listOf("ia", "ua"), "y" to listOf("un", "vn"), "z" to listOf("ou"),
    )

    private val MSPY = mapOf(
        "a" to listOf("a"), "b" to listOf("ou"), "c" to listOf("iao"),
        "d" to listOf("iang", "uang"), "e" to listOf("e"), "f" to listOf("en"),
        "g" to listOf("eng"), "h" to listOf("ang"), "i" to listOf("i"), "j" to listOf("an"),
        "k" to listOf("ao"), "l" to listOf("ai"), "m" to listOf("ian"), "n" to listOf("in"),
        "o" to listOf("o", "uo"), "p" to listOf("un", "vn"), "q" to listOf("iu"),
        "r" to listOf("uan", "van", "er"), "s" to listOf("ong", "iong"), "t" to listOf("ue", "ve"),
        "u" to listOf("u"), "v" to listOf("ui"), "w" to listOf("ia", "ua"), "x" to listOf("ie"),
        "y" to listOf("uai", "v"), "z" to listOf("ei"), ";" to listOf("ing"),
    )

    private val ZIRANMA = mapOf(
        "a" to listOf("a"), "b" to listOf("ou"), "c" to listOf("iao"),
        "d" to listOf("iang", "uang"), "e" to listOf("e"), "f" to listOf("en"),
        "g" to listOf("eng"), "h" to listOf("ang"), "i" to listOf("i"), "j" to listOf("an"),
        "k" to listOf("ao"), "l" to listOf("ai"), "m" to listOf("ian"), "n" to listOf("in"),
        "o" to listOf("o", "uo"), "p" to listOf("un", "vn"), "q" to listOf("iu"),
        "r" to listOf("uan", "van", "er"), "s" to listOf("ong", "iong"), "t" to listOf("ue", "ve"),
        "u" to listOf("u"), "v" to listOf("ui", "v"), "w" to listOf("ia", "ua"), "x" to listOf("ie"),
        "y" to listOf("ing", "uai"), "z" to listOf("ei"),
    )

    data class Scheme(val name: String, val finals: Map<String, List<String>>, val zero: String)

    val SCHEMES = mapOf(
        "xiaohe" to Scheme("小鹤双拼", XIAOHE, "literal"),
        "mspy" to Scheme("微软双拼", MSPY, "o"),
        "ziranma" to Scheme("自然码", ZIRANMA, "literal"),
    )

    private fun finVariants(fin: String): List<String> {
        val alt = fin.replace("v", "u")
        return if (alt == fin) listOf(fin) else listOf(fin, alt)
    }

    private fun groupOptions(k1: String, k2: String?, scheme: Scheme): List<Seg> {
        val opts = ArrayList<Seg>()
        if (k2 == null) {
            opts.add(Seg(SPECIAL_INITIALS[k1] ?: k1, true))
            return opts
        }
        val ini = SPECIAL_INITIALS[k1] ?: k1
        for (fin0 in scheme.finals[k2] ?: listOf(k2)) {
            for (fin in finVariants(fin0)) {
                val syl = ini + fin
                if (syl in Pinyin.SYLLABLES && Seg(syl, false) !in opts) {
                    opts.add(Seg(syl, false))
                }
            }
        }
        if (scheme.zero == "o") {
            if (k1 == "o") {
                for (fin0 in scheme.finals[k2] ?: emptyList()) {
                    if (fin0 in Pinyin.SYLLABLES && Seg(fin0, false) !in opts) {
                        opts.add(Seg(fin0, false))
                    }
                    if (k2 == "o" && (fin0 == "o" || fin0 == "uo") && Seg(fin0, false) !in opts) {
                        opts.add(Seg(fin0, false))
                    }
                }
            }
        } else {
            if (k1 == "a" || k1 == "o" || k1 == "e") {
                if (k1 == k2 && Seg(k1, false) !in opts) opts.add(Seg(k1, false))
                val lit = k1 + k2
                if (lit in Pinyin.SYLLABLES && Seg(lit, false) !in opts) opts.add(Seg(lit, false))
                for (fin0 in scheme.finals[k2] ?: emptyList()) {
                    if (fin0.startsWith(k1) && fin0 in Pinyin.SYLLABLES && Seg(fin0, false) !in opts) {
                        opts.add(Seg(fin0, false))
                    }
                }
            }
        }
        if (opts.isEmpty()) {
            val first = (scheme.finals[k2] ?: listOf(k2))[0]
            opts.add(Seg(ini + first, true))
        }
        return opts
    }

    /** 把双拼按键序列解码为若干候选路径（每 2 键 1 音节）。 */
    fun decode(keys: String, schemeName: String): List<List<Seg>> {
        val scheme = SCHEMES[schemeName] ?: error("未知双拼方案: $schemeName")
        val ks = keys.replace("'", "")
        val groups = ArrayList<List<Seg>>()
        var i = 0
        while (i < ks.length) {
            val k1 = ks[i].toString()
            val k2 = if (i + 1 < ks.length) ks[i + 1].toString() else null
            groups.add(groupOptions(k1, k2, scheme))
            i += if (k2 != null) 2 else 1
        }
        if (groups.isEmpty()) return listOf(emptyList())
        // 笛卡尔积（上限 8 条路径）
        var paths: List<List<Seg>> = listOf(emptyList())
        for (g in groups) {
            val next = ArrayList<List<Seg>>()
            for (base in paths) {
                for (seg in g) {
                    next.add(base + seg)
                    if (next.size >= 8) break
                }
                if (next.size >= 8) break
            }
            paths = next
        }
        return paths
    }
}
