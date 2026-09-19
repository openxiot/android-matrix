package cc.openxiot.wematrix.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.openxiot.wematrix.data.api.MobileDashboardWidget
import cc.openxiot.wematrix.ui.components.EmptyState
import cc.openxiot.wematrix.ui.components.ErrorMessage
import cc.openxiot.wematrix.ui.components.LoadingIndicator
import cc.openxiot.wematrix.ui.core.SessionState
import cc.openxiot.wematrix.ui.main.PageTitle
import kotlinx.coroutines.launch

/**
 * 首页（可自定义看板）——**只读**渲染。
 *
 * 顺序 = 布局 `widgets[]`（= 阅读顺序）。`FULL` 整宽、**连续两个 HALF 并排**占一行；
 * 落单的 HALF 也整宽占一行（占位补齐）。数分两处取（见 [MobileDashboardViewModel]）：布局
 * （未保存时后端返回预置）→ 统一 render 每张卡。**没取到 / 未选项目都是空态**，不放一屏 0。
 *
 * 「编辑」入口在标题行右侧、**仅空间管理员**（[SessionState.canEditById]）可见，点了进二级页
 * [DashboardEditScreen]（在那里增/删/排序/改 config，保存走版本 CAS 落库）。
 */
@Composable
fun HomeScreen(
    rootId: String?,
    onEditDashboard: () -> Unit = {},
    dashboardViewModel: MobileDashboardViewModel = viewModel()
) {
    val state by dashboardViewModel.uiState.collectAsStateWithLifecycle()
    val canEdit = SessionState.canEditById[rootId] ?: false
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    // 切项目（rootId 变）重新取整页。
    LaunchedEffect(rootId) { dashboardViewModel.load(rootId) }
    // 从二级页（编辑 / 告警 / 历史…）返回时已 RESUMED，重取一次，免得看到一份过期的数。
    // 覆盖了「编辑页保存后返回」那一路 —— 编辑页用独立的 VM，这里不跟它共享状态。
    LifecycleResumeEffect(rootId) {
        dashboardViewModel.load(rootId)
        onPauseOrDispose { }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PageTitle(
            title = "首页",
            actions = {
                if (canEdit && rootId != null) {
                    TextButton(onClick = onEditDashboard) { Text("编辑") }
                }
            }
        )
        when {
            state.error != null -> ErrorMessage(
                message = state.error!!,
                onRetry = { dashboardViewModel.load(rootId) }
            )

            rootId.isNullOrEmpty() -> EmptyState("请先在项目列表中选择一个项目")

            state.isLoading -> LoadingIndicator()

            else -> {
                Box(Modifier.fillMaxSize()) {
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = {
                            scope.launch {
                                isRefreshing = true
                                dashboardViewModel.refreshNow(rootId)
                                isRefreshing = false
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        ReadOnlyContent(state, Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

/** 只读排布：FULL 整宽、连续两个 HALF 并排占一行。 */
@Composable
private fun ReadOnlyContent(state: MobileDashboardUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val widgets = state.widgets
        var i = 0
        while (i < widgets.size) {
            val widget = widgets[i]
            val mate = i + 1 < widgets.size && widget.size == DashboardTypes.SIZE_HALF
                && widgets[i + 1].size == DashboardTypes.SIZE_HALF
            if (mate) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DashboardWidgetHost(
                        widget = widget,
                        data = state.dataById[widget.id],
                        error = state.messageById[widget.id],
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
                    data = state.dataById[widget.id],
                    error = state.messageById[widget.id],
                    modifier = Modifier.fillMaxWidth()
                )
                i += 1
            }
        }
    }
}