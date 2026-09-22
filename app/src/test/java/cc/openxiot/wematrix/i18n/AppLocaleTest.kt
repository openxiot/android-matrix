package cc.openxiot.wematrix.i18n

import cc.openxiot.wematrix.APP_LANGUAGES
import cc.openxiot.wematrix.AppLocale
import cc.openxiot.wematrix.resolveAppLocale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * 「该给应用套哪个语言」的判断。
 *
 * ⚠️ **这一版与上一版是反的。** 上一版里「跟随系统」要显式判断系统语言，因为当时只有
 * 一份 `values-zh`，带隐式 `Hans`，`zh-Hant` 系的系统匹配不上就会掉到英文 —— 所以
 * `SYSTEM` 在中文系统上要返回 `SIMPLIFIED_CHINESE`。现在 `values-zh-rTW` / `-rHK` 是
 * 真的繁体译文了，让框架照系统 locale 自己解析才是对的；再强行把 `zh-TW` 折成简体，
 * 等于让那两份译文**永远选不中**。
 *
 * 那条「繁体不掉英文」的保证没有消失，只是**从 Kotlin 挪到了资源层**，由
 * `values-zh-rTW` / `-rHK` 两个目录的存在与内容兑现，由 `StringsParityTest` 的繁体守卫
 * 与 `tools/i18n/validate.py` 守着。本测试负责的是另一半：**确认我们没有再把方向搞反**。
 */
class AppLocaleTest {

    @Test
    fun `跟随系统一律不套 wrapper，交给框架按系统 locale 解析`() {
        // 与系统是什么语言无关：不套 wrapper 是唯一正确的做法。
        // 参数故意传了各种系统语言，结果都一样 —— 这是「不依赖系统语言」的证明。
        for (system in listOf("zh-CN", "zh-TW", "zh-HK", "en-US", "ja-JP", "de-DE", "ar-EG")) {
            assertNull(
                "系统 $system 时不该套 wrapper，否则会盖掉框架自己的解析",
                resolveAppLocale(AppLocale.SYSTEM),
            )
        }
    }

    @Test
    fun `选中的语言按 tag 原样解析，不折成别的语言`() {
        // ⚠️ 这里比的是 `forLanguageTag(tag)`，**不是** `Locale.SIMPLIFIED_CHINESE`。
        // 后者是 `zh-CN-#Hans`，带上了国家和脚本；而 `forLanguageTag("zh")` 是裸 `zh`。
        // 两者都能匹配 values-zh，但机制上我们要的是**后者**：规则统一成
        // 「tag 进来、同 tag 的 Locale 出去」，中文不享受特例 —— 上一版的
        // `zh → SIMPLIFIED_CHINESE` 特判正是把繁体译文变成死资源的那处。
        for (tag in listOf(AppLocale.ZH, AppLocale.EN, "zh-TW", "zh-HK", "ja", "de")) {
            assertEquals(
                "$tag 应当原样解析（不许折成别的语言）",
                Locale.forLanguageTag(tag),
                resolveAppLocale(tag),
            )
        }

        // 这两条是这次的核心：繁体必须保住地区，否则选不中 values-zh-rTW / -rHK，
        // 两份译文白做（上一版就是这样：繁体用户被硬折成简体）
        assertEquals("zh", resolveAppLocale("zh-TW")!!.language)
        assertEquals("TW", resolveAppLocale("zh-TW")!!.country)
        assertEquals("HK", resolveAppLocale("zh-HK")!!.country)
        // 简体不指定地区：将来若加 values-zh-rCN，裸 zh 也不至于被钉死在某个地区上
        assertEquals("", resolveAppLocale(AppLocale.ZH)!!.country)
    }

    @Test
    fun `三字母代码与地区限定符原样解析`() {
        // values-b+kmr / values-b+ckb 靠这一条；解析错了会静默掉到英文
        assertEquals(Locale.forLanguageTag("kmr"), resolveAppLocale("kmr"))
        assertEquals(Locale.forLanguageTag("ckb"), resolveAppLocale("ckb"))
        // be 用的是 BCP-47 的 be，不是 web 那边已废弃的 by
        assertEquals(Locale.forLanguageTag("be"), resolveAppLocale("be"))
        assertEquals("pt", resolveAppLocale("pt-BR")!!.language)
        assertEquals("BR", resolveAppLocale("pt-BR")!!.country)
    }

    @Test
    fun `语言表里每一种都能解析出非空 Locale`() {
        // 这条防的是「往 APP_LANGUAGES 里手滑写错一个 tag」：forLanguageTag 对良构性
        // 存疑的输入**静默**返回 Locale.ROOT（language 为空），于是那个语言在真机上
        // 表现成英文，且不报任何错。这里逐个钉住。
        for (lang in APP_LANGUAGES) {
            val locale = resolveAppLocale(lang.tag)
            assertNotNull("${lang.tag}（${lang.endonym}）解析成了 null", locale)
            assertTrue(
                "${lang.tag}（${lang.endonym}）解析出空 language，forLanguageTag 返回了 ROOT",
                locale!!.language.isNotEmpty(),
            )
        }
    }

    @Test
    fun `落盘白名单与语言表严格相等`() {
        // 白名单少一个 tag 的表现是「选了该语言、重进又变回跟随系统」，静默且难查
        assertTrue(AppLocale.SYSTEM in AppLocale.VALID)
        for (lang in APP_LANGUAGES) {
            assertTrue("${lang.tag} 不在落盘白名单里，选了也存不住", lang.tag in AppLocale.VALID)
        }
        assertEquals(
            "白名单里混进了不在语言表里的 tag",
            APP_LANGUAGES.size + 1,
            AppLocale.VALID.size,
        )
    }

    @Test
    fun `非法取值退化成跟随系统`() {
        // read() 已经会把非法值收敛成 SYSTEM，这里是第二道：万一有人绕过 read 直接传
        assertNull(resolveAppLocale(""))
        assertNull(resolveAppLocale("zh-Hant"))  // 纯脚本标签不在语言表里
        assertNull(resolveAppLocale("en-GB"))    // 有意不做，不在表里
        assertNull(resolveAppLocale("klingon"))
        assertNull(resolveAppLocale("../../etc"))
    }

    @Test
    fun `语言表本身没有重复 tag`() {
        val tags = APP_LANGUAGES.map { it.tag }
        assertEquals("语言表里有重复 tag，LazyColumn 的 key 重复会在运行时崩",
            tags.size, tags.toSet().size)
    }

    @Test
    fun `繁体与简体是三种各自独立的语言`() {
        // 上一版把三者当一种处理；现在它们是三份不同的资源目录
        val tags = setOf(AppLocale.ZH, "zh-TW", "zh-HK")
        assertEquals(3, tags.size)
        for (t in tags) {
            assertTrue("$t 不在语言表里，用户就选不到它", APP_LANGUAGES.any { it.tag == t })
        }
    }
}
