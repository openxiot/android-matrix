package cc.openxiot.wematrix.ui.modbus

import cc.openxiot.wematrix.data.api.ModbusCommand
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * [buildRequestFrame] / [buildResponseFrame] 与 [PointOptions] 数量口径的**逐字节**回归。
 *
 * **为什么值得单独一个测试**：这是一段从 webapp-matrix 的 `request.frame.ts` +
 * `point.options.ts` 跨语言抄过来的位运算。抄错了不会编译报错、也不会运行报错 ——
 * 只会静静地生成一帧发给现场设备。故这里拿**跑出来的**期望值钉死它。
 *
 * **期望值的来源（`app/src/test/resources/frames.reference.json`）**：不是手写的，
 * 是把 webapp-matrix 那两个文件用 vitest 跑一遍导出来的 —— 手写期望值等于把手抄两遍，
 * 错了还会互相印证。要改这份 fixture，先改 web 的实现，再照下面重新导出：
 *
 * ```
 * # 在 webapp-matrix 里新建 /tmp/framecheck/（工程内不落任何文件）：
 * #   frames.spec.ts  按绝对路径 import request.frame / point.options，
 * #                   逐条 build 后 writeFileSync 出 reference.json
 * #   commands.json   用例表（命令定义 + 从站地址）
 * cd webapp-matrix && npx vitest run --root /tmp/framecheck
 * ```
 *
 * **已知的有意偏离**：web 的 `<<` 是 32 位**有符号**的，4 字节全 1 会变成 -1，
 * 于是 uint32/float32 在 web 上显示成 `0x-1`。本端用 Long，给的是正确的 `0xFFFFFFFF`。
 * 这只影响 `describeRequestFrame` 的解读文字，**不影响任何一帧的字节** ——
 * fixture 里 `06_write_reg_uint32_pattern` 与 `10_write_float32_pattern` 两例把这条钉住了
 * （帧必须与 web 逐字节相同，解读文字才允许不一样）。
 */
@RunWith(Parameterized::class)
class RequestFrameTest(private val case: FrameCase) {

    @Test
    fun requestFrame() {
        val frame = buildRequestFrame(case.command, case.slaveId)
        if (case.expected.request == null) {
            // 数据不完整：web 给 {ok:false}，本端给 null（见 RequestFrame 的返回口径）
            assertIncomplete(frame == null, "请求帧")
            return
        }
        assertIncomplete(frame != null, "请求帧")
        assertEquals("请求帧 ${frame!!.hex}", case.expected.request, frame.hex)
        assertEquals("请求帧字节个数", case.expected.request.split(" ").size, frame.count)
    }

    @Test
    fun responseFrames() {
        val preview = buildResponseFrame(case.command, case.slaveId)
        if (case.expected.response == null) {
            assertIncomplete(preview == null, "应答帧")
            return
        }
        assertIncomplete(preview != null, "应答帧")
        assertEquals("应答帧", case.expected.response, preview!!.frame.hex)
        assertEquals("异常应答帧", case.expected.exception, preview.exception.hex)
    }

    @Test
    fun quantityConvention() {
        assertEquals(
            "帧里的数量",
            case.expected.frameQuantity,
            frameQuantityOf(case.command.fc, case.command.quantity, case.command.dataType)
        )
        assertEquals(
            "位名称个数",
            case.expected.bitCount,
            expectedBitCount(case.command.fc, case.command.quantity)
        )
        assertEquals(
            "应答字段个数",
            case.expected.fieldCount,
            expectedFieldCount(case.command.fc, case.command.quantity, case.command.dataType)
        )
    }

    /** 页面上「位名称」那几行：偏移用字符串比，免得 Gson 把 JSON 数字解成 Double 再比 */
    @Test
    fun bitNameRows() {
        assertEquals(
            "位名称行",
            case.expected.bitNames,
            bitNameRows(case.command).map { (offset, name) -> listOf(offset.toString(), name) }
        )
    }

    /** 页面上「应答字段名」那几行 */
    @Test
    fun fieldNameRows() {
        assertEquals("应答字段名行", case.expected.fieldNames, fieldNameRows(case.command))
    }

    private fun assertIncomplete(blank: Boolean, what: String) {
        assertEquals(
            case.name + "：这条命令缺必要字段（如 0F 没给线圈），$what 应当生成不了",
            true,
            blank
        )
    }

    override fun toString(): String = case.name

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<FrameCase> {
            val json = RequestFrameTest::class.java.getResourceAsStream("/frames.reference.json")
                ?: error("找不到 frames.reference.json —— 测试资源没打进 classpath")
            val type = object : TypeToken<List<FrameCase>>() {}.type
            return json.use { Gson().fromJson(it.reader(), type) }
        }
    }
}

/**
 * 一条用例：命令定义 + 从站地址 + 期望结果。
 *
 * 命令与期望值放同一个对象里（而不是两份文件按下标对齐）—— 拆开的话加减用例时会错位，
 * 而错位的表现恰好是「测试通过但比错了东西」。
 */
data class FrameCase(
    val name: String,
    val slaveId: Int? = null,
    val command: ModbusCommand,
    val expected: Expected
) {
    override fun toString(): String = name
}

data class Expected(
    val request: String? = null,
    val response: String? = null,
    val exception: String? = null,
    val frameQuantity: Int = 0,
    val bitCount: Int = 0,
    val fieldCount: Int = 0,
    /** `[[偏移, 位名], …]`，偏移在 fixture 里就是字符串 */
    val bitNames: List<List<String>> = emptyList(),
    val fieldNames: List<String> = emptyList()
)
