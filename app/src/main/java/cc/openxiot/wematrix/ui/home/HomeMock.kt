package cc.openxiot.wematrix.ui.home

import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * 首页数据看板 mock 数据。
 *
 * 与 web 端 webapp-matrix/src/app/pages/dashboard/dashboard.mock.ts 保持一致：
 *   - 用 mulberry32 种子 PRNG，以「当天日期」为种子 → 同一天内刷新稳定，跨天自然变化
 *   - 类型名直接产出中文短语
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

data class DeviceTypeStat(val type: String, val count: Int)

data class DeviceStats(
    val total: Int,
    val online: Int,
    val offline: Int,
    val byType: List<DeviceTypeStat>
)

data class DailyEnergy(val date: String, val value: Int)

data class EnergyStats(
    val monthTotal: Int,
    val daily: List<DailyEnergy>
)

data class AlarmTypeStat(val type: String, val count: Int)

data class AlarmPoint(val time: String, val count: Int)

data class AlarmStats(
    val todayCount: Int,
    val byType: List<AlarmTypeStat>,
    val curve: List<AlarmPoint>
)

private val DEVICE_TYPES = listOf("智能网关", "温湿度传感器", "智能插座", "智能门锁", "网络摄像头", "烟感传感器")
private val ALARM_TYPES = listOf("高温报警", "烟雾报警", "非法闯入", "电量过低", "设备离线", "门未关闭")

fun mockDeviceStats(date: LocalDate): DeviceStats {
    val rand = mulberry32(seedOf(date))
    val byType = DEVICE_TYPES.map { DeviceTypeStat(it, randInt(rand, 12, 42)) }
    val total = byType.sumOf { it.count }
    // 在线率约 75% ~ 95%
    val online = (total * (0.75 + rand() * 0.2)).roundToInt()
    return DeviceStats(total = total, online = online, offline = total - online, byType = byType)
}

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

fun mockAlarmStats(date: LocalDate, now: LocalTime): AlarmStats {
    val rand = mulberry32(seedOf(date) + 2)
    val byType = ALARM_TYPES.map { AlarmTypeStat(it, randInt(rand, 1, 12)) }
    val todayCount = byType.sumOf { it.count }
    // 近 24 小时，以当前整点为终点；夜间休息时段报警偏少
    val curve = mutableListOf<AlarmPoint>()
    for (i in 23 downTo 0) {
        val h = (now.hour - i + 24) % 24
        val night = h >= 23 || h < 6
        curve.add(
            AlarmPoint(
                time = "%02d:00".format(h),
                count = maxOf(0, (if (night) 1 else 3) + randInt(rand, -1, 4))
            )
        )
    }
    return AlarmStats(todayCount = todayCount, byType = byType, curve = curve)
}
