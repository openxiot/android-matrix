package cc.openxiot.android.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.openxiot.android.data.api.ProductEntity
import cc.openxiot.android.data.api.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProductUiState(
    val isLoading: Boolean = true,
    val products: List<ProductEntity> = emptyList(),
    val error: String? = null,
    val isDetailLoading: Boolean = false,
    val detailError: String? = null
)

class ProductViewModel : ViewModel() {
    private val productService = RetrofitClient.productService

    private val _uiState = MutableStateFlow(ProductUiState())
    val uiState: StateFlow<ProductUiState> = _uiState.asStateFlow()

    init {
        loadProducts()
    }

    fun loadProducts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = productService.getProducts()
                if (response.isSuccessful && response.body()?.success == true) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        products = response.body()!!.data ?: emptyList()
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = response.body()?.message ?: "获取产品列表失败"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "网络错误"
                )
            }
        }
    }

    fun loadProductDetail(productId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDetailLoading = true, detailError = null)
            try {
                val response = productService.getProductDetail(productId)
                if (response.isSuccessful && response.body()?.success == true) {
                    val detail = response.body()!!.data
                    if (detail != null) {
                        val updated = _uiState.value.products.toMutableList()
                        val idx = updated.indexOfFirst { it.id == productId }
                        if (idx >= 0) updated[idx] = detail
                        else updated.add(detail)
                        _uiState.value = _uiState.value.copy(
                            isDetailLoading = false,
                            products = updated
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isDetailLoading = false,
                            detailError = "产品数据为空"
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isDetailLoading = false,
                        detailError = response.body()?.message ?: "获取产品详情失败"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDetailLoading = false,
                    detailError = e.message ?: "网络错误"
                )
            }
        }
    }
}
