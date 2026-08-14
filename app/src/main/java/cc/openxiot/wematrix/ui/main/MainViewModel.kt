package cc.openxiot.wematrix.ui.main

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
import cc.openxiot.wematrix.WeMatrixApp

enum class BottomTab(val label: String, val icon: ImageVector) {
    Home("首页", Icons.Default.Home),
    Projects("项目", Icons.Default.Business),
    Devices("设备", Icons.Default.DevicesOther),
    Products("产品", Icons.Default.Inventory2),
    Profile("我", Icons.Default.Person)
}

class MainViewModel : ViewModel() {
    var currentTab by mutableStateOf(BottomTab.Home)
        private set

    private val tokenManager = WeMatrixApp.instance.tokenManager

    val currentOrgId: String? get() = tokenManager.currentOrgId
    val currentOrgName: String? get() = tokenManager.currentOrgName
    val currentRootSpaceId: String? get() = tokenManager.currentRootSpaceId

    fun selectTab(tab: BottomTab) {
        currentTab = tab
    }
}
