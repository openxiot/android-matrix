package cc.openxiot.matrix.i18n

import cc.openxiot.matrix.APP_LANGUAGES
import cc.openxiot.matrix.appLanguage
import cc.openxiot.matrix.ui.profile.filterLanguages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * 语言表与搜索。
 *
 * 这些断言能写成纯 JVM 测试，正是「语言表放 Kotlin 数据表、不放 132 条资源」的收益 ——
 * 走资源就得引 Robolectric 才能在单测里读到 `Resources`。
 */
class AppLanguagesTest {

    @Test
    fun `表里没有空字段`() {
        for (lang in APP_LANGUAGES) {
            assertTrue("${lang.tag} 的 tag 为空", lang.tag.isNotBlank())
            assertTrue("${lang.tag} 没有自称，列表里会是空白行", lang.endonym.isNotBlank())
            assertTrue("${lang.tag} 没有英文名，搜英文就找不到它", lang.englishName.isNotBlank())
        }
    }

    @Test
    fun `自称与英文名都不重复`() {
        // 重复的表现是列表里出现两行一模一样的字，用户不知道点哪个。
        // tag 的唯一性由 AppLocaleTest 管，这里管另外两个可见字段。
        for (field in listOf("自称" to { l: cc.openxiot.matrix.AppLanguage -> l.endonym },
                             "英文名" to { l: cc.openxiot.matrix.AppLanguage -> l.englishName })) {
            val values = APP_LANGUAGES.map(field.second)
            val dup = values.groupingBy { it }.eachCount().filterValues { it > 1 }
            assertTrue("${field.first}有重复：$dup", dup.isEmpty())
        }
    }

    @Test
    fun `空搜索返回全部`() {
        assertEquals(APP_LANGUAGES.size, filterLanguages("").size)
        assertEquals(APP_LANGUAGES.size, filterLanguages("   ").size)
    }

    @Test
    fun `按自称搜`() {
        assertEquals(listOf("ja"), filterLanguages("日本語").map { it.tag })
        assertEquals(listOf("de"), filterLanguages("Deutsch").map { it.tag })
    }

    @Test
    fun `按英文名搜，不区分大小写`() {
        assertEquals(listOf("ja"), filterLanguages("Japanese").map { it.tag })
        assertEquals(listOf("de"), filterLanguages("german").map { it.tag })
        assertEquals(listOf("de"), filterLanguages("GERMAN").map { it.tag })
    }

    @Test
    fun `按 tag 搜`() {
        // 开发/测试时按代码里的写法搜，是最省事的一路
        assertEquals(listOf("zh-TW"), filterLanguages("zh-TW").map { it.tag })
        assertTrue("搜 pt 应同时命中 pt 与 pt-BR", filterLanguages("pt").map { it.tag }.containsAll(listOf("pt", "pt-BR")))
    }

    @Test
    fun `中文输入靠自称子串命中`() {
        // 「日本」命中 日本語 —— 是自称的子串，不是我们有中文名表。
        // 写这条是为了把这个**偶然性质**钉住：哪天有人改了 ja 的自称，这里会提醒他
        // 「中文用户搜日本就找不到了」，而不是静默退化。
        assertEquals(listOf("ja"), filterLanguages("日本").map { it.tag })
        // 汉字圈之外的中文输入命中不了，这是有意的（没有中文名表）
        assertTrue("「德国」不该命中 —— 我们没有中文名表", filterLanguages("德国").isEmpty())
    }

    @Test
    fun `搜不到时返回空而不是全部`() {
        // 返回空才会显示「没有匹配的语言」；返回全部的话用户会以为搜索框坏了
        assertTrue(filterLanguages("klingon").isEmpty())
        assertTrue(filterLanguages("zzz").isEmpty())
    }

    @Test
    fun `按 tag 查表`() {
        assertEquals("日本語", appLanguage("ja")?.endonym)
        assertEquals("中文(繁體)", appLanguage("zh-TW")?.endonym)
        assertNull("SYSTEM 不是语言，查不到", appLanguage("system"))
        assertNull(appLanguage("en-GB"))  // 有意不做
        assertNull(appLanguage(""))
    }

    @Test
    fun `RTL 标记与自称的书写方向一致`() {
        // isRtl 是 Batch 4 做 RTL 布局复审时的**清单来源**，标错了就会漏审一个语言
        // （表现是阿拉伯语下拖拽换位错乱）。这里用一条**独立**信号交叉验证：
        // 语言的自称必然用它自己的文字书写，所以首字符的 Unicode 方向就是该语言的方向。
        //
        // 这不是 ICU 的答案（ICU 还要看 likely-subtags），但两者不一致时一定是
        // 有一边错了，值得当场炸出来而不是等真机。
        for (lang in APP_LANGUAGES) {
            val direction = Character.getDirectionality(lang.endonym.codePointAt(0))
            val looksRtl = direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT ||
                direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
            assertEquals(
                "${lang.tag}（${lang.endonym}）标了 isRtl=${lang.isRtl}，" +
                    "但自称的首字符方向看起来是 ${if (looksRtl) "RTL" else "LTR"}",
                looksRtl,
                lang.isRtl,
            )
        }
    }

    @Test
    fun `五种 RTL 语言都在表里`() {
        val rtl = APP_LANGUAGES.filter { it.isRtl }.map { it.tag }.toSet()
        assertEquals(setOf("ar", "he", "fa", "ur", "ckb"), rtl)
        // 库尔德语两文只有索拉尼是 RTL，库尔曼吉用拉丁字母
        assertTrue(appLanguage("kmr")!!.isRtl.not())
    }

    @Test
    fun `表里包含简繁英三种中文与英文`() {
        for (tag in listOf("en", "zh", "zh-TW", "zh-HK")) {
            assertNotNull("$tag 必须在可选表里", appLanguage(tag))
        }
        // 且它们的 tag 能被 Locale 解析（解析不了就是选不中的死条目）
        for (tag in listOf("en", "zh", "zh-TW", "zh-HK")) {
            assertTrue(Locale.forLanguageTag(tag).language.isNotEmpty())
        }
    }
}
