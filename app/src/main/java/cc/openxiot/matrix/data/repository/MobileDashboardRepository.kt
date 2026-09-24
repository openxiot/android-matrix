package cc.openxiot.matrix.data.repository

import cc.openxiot.matrix.R
import cc.openxiot.matrix.data.api.MobileDashboardLayout
import cc.openxiot.matrix.data.api.MobileDashboardWidget
import cc.openxiot.matrix.data.api.MobileRenderRequest
import cc.openxiot.matrix.data.api.MobileRenderResponse
import cc.openxiot.matrix.data.api.RetrofitClient

/**
 * 移动端看板（可自定义首页）的数据访问。
 *
 * 每个方法都要求调用方显式传 `spaceId`（当前项目**根空间**）：后端只用它校验权限，
 * 不参与过滤（布局本来就是每根空间一份）。不在这里隐式取 TokenManager，与
 * [StatisticsRepository] 同一口径 —— 让「这是鉴权作用域」在调用点看得见。
 *
 * 服务端失败（权限 / 冲突 / 数据错）统一转 `Result.failure` 并带上服务端 message：
 * 页面据此显示「已被他人修改，请重新加载」之类的人话，而不是吞掉。
 */
class MobileDashboardRepository {
    private val service get() = RetrofitClient.matrixService

    /**
     * 读当前布局；从未保存时后端返回**预置**（version=0），页面拿到就能渲染，
     * 不用自己判断「要不要 404 兜底」。
     */
    suspend fun getLayout(spaceId: String): Result<MobileDashboardLayout> = runCatching {
        val r = service.getMobileLayout(spaceId)
        if (r.isSuccessful && r.body()?.success == true) {
            r.body()!!.data ?: failWith(R.string.err_dashboard_layout_empty)
        } else {
            r.failWith(R.string.err_dashboard_layout)
        }
    }

    /** 只读预置（不进库）。「恢复默认」用它把预置填进草稿，仍需手动保存才落库。 */
    suspend fun getPreset(spaceId: String): Result<MobileDashboardLayout> = runCatching {
        val r = service.getMobileLayoutPreset(spaceId)
        if (r.isSuccessful && r.body()?.success == true) {
            r.body()!!.data ?: failWith(R.string.err_dashboard_preset_empty)
        } else {
            r.failWith(R.string.err_dashboard_preset)
        }
    }

    /**
     * 取数。`widgets` 为空 = 渲染已保存的布局；非空 = 渲染草稿（编辑态预览）。
     *
     * 返回的每张卡自带 `success` / `data` / `message`：**单卡失败不拖垮整屏**，
     * 页面按 id 各自处理，外层没有统一错误码。
     */
    suspend fun render(spaceId: String, widgets: List<MobileDashboardWidget>?): Result<MobileRenderResponse> =
        runCatching {
            val r = service.renderMobileDashboard(spaceId, MobileRenderRequest(widgets))
            if (r.isSuccessful && r.body()?.success == true) {
                r.body()!!.data ?: failWith(R.string.err_dashboard_data_empty)
            } else {
                r.failWith(R.string.err_dashboard_data)
            }
        }

    /**
     * 保存布局（乐观锁 CAS）。`version` 必须等于上次读到的值；带上它的那一刻已被别人改过，
     * 服务端拒绝并返回固定文案（"layout has been modified by someone else..."），这里原样带出 ——
     * 页面拿到失败**保留草稿**，让用户可以复制再重载，而不是静默丢弃输入。
     */
    suspend fun saveLayout(
        spaceId: String,
        version: Long,
        widgets: List<MobileDashboardWidget>
    ): Result<MobileDashboardLayout> = runCatching {
        val r = service.saveMobileLayout(spaceId, MobileDashboardLayout(version = version, widgets = widgets))
        if (r.isSuccessful && r.body()?.success == true) {
            r.body()!!.data ?: failWith(R.string.err_dashboard_save_empty)
        } else {
            r.failWith(R.string.err_dashboard_save)
        }
    }
}