package cc.openxiot.wematrix.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.openxiot.wematrix.R
import cc.openxiot.wematrix.data.api.ProductEntity
import cc.openxiot.wematrix.data.api.RetrofitClient
import cc.openxiot.wematrix.ui.core.UiText
import cc.openxiot.wematrix.ui.core.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProductUiState(
    val isLoading: Boolean = true,
    val products: List<ProductEntity> = emptyList(),
    val error: UiText? = null,
    val isDetailLoading: Boolean = false,
    val detailError: UiText? = null
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
                        error = response.body()?.message?.takeIf { it.isNotBlank() }?.let { UiText.Raw(it) }
                            ?: UiText.Res(R.string.err_product_list)
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toUiText()
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
                            detailError = UiText.Res(R.string.product_empty_data)
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isDetailLoading = false,
                        detailError = response.body()?.message?.takeIf { it.isNotBlank() }?.let { UiText.Raw(it) }
                            ?: UiText.Res(R.string.err_product_detail)
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDetailLoading = false,
                    detailError = e.toUiText()
                )
            }
        }
    }
}
