package cc.openxiot.wematrix

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cc.openxiot.wematrix.data.local.TokenManager

object AppState {
    var isLoggedIn by mutableStateOf(false)
        private set
    var isDarkMode by mutableStateOf(false)
        private set

    fun init(tokenManager: TokenManager) {
        isLoggedIn = tokenManager.isLoggedIn
        isDarkMode = tokenManager.isDarkMode
    }

    fun setLoggedIn(tokenManager: TokenManager) {
        isLoggedIn = true
    }

    fun toggleDarkMode(tokenManager: TokenManager) {
        isDarkMode = !isDarkMode
        tokenManager.isDarkMode = isDarkMode
    }
}
