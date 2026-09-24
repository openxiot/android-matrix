package cc.openxiot.matrix.ui.home

import cc.openxiot.matrix.ui.home.MobileRenderFormat.Slice

/**
 * 看板卡片的**显示层折算**（纯 Kotlin，不碰 Compose，可被 JVM 单测覆盖）：把后端**全量下发**
 * 的数据折成屏幕上看得到的那点东西。镜像 webapp-matrix 的 `dashboard.folding.ts`
 * （`truncatePoints`）—— 同一份数据、同一套边界。
 *
 * 为什么折算在显示层：后端全量下发、不打上限、**也不读 `limit`**（取数层与展示决策不耦合），
 * 所以用户改「显示片数」不必重新取数，改的就是这一层。
 */

/**
 * 「前 N + 其他」：`limit` 是用户配的**显示片数**，`otherLabel` 是末尾那一片的名字
 * （调用方给「其他」；本文件不认识文案）。
 *
 * 边界（与 web 的 `truncatePoints` 逐条一致）：
 * - `limit` 没设 / ≤ 0 / 片数本来就不超过它 → **原样返回**；
 * - 正好等于片数也一样 —— 「前 3 + 其他 0」那种片是纯噪音，不补。
 *
 * 「其他」= 余下各片之和（不是片数）。
 */
fun truncateSlices(slices: List<Slice>, limit: Int?, otherLabel: String): List<Slice> {
    if (limit == null || limit <= 0 || slices.size <= limit) return slices
    val head = slices.take(limit)
    val rest = slices.drop(limit).sumOf { it.count }
    return head + Slice(otherLabel, rest)
}

/**
 * 服务卡最后那行角标要不要画「采于 <时间>」。
 *
 * **半宽卡不画**：并排的两张半宽卡高度要尽量一样，而服务卡比统计卡多出来的正好就是这一行
 * ——「采于」对一张「一眼看个读数」的半格卡是可有可无的信息。整宽服务卡照旧画（那是张
 * 正经的信息卡，采集时间有意义）。
 *
 * **失败原因（`⚠ …`）不受这条影响**：它在 [cc.openxiot.matrix.ui.home.DashboardCards] 里
 * 单独判断，半宽卡上照旧显示 —— 一条错误提示不该因为卡片变窄就消失。两边的截断都落在
 * 「半宽 = 更少的信息」，而不是「半宽 = 更少的真相」。
 */
fun serviceShowsRecordedAt(recordedAt: Long?, half: Boolean): Boolean =
    recordedAt != null && !half
