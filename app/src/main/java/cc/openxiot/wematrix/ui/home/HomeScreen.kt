package cc.openxiot.wematrix.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.openxiot.wematrix.ui.components.EmptyState
import cc.openxiot.wematrix.ui.components.ErrorMessage
import cc.openxiot.wematrix.ui.components.LoadingIndicator
import cc.openxiot.wematrix.ui.main.PageTitle

/**
 * 首页（可自定义看板）。
 *
 * 顺序 = 布局 `widgets[]` 的数组顺序（= 阅读顺序）。尺寸两档：
 * - `FULL`：整宽占一行；
 * - `HALF`：**连续两个** HALF 并排占一行（落单的 HALF 也整宽占一行，占位补齐）。
 *
 * 一屏分两处取（见 [MobileDashboardViewModel]）：先 [MobileDashboardRepository.getLayout] 取布局
 * （从未保存时后端直接返回预置），再 [MobileDashboardRepository.render] 统一取每张卡的数。
 * **没取到 / 未选项目都是空态**，不放一屏 0。
 */
@Composable
fun HomeScreen(
    rootId: String?,
    dashboardViewModel: MobileDashboardViewModel = viewModel()
) {
    val state by dashboardViewModel.uiState.collectAsStateWithLifecycle()

    // 每次进首页都重取（切 Tab 回来 / 切项目都取一次）：接口只读、代价几次请求，
    // 比让用户看一份可能已经过期的数划算
    LaunchedEffect(rootId) { dashboardViewModel.load(rootId) }

    Column(modifier = Modifier.fillMaxSize()) {
        PageTitle(title = "首页")

        when {
            // 整页级失败（布局/取数全挂）就明说：静默留一屏空白，看板就成了「项目里一个设备都没有」
            state.error != null -> ErrorMessage(
                message = state.error!!,
                onRetry = { dashboardViewModel.load(rootId) }
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
                val widgets = state.widgets
                var i = 0
                while (i < widgets.size) {
                    val widget = widgets[i]
                    val data = state.dataById[widget.id]
                    val message = state.messageById[widget.id]
                    // 连续两个 HALF 并排占一行；其余（FULL、或落单的 HALF）整宽占一行
                    val mate = i + 1 < widgets.size && widget.size == DashboardTypes.SIZE_HALF
                        && widgets[i + 1].size == DashboardTypes.SIZE_HALF
                    if (mate) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            DashboardWidgetHost(
                                widget = widget,
                                data = data,
                                error = message,
                                modifier = Modifier.weight(1f)
                            )
                            DashboardWidgetHost(
                                widget = widgets[i + 1],
                                data = state.dataById[widgets[i + 1].id],
                                error = state.messageById[widgets[i + 1].id],
                                modifier = Modifier.weight(1f)
                            )
                        }
                        i += 2
                    } else {
                        DashboardWidgetHost(
                            widget = widget,
                            data = data,
                            error = message,
                            modifier = Modifier.fillMaxWidth()
                        )
                        i += 1
                    }
                }
            }
        }
    }
}