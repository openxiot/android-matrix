package cc.openxiot.wematrix.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * `values/`（英文，默认）与 `values-zh/`（中文）两份资源的对账。
 *
 * 这个测试存在的理由：427 条文案靠人眼盯「有没有漏翻」是不现实的，而漏翻的表现很隐蔽
 * —— 多数是英文界面上冒出一句中文，只有跑到那一屏才看得见。这里把它变成构建期错误。
 *
 * 顺带覆盖一类会**运行时崩溃**的错误：中英两边的占位符个数不一致（`%1$s` 少了一个），
 * `getString` 会抛 `MissingFormatArgumentException`，同样是跑到那一屏才发现。
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
    private val zhDir = File(resDir, "values-zh")

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
                // 缺省是 true；translatable="false" 的条目两种语言共用一份，不参与对账
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

    /** 占位符的多重集：`%1$s` / `%2$d` / `%s` / `%d`。`%%`（字面百分号）不匹配，正确。 */
    private fun placeholders(value: String): List<String> =
        Regex("""%(\d+\$)?[sd]""").findAll(value).map { it.value }.sorted().toList()

    private val cjk = Regex("[\\u4e00-\\u9fff]")

    /**
     * 允许中英完全相同的文案：专有名词、缩写、品牌。
     * 往这里加东西应当是一次**有意识**的决定 —— 它是「到底翻没翻」的唯一防线。
     */
    private val allowIdentical = setOf(
        "Modbus", "OAuth", "APK", "OK", "IP", "IMEI", "CRC", "MQTT", "JSON", "HTTP",
        "DTU", "Android", "WeMatrix", "Wi-Fi", "Build", "ID",
    )

    /**
     * 一个值里**没有可翻译的东西**：去掉占位符后，连一个字母都没有。
     *
     * 例：`%1$s: %2$s`（服务名 + 冒号 + 一条错误）。它由标点和两个参数组成，没有词可翻，
     * 两种语言必然逐字相同。这和 [allowIdentical] 是两件事：那边是「有词，但本来就该
     * 保持原文」（Modbus、OAuth 这类专有名词），这边干脆没有词。
     *
     * 不放进 [allowIdentical]：那是个**值**的白名单，而这里是一条**规则** —— 将来再冒出
     * 一条纯排版串（`%1$s · %2$s` 之类）不必再来加一次。
     */
    private fun hasNothingToTranslate(value: String): Boolean =
        value.replace(Regex("""%(\d+\$)?[sd]"""), "").none { it.isLetter() }

    // ---------- 断言 ----------

    @Test
    fun `两个目录的文件成对`() {
        val en = stringFiles(defaultDir).map { it.name }
        val zh = stringFiles(zhDir).map { it.name }
        assertEquals("values-zh/ 与 values/ 的 strings 文件必须一一对应", en, zh)
    }

    @Test
    fun `默认语言 values 里不出现中文`() {
        stringFiles(defaultDir).forEach { file ->
            parse(file).strings.values.filter { it.translatable }.forEach { e ->
                assertTrue(
                    "${file.name} 的 ${e.name} 含中文：'${e.value}' —— " +
                        "values/ 是默认资源（非中文系统落到这里），必须是英文。" +
                        "中文请放 values-zh/；确实该两种语言共用的加 translatable=\"false\"",
                    !cjk.containsMatchIn(e.value),
                )
            }
        }
    }

    @Test
    fun `key 集合一致`() {
        stringFiles(defaultDir).forEach { file ->
            val en = parse(file)
            val zh = parse(File(zhDir, file.name))

            val enKeys = en.strings.values.filter { it.translatable }.map { it.name }.toSet()
            val zhKeys = zh.strings.keys
            assertEquals("${file.name}: values-zh/ 里缺或多这些 key", enKeys, zhKeys)

            assertEquals(
                "${file.name}: plurals 的 key 集合不一致",
                en.plurals.keys, zh.plurals.keys,
            )
        }
    }

    @Test
    fun `占位符个数一致`() {
        stringFiles(defaultDir).forEach { file ->
            val en = parse(file)
            val zh = parse(File(zhDir, file.name))

            en.strings.values.filter { it.translatable }.forEach { e ->
                val zhValue = zh.strings[e.name]?.value ?: return@forEach
                assertEquals(
                    "${file.name}: ${e.name} 的中英占位符不一致（会 MissingFormatArgumentException）\n" +
                        "  英文: ${e.value}\n  中文: $zhValue",
                    placeholders(e.value), placeholders(zhValue),
                )
            }

            en.plurals.forEach { (name, enItems) ->
                val zhItems = zh.plurals[name] ?: return@forEach
                enItems.forEach { (q, enValue) ->
                    val zhValue = zhItems[q] ?: return@forEach
                    assertEquals(
                        "${file.name}: plurals/$name[$q] 的中英占位符不一致",
                        placeholders(enValue), placeholders(zhValue),
                    )
                }
            }
        }
    }

    @Test
    fun `复数档位齐备`() {
        stringFiles(defaultDir).forEach { file ->
            val en = parse(file)
            val zh = parse(File(zhDir, file.name))

            en.plurals.forEach { (name, enItems) ->
                val zhItems = zh.plurals[name] ?: return@forEach
                assertTrue(
                    "${file.name}: plurals/$name 英文缺 other 档",
                    enItems.containsKey("other"),
                )
                assertTrue(
                    "${file.name}: plurals/$name 英文缺 one 档 —— " +
                        "「1 coil」和「2 coils」在英文里必须分开写（中文只需 other 一档）",
                    enItems.containsKey("one"),
                )
                assertTrue(
                    "${file.name}: plurals/$name 中文缺 other 档（CLDR：zh 无 one，只需 other）",
                    zhItems.containsKey("other"),
                )
            }
        }
    }

    @Test
    fun `中文确实翻了`() {
        val identical = mutableListOf<String>()
        stringFiles(defaultDir).forEach { file ->
            val en = parse(file)
            val zh = parse(File(zhDir, file.name))
            en.strings.values.filter { it.translatable }.forEach { e ->
                val zhValue = zh.strings[e.name]?.value ?: return@forEach
                if (zhValue == e.value && e.value !in allowIdentical &&
                    !hasNothingToTranslate(e.value)
                ) {
                    identical += "${file.name}: ${e.name} = '${e.value}'"
                }
            }
        }
        assertTrue(
            "这些 key 的中英文案逐字相同，像是漏翻了。确实该相同的，加进 allowIdentical：\n" +
                identical.joinToString("\n"),
            identical.isEmpty(),
        )
    }
}
