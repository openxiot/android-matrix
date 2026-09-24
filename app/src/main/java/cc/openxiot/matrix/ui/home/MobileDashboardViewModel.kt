package cc.openxiot.matrix.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.openxiot.matrix.R
import cc.openxiot.matrix.data.api.MobileCatalog
import cc.openxiot.matrix.data.api.MobileDashboardWidget
import cc.openxiot.matrix.data.repository.MobileDashboardCatalogRepository
import cc.openxiot.matrix.data.repository.MobileDashboardRepository
import cc.openxiot.matrix.ui.core.UiText
import cc.openxiot.matrix.ui.core.toUiText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 首页看板的 UI 状态。
 *
 * 只读侧：`widgets` 是布局顺序（= 阅读顺序）；`dataById` / `messageById` 把取了数的每张卡按 id 收
 * 在一起 —— **不按下标对应**。某张卡失败进 `messageById`，自己有 id 时 `data` 为空，**不拖垮整屏**。
 *
 * 编辑侧：`draft` 是**未入库**的草稿；改的是草稿，直到「保存布局」（拿 `draftVersion` 做 CAS）才落库
 * —— 冲突 / 非管理员时服务端拒绝并保留草稿。
 */
data class MobileDashboardUiState(
    val isLoading: Boolean = false,
    /** 整页级失败（布局/取数全挂）：非空时整页只显示这条告警 */
    val error: UiText? = null,
    val widgets: List<MobileDashboardWidget> = emptyList(),
    /** 已保存布局的乐观锁版本（保存时 CAS 用；缺省 0 = 从没保存过的预置） */
    val version: Long = 0,
    val dataById: Map<String, Map<String, Any?>> = emptyMap(),
    val messageById: Map<String, UiText> = emptyMap(),

    // ---- 编辑态 ----
    /** 编辑页的**实时预览**：按草稿 render 出的每张卡取数（按 id 收，同 [dataById]/[messageById]） */
    val previewById: Map<String, Map<String, Any?>> = emptyMap(),
    val previewMessageById: Map<String, UiText> = emptyMap(),
    val editing: Boolean = false,
    val draft: List<MobileDashboardWidget> = emptyList(),
    val dirty: Boolean = false,
    val saving: Boolean = false,
    /** 加卡弹层可见 */
    val pickerVisible: Boolean = false,
    /** 正在编辑的卡 id（null = 没有开着的编辑器）；编辑态下每张卡可点开 */
    val editingId: String? = null,
    /** [editingId] 那张是**刚加进来、还没提交过**的新卡：关掉编辑器要把它撤掉（见 [closeEditor]） */
    val editingIsNew: Boolean = false,
    /** 级联候选（进编辑态取一次）；设备/服务/方法/字段的选择都读它 */
    val catalog: MobileCatalog? = null,
    /** 保存 / 恢复的反馈（含服务端冲突原文） */
    val message: UiText? = null,
    /** 刚保存成功（编辑页据此自动返回上一页）；下一次进编辑态 / 退出时清掉 */
    val saved: Boolean = false
)

/**
 * 首页（可自定义看板）。
 *
 * 只读数据流：`GET /layout/{spaceId}`（未保存时后端直接返回预置，version=0）→ `POST /render` 取每张
 * 卡的数。两次都只读、按当前项目根空间鉴权。**没取到 / 未选项目都是空态**，不放一屏 0（0 是真读数）。
 *
 * 编辑数据流：进编辑态把布局拷贝成 `draft`，拉一次全局 `catalog`；加/删/排序/改 config 都只改草稿，
 * 不出网。保存 = `PUT`（版本 CAS）；恢复默认 = `GET /preset` 进草稿（不落库），仍需手动保存。
 */
class MobileDashboardViewModel : ViewModel() {
    private val repository = MobileDashboardRepository()
    private val catalogRepository = MobileDashboardCatalogRepository()

    private val _uiState = MutableStateFlow(MobileDashboardUiState())
    val uiState: StateFlow<MobileDashboardUiState> = _uiState.asStateFlow()

    /** 已保存布局（dirty 判定的基线）；savedWidgets 对齐 `widgets`，不用去读 state */
    private var committed: List<MobileDashboardWidget> = emptyList()

    private var idCounter = 0L

    /** 页面下拉刷新入口：挂起等整页重取完（只在回来更新指示器时用）。 */
    suspend fun refreshNow(rootId: String?) {
        doLoad(rootId)
    }

    /**
     * 取整页：布局 + 每张卡的取数。每次进首页都重取（切 Tab / 切项目回来都取一次）：
     * 接口只读、代价几次请求，比让用户看一份可能过期的数划算。
     */
    fun load(rootId: String?) {
        viewModelScope.launch { doLoad(rootId) }
    }

    private suspend fun doLoad(rootId: String?) {
        if (rootId.isNullOrEmpty()) {
            _uiState.value = MobileDashboardUiState()
            return
        }
        // 已有布局就不打转（切 Tab 回来的重取），免得整页闪一下空白
        _uiState.value = _uiState.value.copy(
            isLoading = _uiState.value.widgets.isEmpty(),
            error = null
        )

        val layout = repository.getLayout(rootId).getOrElse { e ->
            _uiState.value = MobileDashboardUiState(error = e.toUiText())
            return
        }

        val result = repository.render(rootId, layout.widgets).getOrElse { e ->
            _uiState.value = MobileDashboardUiState(
                widgets = layout.widgets,
                error = e.toUiText(R.string.err_dashboard_data)
            )
            return
        }

        val dataById = HashMap<String, Map<String, Any?>>()
        val messageById = HashMap<String, UiText>()
        result.widgets.forEach { item ->
            if (item.success && item.data != null) {
                dataById[item.id.orEmpty()] = item.data
            } else {
                item.id?.let {
                    messageById[it] = item.message?.let { m -> UiText.Raw(m) }
                        ?: UiText.Res(R.string.err_dashboard_data)
                }
            }
        }
        committed = layout.widgets
        _uiState.value = MobileDashboardUiState(
            isLoading = false,
            widgets = layout.widgets,
            version = layout.version ?: 0,
            dataById = dataById,
            messageById = messageById,
            catalog = _uiState.value.catalog
        )
    }

    // ---- 编辑预览（草稿实时 render） ----

    private var previewJob: Job? = null

    /**
     * 草稿的实时预览：草稿每变一次就**防抖** 400ms 后按草稿真 render 一遍，填 [previewById] /
     * [previewMessageById]。不完整 / 未配满的卡后端会逐卡回 message（不拖垮整页），正好在编辑器里
     * 亮出「哪里还没配」。render 只读、代价每次编辑一两个 POST，值得 —— 编辑器看到的是真实卡效果。
     */
    fun schedulePreview(rootId: String?) {
        val draft = _uiState.value.draft
        if (rootId.isNullOrEmpty() || draft.isEmpty()) {
            _uiState.value = _uiState.value.copy(previewById = emptyMap(), previewMessageById = emptyMap())
            return
        }
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            delay(400)
            if (draft != _uiState.value.draft) return@launch // 防抖期间又被改了，交给下一次调度
            val resp = repository.render(rootId, draft).getOrNull() ?: return@launch // 整页挂了：保留上次预览
            val data = HashMap<String, Map<String, Any?>>()
            val msg = HashMap<String, UiText>()
            resp.widgets.forEach { item ->
                if (item.success && item.data != null) {
                    data[item.id.orEmpty()] = item.data
                } else {
                    item.id?.let {
                        msg[it] = item.message?.let { m -> UiText.Raw(m) }
                            ?: UiText.Res(R.string.err_config_incomplete)
                    }
                }
            }
            _uiState.value = _uiState.value.copy(previewById = data, previewMessageById = msg)
        }
    }

    // ---- 编辑 ----

    fun enterEdit(rootId: String?) {
        if (rootId == null || _uiState.value.editing) return
        viewModelScope.launch {
            val catalog = catalogRepository.catalog(rootId).getOrNull()
            // 布局没取到（用户直接进编辑）就现场补取一次；取不到则编辑空布局
            val loaded = if (_uiState.value.widgets.isEmpty()) {
                repository.getLayout(rootId).getOrNull()
            } else {
                null
            }
            val widgets = _uiState.value.widgets.ifEmpty { loaded?.widgets ?: emptyList() }
            // **版本必须跟着布局一起带进来**：编辑页是**另一个 VM 实例**（首页那份不共享），它的
            // version 从 0 起步。只搬 widgets 不搬 version 的话，保存会一直提交 0 —— 而库里已有
            // 文档时（version ≥ 1）服务端 CAS 每次都判「别人改过了」，布局永远存不进去。
            val version = loaded?.version ?: _uiState.value.version
            // 半格**物化**：草稿里每张半宽卡都带显式 side，是「拖动只纵向让位」的前提
            // （见 DashboardLayout 文件头那条不变量）。**基线也过一遍**，这样「物化」本身不算改动 ——
            // 否则打开编辑页什么都没做，「保存布局」就已经是可点的了。
            val settled = deriveSides(widgets)
            committed = settled
            _uiState.value = _uiState.value.copy(
                editing = true,
                version = version,
                draft = settled.map(::copyWidget),
                catalog = catalog,
                dirty = false,
                saving = false,
                pickerVisible = false,
                editingId = null,
                editingIsNew = false,
                message = null,
                saved = false,
                previewById = emptyMap(),
                previewMessageById = emptyMap()
            )
        }
    }

    fun showPicker() {
        _uiState.value = _uiState.value.copy(pickerVisible = true, message = null)
    }

    fun hidePicker() {
        _uiState.value = _uiState.value.copy(pickerVisible = false)
    }

    /**
     * picker 加一张卡：默认 config + 默认尺寸，插到末尾，顺手打开它的编辑器。
     *
     * 半宽卡的半格按 [nextHalfSide] 定：末张是落单的 `LEFT`（右半格空着）就填进去并排，
     * 否则另起一行靠左。
     */
    fun addWidget(type: String) {
        val size = DashboardTypes.defaultSize(type)
        val draft = _uiState.value.draft
        // id 要避开**草稿里已有的**：库里那份布局可能就带着上一轮存下的 `draft-N`，而计数器是
        // 本 VM 实例自己的（重开编辑页从 0 起步），照 `++` 加会造出重复 id（见 [newDraftId]）
        val (id, next) = newDraftId(draft.mapNotNull { it.id }, idCounter)
        idCounter = next
        val widget = MobileDashboardWidget(
            id = id,
            type = type,
            size = size,
            side = if (size == DashboardTypes.SIZE_HALF) nextHalfSide(draft) else null,
            config = DashboardTypes.defaultConfig(type)
        )
        _uiState.value = _uiState.value.copy(
            draft = draft + widget,
            pickerVisible = false,
            editingId = widget.id,
            editingIsNew = true
        )
        refreshDirty()
    }

    fun openEditor(id: String) {
        _uiState.value = _uiState.value.copy(editingId = id, editingIsNew = false, message = null)
    }

    /**
     * 关掉编辑器。
     *
     * **刚加进来、还没提交过的卡，关掉 = 撤销** —— 与 web `cancelEditor` 同口径（`editorIsNew`
     * 时把那张 filter 掉）：用户点了「添加卡片」、弹层里又没配完，滑掉它就该当没加过，
     * 而不是在草稿里留一张 config 空着、要他自己再删一次的卡。
     *
     * 已经在草稿里的老卡关掉只是收弹层：它的改动本来就只在弹层里，点「确认」才写回草稿。
     */
    fun closeEditor() {
        val state = _uiState.value
        val id = state.editingId
        if (state.editingIsNew && id != null) {
            _uiState.value = state.copy(
                draft = state.draft.filterNot { it.id == id },
                editingId = null,
                editingIsNew = false
            )
            refreshDirty()
            return
        }
        _uiState.value = state.copy(editingId = null)
    }

    /**
     * 保存编辑器里改完的卡（title / size / config 整体替换，保留 id 与 type）。
     *
     * 尺寸变化会牵动半格：改成整宽 → 清掉 `side`（它没有半格）；刚改成半宽 → 按 [nextHalfSide]
     * 补一个默认位。**尺寸没动就原样保留它自己的半格** —— 那是用户拖出来的，重开编辑器保存一次
     * 不能把它抹掉。
     */
    fun commitCard(id: String, title: String?, size: String, config: Map<String, Any?>) {
        val draft = _uiState.value.draft
        val idx = draft.indexOfFirst { it.id == id }
        if (idx < 0) return
        val before = draft[idx]
        val keepSide = size == DashboardTypes.SIZE_HALF && size == before.size && before.side != null
        val updated = draft.toMutableList().apply {
            this[idx] = before.copy(
                title = title?.takeIf { it.isNotBlank() },
                size = size,
                side = when {
                    size != DashboardTypes.SIZE_HALF -> null
                    keepSide -> before.side
                    else -> nextHalfSide(draft, idx)
                },
                config = config
            )
        }
        _uiState.value = _uiState.value.copy(
            draft = updated,
            editingId = null,
            // 提交过就不再是「新卡」，之后关弹层不该把它撤掉
            editingIsNew = false
        )
        refreshDirty()
    }

    /**
     * 删掉一张卡：只把它从草稿里摘掉，**不动别的卡** —— 删掉一对里的左半张，右半张留在右半格、
     * 左边空着。这是「半格写在自己身上」白拿的：`side` 是每张卡自己的字段，谁也不靠邻居推，
     * 所以没有任何需要「补位」的地方（要空白还是满格，用户自己拖）。
     */
    fun removeWidget(id: String) {
        val state = _uiState.value
        val closing = state.editingId == id
        _uiState.value = state.copy(
            draft = state.draft.filterNot { it.id == id },
            editingId = if (closing) null else state.editingId,
            editingIsNew = if (closing) false else state.editingIsNew
        )
        refreshDirty()
    }

    /**
     * 拖拽结束后把**最终顺序**整体交还草稿（只换顺序、不改卡内容）。编辑页拖拽时在本地显示行上
     * 实时让位，松手只调这一次 —— 比每跨一格调一次少一串中间态。
     *
     * 传进来的是 [arrange] 的产物：**展示的那份列表就是这一份**，所以卡不会回弹、落点框也不可能
     * 和落点对不上。
     */
    fun applyReorder(newOrder: List<MobileDashboardWidget>) {
        val draft = _uiState.value.draft
        if (draft.size != newOrder.size) return
        _uiState.value = _uiState.value.copy(draft = newOrder)
        refreshDirty()
    }

    private fun refreshDirty() {
        _uiState.value = _uiState.value.copy(dirty = _uiState.value.draft != committed)
    }

    /** 保存：PUT（版本 CAS）。冲突 / 非管理员 → 服务端 message 显示出来、保留草稿。 */
    fun save(rootId: String?) {
        val state = _uiState.value
        if (rootId == null || !state.dirty || state.saving) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(saving = true, message = null)
            repository.saveLayout(rootId, state.version, state.draft)
                .onSuccess { layout ->
                    committed = layout.widgets
                    _uiState.value = _uiState.value.copy(
                        editing = false,
                        draft = emptyList(),
                        widgets = layout.widgets,
                        version = layout.version ?: 0,
                        saving = false,
                        editingId = null,
                        editingIsNew = false,
                        pickerVisible = false,
                        message = null,
                        saved = true
                    )
                    load(rootId) // 按新布局取一次数
                }
                .onFailure { e ->
                    // 失败保留草稿（含改动），只把服务端 message 亮出来
                    _uiState.value = _uiState.value.copy(
                        saving = false,
                        message = e.toUiText(R.string.err_dashboard_save)
                    )
                }
        }
    }

    /** 恢复默认：`GET /preset` 只进草稿（不落库），仍需手动保存。 */
    fun restoreDefault(rootId: String?) {
        val state = _uiState.value
        if (rootId == null || state.saving) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(saving = true, message = null)
            repository.getPreset(rootId)
                .onSuccess { preset ->
                    _uiState.value = _uiState.value.copy(
                        saving = false,
                        // 预置直接进草稿，仍需手动保存；半格照样物化一遍（服务端已推导，这里不依赖它）
                        draft = deriveSides(preset.widgets),
                        message = UiText.Res(R.string.home_default_layout_loaded)
                    )
                    refreshDirty()
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        saving = false,
                        message = e.toUiText(R.string.err_dashboard_restore)
                    )
                }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private fun copyWidget(w: MobileDashboardWidget) = w.copy(config = w.config.toMutableMap())
}