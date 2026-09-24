package cc.openxiot.matrix.i18n

import cc.openxiot.matrix.APP_LANGUAGES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * `values/`（英文，默认）与 **每一个** `values-*` 语言目录的对账。
 *
 * 这个测试存在的理由：几百条文案 × 60 多个语言，靠人眼盯「有没有漏翻」是不现实的；
 * 而漏翻与抄错的表现很隐蔽 —— 多数是某个语言的界面上冒出一句英文/中文，只有跑到
 * 那一屏、且恰好用那种语言才看得见。这里把它变成构建期错误。
 *
 * 也覆盖一类**会运行时崩溃**的错误：两边的占位符个数不一致（`%1$s` 少了一个），
 * `getString` 会抛 `MissingFormatArgumentException`；以及复数档位缺失，Android 会
 * **静默**回落到 `other`（俄语的 5 变成语法错的 «5 товаров»，全绿但译文是错的）。
 *
 * ## 与 `tools/i18n/validate.py` 的分工
 *
 * 两者都查这些目录，但用的**不是同一份知识**，且跑在不同地方：
 * 本测试在 CI 里（`build-release.yml` 有 `testDebugUnitTest`），守的是「仓库里已提交的
 * 文件有没有漂」——手改、批次只提交了一半、生成器跑完没提交新的。
 *
 * `validate.py` 跑在开发机上，吃的是一份**独立的外部对照**（`langs.py` 的复数表由
 * `cldr_check.py` 拿真实 CLDR 数据核对过）。**逐语言的复数档位要求只在那边查** ——
 * 本测试不抄那份表，因为抄一遍就是第二个真源，加语言时必有一边忘记改。
 * 这里只查不依赖语言表的、任何语言都成立的性质（见 `复数档位合法`）。
 */
class StringsParityTest {

    // ---------- 定位资源目录 ----------

    private val resDir: File = run {
        val fromProp = System.getProperty("app.resDir")?.let(::File)
        if (fromProp != null && fromProp.isDirectory) return@run fromProp
        // AGP 单测的工作目录通常是模块目录（app/），但不赌它：向上找到含 src/main/res 的那一级
        generateSequence(File("").absoluteFile) { it.parentFile }
            .mapNotNull { dir ->
                listOf(File(dir, "app/src/main/res"), File(dir, "src/main/res"))
                    .firstOrNull(File::isDirectory)
            }
            .firstOrNull()
            ?: error("定位不到 res 目录（试过 system property app.resDir 与向上查找）—— 拒绝静默通过")
    }

    private val defaultDir = File(resDir, "values")

    /**
     * 磁盘上的语言目录。用**通配 + 限定符语法**筛，而不是照抄语言表。
     *
     * 这样「多了个没人认识的目录」也能被抓到（见 `语言目录与语言表一一对应`），
     * 而照抄表的话，漏掉的目录恰好就是表里也没有的那个，永远发现不了。
     *
     * 两道筛都要，缺一不可：
     *  1. **必须 `startsWith("values-")`**。`res/` 底下还有 `xml/`、`raw/` 这些目录，
     *     而 `xml` 恰好是三个小写字母 —— 只看「后缀像不像语言限定符」的话，
     *     `res/xml/` 会被当成一个语言目录（第一版就栽在这，报的是「xml/ 与 values/
     *     的 strings 文件必须一一对应」）。
     *  2. **后缀得是个语言限定符**。`values-` 这个前缀不只给语言用：`values-night`
     *     （深色）、`values-v29`（API 级别）、`values-sw600dp`（屏幕宽度）都是同一族，
     *     它们不是语言，不该进对账。
     */
    private val localeDirs: List<File> = resDir.listFiles { f ->
        f.isDirectory && f.name.startsWith("values-") &&
            LOCALE_DIR.matchEntire(f.name.removePrefix("values-")) != null
    }?.sortedBy { it.name } ?: emptyList()

    private companion object {
        /** `zh` / `zh-rTW` / `b+kmr` / `pt-rBR`；`night`/`v29`/`sw600dp` 都匹配不上 */
        val LOCALE_DIR = Regex("""([a-z]{2,3}|b\+[a-z0-9+]{2,8})(-r[A-Z]{2})?""")

        /** CLDR 的档位词汇表。**不区分语言** —— 具体某语言要哪几档由 validate.py 管。 */
        val CLDR_QUANTITIES = setOf("zero", "one", "two", "few", "many", "other")

        /**
         * 与英文逐字相同占比的上限。
         *
         * 换掉了原先「值不得与英文逐字相同」那条规则：**64 个语言下它会全线误报**。
         * 任何语言的 UI 都会留下一批专有名词（`Modbus`、`MQTT`、`OK`、`WeMatrix`），
         * 越短的语言越明显 —— web 工程自己的词典就有 8–16% 与英文相同，那是正常值。
         *
         * 比例规则抓的是**整份照抄英文**（100% 相同，当场炸），而放过零散的专有名词。
         * 实测：`zh` 1%，`de` 3%（搬运来的那 223 条里有德文与英文同形的词）。
         */
        const val MAX_IDENTICAL_RATIO = 0.50
    }

    // ---------- 解析 ----------

    private class Entry(val name: String, val value: String, val translatable: Boolean)

    private class Res {
        val strings = mutableMapOf<String, Entry>()
        /** plurals: name -> (quantity -> value) */
        val plurals = mutableMapOf<String, MutableMap<String, String>>()
    }

    private fun stringFiles(dir: File): List<File> =
        dir.listFiles { f -> f.isFile && f.name.startsWith("strings") && f.name.endsWith(".xml") }
            ?.sortedBy { it.name }
            ?: emptyList()

    private fun parse(file: File): Res {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val res = Res()
        val root = doc.documentElement

        val stringNodes = root.getElementsByTagName("string")
        for (i in 0 until stringNodes.length) {
            val el = stringNodes.item(i) as Element
            val name = el.getAttribute("name")
            res.strings[name] = Entry(
                name = name,
                value = el.textContent,
                // 缺省是 true；translatable="false" 的条目所有语言共用一份，不参与对账
                translatable = el.getAttribute("translatable") != "false",
            )
        }

        val pluralNodes = root.getElementsByTagName("plurals")
        for (i in 0 until pluralNodes.length) {
            val el = pluralNodes.item(i) as Element
            val name = el.getAttribute("name")
            val items = el.getElementsByTagName("item")
            val map = mutableMapOf<String, String>()
            for (j in 0 until items.length) {
                val item = items.item(j) as Element
                map[item.getAttribute("quantity")] = item.textContent
            }
            res.plurals[name] = map
        }
        return res
    }

    /** 英文侧各文件只解析一次（63 个语言目录 × 15 个文件，重复解析纯属浪费） */
    private val english: Map<File, Res> by lazy {
        stringFiles(defaultDir).associateWith { parse(it) }
    }

    /** 参与对账的英文 key（剔掉 `translatable="false"`）。 */
    private fun enKeysOf(res: Res): Set<String> =
        res.strings.values.filter { it.translatable }.map { it.name }.toSet()

    /** 占位符的多重集：`%1$s` / `%2$d` / `%s` / `%d`。`%%`（字面百分号）不匹配，正确。 */
    private fun placeholders(value: String): List<String> =
        Regex("""%(\d+\$)?[sd]""").findAll(value).map { it.value }.sorted().toList()

    private val cjk = Regex("[\\u4e00-\\u9fff]")

    /** 日语正用汉字、韩语偶有汉字，故这两者不查汉字残留（中文三种当然也不查）。 */
    private val hanAllowed = setOf("zh", "zh-TW", "zh-HK", "ja", "ko")

    /**
     * 一个值里**没有可翻译的东西**：去掉占位符后，连一个字母都没有。
     *
     * 例：`%1$s: %2$s`（服务名 + 冒号 + 一条错误）。它由标点和两个参数组成，没有词可翻，
     * 所有语言必然逐字相同 —— 从「同英文比例」的分子分母里都剔掉，免得稀释比例。
     */
    private fun hasNothingToTranslate(value: String): Boolean =
        value.replace(Regex("""%(\d+\$)?[sd]"""), "").none { it.isLetter() }

    // ---------- 语言表的投影 ----------

    /** tag → 资源目录名。与 `tools/i18n/langs.py` 的规则一致（那边有断言守着）。 */
    private fun dirNameOf(tag: String): String {
        val parts = tag.split("-")
        val lang = parts[0]
        // 三字母代码必须用 b+ 形式，否则 aapt2 不认
        val head = if (lang.length == 3) "b+$lang" else lang
        val rest = parts.drop(1).joinToString("") { p ->
            if (p.length == 2 && p.all { it.isUpperCase() }) "-r$p" else "-$p"
        }
        return "values-$head$rest"
    }

    // ---------- 断言 ----------

    @Test
    fun `语言目录与语言表一一对应`() {
        val expected = APP_LANGUAGES
            .filter { it.tag != "en" }   // 英文的资源目录就是默认的 values/
            .map { dirNameOf(it.tag) }
            .toSet()
        val actual = localeDirs.map { it.name }.toSet()

        assertEquals(
            "磁盘上的语言目录与 APP_LANGUAGES 对不上。\n" +
                "  表里有、磁盘上没有：${(expected - actual).sorted()}（漏建目录 = 该语言在真机上永远显示英文）\n" +
                "  磁盘上有、表里没有：${(actual - expected).sorted()}（用户选不到 = 死资源，或目录名拼错了）",
            expected,
            actual,
        )
    }

    @Test
    fun `每个语言目录的文件与 values 成对`() {
        val en = stringFiles(defaultDir).map { it.name }
        assertTrue("values/ 一个 strings 文件都没有，定位错了吧", en.isNotEmpty())
        for (dir in localeDirs) {
            assertEquals("${dir.name}/ 与 values/ 的 strings 文件必须一一对应", en, stringFiles(dir).map { it.name })
        }
    }

    @Test
    fun `默认语言 values 里不出现中文`() {
        stringFiles(defaultDir).forEach { file ->
            parse(file).strings.values.filter { it.translatable }.forEach { e ->
                assertTrue(
                    "${file.name} 的 ${e.name} 含中文：'${e.value}' —— " +
                        "values/ 是默认资源（所有未翻译的语言都落在这里），必须是英文。" +
                        "中文请放 values-zh/；确实该所有语言共用的加 translatable=\"false\"",
                    !cjk.containsMatchIn(e.value),
                )
            }
        }
    }

    @Test
    fun `key 集合一致`() {
        for (dir in localeDirs) {
            val tag = tagOf(dir)
            for (file in stringFiles(defaultDir)) {
                val en = english.getValue(file)
                val other = parse(File(dir, file.name))

                // ⚠️ 这个**不对称**是故意的，别「顺手修平」：英文侧按 translatable 过滤、
                // 语言侧不过滤。它正是「translatable="false" 的条目不许抄进语言目录」
                // 这条规则唯一的执行者 —— 谁把英文那两条复制进 62 个目录，这里就会红。
                // （当前全项目已无 translatable="false"，此断言暂为恒真；留着是为了
                //   下一条这样的文案出现时立刻生效，而不是等人重新想起来。）
                assertEquals(
                    "${dir.name}/${file.name}（$tag）: 多出或缺少这些 key",
                    enKeysOf(en), other.strings.keys,
                )
                assertEquals(
                    "${dir.name}/${file.name}（$tag）: plurals 的 key 集合不一致",
                    en.plurals.keys, other.plurals.keys,
                )
            }
        }
    }

    @Test
    fun `占位符一致`() {
        val problems = mutableListOf<String>()
        for (dir in localeDirs) {
            val tag = tagOf(dir)
            for (file in stringFiles(defaultDir)) {
                val en = english.getValue(file)
                val other = parse(File(dir, file.name))

                en.strings.values.filter { it.translatable }.forEach { e ->
                    val v = other.strings[e.name]?.value ?: return@forEach
                    if (placeholders(e.value) != placeholders(v)) {
                        problems += "${dir.name}/${file.name} ${e.name}（$tag）\n" +
                            "      英文: ${e.value}\n      $tag: $v"
                    }
                }
                en.plurals.forEach { (name, enItems) ->
                    val items = other.plurals[name] ?: return@forEach
                    enItems.forEach { (q, enValue) ->
                        val v = items[q] ?: return@forEach
                        if (placeholders(enValue) != placeholders(v)) {
                            problems += "${dir.name}/${file.name} plurals/$name[$q]（$tag）\n" +
                                "      英文: $enValue\n      $tag: $v"
                        }
                    }
                }
            }
        }
        assertTrue(
            "这些条目的占位符与英文不一致 —— 少一个会在真机抛 MissingFormatArgumentException，\n" +
                "顺序/个数都要对上（位置可以按目标语语法调整，那是 %1\$s 序号的作用）：\n" +
                problems.joinToString("\n"),
            problems.isEmpty(),
        )
    }

    /**
     * `other` 必须存在，且不许出现 CLDR 词汇表之外的档位。
     *
     * **逐语言的档位要求不在这里**（那要一张语言表，会变成第二个真源）—— 在
     * `tools/i18n/validate.py` 里查，它的表由 `cldr_check.py` 拿真实 CLDR 核对过。
     * 这里查的是任何语言都成立的两条：
     *  - `other` 缺席 = Android 直接报错（它是唯一的必填档）；
     *  - 档位名写错（`others`、`1`、`ONE`）= 该档**静默失效**，看起来像翻译漏了。
     */
    @Test
    fun `复数档位合法`() {
        for (dir in localeDirs) {
            for (file in stringFiles(defaultDir)) {
                parse(File(dir, file.name)).plurals.forEach { (name, items) ->
                    assertTrue(
                        "${dir.name}/${file.name}: plurals/$name 缺 other 档 —— " +
                            "Android 要求 other 必须存在，缺了它整条 plurals 会取不到值",
                        items.containsKey("other"),
                    )
                    val bad = items.keys - CLDR_QUANTITIES
                    assertTrue(
                        "${dir.name}/${file.name}: plurals/$name 有非法档位 $bad —— " +
                            "合法值只有 $CLDR_QUANTITIES；拼错的档位不会报错，只会静默失效",
                        bad.isEmpty(),
                    )
                }
            }
        }
    }

    @Test
    fun `每个语言确实翻了`() {
        val failures = mutableListOf<String>()
        for (dir in localeDirs) {
            val tag = tagOf(dir)
            var total = 0
            var identical = 0
            val samples = mutableListOf<String>()

            for (file in stringFiles(defaultDir)) {
                val en = english.getValue(file)
                val other = parse(File(dir, file.name))

                en.strings.values.filter { it.translatable }.forEach { e ->
                    if (hasNothingToTranslate(e.value)) return@forEach
                    val v = other.strings[e.name]?.value ?: return@forEach
                    total++
                    if (v == e.value) {
                        identical++
                        if (samples.size < 8) samples += "${file.name}:${e.name} = '${e.value}'"
                    }
                }
                // 复数按 other 档比（各语言的档位集不同，other 是共有的那一档）
                en.plurals.forEach { (name, enItems) ->
                    val enOther = enItems["other"] ?: return@forEach
                    val v = other.plurals[name]?.get("other") ?: return@forEach
                    if (hasNothingToTranslate(enOther)) return@forEach
                    total++
                    if (v == enOther) {
                        identical++
                        if (samples.size < 8) samples += "${file.name}:plurals/$name[other] = '$enOther'"
                    }
                }
            }

            if (total == 0) {
                failures += "${dir.name}（$tag）: 一条可比对的条目都没有，怎么算出来的？"
                continue
            }
            val ratio = identical.toDouble() / total
            if (ratio > MAX_IDENTICAL_RATIO) {
                failures += "${dir.name}（$tag）: $identical/$total 条与英文逐字相同" +
                    "（${(ratio * 100).toInt()}%，上限 ${(MAX_IDENTICAL_RATIO * 100).toInt()}%）" +
                    "，像是整份照抄了英文。抽样：\n        " + samples.joinToString("\n        ")
            }
        }
        assertTrue(
            "这些语言与英文逐字相同的比例过高。专有名词（Modbus / OK / WeMatrix）逐字相同是正常的，\n" +
                "所以规则是**比例**而不是逐条 —— 触发它意味着那一份基本没翻：\n" +
                failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    @Test
    fun `非中文语言里没有汉字残留`() {
        val problems = mutableListOf<String>()
        for (dir in localeDirs) {
            val tag = tagOf(dir)
            if (tag in hanAllowed) continue
            for (file in stringFiles(defaultDir)) {
                parse(File(dir, file.name)).strings.forEach { (name, e) ->
                    if (cjk.containsMatchIn(e.value)) {
                        problems += "${dir.name}/${file.name}:$name（$tag）= '${e.value}'"
                    }
                }
            }
        }
        assertTrue(
            "这些条目里混进了汉字 —— 多半是从 values-zh/ 整段复制过来的，那一屏会是中文：\n" +
                problems.take(40).joinToString("\n") +
                if (problems.size > 40) "\n… 另有 ${problems.size - 40} 条" else "",
            problems.isEmpty(),
        )
    }

    /**
     * 繁体守卫 —— **从 `AppLocaleTest` 迁移过来的那条保证**。
     *
     * 上一版靠 Kotlin 里 `zh → SIMPLIFIED_CHINESE` 的特判保证「繁体系统不掉英文」；现在
     * 那个特判必须删掉（留着会让 `values-zh-rTW` / `-rHK` 永远选不中），保证就落到这里：
     * 这两个目录必须存在（由 `语言目录与语言表一一对应` 守），且**内容得真的是繁体**。
     *
     * 判据是「与简体逐字相同的条目不能占多数」：整份复制 `values-zh/` 会 100% 相同、
     * 当场炸；而「软件」「版本」这类简繁同形的词占少数，不会误报。
     */
    @Test
    fun `繁体目录不能是简体照抄`() {
        val zhDir = File(resDir, "values-zh")
        if (!zhDir.isDirectory) return   // 中文目录都没有的话，别的测试已经在报了

        for (dir in localeDirs.filter { it.name in setOf("values-zh-rTW", "values-zh-rHK") }) {
            var total = 0
            var same = 0
            val samples = mutableListOf<String>()
            for (file in stringFiles(defaultDir)) {
                val zh = parse(File(zhDir, file.name))
                val hant = parse(File(dir, file.name))
                zh.strings.forEach { (name, e) ->
                    val v = hant.strings[name]?.value ?: return@forEach
                    total++
                    if (v == e.value) {
                        same++
                        if (samples.size < 10) samples += "${file.name}:$name = '${e.value}'"
                    }
                }
                zh.plurals.forEach { (name, items) ->
                    val other = items["other"] ?: return@forEach
                    val v = hant.plurals[name]?.get("other") ?: return@forEach
                    total++
                    if (v == other) {
                        same++
                        if (samples.size < 10) samples += "${file.name}:plurals/$name[other] = '$other'"
                    }
                }
            }
            if (total == 0) continue
            assertTrue(
                "${dir.name} 有 $same/$total 条与 values-zh/ 逐字相同 —— " +
                    "简繁同形的词（软件、版本）占一些是正常的，过半就说明这份是照抄的简体，\n" +
                    "而繁体用户本该看到繁体。抽样：\n    " + samples.joinToString("\n    "),
                same * 2 <= total,
            )
        }
    }

    /** 目录名 → 语言 tag，供失败信息里写清楚是哪个语言。 */
    private fun tagOf(dir: File): String {
        val suffix = dir.name.removePrefix("values-")
        return APP_LANGUAGES.firstOrNull { dirNameOf(it.tag) == dir.name }?.tag ?: suffix
    }
}
