package cc.openxiot.wematrix.i18n

import cc.openxiot.wematrix.AppLocale
import cc.openxiot.wematrix.resolveAppLocale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/**
 * 「该给应用套哪个语言」的判断。
 *
 * 这条最值得单独测的，是**繁体中文系统**那一组：`values-zh` 带隐式 `Hans` 脚本，
 * 匹配不了 `zh-Hant`，如果放任框架自己解析，zh-TW / zh-HK 会掉到默认的英文 ——
 * 需求要的是中文。这个 bug 只在繁体中文机上显形，写它的人（和跑测试的人）多半都
 * 不会用到那种系统，所以必须由测试来守。
 */
class AppLocaleTest {

    private fun locales(vararg tags: String) = tags.map(Locale::forLanguageTag)

    @Test
    fun `跟随系统——中文系统给简体中文`() {
        val zh = Locale.SIMPLIFIED_CHINESE
        assertEquals(zh, resolveAppLocale(AppLocale.SYSTEM, locales("zh")))
        assertEquals(zh, resolveAppLocale(AppLocale.SYSTEM, locales("zh-CN")))
        assertEquals(zh, resolveAppLocale(AppLocale.SYSTEM, locales("zh-Hans-CN")))
    }

    @Test
    fun `跟随系统——繁体中文系统也给中文，不能掉到英文`() {
        // 这四条是这次真机打包才发现的：aapt2 给 values-zh 隐式打了 Hans 标记，
        // 于是 zh-Hant 系落不到 values-zh，直接落到 values/（英文）
        val zh = Locale.SIMPLIFIED_CHINESE
        assertEquals(zh, resolveAppLocale(AppLocale.SYSTEM, locales("zh-TW")))
        assertEquals(zh, resolveAppLocale(AppLocale.SYSTEM, locales("zh-HK")))
        assertEquals(zh, resolveAppLocale(AppLocale.SYSTEM, locales("zh-MO")))
        assertEquals(zh, resolveAppLocale(AppLocale.SYSTEM, locales("zh-Hant-TW")))
    }

    @Test
    fun `跟随系统——非中文系统原样返回，落到 values 的英文`() {
        assertNull(resolveAppLocale(AppLocale.SYSTEM, locales("en-US")))
        assertNull(resolveAppLocale(AppLocale.SYSTEM, locales("ja-JP")))
        assertNull(resolveAppLocale(AppLocale.SYSTEM, locales("de-DE")))
        // 边缘：系统语言列表为空（极罕见）时别乱套，宁可跟系统
        assertNull(resolveAppLocale(AppLocale.SYSTEM, emptyList()))
    }

    @Test
    fun `跟随系统看的是首选语言，不是整个列表`() {
        // 中文排在第二位不算「中文系统」——否则英文系统上装了中文输入法的用户会莫名变中文
        assertNull(resolveAppLocale(AppLocale.SYSTEM, locales("en-US", "zh-CN")))
        assertEquals(
            Locale.SIMPLIFIED_CHINESE,
            resolveAppLocale(AppLocale.SYSTEM, locales("zh-CN", "en-US")),
        )
    }

    @Test
    fun `显式选的语言压过系统语言`() {
        assertEquals(
            Locale.SIMPLIFIED_CHINESE,
            resolveAppLocale(AppLocale.ZH, locales("en-US")),
        )
        assertEquals(Locale.ENGLISH, resolveAppLocale(AppLocale.EN, locales("zh-CN")))
    }

    @Test
    fun `非法取值退化成跟随系统`() {
        // read() 已经会把非法值收敛成 SYSTEM，这里是第二道：万一有人绕过 read 直接传
        assertEquals(
            Locale.SIMPLIFIED_CHINESE,
            resolveAppLocale("zh-Hant", locales("zh-TW")),
        )
        assertNull(resolveAppLocale("", locales("en-US")))
    }
}
