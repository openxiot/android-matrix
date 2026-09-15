package cc.openxiot.wematrix.ui.home

import java.time.LocalDate
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * 首页**唯一还伪造**的两张能耗卡（本月能耗数字 + 日能耗曲线）。
 *
 * 为什么它还是 mock：后端没有能耗采集 —— 这一条与 web 的 `dashboard.mock.ts` 完全一致
 * （删掉它需要先有电表数据，不是前端的活）。看板上其余每一个数字都已接 `GET /statistics/overview`。
 *
 * 与 web 端保持一致的造法：
 *   - 用 mulberry32 种子 PRNG，以「当天日期」为种子 → 同一天内刷新稳定，跨天自然变化
 *   - 工作日偏高、周末偏低，叠加日常波动
 *
 * 设备与告警的 mock 已经删掉（那份数现在来自真实接口）；`mulberry32` / `randInt` 留着，
 * 是因为能耗这条线还要它们。
 */

/** mulberry32 种子 PRNG：同一种子产生相同的伪随机序列，返回 [0, 1) */
private fun mulberry32(seed: Int): () -> Double {
    var a = seed.toLong() and 0xFFFFFFFFL
    return {
        a = (a + 0x6d2b79f5L) and 0xFFFFFFFFL
        var t = a xor (a ushr 15)
        t = imul(t, 1L or a)
        val x = t xor (t ushr 7)
        t = ((t + imul(x, 61L or t)) and 0xFFFFFFFFL) xor t
        ((t xor (t ushr 14)) and 0xFFFFFFFFL).toDouble() / 4294967296.0
    }
}

/** 32 位无符号乘法，等价于 JS 的 Math.imul */
private fun imul(a: Long, b: Long): Long = (a * b) and 0xFFFFFFFFL

/** 以日期派生种子：同一天内数据稳定，跨天自然变化 */
private fun seedOf(date: LocalDate): Int =
    date.year * 10000 + date.monthValue * 100 + date.dayOfMonth

private fun randInt(rand: () -> Double, min: Int, max: Int): Int =
    floor(rand() * (max - min + 1)).toInt() + min

data class DailyEnergy(val date: String, val value: Int)

data class EnergyStats(
    val monthTotal: Int,
    val daily: List<DailyEnergy>
)

/** 近 30 天（含今天）的日能耗 + 本月合计；同一天内稳定 */
fun mockEnergyStats(date: LocalDate): EnergyStats {
    val rand = mulberry32(seedOf(date) + 1)
    val daily = mutableListOf<DailyEnergy>()
    var monthTotal = 0
    for (i in 29 downTo 0) {
        val d = date.minusDays(i.toLong())
        // 工作日偏高、周末偏低，叠加日常波动
        val dow = d.dayOfWeek.value
        val base = if (dow == 6 || dow == 7) 62 else 78
        val value = (base + rand() * 42).roundToInt()
        daily.add(
            DailyEnergy(
                date = "%02d-%02d".format(d.monthValue, d.dayOfMonth),
                value = value
            )
        )
        monthTotal += value
    }
    return EnergyStats(monthTotal = monthTotal, daily = daily)
}
