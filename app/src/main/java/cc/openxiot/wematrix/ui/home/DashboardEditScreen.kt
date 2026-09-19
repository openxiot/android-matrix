package cc.openxiot.wematrix.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
 * **长按拖拽排序**：拖到哪个槽，那里就画出虚线框+背景色的落点占位，其它行自动让位（animateItem 滑动），
 * 被拖的行本身保持原尺寸、随手指浮动；松手把最终顺序整体交还草稿。末尾一张虚线「添加」卡代替原悬浮按钮。
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
                onReorder = dashboardViewModel::applyReorder
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
    onReorder: (List<MobileDashboardWidget>) -> Unit
) {
    val draft = state.draft
    val density = LocalDensity.current
    val editingWidget = draft.firstOrNull { it.id == state.editingId }

    // 草稿派生的**显示行**：连续两个 HALF 并排成一行、FULL 独占一行。拖拽时在本地 `rows` 上实时让位，
    // 松手才把最终顺序整体交还 draft（draft 变 → remember(draft) 重建 rows，两处一致）。
    var rows by remember(draft) { mutableStateOf(buildRows(draft)) }

    // ---- 拖拽共享状态（长按某个槽开始，整页一个 dragId，各行按自己的槽接线） ----
    var dragKey by remember { mutableStateOf<String?>(null) }          // 正在被拖的行签名（id 拼接）
    var dragStartRows by remember { mutableStateOf<List<List<MobileDashboardWidget>>>(emptyList()) } // 取消时还原
    var dragInsertIndex by remember { mutableStateOf(0) }              // 当前落点行下标
    var dragRowHeightPx by remember { mutableStateOf(0f) }             // 被拖行高度（跨过 = 让一格）
    var dragOffsetPx by remember { mutableStateOf(0f) }                // 手指相对起始的纵向位移
    var rowHeights by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    val startDrag = { signature: String ->
        dragStartRows = rows
        dragKey = signature
        dragInsertIndex = rows.indexOfFirst { rowSignature(it) == signature }.coerceAtLeast(0)
        dragOffsetPx = 0f
        // 高度从 onSizeChanged 缓存里取：比实时测量稳，跨格子算步长用
        dragRowHeightPx = (rowHeights[signature] ?: 168).toFloat()
    }
    val finishDrag = {
        dragKey = null
        dragOffsetPx = 0f
        dragInsertIndex = 0
        dragRowHeightPx = 0f
    }
    val commitDrag = {
        onReorder(rows.flatten())
        finishDrag()
    }
    val cancelDrag = {
        rows = dragStartRows
        finishDrag()
    }

    // 每跨过一行的高度 → 实时让位：把被拖行整行换到新下标，同时把已消耗的整步高位从 offset 里扣掉，
    // 让浮动卡片始终贴手指，而不是跟着槽跳。
    val moveBy = { dy: Float ->
        if (dragRowHeightPx > 0f && rows.isNotEmpty()) {
            dragOffsetPx += dy
            val step = (dragOffsetPx / dragRowHeightPx).roundToInt()
            val target = (dragInsertIndex + step).coerceIn(0, rows.lastIndex)
            if (target != dragInsertIndex) {
                val list = rows.toMutableList()
                val item = list.removeAt(dragInsertIndex)
                list.add(target, item)
                rows = list
                dragInsertIndex = target
                dragOffsetPx -= step * dragRowHeightPx
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        state.message?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (rows.isEmpty()) {
                item(key = "empty") { EmptyState("还没有卡片，点下方「添加」加一张") }
            }
            itemsIndexed(rows, key = { _, row -> rowSignature(row) }) { _, row ->
                val signature = rowSignature(row)
                val isDragged = dragKey == signature
                val placeholderHeight = with(density) { dragRowHeightPx.coerceAtLeast(1f).dp }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 非被拖行用 animateItem 让位时平滑滑开；被拖行自己的行号变化由浮动位移接管
                        .then(if (isDragged) Modifier else Modifier.animateItem())
                        .zIndex(if (isDragged) 1f else 0f)
                        .onSizeChanged {
                            if (!isDragged) rowHeights = rowHeights + (signature to it.height)
                        }
                        .pointerInput(signature, draft.size) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { startDrag(signature) },
                                onDragEnd = { commitDrag() },
                                onDragCancel = { cancelDrag() },
                                onDrag = { change, amount ->
                                    change.consume()
                                    moveBy(amount.y)
                                }
                            )
                        }
                ) {
                    if (isDragged) {
                        // 落点占位：可放置的位置 = 虚线框 + 背景色（也是松手后的归位格）
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(placeholderHeight)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                    RoundedCornerShape(16.dp)
                                )
                                .dashedBorder(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                        )
                        // 被拖的卡片本体：**尺寸不变**（宽度随行、高度即卡片高），只随手指上下浮动
                        RowCard(
                            row = row,
                            state = state,
                            editable = false,
                            onOpen = onOpen,
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    translationY = dragOffsetPx
                                    alpha = 0.95f
                                    shadowElevation = 8.dp.toPx()
                                }
                        )
                    } else {
                        RowCard(
                            row = row,
                            state = state,
                            editable = true,
                            onOpen = onOpen,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            item(key = "add") { AddCardButton(onClick = onShowPicker) }
            item(key = "spacer") { Spacer(Modifier.height(12.dp)) }
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

/** 一个显示行：FULL 独占一行整宽；连续两个 HALF 并排（weight 各半）占一行。 */
@Composable
private fun RowCard(
    row: List<MobileDashboardWidget>,
    state: MobileDashboardUiState,
    editable: Boolean,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (row.size == 2) {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CardSlot(row[0], state, editable, onOpen, Modifier.weight(1f))
            CardSlot(row[1], state, editable, onOpen, Modifier.weight(1f))
        }
    } else {
        CardSlot(row[0], state, editable, onOpen, modifier)
    }
}

/** 行里的一张卡：实时预览 + 虚线框（可编辑）+ 点按开编辑器。被拖时（editable=false）去掉虚线、原样浮动。 */
@Composable
private fun CardSlot(
    widget: MobileDashboardWidget,
    state: MobileDashboardUiState,
    editable: Boolean,
    onOpen: (String) -> Unit,
    modifier: Modifier
) {
    DashboardWidgetHost(
        widget = widget,
        data = state.previewById[widget.id],
        error = state.previewMessageById[widget.id],
        modifier = modifier
            .then(if (editable) Modifier.dashedBorder(MaterialTheme.colorScheme.primary, strokeWidth = 1.5.dp) else Modifier)
            .then(if (editable) Modifier.padding(2.dp) else Modifier)
            .clickable { widget.id?.let(onOpen) }
    )
}

/** 把草稿按「FULL 一行 / 连续两个 HALF 并排一行」派生成显示行（与首页只读排布同口径）。 */
private fun buildRows(widgets: List<MobileDashboardWidget>): List<List<MobileDashboardWidget>> {
    val result = mutableListOf<List<MobileDashboardWidget>>()
    var i = 0
    while (i < widgets.size) {
        val w = widgets[i]
        val mate = i + 1 < widgets.size
            && w.size == DashboardTypes.SIZE_HALF
            && widgets[i + 1].size == DashboardTypes.SIZE_HALF
        if (mate) {
            result.add(listOf(w, widgets[i + 1]))
            i += 2
        } else {
            result.add(listOf(w))
            i += 1
        }
    }
    return result
}

/** 行在 LazyColumn 里的稳定 key（也是拖拽签名）：行内 id 拼接。 */
private fun rowSignature(row: List<MobileDashboardWidget>): String =
    row.joinToString("~") { it.id.orEmpty() }

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