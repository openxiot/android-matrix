package cc.openxiot.matrix.ui.home

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

    // ---- configWith(一次写多个键)：换服务要同时改服务/方法/字段，就得走这个 ----

    @Test
    fun configWith_一次写多个键_每个键都落在同一份结果里() {
        val config = mapOf<String, Any?>("serviceId" to "0x01", "fields" to listOf("a"))

        val next = DashboardTypes.configWith(
            config,
            "serviceId" to "0x02",
            "functionIndex" to 3,
            "fields" to null
        )

        assertEquals("0x02", next["serviceId"])
        assertEquals(3, next["functionIndex"])
        assertFalse("null 值照旧是删键", next.containsKey("fields"))
    }

    @Test
    fun configWith_一次写多个键_不碰原map_也不像连着写两次那样互相覆盖() {
        val config = mapOf<String, Any?>("source" to "serviceField")

        val next = DashboardTypes.configWith(config, "serviceId" to "0x01", "functionIndex" to 3)

        assertEquals(3, next.size) // 旧键 + 两个新键，两个新键都在
        assertEquals(1, config.size)
    }

    @Test
    fun configWith_一个键都不写就是原样() {
        val config = mapOf<String, Any?>("did" to "d1")

        assertEquals(config, DashboardTypes.configWith(config, *emptyArray<Pair<String, Any?>>()))
    }

    // ---- 尺寸档位：半宽开放给谁（后端 `MobileDashboardWidgetStructureValidator.HALF_TYPES` 的另一半） ----

    @Test
    fun sizeAllowed_整宽五类卡都能选() {
        DashboardTypes.ALL.forEach { type ->
            assertTrue(type, DashboardTypes.sizeAllowed(type, DashboardTypes.SIZE_FULL))
        }
    }

    @Test
    fun sizeAllowed_半宽只给统计卡与服务卡() {
        assertTrue(DashboardTypes.sizeAllowed(DashboardTypes.STAT, DashboardTypes.SIZE_HALF))
        assertTrue(DashboardTypes.sizeAllowed(DashboardTypes.SERVICE, DashboardTypes.SIZE_HALF))

        listOf(DashboardTypes.LINE, DashboardTypes.DISTRIBUTION, DashboardTypes.DEVICE).forEach { type ->
            assertFalse(type, DashboardTypes.sizeAllowed(type, DashboardTypes.SIZE_HALF))
        }
    }

    /** 默认档位与服务卡的关系：服务卡**可以**半宽，但加进来时仍是整宽（半宽是可选档，不是默认）。 */
    @Test
    fun defaultSize_只有统计卡默认半宽() {
        assertEquals(DashboardTypes.SIZE_HALF, DashboardTypes.defaultSize(DashboardTypes.STAT))
        assertEquals(DashboardTypes.SIZE_FULL, DashboardTypes.defaultSize(DashboardTypes.SERVICE))
        assertEquals(DashboardTypes.SIZE_FULL, DashboardTypes.defaultSize(DashboardTypes.LINE))
    }

    // ---- configBool：缺键 / 非布尔都走缺省（web `readBoolean` 同口径） ----

    @Test
    fun configBool_写了就用写的() {
        assertTrue(DashboardTypes.configBool(mapOf("showUnit" to true), "showUnit", false))
        assertFalse(DashboardTypes.configBool(mapOf("showUnit" to false), "showUnit", true))
    }

    @Test
    fun configBool_缺键取缺省() {
        assertTrue(DashboardTypes.configBool(emptyMap(), "showUnit", true))
        assertFalse(DashboardTypes.configBool(emptyMap(), "showUnit", false))
    }

    @Test
    fun configBool_非布尔值一律走缺省() {
        // 这三个正是 web spec 里钉的：'false' 字符串 / 0 / null 都不是 Boolean
        assertTrue(DashboardTypes.configBool(mapOf("showUnit" to "false"), "showUnit", true))
        assertTrue(DashboardTypes.configBool(mapOf("showUnit" to 0), "showUnit", true))
        assertTrue(DashboardTypes.configBool(mapOf("showUnit" to null), "showUnit", true))
    }

    // ---- configInt ----

    @Test
    fun configInt_数字能读_别的都是没设() {
        assertEquals(500, DashboardTypes.configInt(mapOf("maxPoints" to 500), "maxPoints"))
        assertEquals(24, DashboardTypes.configInt(mapOf("maxPoints" to 24L), "maxPoints"))
        assertEquals(5, DashboardTypes.configInt(mapOf("maxPoints" to 5.0), "maxPoints"))
        assertNull(DashboardTypes.configInt(mapOf("maxPoints" to "500"), "maxPoints"))
        assertNull(DashboardTypes.configInt(mapOf("maxPoints" to null), "maxPoints"))
        assertNull(DashboardTypes.configInt(emptyMap(), "maxPoints"))
    }
}
