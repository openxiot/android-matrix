package cc.openxiot.matrix.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 版本比对的边界钉子。
 *
 * 这里钉的不是「能比大小」——那是当然的——而是**比错的那两条路**：字符串比（`1.0.10` 会被
 * 判成旧于 `1.0.9`）与 Double 比（`1.0.10` 与 `1.0.1` 会撞成一个数）。两条都会让用户
 * **漏掉升级**，而且症状是「点了版本更新说已是最新」，从界面上完全看不出错。
 *
 * 另一半是「不知道」必须说不知道：清单里的版本号是手写的，写歪一个字符就解析不出来，
 * 这时要返回 null 让上层报错，**不能**顺着当成「比本机旧」——那等于把一次发版吃掉。
 */
class VersionCompareTest {

    // ---- parse ----

    @Test
    fun parse_点分数字与前缀() {
        assertEquals(listOf(1, 0, 5), VersionCompare.parse("1.0.5"))
        assertEquals(listOf(1, 0, 5), VersionCompare.parse("v1.0.5"))
        assertEquals(listOf(1, 0, 5), VersionCompare.parse("V1.0.5"))
        assertEquals(listOf(1, 0, 5), VersionCompare.parse("  1.0.5  "))
        assertEquals(listOf(0, 0, 0), VersionCompare.parse("0.0.0"))
    }

    /** 段数不固定：`1.0` 与 `1.0.0` 是同一个版本，短的一侧在 compare 里补 0。 */
    @Test
    fun parse_段数可多可少() {
        assertEquals(listOf(1), VersionCompare.parse("1"))
        assertEquals(listOf(1, 0), VersionCompare.parse("1.0"))
        assertEquals(listOf(1, 0, 5, 1), VersionCompare.parse("1.0.5.1"))
    }

    @Test
    fun parse_空与纯前缀() {
        assertNull(VersionCompare.parse(null))
        assertNull(VersionCompare.parse(""))
        assertNull(VersionCompare.parse("   "))
        assertNull(VersionCompare.parse("v"))
        assertNull(VersionCompare.parse("V"))
    }

    /** 预发布标记、非数字段、空段、分隔符写错 —— 都不猜，直接判不可识别。 */
    @Test
    fun parse_畸形式一律null() {
        assertNull(VersionCompare.parse("1.0.x"))
        assertNull(VersionCompare.parse("1.0.5-rc1"))
        assertNull(VersionCompare.parse("1.0.5+build7"))
        assertNull(VersionCompare.parse("1..0"))
        assertNull(VersionCompare.parse(".1"))
        assertNull(VersionCompare.parse("1."))
        assertNull(VersionCompare.parse("1,0,5"))
        assertNull(VersionCompare.parse("矩阵1.0.5"))
        assertNull(VersionCompare.parse("-1.0.5"))
        assertNull(VersionCompare.parse("+1.0.5"))
        // 段**内部**的空格不容忍：只容忍首尾（见 parse_点分数字与前缀 里的 "  1.0.5  "）
        assertNull(VersionCompare.parse(" 1 . 0 "))
        assertNull(VersionCompare.parse("1. 0"))
    }

    /**
     * 前导零按不可识别处理（`1.05` 数值上等于 `1.5`，但按 versionCode 公式差 500）。
     * 这一条同时护着 CI 那条公式，改动前先看 [VersionCompare] 的注释。
     */
    @Test
    fun parse_前导零判不可识别() {
        assertNull(VersionCompare.parse("1.05"))
        assertNull(VersionCompare.parse("1.0.05"))
        assertNull(VersionCompare.parse("01.0.5"))
        // 单独的 0 段是合法的（1.0.5 里就有），别把这条规则扩大化
        assertEquals(listOf(1, 0, 5), VersionCompare.parse("1.0.5"))
    }

    /** 段长到装不下 Int 就当不可识别，别让它溢出成一个负数继续参与比较。 */
    @Test
    fun parse_段超出Int判不可识别() {
        assertNull(VersionCompare.parse("99999999999.0.0"))
        assertNull(VersionCompare.parse("2147483648.0.0"))
        // 刚好装得下
        assertEquals(listOf(2147483647, 0, 0), VersionCompare.parse("2147483647.0.0"))
    }

    // ---- compare ----

    @Test
    fun compare_更新更旧相同() {
        assertNewer("1.0.6", "1.0.5")
        assertOlder("1.0.4", "1.0.5")
        assertSame("1.0.5", "1.0.5")
    }

    /**
     * 字符串比会在这里翻车：`"1.0.10" < "1.0.9"`，而 1.0.10 才是新的。
     * 这条挂了说明有大版本号被静默吃掉。
     */
    @Test
    fun compare_两位段按数值比不按字符串比() {
        assertNewer("1.0.10", "1.0.9")
        assertOlder("1.0.9", "1.0.10")
        assertNewer("1.10.0", "1.9.0")
        assertNewer("10.0.0", "9.99.99")
    }

    /**
     * Double 比会在这里翻车：`1.0.10` 与 `1.0.1` 都会变成 `1.01`，冲突破解成「不是更新」。
     */
    @Test
    fun compare_段位数不同不塌成一个数() {
        assertNewer("1.0.10", "1.0.1")
        assertNewer("1.1.0", "1.0.10")
    }

    @Test
    fun compare_段数不同按缺位补零() {
        assertSame("1.0", "1.0.0")
        assertSame("1.0.0", "1.0")
        assertSame("1", "1.0.0")
        assertNewer("1.0.1", "1.0")
        assertOlder("1.0", "1.0.1")
        assertNewer("1.1", "1.0.9")
    }

    @Test
    fun compare_v前缀两侧都认() {
        assertNewer("v1.0.6", "1.0.5")
        assertNewer("1.0.6", "v1.0.5")
        assertNewer("v1.0.6", "v1.0.5")
        assertSame("v1.0.5", "1.0.5")
    }

    /**
     * 关键的「不知道」：任一侧解析不出来就返回 null，**不是 0 也不是负数**。
     * 返回负数会让上层把一次真实发版当成「本机更新」，返回 0 会当成「已是最新」，
     * 两种都是静默吃掉一个版本。
     */
    @Test
    fun compare_任一侧不可识别返回null() {
        assertNull(VersionCompare.compare(null, "1.0.5"))
        assertNull(VersionCompare.compare("1.0.6", null))
        assertNull(VersionCompare.compare("", "1.0.5"))
        assertNull(VersionCompare.compare("1.0.x", "1.0.5"))
        assertNull(VersionCompare.compare("1.0.6", "1.0.5-rc1"))
        assertNull(VersionCompare.compare("最新版", "1.0.5"))
        assertNull(VersionCompare.compare(null, null))
    }

    private fun assertNewer(remote: String, local: String) {
        val result = VersionCompare.compare(remote, local)
        assertTrue("$remote 应新于 $local，实际 $result", result != null && result > 0)
    }

    private fun assertOlder(remote: String, local: String) {
        val result = VersionCompare.compare(remote, local)
        assertTrue("$remote 应旧于 $local，实际 $result", result != null && result < 0)
    }

    private fun assertSame(remote: String, local: String) {
        assertEquals("$remote 与 $local 应等值", 0, VersionCompare.compare(remote, local))
    }
}
