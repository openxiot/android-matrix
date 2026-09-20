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

    // ---- 单卡拖拽状态（长按最外层 Box 开始；行在实时让位重排时手势不中断，故不挂在行上） ----
    val listState = rememberLazyListState()
    var dragId by remember { mutableStateOf<String?>(null) }   // 正在拖的那张一的卡 id
    var dragOriginRow by remember { mutableStateOf(0) }        // 起始所在行
    var dragPlaceIdx by remember { mutableStateOf(0) }         // 草稿里的**插入位**（让位实时挪到这）
    var dragOffsetPx by remember { mutableStateOf(0f) }        // 手指纵向位移
    var dragOffsetX by remember { mutableStateOf(0f) }         // 手指横向位移（半宽成对左右互移用）
    var dragSwap by remember { mutableStateOf(false) }         // 半宽成对：越过行中隔线 → 换到同伴位置
    var dragAnchorPx by remember { mutableStateOf(0f) }        // 起始卡在**视口**里的 y（浮动卡叠加的坐标基准）
    // 每张卡的实测尺寸（px）：行高/落点映射/标注高度都用它
    var cardSizes by remember { mutableStateOf<Map<String, IntSize>>(emptyMap()) }
    // 被拖卡实测高（px）：取拖前测下来的原高，卡在该宽度下内容高稳定；读存量最稳，保证插槽与卡等大。
    val dragCardH = dragId?.let { cardSizes[it]?.height } ?: 0
    // 插槽高度（dp）：cardSizes 存的是 px，要除以 density 才得当 dp（直接 .dp 会呈 3x）。
    val dragMarkerH = with(density) { if (dragCardH > 0) (dragCardH / density.density).dp else 150.dp }

    val rowHeightPx = { row: List<MobileDashboardWidget> ->
        row.mapNotNull { cardSizes[it.id]?.height }.maxOrNull() ?: 160
    }
    val rowTopPx = { r: Int ->
        rows.take(r).sumOf { rowHeightPx(it) }
    }
    // 让位展示序：起始草稿里把被拖卡挪到 dragPlaceIdx —— **只改显示**，草稿/预览不动。
    // 落点一行就是那张被拖卡的插槽（tertiary 标记），其余卡随之 animateItem 让位。
    val displaySeq =
        if (dragId == null) draft
        else {
            val s = draft.filterNot { it.id == dragId }
            if (s.size != draft.size) s.toMutableList().apply {
                add(dragPlaceIdx.coerceIn(0, size), draft.first { it.id == dragId })
            } else draft
        }
    val displayRows = remember(draft, dragId, dragPlaceIdx) { buildRows(displaySeq) }
    // 行签名 → 行内容：命中 y 后反查是哪个行/哪张卡
    val rowByKey = displayRows.associate { rowSignature(it) to it }

    // 长按某张卡开始拖。
    fun startDrag(cardId: String, row: List<MobileDashboardWidget>) {
        val originRow = rows.indexOfFirst { it.any { c -> c.id == cardId } }
        if (originRow < 0) return
        dragId = cardId
        dragOriginRow = originRow
        dragPlaceIdx = draft.indexOfFirst { it.id == cardId }.coerceAtLeast(0)
        dragOffsetPx = 0f
        dragOffsetX = 0f
        dragSwap = false
        dragAnchorPx = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == rowSignature(row) }?.offset?.toFloat() ?: 0f
    }

    fun moveBy(dy: Float) {
        if (dragId == null || rows.isEmpty()) return
        dragOffsetPx += dy
        // 卡中心当前落到的行：基线 = 起始行顶（指位是相对位移，不能跟行的绝对坐标比）。
        val base = rowTopPx(dragOriginRow)
        val cardCenter = base + dragOffsetPx + (dragCardH.takeIf { it > 0 } ?: 160) / 2f
        var t = 0
        for (r in rows.indices) {
            val bottom = rowTopPx(r) + rowHeightPx(rows[r])
            if (cardCenter < bottom) { t = r; break }
            t = r + 1
        }
        // 目标行 → 插入位（与提交重排同一套换算），让位实时把展示序挪到这
        val s = draft.filterNot { it.id == dragId }
        var idx = rows.take(t.coerceIn(0, rows.lastIndex)).sumOf { it.size }
        if (dragOriginRow < t) idx -= 1
        dragPlaceIdx = idx.coerceIn(0, s.size)
    }

    fun resetDrag() {
        dragId = null
        dragOriginRow = 0
        dragPlaceIdx = 0
        dragOffsetPx = 0f
        dragOffsetX = 0f
        dragSwap = false
        dragAnchorPx = 0f
    }

    fun commitDrag() {
        val id = dragId ?: return
        val originIdx = draft.indexOfFirst { it.id == id }
        if (originIdx < 0) { resetDrag(); return }
        // 落点仍回原索引位、且已左右越过中隔线 → 与同行半宽同伴互换
        if (dragPlaceIdx == originIdx && dragSwap) {
            val mate = when {
                originIdx > 0 && draft[originIdx - 1].size == DashboardTypes.SIZE_HALF -> originIdx - 1
                originIdx + 1 < draft.size && draft[originIdx + 1].size == DashboardTypes.SIZE_HALF -> originIdx + 1
                else -> -1
            }
            if (mate >= 0) {
                val order = draft.toMutableList()
                order[originIdx] = draft[mate]
                order[mate] = draft[originIdx]
                if (order != draft) onReorder(order)
            }
            resetDrag(); return
        }
        // 跨位：让位期间展示序已排好，直接落库
        val seq = displaySeq
        if (seq != draft) onReorder(seq)
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                // 拖拽统一挂**最外层**：行在实时让位重排时 pointerInput 的 key 不变，手势不中断
                .pointerInput(listState, draft.size) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { start: Offset ->
                            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                start.y >= it.offset && start.y <= it.offset + it.size
                            }
                            val row = info?.key?.let(rowByKey::get)
                            if (row != null) {
                                val picked = when {
                                    row.size > 1 && start.x > size.width / 2f -> row[1]
                                    row.size > 1 -> row[0]
                                    else -> row[0]
                                }
                                picked.id?.let { startDrag(it, row) }
                            }
                        },
                        onDragEnd = { commitDrag() },
                        onDragCancel = { cancelDrag() },
                        onDrag = { change, amount ->
                            change.consume()
                            moveBy(amount.y)
                            if (amount.x != 0f) {
                                dragOffsetX += amount.x
                                // 半宽成对：依草稿相邻判定左右卡，卡中心越过行中隔线 → 与同伴互换
                                val oi = draft.indexOfFirst { it.id == dragId }
                                if (oi >= 0) {
                                    val isPair = (oi > 0 && draft[oi - 1].size == DashboardTypes.SIZE_HALF) ||
                                        (oi + 1 < draft.size && draft[oi + 1].size == DashboardTypes.SIZE_HALF)
                                    if (isPair) {
                                        val left = oi + 1 < draft.size && draft[oi + 1].size == DashboardTypes.SIZE_HALF
                                        dragSwap = if (left)
                                            (size.width * 0.25f + dragOffsetX) > size.width / 2f
                                        else
                                            (size.width * 0.75f + dragOffsetX) < size.width / 2f
                                    }
                                }
                            }
                        }
                    )
                }
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (displayRows.isEmpty()) {
                    item(key = "empty") { EmptyState("还没有卡片，点下方「添加」加一张") }
                }
                itemsIndexed(displayRows, key = { _, row -> rowSignature(row) }) { _, row ->
                    // animateItem：让位时行按新 key 平滑滑开/合拢；拖拽手势在最外层，不因这些行的 key 变化而中断
                    Box(Modifier.animateItem()) {
                        RowContent(
                            row = row,
                            state = state,
                            onOpen = onOpen,
                            dragId = dragId,
                            markerHeight = dragMarkerH,
                            onSize = { id, size -> if (dragId != id) cardSizes = cardSizes + (id to size) }
                        )
                    }
                }
                item(key = "add") { AddCardButton(onClick = onShowPicker) }
                item(key = "spacer") { Spacer(Modifier.height(12.dp)) }
            }

            // 浮动卡本体：叠加在列表之上，坐标 = 起始卡视口 y + 手指相对位移。
            // 让位只重排列表、不惊动它，卡始终贴着手指且尺寸不变。
            dragId?.let { id ->
                val dragged = draft.firstOrNull { it.id == id }
                if (dragged != null) {
                    val wide = if (dragged.size == DashboardTypes.SIZE_HALF) 0.5f else 1f
                    DashboardWidgetHost(
                        widget = dragged,
                        data = state.previewById[dragged.id],
                        error = state.previewMessageById[dragged.id],
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth(wide)
                            .graphicsLayer {
                                translationY = dragAnchorPx + dragOffsetPx
                                translationX = dragOffsetX
                                alpha = 0.95f
                                shadowElevation = 8.dp.toPx()
                            }
                    )
                }
            }
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
    markerHeight: Dp,
    onSize: (String, IntSize) -> Unit
) {
    if (row.size == 2) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SlotHost(row[0], state, onOpen, dragId, markerHeight, onSize, Modifier.weight(1f))
            SlotHost(row[1], state, onOpen, dragId, markerHeight, onSize, Modifier.weight(1f))
        }
    } else {
        val wide = if (row[0].size == DashboardTypes.SIZE_HALF) 0.5f else 1f
        SlotHost(row[0], state, onOpen, dragId, markerHeight, onSize, Modifier.fillMaxWidth(wide))
    }
}

/**
 * 一个槽位。被拖卡在**展示序**里已是「落点位置」，这里只画它的插槽（tertiary 虚线框 + 背景，
 * 高=卡高、宽=槽位），卡本体由外层叠加的浮动位负责。其余卡照常渲染。
 */
@Composable
private fun SlotHost(
    widget: MobileDashboardWidget,
    state: MobileDashboardUiState,
    onOpen: (String) -> Unit,
    dragId: String?,
    markerHeight: Dp,
    onSize: (String, IntSize) -> Unit,
    modifier: Modifier
) {
    Box(modifier = modifier) {
        if (dragId == widget.id) {
            // 落点插槽：占住卡要落的位置，让旁边的卡实时让位
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(markerHeight)
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                    .dashedBorder(color = MaterialTheme.colorScheme.tertiary, strokeWidth = 2.dp)
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
                    .onSizeChanged { onSize(widget.id.orEmpty(), it) }
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