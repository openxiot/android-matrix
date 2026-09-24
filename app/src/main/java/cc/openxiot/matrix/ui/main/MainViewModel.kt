package cc.openxiot.matrix.ui.main

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import cc.openxiot.matrix.MatrixApp
import cc.openxiot.matrix.R
import cc.openxiot.matrix.ui.core.SessionState

/**
 * 底部 tab。带的是**资源 id 不是字符串**：枚举是 `MainViewModel` 的静态成员，比组合活得久，
 * 存字符串就等于把语言钉在类加载那一刻 —— 切完语言底部还是一排旧语言。
 */
enum class BottomTab(@StringRes val labelRes: Int, val icon: ImageVector) {
    Home(R.string.tab_home, Icons.Default.Home),
    Projects(R.string.tab_project, Icons.Default.Business),
    Devices(R.string.tab_device, Icons.Default.DevicesOther),
    Products(R.string.tab_product, Icons.Default.Inventory2),
    Profile(R.string.tab_profile, Icons.Default.Person)
}

class MainViewModel : ViewModel() {
    var currentTab by mutableStateOf(BottomTab.Home)
        private set

    private val tokenManager = MatrixApp.instance.tokenManager

    val currentOrgId: String? get() = tokenManager.currentOrgId
    val currentOrgName: String? get() = tokenManager.currentOrgName

    /** 读响应式来源（[SessionState]）而不是 SharedPreferences：切项目后本组合才会重组刷新。 */
    val currentRootSpaceId: String? get() = SessionState.currentRootSpaceId

    fun selectTab(tab: BottomTab) {
        currentTab = tab
    }
}
