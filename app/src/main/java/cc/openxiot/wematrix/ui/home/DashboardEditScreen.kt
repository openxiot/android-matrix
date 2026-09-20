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
import androidx.compose.runtime.rememberUpdatedState
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
import kotlin.math.abs
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

    // ---- 单卡拖拽状态（长按最外层 Box 开始；行在实时让位重排时手势不中断，故不挂在行上） ----
    val listState = rememberLazyListState()
    var dragId by remember { mutableStateOf<String?>(null) }   // 正在拖的那张卡 id
    var dragPlaceIdx by remember { mutableStateOf(0) }         // 被拖卡在「去掉自己」序列里的插入位
    var dragOffsetPx by remember { mutableStateOf(0f) }        // 手指纵向位移（浮动卡跟随用）
    var dragOffsetX by remember { mutableStateOf(0f) }         // 手指横向位移（半宽成对左右互移用）
    var dragSwap by remember { mutableStateOf(false) }         // 半宽成对：越过行中隔线 → 换到同伴位置
    var dragAnchorPx by remember { mutableStateOf(0f) }        // 起始卡在**视口**里的 y（浮动卡叠加的坐标基准）
    // 每张卡的实测尺寸（px）：插槽高度用
    var cardSizes by remember { mutableStateOf<Map<String, IntSize>>(emptyMap()) }
    val dragCardH = dragId?.let { cardSizes[it]?.height } ?: 0
    // 插槽高度（dp）：cardSizes 存的是 px，要除以 density 才得当 dp（直接 .dp 会呈 3x）。
    val dragMarkerH = with(density) { if (dragCardH > 0) (dragCardH / density.density).dp else 150.dp }

    // 让位展示序：把被拖卡挪到 dragPlaceIdx —— **只改显示**，草稿/预览不动。
    // 落点那行就是被拖卡的插槽（tertiary 标记），其余卡随之 animateItem 让位。
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

    fun startDrag(cardId: String, row: List<MobileDashboardWidget>) {
        val idx = draft.indexOfFirst { it.id == cardId }
        if (idx < 0) return
        dragId = cardId
        dragPlaceIdx = idx
        dragOffsetPx = 0f
        dragOffsetX = 0f
        dragSwap = false
        // 浮动卡的坐标基准 = 起始行当前在视口里的 y
        dragAnchorPx = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == rowSignature(row) }?.offset?.toFloat() ?: 0f
    }

    /**
     * 长按起点：按 y 命中**展示行**、按 x 选半宽行的左/右卡。
     *
     * `LazyListItemInfo.size` 是主轴（纵向）尺寸的 Int，取不到宽度，故横向从中点分左右直接用
     * 容器宽 —— 行两边是 16dp 对称内边距，行自身的中点就是容器中点。
     */
    fun startDragAt(start: Offset, boxWidth: Float) {
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            it.index < displayRows.size && start.y >= it.offset && start.y <= it.offset + it.size
        } ?: return
        val key = info.key as? String ?: return
        val row = rowByKey[key] ?: return
        val picked = when {
            row.size > 1 && start.x > boxWidth / 2f -> row[1]
            row.size > 1 -> row[0]
            else -> row[0]
        }
        picked.id?.let { startDrag(it, row) }
    }

    /**
     * 把 [card] 插进「去掉自己」的序列 `s` 的第 `p` 位后，它在展示行里的行号。
     * 纯序列推算 —— 和渲染用的是同一套 [buildRows]，不涉及任何坐标几何。
     */
    fun cardRowIndex(s: List<MobileDashboardWidget>, card: MobileDashboardWidget, p: Int): Int {
        val seq = s.toMutableList().apply { add(p.coerceIn(0, size), card) }
        return buildRows(seq).indexOfFirst { row -> row.any { it.id == card.id } }
    }

    fun dragBy(amount: Offset, fingerY: Float, boxWidth: Float) {
        val id = dragId ?: return
        if (displayRows.isEmpty()) return
        dragOffsetPx += amount.y
        if (amount.x != 0f) {
            dragOffsetX += amount.x
            // 半宽成对：依草稿相邻判定左右卡，卡中心越过行中隔线 → 与同伴互换
            val oi = draft.indexOfFirst { it.id == id }
            if (oi >= 0) {
                val isPair = (oi > 0 && draft[oi - 1].size == DashboardTypes.SIZE_HALF) ||
                    (oi + 1 < draft.size && draft[oi + 1].size == DashboardTypes.SIZE_HALF)
                if (isPair) {
                    val left = oi + 1 < draft.size && draft[oi + 1].size == DashboardTypes.SIZE_HALF
                    dragSwap = if (left)
                        (boxWidth * 0.25f + dragOffsetX) > boxWidth / 2f
                    else
                        (boxWidth * 0.75f + dragOffsetX) < boxWidth / 2f
                }
            }
        }
        // 手指压在展示序的哪一行
        val d = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            it.index < displayRows.size && fingerY >= it.offset && fingerY <= it.offset + it.size
        }?.index ?: return // 落在行间距 / 列表外：保持现落点
        // 落点 = 手指所在的那一行：在 0..|s| 里选一个插入位 p，使这张卡的行号最接近 d。
        // 行号只由序列决定（见 [cardRowIndex]），所以「落点行」永远和实际渲染出来的行一致 ——
        // 老写法按卡高累加推算绝对行位、漏算 12dp 行间距，落点会一直偏一点。
        // 行号对这张卡不可达时（如落单半宽卡只能并排或独占一行）退到行号最接近的候选，
        // 再用「离当前插入位最近」打破平手，插槽始终连续跟手、不卡住也不抖。
        val s = draft.filterNot { it.id == id }
        val card = draft.first { it.id == id }
        var best = dragPlaceIdx
        var bestRowDist = Int.MAX_VALUE
        var bestMoveDist = Int.MAX_VALUE
        for (p in 0..s.size) {
            val rowDist = abs(cardRowIndex(s, card, p) - d)
            val moveDist = abs(p - dragPlaceIdx)
            if (rowDist < bestRowDist || (rowDist == bestRowDist && moveDist < bestMoveDist)) {
                bestRowDist = rowDist
                bestMoveDist = moveDist
                best = p
            }
        }
        dragPlaceIdx = best
    }

    fun resetDrag() {
        dragId = null
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
        // 跨位：用当前 state 里的插入位**现拼**重排序。displaySeq 只是渲染用的瞬时值、
        // 手势闭包里多半是旧捕获（指针 input 的 key 在拖拽中不变，闭包不刷新），
        // 从 state 现读插入位落库，卡才不回弹原位。
        val seq = draft.filterNot { it.id == id }.toMutableList().apply {
            add(dragPlaceIdx.coerceIn(0, size), draft.first { it.id == id })
        }
        if (seq != draft) onReorder(seq)
        resetDrag()
    }

    fun cancelDrag() {
        resetDrag()
    }

    // 手势挂在 pointerInput 上，而它的 key（listState / draft.size）在拖拽与重排期间都不变 →
    // 闭包不会刷新，直接调上面的函数会一直用**启动那一刻**的 draft/displayRows/rowByKey 快照
    // （重排一次后行签名全变、旧表里查不到 → 半宽卡就再也拿不起来）。经 rememberUpdatedState
    // 每次重组刷新引用，手势永远调到最新一次重组的函数。
    val currentStartDrag by rememberUpdatedState<(Offset, Float) -> Unit> { s, w -> startDragAt(s, w) }
    val currentDragBy by rememberUpdatedState<(Offset, Float, Float) -> Unit> { a, y, w -> dragBy(a, y, w) }
    val currentCommitDrag by rememberUpdatedState<() -> Unit> { commitDrag() }
    val currentCancelDrag by rememberUpdatedState<() -> Unit> { cancelDrag() }

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
                .pointerInput(listState) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { start: Offset -> currentStartDrag(start, size.width.toFloat()) },
                        onDragEnd = { currentCommitDrag() },
                        onDragCancel = { currentCancelDrag() },
                        onDrag = { change, amount ->
                            change.consume()
                            // change.position 是本节点（= 视口）坐标，用来判定手指落在哪一行
                            currentDragBy(amount, change.position.y, size.width.toFloat())
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

            // 浮动卡本体：叠加在列表之上，纵向 = 起始卡视口 y + 手指相对位移，横向起点 =
            // 卡**原地**在行里的位置。让位只重排列表、不惊动它，卡始终贴着手指且尺寸不变。
            dragId?.let { id ->
                val dragged = draft.firstOrNull { it.id == id }
                if (dragged != null) {
                    val half = dragged.size == DashboardTypes.SIZE_HALF
                    // 半宽行的右卡：横向起点是右半边（否则长按一按就从左半边飞出来）。
                    // 判定用 buildRows —— 与渲染同一套配对规则，[半宽,半宽,半宽] 这种也能算对。
                    val rightHalf = half && buildRows(draft)
                        .firstOrNull { r -> r.any { it.id == id } }
                        ?.let { r -> r.size > 1 && r[1].id == id }
                        ?: false
                    FloatingCardSlot(
                        rightHalf = rightHalf,
                        halfWidth = half,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(horizontal = 16.dp)
                            .graphicsLayer {
                                translationY = dragAnchorPx + dragOffsetPx
                                translationX = dragOffsetX
                                alpha = 0.95f
                                // 不要在这里打 shadowElevation：这一层是**整行宽**的（半宽右卡
                                // 左边还留着一个占位 Spacer），而 graphicsLayer 的投影是按图层的
                                // 矩形外框投的、不认内容 —— 会凭空在卡左边投出半宽一块阴影。
                                // 卡本体（DashboardWidgetHost 的 Card）本来就是 0 高度，跟随手感
                                // 靠 0.95 的透明度和虚线槽位表达，不需要投影。
                            }
                    ) {
                        DashboardWidgetHost(
                            widget = dragged,
                            data = state.previewById[dragged.id],
                            error = state.previewMessageById[dragged.id],
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
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
 * 浮动卡的横向落位（与 [RowContent] 同口径，好让卡一按下去就在原地，不横跳）：
 * 整宽卡占满整行；落单半宽卡占左半边；半宽行的**右卡**左边留白、占右半边。
 */
@Composable
private fun FloatingCardSlot(
    rightHalf: Boolean,
    halfWidth: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    when {
        rightHalf -> Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.weight(1f))
            Box(Modifier.weight(1f)) { content() }
        }

        halfWidth -> Box(modifier.fillMaxWidth(0.5f)) { content() }

        else -> Box(modifier.fillMaxWidth()) { content() }
    }
}

/**
 * 一个显示行：FULL 独占一行整宽；连续两个 HALF 并排（weight 各半）占一行。
 * 落单的 HALF **保持半宽**（不「占位补成整宽」），只靠左。
 *
 * 传入的 `row` 已是**展示序**的行：被拖卡不在原位置，而在它的落点行（见 [SlotHost]）。
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