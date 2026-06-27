package site.unclefish.wearmixue.model

import site.unclefish.wearmixue.core.AuthSession

data class MixueStore(
    val shopId: String,
    val shopName: String,
    val shopAddress: String,
    val poiId: String = "",
    val distanceText: String = "",
    val deliveryType: Int? = null
)

data class AttributeGroup(
    val name: String,
    val options: List<ProductAttribute>
)

data class ProductAttribute(
    val id: String,
    val name: String
)

data class MixueProduct(
    val productId: String,
    val productName: String,
    val productLogo: String,
    val productPrice: Int,
    val modifiedTime: String,
    val productType: Int,
    val singleNotDelivery: Int = 0,
    val productCode: String = "",
    val douyinGoodsId: String = "",
    val couponCode: String = "",
    val couponRuleId: String = "",
    val couponRuleName: String = "",
    val couponLogo: String = "",
    val attributeGroups: List<AttributeGroup> = emptyList()
) {
    val hasCoupon: Boolean get() = couponCode.isNotBlank() && couponRuleId.isNotBlank()
}

data class CartLine(
    val product: MixueProduct,
    val selectedAttributes: List<ProductAttribute>,
    val amount: Int = 1
) {
    val selectNames: String
        get() = selectedAttributes.joinToString("/") { it.name }
}

data class PriceQuote(
    val originPrice: Int,
    val discountPrice: Int,
    val total: Int,
    val discountDesc: String,
    val rawProducts: List<Any?>
) {
    val isZero: Boolean get() = total == 0
}

data class MixueOrder(
    val orderCode: String,
    val shopName: String = "",
    val takeNo: String = "",
    val status: String = "",
    val price: Int = 0,
    val orderStatus: Int = -1
) {
    val isZeroPrice: Boolean get() = price == 0
    val canCancelFromWatch: Boolean get() = isZeroPrice && orderStatus !in setOf(70, 80, 90)
}

sealed interface LoadState<out T> {
    data object Idle : LoadState<Nothing>
    data object Loading : LoadState<Nothing>
    data class Success<T>(val value: T) : LoadState<T>
    data class Error(val message: String) : LoadState<Nothing>
}
