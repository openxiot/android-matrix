package cc.openxiot.wematrix.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.openxiot.wematrix.ui.modbus.RangePreset
import cc.openxiot.wematrix.ui.modbus.rangePresets
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * 告警页与两个历史页共用的筛选控件：时间范围（三档预设 + 自定义）与下拉筛选。
 *
 * web 侧是三个页面各写一遍（`nz-segmented` + `nz-select`），但那三份的时间窗口本来就是同一个口径
 * （见 `ModbusHistoryFormat` 的 [RangePreset]）—— 控件也照同一份来，免得三个页面的筛选长得不一样。
 *
 * 这里只放控件；窗口怎么算、筛选怎么映射成查询参数都在各页自己的 VM 里。
 */

/** 一个下拉选项：`value` 是查询参数的取值，`label` 是页面上的文字 */
data class FilterOption<T>(val value: T, val label: String)

/**
 * 时间范围的三档预设 + 自定义。
 *
 * 自定义**只负责把选项选出来**（回调 [onPresetChange] 收到 [RangePreset.CUSTOM]），
 * 弹日期选择是调用方的事 —— 弹窗要回填当前窗口，那个状态在各页的 VM 里。
 */
@Composable
fun RangePresetChips(
    preset: RangePreset,
    onPresetChange: (RangePreset) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rangePresets.forEach { item ->
            FilterChip(
                selected = preset == item,
                onClick = { onPresetChange(item) },
                label = {
                    Text(item.label, style = MaterialTheme.typography.labelMedium)
                }
            )
        }
    }
}

/**
 * 一个下拉筛选：`value` 是当前选中的键（**null = 不限**），显示的是 [options] 里对应的标签；
 * 不限时显示 [placeholder]。
 *
 * **「不限」是菜单里单独的一项，不是 options 里的一项**：告警页的「处理」「状态」两个筛选里，
 * `false`（只看未处理 / 只看未恢复）是一个**明确的取值**，与「不传」在后端是两件事。
 * 把它们混成一个列表，早晚会有人给「不限」填上 false，那一档就永远查不出来了。
 *
 * 选中时整颗按钮走主色：五个筛选里哪几个正生效，一眼看得出来。
 */
@Composable
fun <T> FilterDropdown(
    label: String,
    value: T?,
    options: List<FilterOption<T>>,
    onSelect: (T?) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "全部"
) {
    var expanded by remember { mutableStateOf(false) }
    val active = value != null
    val valueLabel = options.firstOrNull { it.value == value }?.label ?: placeholder

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            modifier = Modifier.height(36.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(placeholder) },
                onClick = {
                    expanded = false
                    onSelect(null)
                }
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        onSelect(option.value)
                    }
                )
            }
        }
    }
}

/**
 * 自定义时间范围的日期区间选择。
 *
 * **手机上只选到「天」**：web 的 `nz-range-picker` 带 `nzShowTime`、能选到秒，但触屏上选时刻要再
 * 点开一层时间轮盘，为一个已经收窄到天的窗口多付一层操作不值。窗口按整天算 ——
 * `[起始日 00:00:00.000, 结束日次日 00:00:00.000 - 1ms]`，把用户选的那两天整都盖住。
 *
 * **时区这一层不能省**：`DateRangePicker` 交出来的是所选日期在 **UTC** 的零点，直接当本地时刻用会
 * 整体偏掉一个时区（在东八区就是选了「15 日」却从 15 日 08:00 开始）。故取日期时按 UTC 读、
 * 落成窗口时按本地时区算；回填选择器时再反过来。这一对换算是这个控件唯一的坑。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomRangeDialog(
    initialFrom: Long?,
    initialTo: Long?,
    onDismiss: () -> Unit,
    onConfirm: (from: Long, to: Long) -> Unit
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialFrom?.let(::toPickerDay),
        initialSelectedEndDateMillis = initialTo?.let(::toPickerDay)
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val start = state.selectedStartDateMillis ?: return@TextButton
                    // 只点了起始日就按「那一天」算，而不是逼用户再点一次结束日
                    val end = state.selectedEndDateMillis ?: start
                    onConfirm(dayStart(start), dayEnd(end))
                },
                enabled = state.selectedStartDateMillis != null
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    ) {
        // 不做模式切换（日历 / 手输）：日期区间在日历上点两下最直接，多一条路径就多一处要验
        DateRangePicker(state = state, showModeToggle = false)
    }
}

/** 选择器里的「某一天」（UTC 零点）→ 本地时区那一天的 00:00:00.000 */
private fun dayStart(pickerMillis: Long): Long =
    toLocalDate(pickerMillis).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** 选择器里的「某一天」→ 本地时区那一天的 23:59:59.999 */
private fun dayEnd(pickerMillis: Long): Long =
    toLocalDate(pickerMillis).plusDays(1)
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1

/** 本地时刻 → 它落在哪一天（读回选择器要的 UTC 零点） */
private fun toPickerDay(localMillis: Long): Long =
    Instant.ofEpochMilli(localMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun toLocalDate(pickerMillis: Long) =
    Instant.ofEpochMilli(pickerMillis).atZone(ZoneOffset.UTC).toLocalDate()

/**
 * 一行筛选控件：窄屏放不下就横滑，不换行。
 *
 * 手机宽度放不下五个筛选，硬挤会把文字压成省略号；横滑则每个控件都保持可读 ——
 * 筛选的当前值本来就该一眼看清。
 */
@Composable
fun FilterRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
