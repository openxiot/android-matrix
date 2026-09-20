package cc.openxiot.wematrix.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DashboardTypes.configWith]：config 的改写口径 —— **null 是删键**，不是写一个值为 null 的项。
 *
 * 这条口径是后端校验器的硬要求（`booleanErr` 按 `containsKey` 判、`limit` 同样），
 * 也是 web 那边「没配 = 键不存在」的同构写法，所以钉在单测里。
 */
class DashboardTypesTest {

    @Test
    fun configWith_writesTheKeyWhenValueIsPresent() {
        val config = mapOf<String, Any?>("source" to "alarmCount")

        val next = DashboardTypes.configWith(config, "maxPoints", 500)

        assertEquals(500, next["maxPoints"])
        assertEquals("alarmCount", next["source"])
    }

    @Test
    fun configWith_removesTheKeyWhenValueIsNull() {
        val config = mapOf<String, Any?>("serviceId" to "0x01", "field" to "temp")

        val next = DashboardTypes.configWith(config, "field", null)

        assertFalse("键要真的不在，而不是值等于 null", next.containsKey("field"))
        assertEquals("0x01", next["serviceId"])
    }

    @Test
    fun configWith_removingAMissingKeyIsANoOp() {
        val config = mapOf<String, Any?>("did" to "d1")

        val next = DashboardTypes.configWith(config, "pid", null)

        assertEquals(mapOf<String, Any?>("did" to "d1"), next)
    }

    @Test
    fun configWith_doesNotMutateTheOriginal() {
        val config = mapOf<String, Any?>("did" to "d1", "pid" to "d1.1.1")

        DashboardTypes.configWith(config, "pid", null)
        DashboardTypes.configWith(config, "showUnit", false)

        assertEquals(2, config.size)
        assertEquals("d1.1.1", config["pid"])
        assertNull(config["showUnit"])
    }

    @Test
    fun configWith_writingNullOverANonNullValueRemovesIt() {
        val config = mapOf<String, Any?>("showUnit" to false)

        val next = DashboardTypes.configWith(config, "showUnit", null)

        assertTrue(next.isEmpty())
    }
}
