package com.liteime.ime.core

/** 拼音工具：音节表、去声调、模糊音折叠、零声母别名。与 Python core/pinyin.py 等价。 */
object Pinyin {

    val SYLLABLES: Set<String> = """
        a o e ai ei ao ou an en ang eng ong
        ba bo bai bei bao ban ben bang beng bi bie biao bian bin bing bu
        pa po pai pei pao pou pan pen pang peng pi pie piao pian pin ping pu
        ma mo me mai mei mao mou man men mang meng mi mie miao miu mian min ming mu
        fa fo fei fou fan fen fang feng fu
        da de dai dei dao dou dan den dang deng dong di dia die diao diu dian ding du duo dui duan dun
        ta te tai tao tou tan tang teng tong ti tie tiao tian ting tu tuo tui tuan tun
        na ne nai nei nao nou nan nang neng nong ni nie niao niu nian nin niang ning nu nuo nuan nv nve
        la le lai lei lao lou lan lang leng long li lia lie liao liu lian lin liang ling lu luo luan lun lv lve
        ga ge gai gei gao gou gan gen gang geng gong gu gua guo guai gui guan gun guang
        ka ke kai kei kao kou kan ken kang keng kong ku kua kuo kuai kui kuan kun kuang
        ha he hai hei hao hou han hen hang heng hong hu hua huo huai hui huan hun huang
        ji jia jie jiao jiu jian jin jiang jing jiong ju jue juan jun
        qi qia qie qiao qiu qian qin qiang qing qiong qu que quan qun
        xi xia xie xiao xiu xian xin xiang xing xiong xu xue xuan xun
        zha zhe zhi zhai zhei zhao zhou zhan zhen zhang zheng zhong zhu zhua zhuo zhuai zhui zhuan zhun zhuang
        cha che chi chai chao chou chan chen chang cheng chong chu chua chuo chuai chui chuan chun chuang
        sha she shi shai shei shao shou shan shen shang sheng shu shua shuo shuai shui shuan shun shuang
        re ri rao rou ran ren rang reng rong ru rua ruo rui ruan run
        za ze zi zai zei zao zou zan zen zang zeng zong zu zuo zui zuan zun
        ca ce ci cai cao cou can cen cang ceng cong cu cuo cui cuan cun
        sa se si sai sao sou san sen sang seng song su suo sui suan sun
        ya yo ye yao you yan yin yang ying yong yi yu yue yuan yun
        wa wo wai wei wan wen wang weng wu
    """.trimIndent().split(Regex("\\s+")).filter { it.isNotEmpty() }.toSet()

    /** 零声母别名：i/u/v 开头的韵母 -> 带 y/w 的标准写法 */
    val ALIASES: Map<String, String> = mapOf(
        "i" to "yi", "ia" to "ya", "ie" to "ye", "iao" to "yao", "iu" to "you",
        "ian" to "yan", "in" to "yin", "iang" to "yang", "ing" to "ying",
        "iong" to "yong", "u" to "wu", "ua" to "wa", "uo" to "wo", "ui" to "wei",
        "uai" to "wai", "uan" to "wan", "un" to "wen", "uang" to "wang",
        "v" to "yu", "ve" to "yue", "van" to "yuan", "vn" to "yun",
    )

    private val TONE_MAP = mapOf(
        'ā' to 'a', 'á' to 'a', 'ǎ' to 'a', 'à' to 'a',
        'ē' to 'e', 'é' to 'e', 'ě' to 'e', 'è' to 'e',
        'ī' to 'i', 'í' to 'i', 'ǐ' to 'i', 'ì' to 'i',
        'ō' to 'o', 'ó' to 'o', 'ǒ' to 'o', 'ò' to 'o',
        'ū' to 'u', 'ú' to 'u', 'ǔ' to 'u', 'ù' to 'u',
        'ǖ' to 'v', 'ǘ' to 'v', 'ǚ' to 'v', 'ǜ' to 'v', 'ü' to 'v',
    )

    fun stripTones(s: String): String {
        val sb = StringBuilder()
        for (ch in s.lowercase()) {
            when {
                TONE_MAP.containsKey(ch) -> sb.append(TONE_MAP[ch])
                ch in 'a'..'z' || ch == '\'' -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun normalizeInput(raw: String): String {
        val sb = StringBuilder()
        for (ch in raw.lowercase()) {
            if (ch in 'a'..'z' || ch == '\'' || ch == 'v') sb.append(ch)
        }
        return sb.toString()
    }

    fun foldFuzzy(s: String): String {
        var r = s
        when {
            r.startsWith("zh") -> r = "z" + r.substring(2)
            r.startsWith("ch") -> r = "c" + r.substring(2)
            r.startsWith("sh") -> r = "s" + r.substring(2)
        }
        if (r.startsWith("n")) r = "l" + r.substring(1)
        r = r.replace("ang", "an").replace("eng", "en").replace("ing", "in")
            .replace("uang", "uan").replace("ong", "un")
        return r
    }

    fun sylEq(a: String, b: String, fuzzy: Boolean = false): Boolean {
        if (a == b) return true
        return fuzzy && foldFuzzy(a) == foldFuzzy(b)
    }

    fun sylHasPrefix(target: String, prefix: String, fuzzy: Boolean = false): Boolean {
        if (target.startsWith(prefix)) return true
        return fuzzy && foldFuzzy(target).startsWith(foldFuzzy(prefix))
    }
}
