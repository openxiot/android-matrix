package cc.openxiot.wematrix.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.openxiot.wematrix.data.api.MobileDashboardWidget
import cc.openxiot.wematrix.data.repository.ProductSpecRepository
import cc.openxiot.wematrix.ui.components.ConfirmDialog
import cc.openxiot.wematrix.ui.components.EmptyState
import cc.openxiot.wematrix.ui.components.LoadingIndicator
import kotlin.math.roundToInt

/**
 * 「编辑布局」二级页（首页的编辑态搬到这里）。
 *
 * **没有底部导航栏**：作为独立 destination 压在 Main（带底栏的骨架）之上，顶栏是返回图标 +
 * 「编辑布局」。改动的是**草稿**（VM `draft`），不入库，直到「保存布局」；保存成功后 [MobileDashboardUiState.saved]
 * 置位、自动 `onBack` 回首页（首页在 RESUMED 时重取，能看到新布局）。返回图标在有未保存改动时
 * 会先弹一个「丢弃」确认。
 *
 * 列表 = 草稿每张卡的**实时预览**（虚线框 = 可编辑）：点按卡打开它的编辑器弹层（同 webapp），
 * 长按拖动排序；末尾一张虚线「添加」卡代替原悬浮按钮。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardEditScreen(
    rootId: String?,
    onBack: () -> Unit,
    dashboardViewModel: MobileDashboardViewModel = viewModel()
) {
    val state by dashboardViewModel.uiState.collectAsStateWithLifecycle()
    val productSpec = remember { ProductSpecRepository() }

    var confirmExit by remember { mutableStateOf(false) }

    // 打开即进编辑态（取 catalog + 把已存布局拷贝成 draft）。
    LaunchedEffect(rootId) {
        if (!state.editing) dashboardViewModel.enterEdit(rootId)
    }
    // 保存成功 → 自动返回首页。
    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }
    // 草稿每动一次 → 防抖重 render 预览。
    LaunchedEffect(rootId, state.draft) {
        dashboardViewModel.schedulePreview(rootId)
    }

    val requestExit = {
        if (state.dirty) confirmExit = true else onBack()
    }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = requestExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                    Text(
                        text = "编辑布局",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    // 恢复默认只改草稿，仍需手动保存
                    TextButton(onClick = { dashboardViewModel.restoreDefault(rootId) }, enabled = !state.saving) {
                        Text("恢复默认")
                    }
                    TextButton(
                        onClick = { dashboardViewModel.save(rootId) },
                        enabled = state.dirty && !state.saving
                    ) {
                        Text("保存布局")
                    }
                }
            }
        }
    ) { padding ->
        if (!state.editing) {
            LoadingIndicator(Modifier.fillMaxSize().padding(padding))
        } else {
            EditingContent(
                state = state,
                productSpec = productSpec,
                contentPadding = padding,
                onShowPicker = dashboardViewModel::showPicker,
                onDismissPicker = dashboardViewModel::hidePicker,
                onAdd = dashboardViewModel::addWidget,
                onCommitCard = dashboardViewModel::commitCard,
                onDeleteCard = dashboardViewModel::removeWidget,
                onOpen = dashboardViewModel::openEditor,
                onCloseEditor = dashboardViewModel::closeEditor,
                onMove = dashboardViewModel::moveWidget
            )
        }
    }

    if (confirmExit) {
        ConfirmDialog(
            title = "放弃修改？",
            message = "还有未保存的改动，退出将丢失。",
            onConfirm = onBack,
            onDismiss = { confirmExit = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditingContent(
    state: MobileDashboardUiState,
    productSpec: ProductSpecRepository,
    contentPadding: PaddingValues,
    onShowPicker: () -> Unit,
    onDismissPicker: () -> Unit,
    onAdd: (String) -> Unit,
    onCommitCard: (String, String, String, Map<String, Any?>) -> Unit,
    onDeleteCard: (String) -> Unit,
    onOpen: (String) -> Unit,
    onCloseEditor: () -> Unit,
    onMove: (Int, Int) -> Unit
) {
    val draft = state.draft
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    val thresholdPx = with(LocalDensity.current) { 96.dp.toPx() }
    val editingWidget = draft.firstOrNull { it.id == state.editingId }

    Column(Modifier.fillMaxSize().padding(contentPadding)) {
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
            // 与首页只读一致的排布：FULL 整宽、连续两个 HALF 并排占一行 —— 半宽就该显示成半宽。
            var i = 0
            while (i < draft.size) {
                val widget = draft[i]
                val mate = i + 1 < draft.size
                    && widget.size == DashboardTypes.SIZE_HALF
                    && draft[i + 1].size == DashboardTypes.SIZE_HALF
                if (mate) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        EditPreviewSlot(
                            widget = draft[i], index = i, state = state,
                            dragId = dragId, dragOffsetPx = dragOffsetPx, thresholdPx = thresholdPx,
                            onOpen = onOpen, onMove = onMove,
                            onDragStart = { dragId = draft[i].id; dragOffsetPx = 0f },
                            onDragDelta = { dy -> if (dragId == draft[i].id) dragOffsetPx += dy },
                            onDragEnd = {
                                if (dragId == draft[i].id) {
                                    val step = (dragOffsetPx / thresholdPx).roundToInt()
                                    val target = (i + step).coerceIn(0, draft.size - 1)
                                    if (target != i) onMove(i, target)
                                }
                                dragId = null; dragOffsetPx = 0f
                            },
                            modifier = Modifier.weight(1f)
                        )
                        EditPreviewSlot(
                            widget = draft[i + 1], index = i + 1, state = state,
                            dragId = dragId, dragOffsetPx = dragOffsetPx, thresholdPx = thresholdPx,
                            onOpen = onOpen, onMove = onMove,
                            onDragStart = { dragId = draft[i + 1].id; dragOffsetPx = 0f },
                            onDragDelta = { dy -> if (dragId == draft[i + 1].id) dragOffsetPx += dy },
                            onDragEnd = {
                                if (dragId == draft[i + 1].id) {
                                    val step = (dragOffsetPx / thresholdPx).roundToInt()
                                    val target = (i + 1 + step).coerceIn(0, draft.size - 1)
                                    if (target != i + 1) onMove(i + 1, target)
                                }
                                dragId = null; dragOffsetPx = 0f
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    i += 2
                } else {
                    EditPreviewSlot(
                        widget = widget, index = i, state = state,
                        dragId = dragId, dragOffsetPx = dragOffsetPx, thresholdPx = thresholdPx,
                        onOpen = onOpen, onMove = onMove,
                        onDragStart = { dragId = widget.id; dragOffsetPx = 0f },
                        onDragDelta = { dy -> if (dragId == widget.id) dragOffsetPx += dy },
                        onDragEnd = {
                            if (dragId == widget.id) {
                                val step = (dragOffsetPx / thresholdPx).roundToInt()
                                val target = (i + step).coerceIn(0, draft.size - 1)
                                if (target != i) onMove(i, target)
                            }
                            dragId = null; dragOffsetPx = 0f
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    i += 1
                }
            }
            if (draft.isEmpty()) EmptyState("还没有卡片，点下方「添加」加一张")
            AddCardButton(onClick = onShowPicker)
            Spacer(Modifier.height(12.dp))
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

/** 编辑列里的一个槽：把草稿卡按它自己的下标接线到共享的拖动状态。 */
@Composable
private fun EditPreviewSlot(
    widget: MobileDashboardWidget,
    index: Int,
    state: MobileDashboardUiState,
    dragId: String?,
    dragOffsetPx: Float,
    thresholdPx: Float,
    onOpen: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier
) {
    DashboardPreview(
        widget = widget,
        data = state.previewById[widget.id],
        error = state.previewMessageById[widget.id],
        dragging = dragId == widget.id,
        dragOffsetPx = dragOffsetPx,
        modifier = modifier,
        onClick = { widget.id?.let(onOpen) },
        onDragStart = onDragStart,
        onDragDelta = onDragDelta,
        onDragEnd = onDragEnd
    )
}

/** 一张草稿卡的实时预览：虚线框 = 可编辑；点按开编辑器、长按（点住）拖动排序。 */
@Composable
private fun DashboardPreview(
    widget: MobileDashboardWidget,
    data: Map<String, Any?>?,
    error: String?,
    dragging: Boolean,
    dragOffsetPx: Float,
    modifier: Modifier,
    onClick: () -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    Box(
        modifier = modifier
            .graphicsLayer {
                if (dragging) {
                    translationY = dragOffsetPx
                    alpha = 0.92f
                }
            }
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
            .dashedBorder(color = MaterialTheme.colorScheme.primary, strokeWidth = if (dragging) 2.dp else 1.5.dp)
            .padding(2.dp)
            .clickable(onClick = onClick)
    ) {
        DashboardWidgetHost(
            widget = widget,
            data = data,
            error = error,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 末尾一张虚线的「添加」卡 —— 代替悬浮按钮。 */
@Composable
private fun AddCardButton(onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .dashedBorder(color = MaterialTheme.colorScheme.outline, strokeWidth = 1.5.dp)
            .clickable(onClick = onClick)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "＋ 添加",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 虚线圆角边框（编辑态卡片 / 加卡占位用）。 */
private fun Modifier.dashedBorder(
    color: Color,
    shape: Shape = RoundedCornerShape(16.dp),
    strokeWidth: Dp = 1.5.dp,
    dash: Dp = 9.dp,
    gap: Dp = 9.dp
): Modifier = drawWithCache {
    val effects = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), gap.toPx()))
    val stroke = Stroke(width = strokeWidth.toPx(), pathEffect = effects)
    val outline = shape.createOutline(size, layoutDirection, this)
    onDrawBehind {
        drawPath(
            path = Path().apply { addOutline(outline) },
            color = color,
            style = stroke
        )
    }
}