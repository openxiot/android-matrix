package cc.openxiot.matrix.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.openxiot.matrix.R
import cc.openxiot.matrix.data.api.MobileDashboardWidget
import cc.openxiot.matrix.data.repository.ProductSpecRepository
import cc.openxiot.matrix.ui.components.ConfirmDialog
import cc.openxiot.matrix.ui.components.EmptyState
import cc.openxiot.matrix.ui.components.LoadingIndicator
import cc.openxiot.matrix.ui.core.asString
import cc.openxiot.matrix.ui.theme.Green
import cc.openxiot.matrix.util.cellComesFirst
import cc.openxiot.matrix.util.physicalOrder
import kotlin.math.abs

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
 * **长按拖拽排序（单卡级）**：长按哪一张就只有那一张被选中移动。
 *
 * **展示 = 落库**：列表画的就是 `arrange(草稿, id, p, σ)` 的产物（再喂给共享的 [pack] 分行），
 * 松手落库**是同一份列表** —— 所以不存在「落点框与结果对不上」：框画在哪一格，就一定落在哪一格。
 * 被拖那张自己的格子画成**绿框**（落点），卡本体浮在手指上；原位置**不留灰框**，后面的卡直接让位。
 * 让位只有纵向这一种：每张半宽卡的「占哪半格」（`side`）是它自己钉住的，插入/抽走别的卡**改变不了**
 * 它（见 [DashboardLayout] 文件头那条不变量）—— 不再有「同伴被重新配对、横着滑过去」这回事。
 *
 * 候选 = 插入位 `p`（`0..|去掉自己的草稿|`）× 半格 `σ`（整宽卡没有 σ，只有 `p`）。把卡拖回自己那一位、
 * 只把 `σ` 翻过来 = **就地翻面**，也是「两张同侧卡想配对」的唯一手法（编辑器里不设左右下拉）。
 * 打分是一个**连续代价** `dy² + k·dx² + ε·|p − 原索引|`：`dy` = 手指到候选框**所在行中心**的距离
 * （行 y 从 [LazyListState] 现取），`dx` = 手指到候选框中心的距离，`ε` 只在几何完全打平时决胜
 * （同一格有多种 (p, σ) 写法）—— 于是那套四级字典序比较与「朝移动方向走」的方向偏好都不需要了。
 * 手指落在行间空隙 / 列表外时保持现落点。
 *
 * 被拖的卡本体尺寸不变，只随手指浮动（横向也贴手指：`σ` 一翻浮层换半边，而累计的横向位移**刚好
 * 抵消**，看不出跳）；松手只把最终列表交给草稿，其余卡整体让一格（animateItem 平滑滑动）。
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                    Text(
                        text = stringResource(R.string.home_edit_layout_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    // 恢复默认只改草稿，仍需手动保存
                    TextButton(onClick = { dashboardViewModel.restoreDefault(rootId) }, enabled = !state.saving) {
                        Text(stringResource(R.string.home_reset_layout))
                    }
                    TextButton(
                        onClick = { dashboardViewModel.save(rootId) },
                        enabled = state.dirty && !state.saving
                    ) {
                        Text(stringResource(R.string.home_save_layout))
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
            title = stringResource(R.string.home_discard_title),
            message = stringResource(R.string.home_discard_message),
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
    var dragIndex by remember { mutableStateOf(0) }            // 落位：被拖卡在**结果列表**里的下标
    var dragSide by remember { mutableStateOf<String?>(null) } // 落位：半宽卡占哪半格（整宽卡恒为 null）
    var dragOffsetPx by remember { mutableStateOf(0f) }        // 手指纵向位移（浮动卡跟随用）
    var dragOffsetX by remember { mutableStateOf(0f) }         // 手指横向位移（浮动卡跟随用）
    var dragAnchorPx by remember { mutableStateOf(0f) }        // 起始行在**视口**里的 y（浮动卡叠加的坐标基准）
    // 每张卡的实测尺寸（px）：落点框的高度用它（被拖那张的尺寸在拖动中不更新，故仍是拖动前的实测值）
    var cardSizes by remember { mutableStateOf<Map<String, IntSize>>(emptyMap()) }
    val dragCardH = dragId?.let { cardSizes[it]?.height } ?: 0
    // 框高（dp）：cardSizes 存的是 px，要除以 density 才得当 dp（直接 .dp 会呈 3x）。
    val dragMarkerH = with(density) { if (dragCardH > 0) (dragCardH / density.density).dp else 150.dp }

    // 展示列表就是「落库列表」：没在拖时是草稿本身，拖动中是 arrange 的产物（唯一一份真值）
    val displayList = remember(draft, dragId, dragIndex, dragSide) {
        val id = dragId
        if (id == null) draft else arrange(draft, id, dragIndex, dragSide)
    }
    val displayRows = remember(displayList) { pack(displayList) }
    // 行 key：LazyColumn 的 item key 与拖拽签名共用这一份（[rowKeys] 保证两两不同 —— 撞 key 会崩，
    // 而 id 重复的脏数据真出现过）
    val rowKeyList = remember(displayRows) { rowKeys(displayRows) }
    // 行签名 → 行内容：命中 y 后反查是哪个行
    val rowByKey = displayRows.mapIndexed { i, row -> rowKeyList[i] to row }.toMap()

    fun startDrag(cardId: String, anchorKey: String) {
        val idx = draft.indexOfFirst { it.id == cardId }
        if (idx < 0) return
        dragId = cardId
        dragIndex = idx
        // 起始半格 = 它自己的有效半格（草稿已物化），于是**第一帧画出来就是原样**，画面不跳
        dragSide = effectiveSides(draft).getOrNull(idx)
        dragOffsetPx = 0f
        dragOffsetX = 0f
        // 浮动卡的坐标基准 = 起始行当前在视口里的 y（起始行就是手指按下那一行）
        dragAnchorPx = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == anchorKey }?.offset?.toFloat() ?: 0f
    }

    /**
     * 长按起点：按 y 命中**展示行**、按 x 选并排行里的左/右卡。
     *
     * `LazyListItemInfo.size` 是主轴（纵向）尺寸的 Int，取不到宽度，故横向从中点分左右直接用
     * 容器宽 —— 行两边是 16dp 对称内边距，行自身的中点就是容器中点。
     * 单格行没有左右可挑：那一格是谁就是谁（落单的半宽卡靠哪边由它自己的 `side` 决定，不按手指）。
     */
    fun startDragAt(start: Offset, boxWidth: Float) {
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            it.index < displayRows.size && start.y >= it.offset && start.y <= it.offset + it.size
        } ?: return
        val key = info.key as? String ?: return
        val row = rowByKey[key] ?: return
        val picked = if (row.size > 1 && start.x > boxWidth / 2f) row[1] else row[0]
        picked.id?.let { startDrag(it, key) }
    }

    /** 候选框**所在行中心**的 y（视口坐标）：行号就是 LazyColumn 的 item 下标（行在最前）。 */
    fun rowCenterPx(rowIndex: Int): Float {
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.isEmpty()) return 0f
        visible.firstOrNull { it.index == rowIndex }?.let { return it.offset + it.size / 2f }
        // 不在可见范围：夹到最近的一行 —— 看不见的行本来就该输给手边那行
        val first = visible.first()
        val last = visible.last()
        val anchor = when {
            rowIndex <= first.index -> first
            rowIndex >= last.index -> last
            else -> visible.last { it.index < rowIndex }
        }
        return anchor.offset + anchor.size / 2f
    }

    fun dragBy(amount: Offset, finger: Offset, boxWidth: Float) {
        val id = dragId ?: return
        if (displayRows.isEmpty()) return
        dragOffsetPx += amount.y
        dragOffsetX += amount.x
        // 手指压在展示序的哪一行：落在行间距 / 列表外就保持现落点（不猜）
        val overRow = listState.layoutInfo.visibleItemsInfo.any {
            it.index < displayRows.size && finger.y >= it.offset && finger.y <= it.offset + it.size
        }
        if (!overRow) return

        val dragged = draft.firstOrNull { it.id == id } ?: return
        val originIdx = draft.indexOfFirst { it.id == id }
        // 候选 = 插入位 × 半格（整宽卡没有半格，只有插入位）。代价是**连续的**：
        //   dy² 行是主要判据（手指离哪一行的框中心近），dx² 在同一行内分左右半格，
        //   ε·|p − 原索引| 只在几何完全打平时决胜（同一格往往有多种 (p, σ) 写法），
        //   于是不需要「朝移动方向走」那类方向偏好，也不会有并列抖动。
        var bestIndex = dragIndex
        var bestSide = dragSide
        var bestCost = Float.MAX_VALUE
        val s = draft.filterNot { it.id == id }
        for (p in 0..s.size) {
            for (sigma in sideCandidates(dragged.size)) {
                val rows = pack(arrange(draft, id, p, sigma))
                val (fr, fc) = cellOf(rows, id)
                if (fr < 0) continue
                // **落点框就是这一格**（展示列表 = arrange 的产物），所以这里量的和画的是同一个东西
                val dy = finger.y - rowCenterPx(fr)
                val dx = finger.x - cellCenterPx(rows[fr], fc, boxWidth)
                val cost = dy * dy + HORIZONTAL_WEIGHT * dx * dx + SIDE_TIEBREAK * abs(p - originIdx)
                if (cost < bestCost) {
                    bestCost = cost
                    bestIndex = p
                    bestSide = sigma
                }
            }
        }
        dragIndex = bestIndex
        dragSide = bestSide
    }

    fun resetDrag() {
        dragId = null
        dragIndex = 0
        dragSide = null
        dragOffsetPx = 0f
        dragOffsetX = 0f
        dragAnchorPx = 0f
    }

    /**
     * 松手：把 `arrange` 的产物整体交给草稿。展示列表与落库列表**是同一个函数算出来的同一份**，
     * 所以卡不会回弹原位，也不存在「框和落点对不上」。
     *
     * 手势闭包里的 `draft` 多半是旧捕获（指针 input 的 key 在拖拽中不变，闭包不刷新），
     * 而 `dragIndex` / `dragSide` 是 `MutableState`，读的是**当下**的值 —— 经
     * `rememberUpdatedState` 每次重组刷新引用后，这里永远是最新那一次重组的状态。
     */
    fun commitDrag() {
        val id = dragId ?: return
        val seq = arrange(draft, id, dragIndex, dragSide)
        if (seq != draft) onReorder(seq)
        resetDrag()
    }

    fun cancelDrag() {
        resetDrag()
    }

    // 手势挂在 pointerInput 上，而它的 key（listState）在拖拽与让位重排期间都不变 →
    // 闭包不会刷新，直接调上面的函数会一直用**启动那一刻**的 draft/displayRows/rowByKey 快照
    // （让位一次后行 key 全变、旧表里查不到 → 卡就再也拿不起来/算不对落点）。经
    // rememberUpdatedState 每次重组刷新引用，手势永远调到最新一次重组的函数。
    val currentStartDrag by rememberUpdatedState<(Offset, Float) -> Unit> { s, w -> startDragAt(s, w) }
    val currentDragBy by rememberUpdatedState<(Offset, Offset, Float) -> Unit> { a, f, w -> dragBy(a, f, w) }
    val currentCommitDrag by rememberUpdatedState<() -> Unit> { commitDrag() }
    val currentCancelDrag by rememberUpdatedState<() -> Unit> { cancelDrag() }

    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        state.message?.let { msg ->
            Text(
                text = msg.asString(),
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
                            // change.position 是本节点（= 视口）坐标：y 判定落在哪一行，x 判定这一行的哪半格
                            currentDragBy(amount, change.position, size.width.toFloat())
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
                    item(key = "empty") { EmptyState(stringResource(R.string.home_dashboard_empty)) }
                }
                itemsIndexed(displayRows, key = { i, _ -> rowKeyList[i] }) { _, row ->
                    // animateItem：让位时行按新 key 平滑滑开/合拢；拖拽手势在最外层，不因这些行的 key 变化而中断
                    Box(Modifier.animateItem()) {
                        RowContent(
                            row = row,
                            state = state,
                            dragId = dragId,
                            onOpen = onOpen,
                            markerHeight = dragMarkerH,
                            onSize = { id, size -> if (dragId != id) cardSizes = cardSizes + (id to size) }
                        )
                    }
                }
                item(key = "add") { AddCardButton(onClick = onShowPicker) }
                item(key = "spacer") { Spacer(Modifier.height(12.dp)) }
            }

            // 浮动卡本体：叠加在列表之上，纵向 = 起始行视口 y + 手指相对位移，横向起点 =
            // **落位那一格**所在的半边（σ 一翻就换半边，而累计的横向位移恰好抵消，看不出跳）。
            // 让位只重排列表、不惊动它，卡始终贴着手指且尺寸不变。
            dragId?.let { id ->
                val dragged = draft.firstOrNull { it.id == id }
                if (dragged != null) {
                    val half = dragged.size == DashboardTypes.SIZE_HALF
                    val rightHalf = half && dragSide == DashboardTypes.SIDE_RIGHT
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
                            error = state.previewMessageById[dragged.id]?.asString(),
                            modifier = Modifier
                                .fillMaxWidth()
                                // 手上这张卡整圈画**实线**框（静置卡是细虚线、落点框是绿虚线，
                                // 只有它是实线），移动时一眼认得出。口径同 [CellHost] 的静置卡：
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
        // 浮动的那张卡也按**物理**半格摆：`side` 是持久化的物理左右，
        // 落点框画在哪、松手后就落在哪，两者必须同一个坐标系。
        rightHalf -> Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (cellComesFirst(true, LocalLayoutDirection.current)) {
                Box(Modifier.weight(1f)) { content() }
                Spacer(Modifier.weight(1f))
            } else {
                Spacer(Modifier.weight(1f))
                Box(Modifier.weight(1f)) { content() }
            }
        }

        halfWidth -> Box(modifier.fillMaxWidth(0.5f)) { content() }

        else -> Box(modifier.fillMaxWidth()) { content() }
    }
}

/**
 * 一个显示行（行由共享的 [pack] 给）：`FULL` 独占一行整宽；两张 `HALF` 并排（weight 各半）；
 * 落单的 `HALF` 也占一行，靠左还是**靠右**看它自己的 `side`，另一半留白（不占位补齐）。
 *
 * 落单那一格用「另一个 weight(1f) 的 Spacer」凑成同行两格，而不是 `fillMaxWidth(0.5f)`：
 * 宽度与并排时那一格逐像素一致，从并排变落单时卡不会悄悄变宽。
 */
@Composable
private fun RowContent(
    row: List<MobileDashboardWidget>,
    state: MobileDashboardUiState,
    dragId: String?,
    onOpen: (String) -> Unit,
    markerHeight: Dp,
    onSize: (String, IntSize) -> Unit
) {
    if (row.size == 2) {
        // 与只读页同一条：同行两张半宽卡**等高**（行高取高的那张）。编辑页也得等高 —— 不然
        // 排好版保存回去、到首页换一种高度，等于编辑时的预览在骗人。
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 数据序第 0 张 = 物理左半格，RTL 下倒序抵消 Row 的镜像。
            // 这一步与 `cellCenterPx`（第 0 格 = 左 1/4）和 `startDragAt`（左手点第 0 张）
            // 是同一个坐标系 —— 三者一旦有一处镜像了，拖动就会选错卡、落点框画错地方。
            physicalOrder(row, LocalLayoutDirection.current).forEach { w ->
                CellHost(w, state, dragId, onOpen, markerHeight, onSize, Modifier.weight(1f).fillMaxHeight())
            }
        }
        return
    }
    val w = row[0]
    if (w.size != DashboardTypes.SIZE_HALF) {
        CellHost(w, state, dragId, onOpen, markerHeight, onSize, Modifier.fillMaxWidth())
        return
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val onRight = w.side == DashboardTypes.SIDE_RIGHT
        if (cellComesFirst(onRight, LocalLayoutDirection.current)) {
            CellHost(w, state, dragId, onOpen, markerHeight, onSize, Modifier.weight(1f))
            Spacer(Modifier.weight(1f))
        } else {
            Spacer(Modifier.weight(1f))
            CellHost(w, state, dragId, onOpen, markerHeight, onSize, Modifier.weight(1f))
        }
    }
}

/**
 * 一格。真卡照常渲染（细虚线框 + 点击进编辑）；**被拖的那张**这一格画成落点框（绿框 + 12% 绿底，
 * 口径同 webapp 看板的 `.drop-outline`），卡本体由外层叠加的浮动位负责。
 *
 * 落点框画的**就是**这张卡在 `arrange` 产物里的那一格（展示列表 = 落库列表），所以框在哪就一定
 * 落在哪 —— 不需要另算一遍「落点框该画在哪」，也就不会对不上。框高用被拖卡的实测高度（拖动中不更新）。
 */
@Composable
private fun CellHost(
    widget: MobileDashboardWidget,
    state: MobileDashboardUiState,
    dragId: String?,
    onOpen: (String) -> Unit,
    markerHeight: Dp,
    onSize: (String, IntSize) -> Unit,
    modifier: Modifier
) {
    if (widget.id != null && widget.id == dragId) {
        Box(modifier = modifier) {
            MarkerBox(
                height = markerHeight,
                background = Green.copy(alpha = 0.12f),
                border = Green,
                strokeWidth = 2.dp
            )
        }
        return
    }
    DashboardWidgetHost(
        widget = widget,
        data = state.previewById[widget.id],
        error = state.previewMessageById[widget.id]?.asString(),
        // 卡始终包一层 fillMaxWidth：DashboardWidgetHost 的 Card 默认包内容宽，
        // 直接传槽宽 modifier 会让半宽卡缩成内容宽（≈ 1/4），必须让它填满槽位。
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { onSize(widget.id.orEmpty(), it) }
            .dashedBorder(color = MaterialTheme.colorScheme.primary, strokeWidth = 1.5.dp)
            .padding(2.dp)
            .clickable { widget.id?.let(onOpen) }
    )
}

/** 落点框：一格占位框（高 = 被拖卡原高），不含卡内容。 */
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

/** 整宽卡可落的半格只有 `null` 一种；半宽卡左右都行（左 = 开一行，右 = 填开着的行）。 */
private fun sideCandidates(size: String?): List<String?> =
    if (size == DashboardTypes.SIZE_HALF) listOf(DashboardTypes.SIDE_LEFT, DashboardTypes.SIDE_RIGHT)
    else listOf(null)

/**
 * 某张卡在排布结果里的 `行 to 格`；不在里面（不该发生）给 `-1 to -1`。
 *
 * 这是「落点框在哪」与「落点算不算得对」共用的同一把尺子 —— 量的是 `arrange` 的产物，
 * 也就是**画出来的那份列表**。
 */
private fun cellOf(rows: List<List<MobileDashboardWidget>>, id: String): Pair<Int, Int> {
    rows.forEachIndexed { r, row ->
        row.forEachIndexed { c, w -> if (w.id == id) return r to c }
    }
    return -1 to -1
}

/**
 * 一格在容器里的横向中心（px）：并排行各占一半（中点在 1/4 与 3/4 处）；单格行整宽在中间，
 * 半宽看它自己的 `side` —— 与 [RowContent] 的摆法同口径（行两边是 16dp 对称内边距，
 * 行自身的中点就是容器中点，故直接用容器宽算）。
 */
private fun cellCenterPx(row: List<MobileDashboardWidget>, cell: Int, boxWidth: Float): Float = when {
    row.size == 2 -> boxWidth * (if (cell == 0) 0.25f else 0.75f)
    row[0].size != DashboardTypes.SIZE_HALF -> boxWidth / 2f
    row[0].side == DashboardTypes.SIDE_RIGHT -> boxWidth * 0.75f
    else -> boxWidth * 0.25f
}

/** 落点打分的两个常数：行是主要判据，横向只在同一行内分左右；ε 只在几何完全打平时决胜。 */
private const val HORIZONTAL_WEIGHT = 4f
private const val SIDE_TIEBREAK = 1f

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
                text = stringResource(R.string.home_add_card),
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