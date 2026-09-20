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
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.unit.IntSize
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
 * 列表 = 草稿每张卡的**实时预览**（虚线框 = 可编辑）：点按卡打开它的编辑器弹层（同 webapp）。
 *
 * **长按拖拽排序（单卡级）**：长按哪一张就只有那一张被选中移动（不会连它半宽的行伴一起走）：
 * - 原位置 → 虚线框 + primary 背景色；落点行 → 虚线框 + tertiary 背景色，两者互不同色；
 * - 两个标注框都和卡片**实际大小一致**（半宽就半宽、高就卡片高）；
 * - 被拖的卡本体尺寸不变，只随手指上下浮动；松手把该卡插到落点行，其余卡重新排（animateItem 平滑让位）。
 * 末尾一张虚线「添加」卡代替原悬浮按钮。
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

    // 草稿派生的**显示行**：连续两个 HALF 并排成一行、FULL 独占一行。拖拽期间行序不变（只挪卡片）。
    val rows = remember(draft) { buildRows(draft) }

    // ---- 单卡拖拽状态（长按某张卡开始，整页共享，每行按槽接线） ----
    var dragId by remember { mutableStateOf<String?>(null) }   // 正在拖的那张一的卡 id
    var dragOriginRow by remember { mutableStateOf(0) }        // 起始所在行
    var dragOffsetPx by remember { mutableStateOf(0f) }        // 手指纵向位移
    var dragOffsetX by remember { mutableStateOf(0f) }         // 手指横向位移（半宽成对在行内左右互移用）
    var dragSwap by remember { mutableStateOf(false) }         // 半宽成对：手指越过行中隔线 → 该换到同伴位置
    var dragTargetRow by remember { mutableStateOf(0) }        // 当前落点行
    // 每张卡的实测尺寸（px）：行高/落点映射/标注高度都用它
    var cardSizes by remember { mutableStateOf<Map<String, IntSize>>(emptyMap()) }
    // 被拖卡实测高（px）：**取拖前测下来的原高**，不用拖动中重测、更不是上次遗留的。
    // 卡在这宽度下内容高是稳定的；浮动卡的第一帧未必回填过、跨次拖拽又残留旧值，直接读存量最稳，
    // 保证原/落点标注始终与卡等大。
    val dragCardH = dragId?.let { cardSizes[it]?.height } ?: 0

    val rowHeightPx = { row: List<MobileDashboardWidget> ->
        row.mapNotNull { cardSizes[it.id]?.height }.maxOrNull() ?: 160
    }
    val rowTopPx = { r: Int ->
        rows.take(r).sumOf { rowHeightPx(it) }
    }

    // 长按某张卡开始拖。按压点 x 决定半宽行里拖的是左卡还是右卡。
    fun startDrag(cardId: String, row: List<MobileDashboardWidget>) {
        val originRow = rows.indexOfFirst { it.any { c -> c.id == cardId } }
        if (originRow < 0) return
        dragId = cardId
        dragOriginRow = originRow
        dragTargetRow = originRow
        dragOffsetPx = 0f
        dragOffsetX = 0f
        dragSwap = false
    }

    fun moveBy(dy: Float) {
        if (dragId == null || rows.isEmpty()) return
        dragOffsetPx += dy
        // 把「手指位移」换算成「卡中心当前落到的行」：基线是**起始行的顶**，
        // 不是整个列表的顶 —— 否则按在下方行的卡一长按，target 会立刻被算到第 0 行。
        val base = rowTopPx(dragOriginRow)
        val cardCenter = base + dragOffsetPx + (dragCardH.takeIf { it > 0 } ?: 160) / 2f
        var t = 0
        for (r in rows.indices) {
            val bottom = rowTopPx(r) + rowHeightPx(rows[r])
            if (cardCenter < bottom) { t = r; break }
            t = r + 1
        }
        dragTargetRow = t.coerceIn(0, rows.lastIndex)
    }

    fun resetDrag() {
        dragId = null
        dragOriginRow = 0
        dragTargetRow = 0
        dragOffsetPx = 0f
        dragOffsetX = 0f
        dragSwap = false
    }

    fun commitDrag() {
        val id = dragId ?: return
        val originIdx = draft.indexOfFirst { it.id == id }
        if (originIdx < 0) { resetDrag(); return }
        // 同行内：半宽成对且已「左右越过行中隔线」→ 与同伴互换位置；否则 no-op（别挪到行首/拆对）
        if (dragTargetRow == dragOriginRow) {
            if (dragSwap && rows.getOrNull(dragOriginRow)?.size == 2) {
                val pair = rows[dragOriginRow]
                val ia = draft.indexOfFirst { it.id == pair[0].id }
                val ib = draft.indexOfFirst { it.id == pair[1].id }
                if (ia >= 0 && ib >= 0 && ia != ib) {
                    val order = draft.toMutableList()
                    order[ia] = draft[ib]
                    order[ib] = draft[ia]
                    if (order != draft) onReorder(order)
                }
            }
            resetDrag(); return
        }
        val order = draft.toMutableList()
        val card = order.removeAt(originIdx)
        var idx = rows.take(dragTargetRow).sumOf { it.size }
        if (dragOriginRow < dragTargetRow) idx -= 1
        idx = idx.coerceIn(0, order.size)
        order.add(idx, card)
        // 松手后把最终顺序交还草稿
        if (order != draft) onReorder(order)
        resetDrag()
    }

    fun cancelDrag() {
        resetDrag()
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
            itemsIndexed(rows, key = { _, row -> rowSignature(row) }) { ri, row ->
                val signature = rowSignature(row)
                val isTargetRow = dragId != null && ri == dragTargetRow
                val originRowHasDrag = dragId != null && ri == dragOriginRow
                val markerH = with(density) { (dragCardH.takeIf { it > 0 } ?: 160).dp }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (originRowHasDrag) Modifier.zIndex(1f) else Modifier)
                        .animateItem()
                        .pointerInput(row.map { it.id }.joinToString("|"), draft.size) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { start: Offset ->
                                    // 半宽行里按 x 选左/右卡
                                    val picked = when {
                                        row.size > 1 && start.x > size.width / 2f -> row[1]
                                        row.size > 1 -> row[0]
                                        else -> row[0]
                                    }
                                    picked.id?.let { startDrag(it, row) }
                                },
                                onDragEnd = { commitDrag() },
                                onDragCancel = { cancelDrag() },
                                onDrag = { change, amount ->
                                    change.consume()
                                    moveBy(amount.y)
                                    if (amount.x != 0f) {
                                        dragOffsetX += amount.x
                                        // 半宽成对行：卡中心横向越过行中隔线 → 和同伴互换。仅拖入同行相邻位时用。
                                        if (row.size == 2 && dragId != null) {
                                            val left = row[0].id == dragId
                                            dragSwap = if (left)
                                                (size.width * 0.25f + dragOffsetX) > size.width / 2f
                                            else
                                                (size.width * 0.75f + dragOffsetX) < size.width / 2f
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    RowContent(
                        row = row,
                        state = state,
                        onOpen = onOpen,
                        dragId = dragId,
                        dragOffsetPx = dragOffsetPx,
                        dragOffsetX = dragOffsetX,
                        dragCardH = dragCardH,
                        onSize = { id, size -> if (dragId != id) cardSizes = cardSizes + (id to size) }
                    )
                    // 落点行 → 标注「可以放置的位置」：虚线框 + tertiary 背景色，尺寸 = 卡片实际大小
                    if (isTargetRow && !originRowHasDrag && dragId != null) {
                        val dragged = draft.firstOrNull { it.id == dragId }
                        if (dragged != null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .fillMaxWidth(
                                        if (dragged.size == DashboardTypes.SIZE_HALF) 0.5f else 1f
                                    )
                                    .height(markerH)
                                    .background(
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .dashedBorder(color = MaterialTheme.colorScheme.tertiary, strokeWidth = 2.dp)
                            )
                        }
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

/**
 * 一个显示行：FULL 独占一行整宽；连续两个 HALF 并排（weight 各半）占一行。
 * 落单的 HALF **保持半宽**（不「占位补成整宽」），只靠左。
 * 行里的那张「被拖卡」：原位置画 primary 虚线框 + 背景，卡本体在此列但随手指上下浮动；其余卡原样。
 */
@Composable
private fun RowContent(
    row: List<MobileDashboardWidget>,
    state: MobileDashboardUiState,
    onOpen: (String) -> Unit,
    dragId: String?,
    dragOffsetPx: Float,
    dragOffsetX: Float,
    dragCardH: Int,
    onSize: (String, IntSize) -> Unit
) {
    if (row.size == 2) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CardHost(row[0], state, onOpen, dragId, dragOffsetPx, dragOffsetX, dragCardH, onSize, Modifier.weight(1f))
            CardHost(row[1], state, onOpen, dragId, dragOffsetPx, dragOffsetX, dragCardH, onSize, Modifier.weight(1f))
        }
    } else {
        val wide = if (row[0].size == DashboardTypes.SIZE_HALF) 0.5f else 1f
        CardHost(row[0], state, onOpen, dragId, dragOffsetPx, dragOffsetX, dragCardH, onSize, Modifier.fillMaxWidth(wide))
    }
}

/**
 * 一张卡。isDragged = 这张卡正在被拖：
 * - 先画 primary 虚线框 + 背景色占位（= 原位置标注，宽=槽位、高=卡高，与卡等大）；
 * - 再画卡本体（尺寸不变），`graphicsLayer.translationY = dragOffsetPx` 让原卡随手指浮动，并回填实测高。
 */
@Composable
private fun CardHost(
    widget: MobileDashboardWidget,
    state: MobileDashboardUiState,
    onOpen: (String) -> Unit,
    dragId: String?,
    dragOffsetPx: Float,
    dragOffsetX: Float,
    dragCardH: Int,
    onSize: (String, IntSize) -> Unit,
    modifier: Modifier
) {
    val isDragged = dragId == widget.id
    val density = LocalDensity.current
    val markerH = with(density) { (dragCardH.takeIf { it > 0 } ?: 160).dp }
    Box(
        modifier = modifier
            .onSizeChanged { if (!isDragged) onSize(widget.id.orEmpty(), it) }
            .then(if (isDragged) Modifier.zIndex(1f) else Modifier)
    ) {
        if (isDragged) {
            // 原位置标注：primary 虚线 + 背景，宽=槽位、高=卡高
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(markerH)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                    .dashedBorder(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
            )
            // 卡本体：尺寸不变，只随手指上下/左右浮动（半宽成对左右互移也是这个位移）
            DashboardWidgetHost(
                widget = widget,
                data = state.previewById[widget.id],
                error = state.previewMessageById[widget.id],
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = dragOffsetPx
                        translationX = dragOffsetX
                        alpha = 0.95f
                        shadowElevation = 8.dp.toPx()
                    }
            )
        } else {
            // 卡始终包一层 fillMaxWidth：DashboardWidgetHost 的 Card 默认包内容宽，
            // 直接传槽宽 modifier 会让半宽卡缩成内容宽（≈ 1/4），必须让它填满槽位。
            DashboardWidgetHost(
                widget = widget,
                data = state.previewById[widget.id],
                error = state.previewMessageById[widget.id],
                modifier = Modifier
                    .fillMaxWidth()
                    .dashedBorder(color = MaterialTheme.colorScheme.primary, strokeWidth = 1.5.dp)
                    .padding(2.dp)
                    .clickable { widget.id?.let(onOpen) }
            )
        }
    }
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