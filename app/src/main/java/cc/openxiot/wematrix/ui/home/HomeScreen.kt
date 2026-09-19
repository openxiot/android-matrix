package cc.openxiot.wematrix.ui.home

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.openxiot.wematrix.data.api.MobileDashboardWidget
import cc.openxiot.wematrix.data.repository.ProductSpecRepository
import cc.openxiot.wematrix.ui.components.EmptyState
import cc.openxiot.wematrix.ui.components.ErrorMessage
import cc.openxiot.wematrix.ui.components.LoadingIndicator
import cc.openxiot.wematrix.ui.core.SessionState
import cc.openxiot.wematrix.ui.main.PageTitle
import kotlin.math.roundToInt

/**
 * 首页（可自定义看板）。
 *
 * 只读：顺序 = 布局 `widgets[]`（= 阅读顺序）。`FULL` 整宽、**连续两个 HALF 并排**占一行；
 * 落单的 HALF 也整宽占一行（占位补齐）。数分两处取（见 [MobileDashboardViewModel]）：布局
 * （未保存时后端返回预置）→ 统一 render 每张卡。**没取到 / 未选项目都是空态**，不放一屏 0。
 *
 * 编辑：入口只对管理员（[SessionState.canEditById]）显示；进编辑态把布局拷贝成草稿，加/删/
 * 排序/改 config 都只动草稿，直到「保存布局」（版本 CAS，服务端冲突会亮提示并保留草稿）。
 * 编辑列表按单列显示（混合半宽会让长按拖拽的下标看不清），每张卡**点按编辑、长按拖动排序**。
 */
@Composable
fun HomeScreen(
    rootId: String?,
    dashboardViewModel: MobileDashboardViewModel = viewModel()
) {
    val state by dashboardViewModel.uiState.collectAsStateWithLifecycle()
    val canEdit = SessionState.canEditById[rootId] ?: false
    val productSpec = remember { ProductSpecRepository() }

    LaunchedEffect(rootId) { dashboardViewModel.load(rootId) }

    Column(modifier = Modifier.fillMaxSize()) {
        PageTitle(title = "首页")

        when {
            state.error != null -> ErrorMessage(
                message = state.error!!,
                onRetry = { dashboardViewModel.load(rootId) }
            )

            rootId.isNullOrEmpty() -> EmptyState("请先在项目列表中选择一个项目")

            state.isLoading -> LoadingIndicator()

            state.editing -> EditingContent(
                state = state,
                productSpec = productSpec,
                onExit = dashboardViewModel::exitEdit,
                onShowPicker = dashboardViewModel::showPicker,
                onDismissPicker = dashboardViewModel::hidePicker,
                onAdd = dashboardViewModel::addWidget,
                onCommitCard = dashboardViewModel::commitCard,
                onDeleteCard = dashboardViewModel::removeWidget,
                onOpen = dashboardViewModel::openEditor,
                onCloseEditor = dashboardViewModel::closeEditor,
                onMove = dashboardViewModel::moveWidget,
                onSave = { dashboardViewModel.save(rootId) },
                onRestoreDefault = { dashboardViewModel.restoreDefault(rootId) }
            )

            else -> {
                Box(Modifier.fillMaxSize()) {
                    ReadOnlyContent(state, Modifier.fillMaxSize())
                    if (canEdit) {
                        TextButton(
                            onClick = { dashboardViewModel.enterEdit(rootId) },
                            modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 4.dp)
                        ) {
                            Text("编辑")
                        }
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

@Composable
private fun EditingContent(
    state: MobileDashboardUiState,
    productSpec: ProductSpecRepository,
    onExit: () -> Unit,
    onShowPicker: () -> Unit,
    onDismissPicker: () -> Unit,
    onAdd: (String) -> Unit,
    onCommitCard: (String, String, String, Map<String, Any?>) -> Unit,
    onDeleteCard: (String) -> Unit,
    onOpen: (String) -> Unit,
    onCloseEditor: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onSave: () -> Unit,
    onRestoreDefault: () -> Unit
) {
    val draft = state.draft
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    val thresholdPx = with(LocalDensity.current) { 96.dp.toPx() }
    val editingWidget = draft.firstOrNull { it.id == state.editingId }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                TextButton(onClick = onExit) { Text("退出") }
                TextButton(onClick = onRestoreDefault) { Text("恢复默认") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onSave, enabled = state.dirty && !state.saving) {
                    Text("保存布局")
                }
            }
            state.message?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                draft.forEachIndexed { index, widget ->
                    val dragging = dragId == widget.id
                    DraftCard(
                        widget = widget,
                        index = index,
                        total = draft.size,
                        isDragging = dragging,
                        dragOffsetPx = dragOffsetPx,
                        onClick = { widget.id?.let(onOpen) },
                        onDragStart = { dragId = widget.id; dragOffsetPx = 0f },
                        onDragDelta = { dy -> if (dragging) dragOffsetPx += dy },
                        onDragEnd = {
                            if (dragging) {
                                val step = (dragOffsetPx / thresholdPx).roundToInt()
                                val target = (index + step).coerceIn(0, draft.size - 1)
                                if (target != index) onMove(index, target)
                            }
                            dragId = null
                            dragOffsetPx = 0f
                        }
                    )
                }
                if (draft.isEmpty()) EmptyState("还没有卡片，点右下角 ＋ 添加")
            }
        }

        FloatingActionButton(
            onClick = onShowPicker,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "添加卡片")
        }
    }

    if (state.pickerVisible) {
        DashboardPickerSheet(
            visible = true,
            onDismiss = onDismissPicker,
            onAdd = onAdd
        )
    }

    editingWidget?.let { w ->
        val id = w.id ?: return@let
        DashboardCardEditorSheet(
            widget = w,
            catalog = state.catalog,
            productSpec = productSpec,
            onCommit = { title, size, config -> onCommitCard(id, title, size, config) },
            onDelete = { onDeleteCard(id) },
            onDismiss = onCloseEditor
        )
    }
}

/** 编辑态的一张草稿卡：点按编辑、长按拖动排序（只算纵向）。 */
@Composable
private fun DraftCard(
    widget: MobileDashboardWidget,
    index: Int,
    total: Int,
    isDragging: Boolean,
    dragOffsetPx: Float,
    onClick: () -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 6.dp else 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { if (isDragging) translationY = dragOffsetPx }
            .pointerInput(widget.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onDrag = { change, amount ->
                        change.consume()
                        onDragDelta(amount.y)
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = DashboardTypes.resolveTitle(widget.title, null, widget.type),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = typeLabel(widget.type) + sizeLabel(widget.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${index + 1}/$total",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
        }
    }
}

private fun typeLabel(type: String?): String = when (type) {
    DashboardTypes.STAT -> "统计 · "
    DashboardTypes.LINE -> "曲线 · "
    DashboardTypes.DISTRIBUTION -> "分布 · "
    DashboardTypes.DEVICE -> "设备 · "
    DashboardTypes.SERVICE -> "服务 · "
    else -> ""
}

private fun sizeLabel(size: String?): String = if (size == DashboardTypes.SIZE_HALF) "半宽" else "整宽"