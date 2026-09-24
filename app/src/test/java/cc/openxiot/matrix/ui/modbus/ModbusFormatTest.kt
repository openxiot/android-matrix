package cc.openxiot.matrix.ui.modbus

import cc.openxiot.matrix.data.api.ModbusServiceFieldAlarm
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 数值文案的两条口径，以及它们的分界。
 *
 * 「读数最多 2 位、配置值原样」是一条**产品口径**：历史页、告警页、看板卡片、调用应答全走
 * [numberText] / [valueText]，改一位小数就是改所有页面的长相 —— 所以钉在这里。另一半同样要钉：
 * 告警阈值、缩放倍数**不许**跟着收 2 位（20.125 被说成 20.13 就是「我配的数被改了」）。
 *
 * 用例对着 web `ValueUtils.spec.ts` 抄：那边是 `Number(value.toFixed(2))`、这边是
 * `Math.round(value * 100) / 100`，两种写法在边界上必须落在一个点上。
 */
class ModbusFormatTest {

    // ---- numberText：读数，最多 2 位 ----

    @Test
    fun numberText_整数不带小数点() {
        assertEquals("0", numberText(0.0))
        assertEquals("23", numberText(23.0))
        assertEquals("-7", numberText(-7.0))
    }

    @Test
    fun numberText_浮点最多留2位() {
        assertEquals("23.46", numberText(23.4567890))
        assertEquals("2.67", numberText(2.675))
        assertEquals("-2.67", numberText(-2.675))
        assertEquals("0.01", numberText(0.005))
    }

    /** 「最多」是字面意思：够 2 位就不动，不够 2 位也不补零。 */
    @Test
    fun numberText_不够2位不补零() {
        assertEquals("23.5", numberText(23.5))
        assertEquals("0.3", numberText(0.3))
    }

    @Test
    fun numberText_收掉浮点误差_收完是整数也不留点零() {
        assertEquals("0.3", numberText(0.1 + 0.2))
        assertEquals("1", numberText(1.0000000000000002))
        // 1.005 收 2 位正好落在 1 上：web 的 `Number("1.00")` 也是 1
        assertEquals("1", numberText(1.005))
    }

    /** 小于 0.005 的读数会落成 0 —— 「留 2 位」的题中之义，不是算错了。 */
    @Test
    fun numberText_小于半个最小位的读数落成0() {
        assertEquals("0", numberText(0.004))
        assertEquals("0", numberText(-0.001))
    }

    /** 特殊值原样交出去，别在这里编一个数出来。 */
    @Test
    fun numberText_NaN与无穷原样() {
        assertEquals("NaN", numberText(Double.NaN))
        assertEquals("Infinity", numberText(Double.POSITIVE_INFINITY))
    }

    // ---- configNumberText：配置值，原样（只压 Gson 的 `.0` 与浮点噪声） ----

    @Test
    fun configNumberText_阈值与缩放不被收成2位() {
        assertEquals("20.125", configNumberText(20.125))
        assertEquals("0.125", configNumberText(0.125))
    }

    @Test
    fun configNumberText_整数不带小数点() {
        // Gson 把 JSON 数字都解成 Double：80 会变成 80.0，而 web 显示 80
        assertEquals("80", configNumberText(80.0))
        assertEquals("1", configNumberText(1.0))
    }

    // ---- valueText：按运行时类型分派 ----

    @Test
    fun valueText_没值说横杠_0说0() {
        assertEquals("-", valueText(null))
        assertEquals("0", valueText(0))
    }

    @Test
    fun valueText_数值走numberText_非数值原样() {
        assertEquals("23.46", valueText(23.4567890))
        assertEquals("23.5", valueText(23.5))
        // 取值表的描述是数据、不翻译
        assertEquals("制冷", valueText("制冷"))
    }

    // ---- formatInvokeValue：Gson 的数值归一 ----

    @Test
    fun formatInvokeValue_整数Double不带点零_浮点最多2位() {
        assertEquals("1", formatInvokeValue(1.0))
        assertEquals("23.46", formatInvokeValue(23.4567890))
    }

    /** Float 先抬成 Double 再收：float32 读数在两端必须是同一个串。 */
    @Test
    fun formatInvokeValue_float32读数按2位说() {
        assertEquals("23.46", formatInvokeValue(23.456789f))
        assertEquals("1", formatInvokeValue(1.0f))
    }

    @Test
    fun formatInvokeValue_空值与非数值() {
        assertEquals("-", formatInvokeValue(null))
        assertEquals("制冷", formatInvokeValue("制冷"))
        assertEquals("true", formatInvokeValue(true))
    }

    // ---- alarmRuleBrief：阈值走配置值口径（回归钉子） ----

    @Test
    fun alarmRuleBrief_阈值原样_不收2位() {
        val alarm = ModbusServiceFieldAlarm(
            compare = ">",
            threshold = 20.125,
            text = "温度过高"
        )

        assertEquals("温度过高(>20.125)", alarmRuleBrief(alarm))
    }
}
