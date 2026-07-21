package cc.openxiot.android.ui.products

import androidx.lifecycle.ViewModel
import cc.openxiot.android.data.api.ProductEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ProductUiState(
    val isLoading: Boolean = false,
    val products: List<ProductEntity> = emptyList(),
    val error: String? = null
)

class ProductViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ProductUiState(products = emptyList()))
    val uiState: StateFlow<ProductUiState> = _uiState.asStateFlow()
}
