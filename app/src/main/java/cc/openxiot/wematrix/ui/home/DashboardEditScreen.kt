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
import cc.openxiot.wematrix.ui.theme.Green
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
 * - 原位置 → **灰框**（「它从哪来」），落点 → **绿框**（「它要去哪」）—— 一冷一暖、
 *   一个中性一个饱和，扫一眼就分得开（口径同 webapp 那个看板：`.cdk-drag-placeholder` 灰、
 *   `.drop-outline` 绿，两个框一起看才读得出「从哪来、往哪去」）；
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
    // 每张卡的实测尺寸（px）：原位框 / 落点框的高度都用它
    var cardSizes by remember { mutableStateOf<Map<String, IntSize>>(emptyMap()) }
    val dragCardH = dragId?.let { cardSizes[it]?.height } ?: 0
    // 框高（dp）：cardSizes 存的是 px，要除以 density 才得当 dp（直接 .dp 会呈 3x）。
    val dragMarkerH = with(density) { if (dragCardH > 0) (dragCardH / density.density).dp else 150.dp }

    // 静态配对表：谁和谁**原本**并排 —— 拖动期间不许为了补上空出来的半格而重新配对
    val staticMates = remember(draft) { staticMates(draft) }

    /**
     * 拖动中的展示格：`draft` 每张卡一格，**被拖卡那一格换成原位框**（其余卡一格都不动）；
     * 落位不在原索引时，再插一格落点框 —— 落点框是「多出来的那一格」，只有它后面的卡整体挪一格。
     * 原位置不会被人补上，这就是「不要自动布局」。
     */
    val displayRows = remember(draft, dragId, dragPlaceIdx, dragSwap, staticMates) {
        val id = dragId
        val cells = if (id == null) draft.map { EditCell.card(it) }
        else dragCells(draft, id, dragPlaceIdx, swap = dragSwap)
        buildEditRows(cells, staticMates)
    }
    // 行签名 → 行内容：命中 y 后反查是哪个行/哪一格
    val rowByKey = displayRows.associate { rowSignature(it) to it }

    fun startDrag(cardId: String, row: List<EditCell>) {
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
        picked.widget?.id?.let { startDrag(it, row) }
    }

    fun dragBy(amount: Offset, fingerY: Float, boxWidth: Float) {
        val id = dragId ?: return
        if (displayRows.isEmpty()) return
        dragOffsetPx += amount.y
        if (amount.x != 0f) {
            dragOffsetX += amount.x
            // 半宽成对：**原本并排**的那张在左/在右（口径同 commitDrag，不是「相邻的半宽卡」——
            // [H1,H2,H3] 里 H2 的同伴是 H1，不是 H3），卡中心越过行中隔线 → 与它互换。
            // 起点按**卡自己**所在的那半格算：在左半格就是 0.25、在右半格就是 0.75。
            val oi = draft.indexOfFirst { it.id == id }
            val mateIdx = mateIndex(draft, id)
            dragSwap = when {
                mateIdx < 0 -> false
                mateIdx < oi -> (boxWidth * 0.75f + dragOffsetX) < boxWidth / 2f // 我在右半格
                else -> (boxWidth * 0.25f + dragOffsetX) > boxWidth / 2f        // 我在左半格
            }
        }
        // 手指压在展示序的哪一行
        val d = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            it.index < displayRows.size && fingerY >= it.offset && fingerY <= it.offset + it.size
        }?.index ?: return // 落在行间距 / 列表外：保持现落点
        // 落点 = 手指所在的那一行：在 0..|s| 里选一个插入位 p，使这张卡的**落点框**行号最接近 d。
        // 行号只由格序列推算（见 [frameRowIndex]，与渲染同一套 [buildEditRows]），不涉及坐标几何，
        // 所以「落点框画在哪一行」和这里算的永远一致 —— 老写法按卡高累加推算绝对行位、漏算 12dp
        // 行间距，落点会一直偏一点。
        // 行号对这张卡不可达时（如原位框占着原格、落点框只能落在它前后）退到最接近的候选，再用
        // 「离当前插入位最近」打破平手：同一位置平手时保持不动，落点框不抖。
        val s = draft.filterNot { it.id == id }
        var best = dragPlaceIdx
        var bestRowDist = Int.MAX_VALUE
        var bestMoveDist = Int.MAX_VALUE
        for (p in 0..s.size) {
            val rowDist = abs(frameRowIndex(draft, id, p, staticMates) - d)
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
            val mate = mateIndex(draft, id)
            if (mate >= 0) {
                val order = draft.toMutableList()
                order[originIdx] = draft[mate]
                order[mate] = draft[originIdx]
                if (order != draft) onReorder(order)
            }
            resetDrag(); return
        }
        // 跨位：用当前 state 里的插入位**现拼**重排序。展示格只是渲染用的瞬时值、
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
                            modifier = Modifier
                                .fillMaxWidth()
                                // 手上这张卡整圈画**实线**框（静置卡是细虚线、落点槽位是绿虚线，
                                // 只有它是实线），移动时一眼认得出。口径同 [SlotHost] 的静置卡：
                                // 框画在本层、再用 2dp 内边距把卡缩进去，框才不会被 Card 自己的
                                // 不透明面盖掉一半（链上先画的画在下面）。
                                // 这一层就是**卡自身**的宽度（半宽右卡由 FloatingCardSlot 缩到右半边），
                                // 不是整行宽 —— 不会重演之前那个半宽幽灵阴影。
                                .solidBorder(
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp
                                )
                                .padding(2.dp)
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
 * 一个显示行：FULL 独占一行整宽；半宽格两两并排（weight 各半）占一行。
 * 落单的 HALF **保持半宽**（不「占位补成整宽」），只靠左。
 *
 * 传入的 `row` 是[拖动中的展示格][EditCell]：可能是真卡，也可能是原位框 / 落点框。
 */
@Composable
private fun RowContent(
    row: List<EditCell>,
    state: MobileDashboardUiState,
    onOpen: (String) -> Unit,
    markerHeight: Dp,
    onSize: (String, IntSize) -> Unit
) {
    if (row.size == 2) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SlotHost(row[0], state, onOpen, markerHeight, onSize, Modifier.weight(1f))
            SlotHost(row[1], state, onOpen, markerHeight, onSize, Modifier.weight(1f))
        }
    } else {
        val wide = if (row[0].size == DashboardTypes.SIZE_HALF) 0.5f else 1f
        SlotHost(row[0], state, onOpen, markerHeight, onSize, Modifier.fillMaxWidth(wide))
    }
}

/**
 * 一格。真卡照常渲染（细虚线框 + 点击进编辑）；**原位框**（被拖卡原来那一格：留空、别的卡不许
 * 补上来）与**落点框**（它要落进去的那一格）只画背景 + 虚线框，高 = 被拖卡原高、宽 = 本格宽度，
 * 卡本体由外层叠加的浮动位负责。
 */
@Composable
private fun SlotHost(
    cell: EditCell,
    state: MobileDashboardUiState,
    onOpen: (String) -> Unit,
    markerHeight: Dp,
    onSize: (String, IntSize) -> Unit,
    modifier: Modifier
) {
    val widget = cell.widget
    Box(modifier = modifier) {
        when {
            // 落点：卡要落进去的那一格。**绿**（饱和 + 同色底），跟静置卡的 primary 细虚线、
            // 原位框的灰区分得开；口径同 webapp 看板的 `.drop-outline`（也是绿框 + 12% 绿底）。
            cell.drop -> MarkerBox(
                height = markerHeight,
                background = Green.copy(alpha = 0.12f),
                border = Green,
                strokeWidth = 2.dp
            )
            // 原位：卡原来那一格，留一个**灰**框（中性、无彩），谁都不去占它。灰与绿是「从哪来 /
            // 往哪去」最省事的一对区分（同 webapp 的 `.cdk-drag-placeholder`）。
            cell.origin -> MarkerBox(
                height = markerHeight,
                background = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                border = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                strokeWidth = 2.dp
            )
            widget != null -> DashboardWidgetHost(
                widget = widget,
                data = state.previewById[widget.id],
                error = state.previewMessageById[widget.id],
                // 卡始终包一层 fillMaxWidth：DashboardWidgetHost 的 Card 默认包内容宽，
                // 直接传槽宽 modifier 会让半宽卡缩成内容宽（≈ 1/4），必须让它填满槽位。
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

/**
 * 原位框 / 落点框：一格占位框（高 = 被拖卡原高），不含卡内容。
 * 两者只靠**颜色**区分（调用处给：原位灰、落点绿），形状与线宽刻意保持同款。
 */
@Composable
private fun MarkerBox(height: Dp, background: Color, border: Color, strokeWidth: Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(background, RoundedCornerShape(16.dp))
            .dashedBorder(color = border, strokeWidth = strokeWidth)
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

/**
 * 拖动中显示的一格。[widget] 是真卡；[origin] / [drop] 是两个框，不是真卡：
 * **原位框**是被拖卡原来那一格（留空），**落点框**是它要落进去的那一格（多出来的那一格）。
 * 两个框都按被拖卡的 [size] 占位，高度由外面的 `markerHeight` 定。
 */
private data class EditCell(
    val widget: MobileDashboardWidget?,
    val size: String,
    val origin: Boolean = false,
    val drop: Boolean = false
) {
    /** 行签名里的这一格：真卡就是 id，两个框各带前缀（不会和真卡 id 撞）。 */
    val key: String
        get() = when {
            drop -> "drop:" + widget?.id.orEmpty()
            origin -> "origin:" + widget?.id.orEmpty()
            else -> widget?.id.orEmpty()
        }

    companion object {
        fun card(w: MobileDashboardWidget) = EditCell(w, sizeOf(w))
        fun origin(w: MobileDashboardWidget) = EditCell(w, sizeOf(w), origin = true)
        fun drop(w: MobileDashboardWidget) = EditCell(w, sizeOf(w), drop = true)

        private fun sizeOf(w: MobileDashboardWidget) = w.size ?: DashboardTypes.SIZE_FULL
    }
}

/** 静态配对表：id → **原本**并排的那张半宽卡 id（没有就是 null）。 */
private fun staticMates(widgets: List<MobileDashboardWidget>): Map<String, String?> {
    val mates = HashMap<String, String?>()
    buildRows(widgets).forEach { row ->
        if (row.size == 2) {
            mates[row[0].id.orEmpty()] = row[1].id
            mates[row[1].id.orEmpty()] = row[0].id
        }
    }
    return mates
}

/**
 * 与 [id] **原本并排**的那张半宽卡在草稿里的下标（没有就是 -1）。
 * 口径是 [buildRows] 的配对结果，不是「草稿里相邻的半宽卡」—— [H1,H2,H3] 里 H2 的同伴是 H1。
 */
private fun mateIndex(draft: List<MobileDashboardWidget>, id: String): Int {
    val idx = draft.indexOfFirst { it.id == id }
    val mate = draft.getOrNull(idx)?.id?.let { staticMates(draft)[it] } ?: return -1
    return draft.indexOfFirst { it.id == mate }
}

/**
 * 拖动中把被拖卡那一格换成**原位框**，并在落位 `p`（去掉自己后的插入位）插入**落点框**。
 *
 * `p == 原索引` 就是「落回原位」：不另插落点框，只把原位框也标成落点色（同一格画两个框没有意义），
 * 也正是刚开始拖的那一帧，画面不跳。原位框占着原索引，故 `p` 在原索引**之后**时要再挪一格，
 * 才是落点框在格序列里的位置。
 *
 * [swap]（纵向还在原位、只是横向越过了行中隔线）是唯一**不画原位框**的情况：那两格之内的左右互换
 * 一定要同伴让位，原位框没有立足之地 —— 同伴挪到被拖卡那一格、落点框占同伴那一格，两格互换后
 * **行数不变**（不会把下面的卡挤下去），也正是松手后 [buildRows] 的排法。
 */
private fun dragCells(
    draft: List<MobileDashboardWidget>,
    dragId: String,
    p: Int,
    swap: Boolean = false
): List<EditCell> {
    val originIdx = draft.indexOfFirst { it.id == dragId }
    if (originIdx < 0) return draft.map { EditCell.card(it) }
    if (swap && p == originIdx) {
        val mate = draft.getOrNull(mateIndex(draft, dragId))
        if (mate != null) {
            return draft.map { w ->
                when (w.id) {
                    dragId -> EditCell.card(mate)
                    else -> if (w.id == mate.id) EditCell.drop(draft[originIdx]) else EditCell.card(w)
                }
            }
        }
    }
    val samePlace = p == originIdx
    val cells = draft.mapIndexed { i, w ->
        when {
            i != originIdx -> EditCell.card(w)
            samePlace -> EditCell.origin(w).copy(drop = true)
            else -> EditCell.origin(w)
        }
    }.toMutableList()
    if (!samePlace) {
        val q = (if (p < originIdx) p else p + 1).coerceIn(0, cells.size)
        cells.add(q, EditCell.drop(draft[originIdx]))
    }
    return cells
}

/**
 * 把格序列派生成显示行。FULL 独占一行；两个相邻 HALF 成行的条件**只有**两条：
 * 它们原本就是一对，或其中一格是落点框 —— 拖动期间谁都不会为了补上空出来的半格而左右挪动
 * （「不要自动布局」），只有落点框参与重新配对。
 */
private fun buildEditRows(cells: List<EditCell>, mates: Map<String, String?>): List<List<EditCell>> {
    val rows = mutableListOf<List<EditCell>>()
    var i = 0
    while (i < cells.size) {
        val b = cells.getOrNull(i + 1)
        if (b != null && canPair(cells[i], b, mates)) {
            rows.add(listOf(cells[i], b))
            i += 2
        } else {
            rows.add(listOf(cells[i]))
            i += 1
        }
    }
    return rows
}

/** 相邻两格能不能并成一行：都是半宽，且原本就是一对、或其中一格是落点框。 */
private fun canPair(a: EditCell, b: EditCell, mates: Map<String, String?>): Boolean {
    if (a.size != DashboardTypes.SIZE_HALF || b.size != DashboardTypes.SIZE_HALF) return false
    if (a.drop || b.drop) return true
    val aId = a.widget?.id ?: return false
    val aMate = mates[aId] ?: return false
    return aMate == b.widget?.id
}

/**
 * 落位 `p` 的**落点框**落在第几行；没有落点框（`p` 就是原索引）时取原位框的行号 ——
 * 两种情况问的都是同一件事：这张卡要落在哪一行。纯格序列推算，和渲染共用 [buildEditRows]。
 *
 * 不给 [dragCells] 传 `swap`：左右互换只改同一行里两格谁左谁右，**行号不变**，问行号时不必区分。
 */
private fun frameRowIndex(
    draft: List<MobileDashboardWidget>,
    dragId: String,
    p: Int,
    mates: Map<String, String?>
): Int {
    val rows = buildEditRows(dragCells(draft, dragId, p), mates)
    val drop = rows.indexOfFirst { row -> row.any { it.drop } }
    return if (drop >= 0) drop else rows.indexOfFirst { row -> row.any { it.origin } }
}

/** 行在 LazyColumn 里的稳定 key（也是拖拽签名）：行内各格的标识拼接。 */
private fun rowSignature(row: List<EditCell>): String =
    row.joinToString("~") { it.key }

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

/**
 * 实线圆角边框（**拖动中的浮动卡**用）。
 *
 * 静置的卡是 primary 细虚线、落点槽位是绿虚线，都是「虚线」；手上这张改成**实线**，
 * 一眼就能看出"正在移动的是它"，不会和底下的卡、以及那个虚线落点槽位混在一起。
 */
private fun Modifier.solidBorder(
    color: Color,
    shape: Shape = RoundedCornerShape(16.dp),
    strokeWidth: Dp = 2.dp
): Modifier = drawWithCache {
    val stroke = Stroke(width = strokeWidth.toPx())
    val outline = shape.createOutline(size, layoutDirection, this)
    onDrawBehind {
        drawPath(
            path = Path().apply { addOutline(outline) },
            color = color,
            style = stroke
        )
    }
}