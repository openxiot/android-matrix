package cc.openxiot.wematrix.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.openxiot.wematrix.data.api.MobileCatalog
import cc.openxiot.wematrix.data.api.MobileDashboardWidget
import cc.openxiot.wematrix.data.repository.MobileDashboardCatalogRepository
import cc.openxiot.wematrix.data.repository.MobileDashboardRepository
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
    val error: String? = null,
    val widgets: List<MobileDashboardWidget> = emptyList(),
    /** 已保存布局的乐观锁版本（保存时 CAS 用；缺省 0 = 从没保存过的预置） */
    val version: Long = 0,
    val dataById: Map<String, Map<String, Any?>> = emptyMap(),
    val messageById: Map<String, String> = emptyMap(),

    // ---- 编辑态 ----
    /** 编辑页的**实时预览**：按草稿 render 出的每张卡取数（按 id 收，同 [dataById]/[messageById]） */
    val previewById: Map<String, Map<String, Any?>> = emptyMap(),
    val previewMessageById: Map<String, String> = emptyMap(),
    val editing: Boolean = false,
    val draft: List<MobileDashboardWidget> = emptyList(),
    val dirty: Boolean = false,
    val saving: Boolean = false,
    /** 加卡弹层可见 */
    val pickerVisible: Boolean = false,
    /** 正在编辑的卡 id（null = 没有开着的编辑器）；编辑态下每张卡可点开 */
    val editingId: String? = null,
    /** 级联候选（进编辑态取一次）；设备/服务/方法/字段的选择都读它 */
    val catalog: MobileCatalog? = null,
    /** 保存 / 恢复的反馈（含服务端冲突原文） */
    val message: String? = null,
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
            _uiState.value = MobileDashboardUiState(error = e.message ?: "网络错误")
            return
        }

        val result = repository.render(rootId, layout.widgets).getOrElse { e ->
            _uiState.value = MobileDashboardUiState(
                widgets = layout.widgets,
                error = e.message ?: "取数失败"
            )
            return
        }

        val dataById = HashMap<String, Map<String, Any?>>()
        val messageById = HashMap<String, String>()
        result.widgets.forEach { item ->
            if (item.success && item.data != null) {
                dataById[item.id.orEmpty()] = item.data
            } else {
                item.id?.let { messageById[it] = item.message ?: "取数失败" }
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
            val msg = HashMap<String, String>()
            resp.widgets.forEach { item ->
                if (item.success && item.data != null) {
                    data[item.id.orEmpty()] = item.data
                } else {
                    item.id?.let { msg[it] = item.message ?: "配置不完整" }
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
            val widgets = _uiState.value.widgets.ifEmpty {
                repository.getLayout(rootId).getOrNull()?.widgets ?: emptyList()
            }
            committed = widgets
            _uiState.value = _uiState.value.copy(
                editing = true,
                draft = widgets.map(::copyWidget),
                catalog = catalog,
                dirty = false,
                saving = false,
                pickerVisible = false,
                editingId = null,
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

    /** picker 加一张卡：默认 config + 默认尺寸，插到末尾，顺手打开它的编辑器。 */
    fun addWidget(type: String) {
        val widget = MobileDashboardWidget(
            id = "draft-" + (++idCounter),
            type = type,
            size = DashboardTypes.defaultSize(type),
            config = DashboardTypes.defaultConfig(type)
        )
        _uiState.value = _uiState.value.copy(
            draft = _uiState.value.draft + widget,
            pickerVisible = false,
            editingId = widget.id
        )
        refreshDirty()
    }

    fun openEditor(id: String) {
        _uiState.value = _uiState.value.copy(editingId = id, message = null)
    }

    fun closeEditor() {
        _uiState.value = _uiState.value.copy(editingId = null)
    }

    /** 保存编辑器里改完的卡（title / size / config 整体替换，保留 id 与 type）。 */
    fun commitCard(id: String, title: String?, size: String, config: Map<String, Any?>) {
        val draft = _uiState.value.draft
        val idx = draft.indexOfFirst { it.id == id }
        if (idx < 0) return
        val updated = draft.toMutableList().apply {
            this[idx] = this[idx].copy(
                title = title?.takeIf { it.isNotBlank() },
                size = size,
                config = config
            )
        }
        _uiState.value = _uiState.value.copy(
            draft = updated,
            editingId = null
        )
        refreshDirty()
    }

    fun removeWidget(id: String) {
        _uiState.value = _uiState.value.copy(
            draft = _uiState.value.draft.filterNot { it.id == id },
            editingId = if (_uiState.value.editingId == id) null else _uiState.value.editingId
        )
        refreshDirty()
    }

    /** 长按拖拽：把 from 位置的那张卡换到 to 位置（其余顺移）。 */
    fun moveWidget(from: Int, to: Int) {
        val draft = _uiState.value.draft
        if (from !in draft.indices || to !in draft.indices || from == to) return
        val list = draft.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        _uiState.value = _uiState.value.copy(draft = list)
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
                        message = e.message ?: "保存失败"
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
                        draft = preset.widgets,
                        message = "已载入默认布局，仍需手动保存"
                    )
                    refreshDirty()
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        saving = false,
                        message = e.message ?: "恢复默认失败"
                    )
                }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private fun copyWidget(w: MobileDashboardWidget) = w.copy(config = w.config.toMutableMap())
}