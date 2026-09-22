package cc.openxiot.wematrix.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.openxiot.wematrix.R
import cc.openxiot.wematrix.data.api.MobileDashboardWidget
import cc.openxiot.wematrix.ui.components.EmptyState
import cc.openxiot.wematrix.ui.components.ErrorMessage
import cc.openxiot.wematrix.ui.components.LoadingIndicator
import cc.openxiot.wematrix.ui.core.SessionState
import cc.openxiot.wematrix.ui.core.asString
import cc.openxiot.wematrix.util.cellComesFirst
import cc.openxiot.wematrix.util.physicalOrder
import cc.openxiot.wematrix.ui.main.PageTitle
import kotlinx.coroutines.launch

/**
 * 首页（可自定义看板）——**只读**渲染。
 *
 * 顺序 = 布局 `widgets[]`（= 阅读顺序），排布走共享的 [pack]（与编辑页、后端同一套规则）：
 * `FULL` 整宽独占一行，`HALF` 两张并排，落单的 `HALF` 也占一行 —— 靠左还是靠右由它自己的
 * `side` 说了算，另一半留白（不占位补齐）。数分两处取（见 [MobileDashboardViewModel]）：布局
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
            title = stringResource(R.string.tab_home),
            actions = {
                if (canEdit && rootId != null) {
                    TextButton(onClick = onEditDashboard) { Text(stringResource(R.string.common_edit)) }
                }
            }
        )
        when {
            state.error != null -> ErrorMessage(
                message = state.error!!,
                onRetry = { dashboardViewModel.load(rootId) }
            )

            rootId.isNullOrEmpty() -> EmptyState(stringResource(R.string.home_no_project))

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

/**
 * 只读排布：行由共享的 [pack] 给（**不再自己抄一份贪心配对** —— 那正是「横向补位」的来源）。
 *
 * - `FULL`：独占一行、整宽；
 * - 两张 `HALF`：一行两格、**等高**（行高取两张里高的那张，矮的撑上去）；
 * - 落单的 `HALF`：也占一行，另一半是**留白**而不是把卡撑满。靠左还是靠右看它自己的 `side`
 *   —— 落单的 `RIGHT`（同伴被删掉/拖走的那半张）就留在右半格，左边空着，**不往左滑**。
 *
 * 落单那一格用「另一个 weight(1f) 的 Spacer」凑成同行两格，而不是 `fillMaxWidth(0.5f)`：
 * 这样它的宽度与并排时那一格**逐像素一致**（并排那格是 `(行宽 - 12dp) / 2`），
 * 从并排变成落单（同伴被删）时卡不会悄悄变宽一点。
 */
@Composable
private fun ReadOnlyContent(state: MobileDashboardUiState, modifier: Modifier = Modifier) {
    val rows = remember(state.widgets) { pack(state.widgets) }
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        rows.forEach { row ->
            when {
                // 同行两张半宽卡**等高**：`IntrinsicSize.Min` 把行高定成两张里更高的那张，
                // 矮的那张 `fillMaxHeight` 撑上去（否则两张各自包内容高，一高一矮参差不齐
                // —— 服务卡少一行「采于」也只是让差距更小，不是没差距）。
                row.size == 2 -> Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 数据序第 0 张 = 物理左半格。RTL 下 Row 会镜像，故倒序抵消 ——
                    // 已保存的排版不该因为换了界面语言而左右翻转（`side` 是物理半格）。
                    physicalOrder(row, LocalLayoutDirection.current).forEach { widget ->
                        ReadOnlyHost(widget, state, Modifier.weight(1f).fillMaxHeight())
                    }
                }
                // 落单的半宽卡：靠哪边看 side，另一边留白（不占位补齐）
                row[0].size == DashboardTypes.SIZE_HALF -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val onRight = row[0].side == DashboardTypes.SIDE_RIGHT
                    if (cellComesFirst(onRight, LocalLayoutDirection.current)) {
                        ReadOnlyHost(row[0], state, Modifier.weight(1f))
                        Spacer(Modifier.weight(1f))
                    } else {
                        Spacer(Modifier.weight(1f))
                        ReadOnlyHost(row[0], state, Modifier.weight(1f))
                    }
                }
                else -> ReadOnlyHost(row[0], state, Modifier.fillMaxWidth())
            }
        }
    }
}

/** 一张卡（只读）：取数结果与失败原因都按 id 认领，**不按下标**。 */
@Composable
private fun ReadOnlyHost(
    widget: MobileDashboardWidget,
    state: MobileDashboardUiState,
    modifier: Modifier = Modifier
) {
    DashboardWidgetHost(
        widget = widget,
        data = state.dataById[widget.id],
        error = state.messageById[widget.id]?.asString(),
        modifier = modifier
    )
}
