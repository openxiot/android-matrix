package cc.openxiot.wematrix.ui.modbus

import cc.openxiot.wematrix.R
import cc.openxiot.wematrix.data.api.ModbusAlarm
import cc.openxiot.wematrix.data.api.ModbusAlarmList
import cc.openxiot.wematrix.ui.core.UiText

/**
 * 阈值告警的展示口径，逐条对齐 webapp-matrix 的
 * `src/app/typedef/define/modbus/ModbusAlarm.ts`。
 *
 * 一条硬规矩（webapp-matrix 的 AGENTS.md）：**来自服务端与用户的文本一律原样显示、永不翻译**
 * —— 告警文本（用户自己敲的）、出值名、取值表描述、单位、服务名、失败消息都在其列。
 * 本文件只把**后端枚举名**换成页面自己的词（级别 / 比较方式 / 关闭原因），
 * 且**未收录的枚举名一律原样给出**：后端加了新枚举时不至于空白。
 *
 * 这里只放「纯函数」，配色之类 Compose 类型留在各 Screen 里。文案一律返回 [UiText]：三个
 * 词典都有「没收录的枚举名原样给出」这一档，那不是能塞进资源的固定标签。
 */

/**
 * 告警级别（后端 `ModbusAlarmPolicy.LEVELS`，线上就是这些字符串）。
 *
 * 三级 + 分级汇总：清单可按级别筛，汇总卡按级别分布。取值顺序即「由轻到重」，
 * 页面的下拉与汇总按这个顺序排，不另排一遍。
 */
val alarmLevels: List<String> = listOf("INFO", "WARN", "CRITICAL")

/**
 * 级别 → 界面标签。
 *
 * 用的是「提示 / 警告 / 严重」而不是「信息 / 警告 / 错误」：这三个词描述的是**要不要人管**，
 * 而不是技术上的严重度分级 —— 看告警页的人要的正是这个判断。
 */
private val LEVEL_LABELS: Map<String, Int> = mapOf(
    "INFO" to R.string.modbus_alarm_level_info,
    "WARN" to R.string.modbus_alarm_level_warn,
    "CRITICAL" to R.string.modbus_alarm_level_critical
)

/** 级别标签：没收录的枚举名原样给出（老数据 / 后端加了新级别时不至于空白） */
fun alarmLevelLabel(level: String?): UiText {
    if (level.isNullOrEmpty()) return UiText.Raw("")
    return LEVEL_LABELS[level]?.let { UiText.Res(it) } ?: UiText.Raw(level)
}

/**
 * 比较方式（后端 `ModbusAlarmPolicy.OPERATORS`，线上是**符号**）：`>` `>=` `<` `<=` `=`。
 *
 * 白名单用符号而不是 `GT`/`GTE` 之类的名字：符号语言中立、与需求原话一致、日志与响应里回显无歧义；
 * 「超过 / 达到」那些**词**是页面自己的文案，符号本身不翻译。
 */
private val OPERATOR_LABELS: Map<String, Int> = mapOf(
    ">" to R.string.modbus_alarm_op_gt,
    ">=" to R.string.modbus_alarm_op_gte,
    "<" to R.string.modbus_alarm_op_lt,
    "<=" to R.string.modbus_alarm_op_lte,
    "=" to R.string.modbus_alarm_op_eq
)

/**
 * 比较方式的界面标签：没收录的符号**原样给出**。
 *
 * 五种比较方式里 `=` 单独说一句：它是给「取值表命名的状态」与「位 0/1」用的，
 * 数值字段也能配（比一个确定的数），只是那种用法少见。
 */
fun alarmOperatorLabel(compare: String?): UiText {
    if (compare.isNullOrEmpty()) return UiText.Raw("")
    return OPERATOR_LABELS[compare]?.let { UiText.Res(it) } ?: UiText.Raw(compare)
}

/**
 * 一条告警**是怎么关掉的**（后端 `ModbusServiceAlarm` 的 `CLOSE_*` 常量，线上就是这些字符串）。
 *
 * 三个值互斥且覆盖完整，一条开着的行只可能从其中一条路出去：
 * - `VALUE` 值回到了不命中任何规则的地方（真·恢复正常）；
 * - `DEFINITION` 定义不再覆盖这个键：出值被删、规则被删、或该出值所有规则都被停用；
 * - `SUPERSEDED` 同一个出值上换了一条规则生效 —— **升级与降级都算**。
 *   降级（严重那条关了、警告那条接管）尤其不能用 `VALUE` 表达：值仍然越限，记「值恢复」是谎话。
 */
private val CLOSE_LABELS: Map<String, Int> = mapOf(
    "VALUE" to R.string.modbus_alarm_close_value,
    "DEFINITION" to R.string.modbus_alarm_close_definition,
    "SUPERSEDED" to R.string.modbus_alarm_close_superseded
)

/** 关闭原因标签：没收录的枚举名原样给出（老数据 / 后端加了新原因时不至于空白） */
fun alarmCloseLabel(closeType: String?): UiText {
    if (closeType.isNullOrEmpty()) return UiText.Raw("")
    return CLOSE_LABELS[closeType]?.let { UiText.Res(it) } ?: UiText.Raw(closeType)
}

/**
 * 「触发条件」的文字：比较方式 + 阈值/状态 + 单位，如 `超过 80℃`、`等于 制冷`。
 *
 * 入参是**行的形状**而不是某个具体类：告警行把快照摊在顶层（`compare`/`threshold`/`state`/`unit`），
 * 而服务定义里的配置挂在字段上、没有 `unit`（单位是字段的属性）—— 两处都要能算这个串。
 *
 * 单位原样缀在数值后面（**不翻译**，它是点表里的数据）；`=` 比状态时不缀单位（状态是取值表的描述、
 * 与单位无关）。`compare` 缺失（老数据）时只给阈值，不硬编一个比较方式上去。
 *
 * 阈值走 [configNumberText]（**不是** [numberText]）：它是用户配的数，页面上得说得出「我配的
 * 是多少」—— 与 web 的裸 `${threshold}` 对齐，不收 2 位。
 */
fun alarmCondition(
    compare: String?,
    threshold: Double?,
    state: String?,
    unit: String? = null
): UiText {
    // 单位原样缀在数值后面（它是点表里的数据，不翻译）
    val target = if (threshold != null) {
        "${configNumberText(threshold)}${unit ?: ""}"
    } else {
        state ?: ""
    }
    return when {
        // compare 缺失（老数据）时只给阈值，不硬编一个比较方式上去
        compare.isNullOrEmpty() -> UiText.Raw(target)
        // 阈值与状态都空：原口径是只给比较方式、不留尾随空格（`filter { isNotEmpty }` 那一条）
        target.isEmpty() -> alarmOperatorLabel(compare)
        // 两者之间那个空格中英都成立，故这层壳没有词可翻，只是一个拼装格式
        else -> UiText.Res(R.string.modbus_alarm_condition, listOf(alarmOperatorLabel(compare), target))
    }
}

/** [alarmCondition] 的告警行重载：定义快照摊在行上 */
fun alarmCondition(alarm: ModbusAlarm): UiText =
    alarmCondition(alarm.compare, alarm.threshold, alarm.state, alarm.unit)

/**
 * 「当前值」：越限那一刻的值 + 单位。单位只缀在**数值**后面 ——
 * `=` 比状态时值是取值表的描述（如「制冷」），与单位无关（同 [alarmCondition] 的口径）。
 */
fun alarmSampleText(sample: Any?, unit: String?): String {
    val text = valueText(sample)
    return if (sample is Number) "$text${unit ?: ""}" else text
}

/** [alarmSampleText] 的告警行重载 */
fun alarmSampleText(alarm: ModbusAlarm): String = alarmSampleText(alarm.sample, alarm.unit)

/**
 * 把「处理」成功后回来的那一条替进清单（**就地替换，不整页刷新**），并按「刚处理掉一条」修正计数。
 *
 * 两个地方容易错，故收成一个纯函数：**计数不能减两次**（后端把重复处理当成功，
 * 一条本来就已经处理过的行再点一次不该让「未处理」变成负数），以及**只动会变的那一格** ——
 * 处理既不改级别也不改告警文本，`byLevel` / `byText` 两份分布原样留着，仍以后端给的那一份为准。
 *
 * 注意清单当前的筛选条件不被重新施加：筛「只看未处理」时，刚处理掉的那一行**仍留在表里**
 * （显示成「已处理」）。这是有意的 —— 用户刚点过的那条要能看见结果，而不是点完就从眼前消失。
 */
fun applyHandledAlarm(list: ModbusAlarmList, updated: ModbusAlarm): ModbusAlarmList {
    val before = list.items.find { it.id == updated.id }
    // `before != null` 这一半不能省：清单里根本没有这一条时（筛选刚换过）什么都没变，
    // 而 `before?.handled !== true` 在 before 为 null 时是**成立**的，会把计数白减一次
    val justHandled = before != null && before.handled != true && updated.handled == true
    return list.copy(
        items = list.items.map { if (it.id == updated.id) updated else it },
        summary = list.summary.copy(
            unhandled = if (justHandled) maxOf(0, list.summary.unhandled - 1) else list.summary.unhandled
        )
    )
}
