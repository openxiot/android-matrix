package cc.openxiot.wematrix.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.MiscellaneousServices
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.openxiot.wematrix.ui.components.EmptyState
import cc.openxiot.wematrix.ui.components.ErrorMessage
import cc.openxiot.wematrix.ui.components.LoadingIndicator
import cc.openxiot.wematrix.ui.main.PageTitle

/**
 * 首页数据看板。
 *
 * 布局对齐 web 的 `pages/main/dashboard`：四张数字卡 + 三张分布饼 + 两条曲线，
 * 手机上卡片两两一行、图表各占一行（web 是 4 / 3 / 2 列的栅格）。
 *
 * 一屏的数分两处取（见 [HomeViewModel]）：设备 / 服务 / 告警全是真实的聚合接口，
 * **只有能耗还是 mock**（后端没有能耗采集）—— 页面上不特别标注，与 web 同处置。
 *
 * 四张卡都能点（对齐 web 0.9.16）：设备与服务去设备页（服务清单挂在设备下，
 * 没有独立的服务列表页），今日告警去告警页，本月能耗去历史页。
 */
@Composable
fun HomeScreen(
    rootId: String?,
    onNavigateToDevices: () -> Unit = {},
    onNavigateToAlarm: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    homeViewModel: HomeViewModel = viewModel()
) {
    val state by homeViewModel.uiState.collectAsStateWithLifecycle()

    // 每次进首页都重取（切 Tab 回来时也取一次）：接口只读、代价一次请求，
    // 比让用户看一份可能已经过期的数划算
    LaunchedEffect(rootId) { homeViewModel.load(rootId) }

    Column(modifier = Modifier.fillMaxSize()) {
        PageTitle(title = "首页")

        when {
            // 取数失败就明说：静默留一屏 0 或空白，看板就成了「项目里一个设备都没有」
            state.error != null -> ErrorMessage(
                message = state.error!!,
                onRetry = { homeViewModel.load(rootId) }
            )

            rootId.isNullOrEmpty() -> EmptyState("请先在项目列表中选择一个项目")

            state.isLoading -> LoadingIndicator()

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 第一行：设备总量（在线数并进这张卡，挂标题行右端）+ 服务总量
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        icon = Icons.Default.Apps,
                        title = "设备总量",
                        value = statValue(state.deviceTotal),
                        unit = statUnit(state.deviceTotal, "台"),
                        extra = "在线 " + statValue(state.onlineCount) + statUnit(state.onlineCount, "台"),
                        onClick = onNavigateToDevices,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        icon = Icons.Default.MiscellaneousServices,
                        title = "服务总量",
                        value = statValue(state.serviceTotal),
                        unit = statUnit(state.serviceTotal, "个"),
                        onClick = onNavigateToDevices,
                        modifier = Modifier.weight(1f)
                    )
                }
                // 第二行：今日告警 + 本月能耗
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        icon = Icons.Default.NotificationsActive,
                        title = "今日告警",
                        value = statValue(state.alarmToday),
                        unit = statUnit(state.alarmToday, "条"),
                        onClick = onNavigateToAlarm,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        icon = Icons.Default.Bolt,
                        title = "本月能耗",
                        value = state.monthEnergy.toString(),
                        unit = "kWh",
                        onClick = onNavigateToHistory,
                        modifier = Modifier.weight(1f)
                    )
                }

                // 三张分布。饼里每一片都是服务端数据，原样显示
                ChartCard(title = "设备类型分布") {
                    if (state.hasDeviceTypes) {
                        DonutChart(state.deviceTypes.map { it.name to it.value })
                    } else {
                        ChartEmpty()
                    }
                }
                ChartCard(title = "服务类型分布") {
                    if (state.hasServiceTypes) {
                        DonutChart(state.serviceTypes.map { it.name to it.value })
                    } else {
                        ChartEmpty()
                    }
                }
                ChartCard(title = "告警类型分布") {
                    if (state.hasAlarmTexts) {
                        DonutChart(state.alarmTexts.map { it.name to it.value })
                    } else {
                        ChartEmpty()
                    }
                }

                // 两条曲线。告警是近 24 小时整点（后端密集零填充，没发生的整点是 0），
                // 日能耗仍是 mock
                ChartCard(title = "日能耗曲线") {
                    LineChart(
                        xLabels = state.energyDaily.map { it.date },
                        yValues = state.energyDaily.map { it.value },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    )
                }
                ChartCard(title = "告警曲线") {
                    LineChart(
                        xLabels = state.alarmHourly.map { it.name },
                        yValues = state.alarmHourly.map { it.value },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    )
                }
            }
        }
    }
}

/*
 * 值位与单位位都随「有没有数」走：没取到时卡片上是**空白**而不是 0
 * —— 0 是有效读数（项目里真没有设备），拿它冒充「还没数」会让卡片报一个假数。
 */
private fun statValue(value: Int?): String = value?.toString() ?: ""

private fun statUnit(value: Int?, unit: String): String = if (value == null) "" else unit

@Composable
private fun StatCard(
    icon: ImageVector,
    title: String,
    value: String,
    unit: String,
    extra: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            // 标题行右端可以再挂一个小口径的数（现在只有设备总量卡用它挂「在线」）。
            // 挂在同一行而不是另起一行是高度账：数字卡每高 1px，下面的图就得让出 1px
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (extra != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = extra,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun ChartCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

/** 空饼画出来是一片空白，不如明说「没有数据」 */
@Composable
private fun ChartEmpty() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "暂无数据",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
