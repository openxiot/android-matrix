package cc.openxiot.wematrix.ui.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cc.openxiot.wematrix.WeMatrixApp

/**
 * 当前会话的响应式派生状态。
 *
 * **为什么要单拉一份这个**：`TokenManager.currentRootSpaceId` 是 SharedPreferences 的
 * 普通 getter —— 可读值、可写，但**不可观察**。Compose 里读它不会登记重组依赖，于是
 * 切项目（在另一张屏上改 SharedPreferences）后回来看板，看到的还是旧项目的数。
 * 这里把「当前项目」提成 `mutableStateOf`，选择项目时经 [setRootSpace] 一并落两份，
 * 读它的所有组合件才会跟着切。
 *
 * [canEditById] 同理：每根空间的管理员态在进项目/看板时异步查一次、按 rootId 缓存，
 * 编辑器入口是否显示直接读它 —— 不用每次进编辑态都重查。
 */
object SessionState {
    private val tokenManager = WeMatrixApp.instance.tokenManager

    /** 当前项目根空间（可观察）。读取端在组合里读它即登记重组。 */
    var currentRootSpaceId by mutableStateOf(tokenManager.currentRootSpaceId)
        private set

    /** rootId → 当前账号能否改该空间看板（角色=admin 或组织管理员）。 */
    val canEditById = mutableStateMapOf<String, Boolean>()

    /**
     * 切换项目：tokenManager 的持久值 + 这里的响应式值**一起写**。
     * 调它的是项目选择页（`selectRootSpace`）与项目改名（重设同名 rootId）。
     */
    fun setRootSpace(rootId: String?, spaceName: String?) {
        tokenManager.currentRootSpaceId = rootId
        tokenManager.currentRootSpaceName = spaceName
        currentRootSpaceId = rootId
        // 项目变了，旧项目的管理员缓存作废（也可能这本就是同一个）
        canEditById.clear()
    }

    /** 登记一个项目的管理员态（项目图加载时算一次）。 */
    fun setCanEdit(rootId: String, canEdit: Boolean) {
        canEditById[rootId] = canEdit
    }
}