package cc.openxiot.wematrix.i18n

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 漏抽门禁：`src/main` 的 Kotlin 源码里不允许再出现**含中文的字符串字面量**。
 *
 * 为什么需要它：这次要把 427 条散在 67 个文件里的中文抽成资源，靠人是数不完的，
 * 而漏掉一条的后果是英文界面上冒出一句中文 —— 隐蔽，且只有跑到那一屏才看得见。
 * 门禁把它变成构建期错误，顺带让「还差多少」变成可度量的事：白名单每清空一个目录，
 * 就是实打实完成了一批。
 *
 * 三件必须做对的事：
 *  1. **剥注释**。本项目的 KDoc 几乎全是中文，不剥的话这个测试第一天就废。
 *  2. **只扫 `src/main`**。`src/test` 里有大量中文测试函数名与断言字面量，不该被管。
 *  3. **不扫日志**。`Log.*` 的参数是给开发者看的，不是用户文案。
 *
 * 另有一条**必须逐处理由**的例外：见 [IGNORE_MARKER]。
 */
class NoHardcodedChineseTest {

    private val srcRoot: File = run {
        val fromProp = System.getProperty("app.srcDir")?.let(::File)
        if (fromProp != null && fromProp.isDirectory) return@run fromProp
        generateSequence(File("").absoluteFile) { it.parentFile }
            .mapNotNull { dir ->
                listOf(File(dir, "app/src/main/java"), File(dir, "src/main/java"))
                    .firstOrNull(File::isDirectory)
            }
            .firstOrNull()
            ?: error("定位不到 src/main/java（试过 system property app.srcDir 与向上查找）—— 拒绝静默通过")
    }

    /**
     * `pending` 的键是**相对包根**写的（`ui/modbus`），而 `relativeTo(srcRoot)` 得到的是
     * `cc/openxiot/wematrix/ui/modbus`。不剥前缀，白名单就一条都匹配不上 —— 表现是门禁
     * 从头红到尾、把 400 多条全倒出来（`pending 的每一条都真的挂在文件上` 那条测试专门
     * 防它复活）。
     */
    private val PKG_PREFIX = "cc/openxiot/wematrix/"

    /**
     * 还没抽的目录 → 理由。**每完成一批就删掉对应条目**。
     *
     * **第 6 批（`ui/modbus` + `ModbusModels.displayName`）完成后已清空 —— 全项目抽取完毕。**
     * 这个 map 从此只是留给将来新模块的地儿：新增界面若一时没跟上，把目录加进来并写明批次，
     * 别去动下面的扫描逻辑。
     *
     * 写成 `emptyMap<...>()` 而不是 `mapOf()`：后者在没有实参时推不出 `K`/`V`，
     * 编译期就会炸（上一批清空白名单时踩过）。
     */
    private val pending: Map<String, String> = emptyMap()

    /**
     * 豁免标记：`// i18n-ignore: 理由`。
     *
     * 极少数中文出现在源码里并不是「界面文案」，而是**数据**。目前全项目只有一处：
     * [cc.openxiot.wematrix.ui.modbus.fieldBaseName] 匹配的「读 / 写」是**用户敲进点表的
     * 命令名前缀**（如「读开关状态」），改成从资源取，英文界面下就再也匹配不上，
     * 字段默认名当场变样 —— 那不是翻译，是改坏功能。
     *
     * 标记行豁免**它的下一行**。为什么不写成行尾注释：那些行本身就贴着行宽上限，
     * 再缀一条理由就超了。这样也不会漂 —— 万一有人往标记和正文之间插了一行，
     * 真正含中文的那行就失去豁免、门禁当场变红（`每处 i18n-ignore 都真的豁免掉了一条中文`
     * 那条测试还专门盯着这个），不会静默放过。
     */
    private val IGNORE_MARKER = "// i18n-ignore"

    /**
     * 豁免点的上限。这个标记是**必须逐处理由**的例外，不是漏斗；
     * 数字一小，谁想拿它糊弄就会撞墙。
     *
     * **2026-09 由 3 抬到 5：** 多语言扩展引入了 `AppLanguages.kt` 的 64 条语言表，
     * 其中 4 条的语言自称是汉字（`中文(简体)` / `中文(繁體)` / `中文(香港)` / `日本語`），
     * 与 [PointOptions] 的「读 / 写」同性质 —— 是**数据**，不是待翻译的界面文案：
     * 语言列表按惯例永远用该语言自己的写法，翻译了就没人认得出。
     *
     * 注意 `日本語` 也在内：本测试的区间是汉字整体（`一-鿿`），**不区分中日**，
     * 平假名/片假名/谚文则不在区间里（所以韩语`한국어`不触发）。要精确区分得引入
     * Unicode script 判定，为这 4 条数据不值当。
     *
     * 这 4 处由 `tools/i18n/gen_kotlin.py` 自动生成，**有界** —— 汉字自称的语言
     * 就那么几种。抬高后的预算正好用满，没有留余量：再加豁免点就得再谈一次。
     */
    private val IGNORE_BUDGET = 5   // 类体里不能用 const（只有顶层/object/companion 可以）

    private val cjk = Regex("[\\u4e00-\\u9fff]")

    @Test
    fun `没有漏抽的中文字面量`() {
        val hits = mutableListOf<String>()

        srcRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val rel = relPath(file)
                if (isPending(rel)) return@forEach
                codeStringLiterals(stripIgnored(file.readText())).forEach { literal ->
                    if (cjk.containsMatchIn(literal)) hits += "$rel: \"$literal\""
                }
            }

        assertTrue(
            "这些字符串字面量还硬编码着中文，应当抽成资源（values/ 英文 + values-zh/ 中文）：\n" +
                hits.joinToString("\n") +
                "\n\n若整个目录还没轮到，把目录加进 NoHardcodedChineseTest.pending 并写明批次；" +
                "若它压根不是文案而是数据，在该行**上一行**写 `// i18n-ignore: 理由`。",
            hits.isEmpty(),
        )
    }

    /** 相对**包根**的路径，与 [pending] 的键同一套写法。 */
    private fun relPath(file: File): String =
        file.relativeTo(srcRoot).invariantSeparatorsPath.removePrefix(PKG_PREFIX)

    private fun isPending(relPath: String): Boolean =
        pending.keys.any { key ->
            // 目录条目匹配其下所有文件；带 .kt 后缀的条目只匹配那一个文件
            if (key.endsWith(".kt")) relPath == key else relPath.startsWith("$key/")
        }

    /**
     * 白名单里的每一条都必须真的挂在某个文件上。
     *
     * 没有这条，[pending] 的键写错（比如漏了剥包名前缀）时不会报错，只会静默失效 ——
     * 门禁照常红着，而「哪一批做完了」再也无从判断。
     */
    @Test
    fun `pending 的每一条都真的挂在文件上`() {
        val all = srcRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { relPath(it) }
            .toList()

        val dead = pending.keys.filterNot { key ->
            if (key.endsWith(".kt")) key in all else all.any { it.startsWith("$key/") }
        }
        assertTrue(
            "pending 里这些条目标不到任何 .kt 文件，白名单已失效（包名前缀写错了？文件改名了？）：$dead",
            dead.isEmpty(),
        )
    }

    /**
     * 被 [IGNORE_MARKER] 豁免的行号（标记行的**下一行**，0 起算）。
     */
    private fun ignoredLines(source: String): Set<Int> {
        val lines = source.split("\n")
        return lines.indices
            .filter { lines[it].contains(IGNORE_MARKER) }
            .map { it + 1 }
            .filter { it < lines.size }
            .toSet()
    }

    /**
     * 把被豁免的行抹成空行再交给状态机 —— 只清内容，行数与其余各行原样保留。
     *
     * 抹空而不是删行：删行会让 `"""` 之类跨行的东西错位，而状态机并不解析语法结构，
     * 保持行数不变是最不容易出错的做法。
     */
    private fun stripIgnored(source: String): String {
        val skip = ignoredLines(source)
        if (skip.isEmpty()) return source
        return source.split("\n")
            .mapIndexed { i, line -> if (i in skip) "" else line }
            .joinToString("\n")
    }

    /** 豁免点逐个列出来，供下面那条预算测试与失败信息共用。 */
    private fun ignoreSites(): List<Pair<String, Int>> =
        srcRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                ignoredLines(file.readText()).map { relPath(file) to it }
            }
            .toList()

    /**
     * 豁免点不许变成漏斗。上限一小，谁想拿它糊弄就会撞墙。
     */
    @Test
    fun `i18n-ignore 的处数在预算内`() {
        val sites = ignoreSites()
        assertTrue(
            "`$IGNORE_MARKER` 是给「这不是界面文案，是数据」用的例外，每一处都得单独说得清理由；" +
                "预算是 $IGNORE_BUDGET 处，现在有 ${sites.size} 处：" +
                sites.joinToString { (rel, line) -> "$rel:${line + 1}" } +
                "\n要么把多出来的改成抽资源，要么先在评审里说服人再抬高预算。",
            sites.size <= IGNORE_BUDGET,
        )
    }

    /**
     * 每一处豁免都真的豁免掉了一条含中文的字面量。
     *
     * 防的是「标记与正文之间被插了一行」：那样豁免落到了别的行上，真正含中文的那行
     * 会重新被上面那条门禁捞出来（会红），但红得莫名其妙 —— 这条给出准确的失败原因。
     */
    @Test
    fun `每处 i18n-ignore 都真的豁免掉了一条中文`() {
        val dead = mutableListOf<String>()
        srcRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val lines = file.readText().split("\n")
                ignoredLines(lines.joinToString("\n")).forEach { at ->
                    val exempted = codeStringLiterals(lines[at]).any { cjk.containsMatchIn(it) }
                    if (!exempted) dead += "${relPath(file)}:${at + 1}（正文是「${lines[at].trim()}」）"
                }
            }

        assertTrue(
            "这些 `$IGNORE_MARKER` 底下那一行并没有含中文的字面量，标记挂空了 —— " +
                "多半是标记和正文之间被插了行。标记必须紧贴它要豁免的那一行：\n" +
                dead.joinToString("\n"),
            dead.isEmpty(),
        )
    }

    /**
     * 取出源码里所有字符串字面量的内容，**已剥掉注释**。
     *
     * 手写状态机而非正则：要正确处理 `//` 出现在字符串里（`"https://…"`）、`\"` 转义、
     * 以及字符字面量 `'"'`。正则在这些地方都会翻车。
     */
    private fun codeStringLiterals(source: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        val n = source.length
        while (i < n) {
            when {
                // 行注释
                source.startsWith("//", i) -> {
                    while (i < n && source[i] != '\n') i++
                }
                // 块注释（KDoc 也是它）
                source.startsWith("/*", i) -> {
                    i += 2
                    while (i < n && !source.startsWith("*/", i)) i++
                    i = (i + 2).coerceAtMost(n)
                }
                // 三引号原始字符串：本项目基本没有，但别让它把后面的都吞掉
                source.startsWith("\"\"\"", i) -> {
                    i += 3
                    val start = i
                    while (i < n && !source.startsWith("\"\"\"", i)) i++
                    out += source.substring(start, i)
                    i = (i + 3).coerceAtMost(n)
                }
                // 字符字面量：跳过去，免得里面的引号被当成字符串开头
                source[i] == '\'' -> {
                    i++
                    while (i < n && source[i] != '\'') {
                        if (source[i] == '\\') i++
                        i++
                    }
                    i++
                }
                // 普通字符串
                source[i] == '"' -> {
                    i++
                    val sb = StringBuilder()
                    while (i < n && source[i] != '"') {
                        if (source[i] == '\\' && i + 1 < n) {
                            i++   // 转义序列整体吞掉，\" 不会提前结束字符串
                        }
                        sb.append(source[i])
                        i++
                    }
                    i++
                    out += sb.toString()
                }
                else -> i++
            }
        }
        return out
    }

    /** 状态机本身的自测 —— 它错了的话，上面那条门禁就是假的。 */
    @Test
    fun `剥注释的状态机`() {
        val src = """
            // 中文注释不算：val a = 1
            /** KDoc 里的 "引号中文" 也不算 */
            val a = "真中文"
            val b = "https://example.com/路径"   // 行内注释里的中文
            val c = "转义\"后面的中文"
            val d = '中'
        """.trimIndent()

        val found = codeStringLiterals(src).filter { cjk.containsMatchIn(it) }
        assertTrue(
            "应当捞出三条：\"真中文\"、URL 里的「路径」（验字符串里的 // 不会把后文吞掉）、" +
                "以及含转义引号的那条；字符字面量 '中' 与两处注释都不算。实际捞出：$found",
            found.size == 3,
        )
        assertTrue("漏了 \"真中文\"", found.any { it == "真中文" })
        assertTrue("URL 被 // 截断了", found.any { it == "https://example.com/路径" })
        assertTrue("漏了含转义的那条", found.any { it.contains("后面的中文") })
    }
}
