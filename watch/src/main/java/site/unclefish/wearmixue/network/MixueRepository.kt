package site.unclefish.wearmixue.network

import org.json.JSONArray
import org.json.JSONObject
import site.unclefish.wearmixue.auth.SessionStore
import site.unclefish.wearmixue.model.AttributeGroup
import site.unclefish.wearmixue.model.CartLine
import site.unclefish.wearmixue.model.MixueOrder
import site.unclefish.wearmixue.model.MixueProduct
import site.unclefish.wearmixue.model.MixueStore
import site.unclefish.wearmixue.model.PriceQuote
import site.unclefish.wearmixue.model.ProductAttribute
import java.security.MessageDigest
import kotlin.math.roundToInt

class MixueRepository(
    private val api: MixueApiClient,
    private val sessionStore: SessionStore
) {
    val isReady: Boolean get() = api.isSignerConfigured

    suspend fun validateToken(): Boolean {
        api.get("/v1/app/isTokenValid")
        return true
    }

    suspend fun findNearbyStores(latitude: Double, longitude: Double): List<MixueStore> {
        val json = api.post(
            "/v2/app/shop/findNear",
            linkedMapOf(
                "latitude" to latitude,
                "longitude" to longitude,
                "distance" to 20,
                "limit" to 20,
                "thirdChannel" to 2
            )
        )
        return json.dataArray("list", "records", "shopList").toStoreList()
    }

    suspend fun getShopInfo(shopId: String): MixueStore? {
        val json = api.get("/v1/app/shop/getByShopIdAndChannel/$shopId/2")
        return json.optJSONObject("data")?.toStore()
    }

    suspend fun getCouponProducts(store: MixueStore): List<MixueProduct> {
        val products = mutableListOf<MixueProduct>()
        var page = 1
        var totalPage: Int
        do {
            val json = api.post(
                "/v1/app/shop/menu/usecoupon/kuaishou/coupon/list",
                linkedMapOf(
                    "orderType" to 1,
                    "shopId" to store.shopId,
                    "poiId" to store.poiId.blankToNull(),
                    "page" to page,
                    "limit" to 10,
                    "priceCategoryChannelType" to "5",
                    "channelType" to "5"
                )
            )
            val data = json.optJSONObject("data") ?: JSONObject()
            val coupons = data.optJSONArray("list") ?: JSONArray()
            products += coupons.toCouponProducts()
            totalPage = data.optInt("totalPage", page)
            page += 1
        } while (page <= totalPage)
        return products.distinctBy { "${it.productId}:${it.couponCode}" }
    }

    private fun JSONArray.toCouponProducts(): List<MixueProduct> {
        return buildList {
            for (index in 0 until length()) {
                val coupon = optJSONObject(index) ?: continue
                if (coupon.optInt("availableNow", 1) != 1 || coupon.optInt("canUseInShop", 1) != 1) continue
                val productArray = coupon.optJSONArray("products") ?: continue
                for (productIndex in 0 until productArray.length()) {
                    val product = productArray.optJSONObject(productIndex) ?: continue
                    if (product.optInt("productStatus", 1) != 1) continue
                    add(
                        product.toProduct(
                            couponCode = coupon.optString("couponCode"),
                            couponRuleId = coupon.optString("couponRuleId"),
                            couponRuleName = coupon.optString("couponRuleNameMx", coupon.optString("couponRuleName")),
                            couponLogo = coupon.optString("couponLogo", product.optString("productLogo")),
                            douyinGoodsId = coupon.optString("skuId")
                        )
                    )
                }
            }
        }
    }

    suspend fun getMenuProducts(shopId: String): List<MixueProduct> {
        val json = api.post(
            "/v2/app/shop/menu/get",
            linkedMapOf(
                "shopId" to shopId,
                "orderType" to 1,
                "channelType" to "5",
                "priceCategoryChannelType" to "5"
            )
        )
        val data = json.opt("data") ?: return emptyList()
        val products = mutableListOf<MixueProduct>()
        collectProducts(data, products)
        return products.distinctBy { it.productId }.filter { it.productId.isNotBlank() }
    }

    suspend fun getProductDetail(store: MixueStore, product: MixueProduct): MixueProduct {
        val json = api.post(
            "/v2/app/shop/product/detail",
            linkedMapOf(
                "productId" to product.productId,
                "shopId" to store.shopId,
                "orderType" to 1
            )
        )
        val data = json.optJSONObject("data") ?: return product
        val detailAttributes = data.optJSONArray("productAttrs").toAttributeGroups()
        return product.copy(
            productType = data.optInt("productType", product.productType),
            attributeGroups = detailAttributes.ifEmpty { product.attributeGroups }
        )
    }

    suspend fun calcPrice(store: MixueStore, line: CartLine): PriceQuote {
        val session = sessionStore.read()
        val json = api.post(
            "/v2/app/shoppingCart/calcPrice",
            linkedMapOf(
                "channelType" to 5,
                "orderType" to 1,
                "shopId" to store.shopId,
                "products" to listOf(line.toApiProduct()),
                "priceCategoryChannelType" to 5,
                "seqNum" to session.seqNum,
                "customerId" to session.customerId
            )
        )
        val data = json.optJSONObject("data") ?: JSONObject()
        val rawProducts = data.optJSONArray("products").toAnyList()
        return PriceQuote(
            originPrice = data.optInt("originPrice", line.product.productPrice),
            discountPrice = data.optInt("discountPrice", 0),
            total = data.optInt("total", data.optInt("price", line.product.productPrice)),
            discountDesc = data.optString("discountDesc"),
            rawProducts = rawProducts
        )
    }

    suspend fun saveZeroOrder(
        store: MixueStore,
        line: CartLine,
        quote: PriceQuote,
        remark: String = ""
    ): MixueOrder {
        if (!quote.isZero) {
            throw MixueApiException("This order is not CNY 0. Watch payment is not supported.")
        }
        val session = sessionStore.read()
        val json = api.post(
            "/v1/app/ksOrder/save",
            linkedMapOf(
                "channelType" to 5,
                "orderType" to 1,
                "shopId" to store.shopId,
                "consigneeAddressId" to 0,
                "deliveryType" to (store.deliveryType ?: 1),
                "products" to line.toOrderProducts(quote),
                "priceCategoryChannelType" to 5,
                "seqNum" to session.seqNum,
                "couponCode" to line.product.couponCode.blankToNull(),
                "customerId" to session.customerId,
                "noBag" to 1,
                "order" to true,
                "participateCollectCups" to 0,
                "remark" to remark,
                "tableware" to 0
            )
        )
        val data = json.optJSONObject("data") ?: JSONObject()
        val orderCode = data.optString("orderCode", data.optString("orderId"))
        if (orderCode.isBlank()) {
            throw MixueApiException("Order saved but no order code was returned.")
        }
        return MixueOrder(orderCode = orderCode)
    }

    suspend fun getOrderInfo(orderCode: String): MixueOrder {
        val json = api.post("/v1/app/ksOrder/info", linkedMapOf("orderCode" to orderCode))
        val data = json.optJSONObject("data") ?: JSONObject()
        return data.toOrder(orderCode)
    }

    suspend fun refreshOrder(orderCode: String): MixueOrder {
        val json = api.post("/v1/app/ksOrder/refresh", linkedMapOf("orderCode" to orderCode))
        val data = json.optJSONObject("data") ?: JSONObject()
        return data.toOrder(orderCode)
    }

    suspend fun getOrders(): List<MixueOrder> {
        val json = api.post(
            "/v1/app/ksOrder/list",
            linkedMapOf("page" to 1, "limit" to 10, "type" to 0, "miniProgType" to 5)
        )
        return buildList {
            val list = json.dataArray("list", "records", "orders")
            for (index in 0 until list.length()) {
                list.optJSONObject(index)?.let { add(it.toOrder()) }
            }
        }
    }

    suspend fun cancelZeroOrder(orderCode: String): MixueOrder {
        val session = sessionStore.read()
        val json = api.post(
            "/v1/app/ksOrder/refund",
            linkedMapOf(
                "customerId" to session.customerId,
                "orderCode" to orderCode,
                "refundReason" to 0,
                "refundReasonType" to 0,
                "refundRemark" to "",
                "refundScene" to 0
            )
        )
        val data = json.optJSONObject("data") ?: JSONObject()
        return MixueOrder(
            orderCode = data.optString("orderCode", orderCode),
            status = "Canceled"
        )
    }

    private fun CartLine.toApiProduct(): LinkedHashMap<String, Any?> {
        val product = this.product
        val attrs = selectedAttributes.map { it.id }
        val deductInfo = if (product.hasCoupon) {
            listOf(
                linkedMapOf(
                    "couponRuleId" to product.couponRuleId,
                    "couponCode" to product.couponCode,
                    "channelSource" to 2
                )
            )
        } else {
            emptyList<Any>()
        }
        return linkedMapOf(
            "douyinGoodsId" to product.douyinGoodsId.blankToNull(),
            "cupId" to "",
            "productId" to product.productId,
            "productName" to product.productName,
            "productLogo" to product.productLogo,
            "productPrice" to product.productPrice,
            "productAmount" to amount,
            "specIds" to emptyList<Any>(),
            "attributeIds" to attrs,
            "selectNames" to selectNames,
            "productType" to product.productType,
            "modifiedTime" to product.modifiedTime,
            "douYinOrderId" to product.couponCode.blankToNull(),
            "couponRuleNameMx" to product.couponRuleName.blankToNull(),
            "couponLogo" to product.couponLogo.blankToNull(),
            "singleNotDelivery" to product.singleNotDelivery,
            "couponRuleId" to product.couponRuleId.blankToNull(),
            "uid" to product.uidFor(selectedAttributes),
            "productVoucherList" to emptyList<Any>(),
            "deductInfoList" to deductInfo
        )
    }

    private fun CartLine.toOrderProducts(quote: PriceQuote): List<Any?> {
        val base = toApiProduct()
        val quoted = quote.rawProducts.firstOrNull() as? Map<*, *> ?: return listOf(base)
        val fieldsFromQuote = listOf(
            "cartProductViewId",
            "productVoucherList",
            "price",
            "originPrice",
            "productPrice",
            "discountPrice"
        )
        fieldsFromQuote.forEach { key ->
            if (quoted.containsKey(key)) {
                base[key] = quoted[key]
            }
        }
        return listOf(base)
    }

    private fun collectProducts(any: Any?, out: MutableList<MixueProduct>) {
        when (any) {
            is JSONObject -> {
                if (any.has("productId") &&
                    any.has("productName") &&
                    (!any.has("productStatus") || any.optInt("productStatus", 1) == 1)
                ) {
                    out += any.toProduct()
                }
                val keys = any.keys()
                while (keys.hasNext()) {
                    collectProducts(any.opt(keys.next()), out)
                }
            }

            is JSONArray -> {
                for (index in 0 until any.length()) {
                    collectProducts(any.opt(index), out)
                }
            }
        }
    }
}

private fun JSONObject.dataArray(vararg nestedNames: String): JSONArray {
    val data = opt("data")
    return when (data) {
        is JSONArray -> data
        is JSONObject -> nestedNames.firstNotNullOfOrNull { data.optJSONArray(it) }
            ?: data.optJSONArray("list")
            ?: JSONArray()
        else -> JSONArray()
    }
}

private fun JSONArray?.toStoreList(): List<MixueStore> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            optJSONObject(index)?.toStore()?.let { add(it) }
        }
    }
}

private fun JSONObject.toStore(): MixueStore {
    val region = optString("regionName")
    val address = optString("shopAddress")
    val distanceText = distanceText()
    return MixueStore(
        shopId = optString("shopId"),
        shopName = optString("shopName"),
        shopAddress = (region + address).ifBlank { address },
        poiId = optString("poiId"),
        distanceText = distanceText,
        deliveryType = if (has("deliveryType")) optInt("deliveryType") else 1
    )
}

private fun JSONObject.distanceText(): String {
    val raw = opt("distance")
    if (raw is String && raw.isNotBlank()) return raw
    val distance = optDouble("distance", Double.NaN)
    return if (distance.isNaN()) {
        ""
    } else if (distance < 1.0) {
        "${(distance * 1000).roundToInt()}m"
    } else {
        String.format("%.1fkm", distance)
    }
}

private fun JSONObject.toProduct(
    couponCode: String = "",
    couponRuleId: String = "",
    couponRuleName: String = "",
    couponLogo: String = "",
    douyinGoodsId: String = ""
): MixueProduct {
    return MixueProduct(
        productId = optString("productId"),
        productName = optString("productName"),
        productLogo = optString("productLogo"),
        productPrice = optInt("productPrice", optInt("originPrice", optInt("price"))),
        modifiedTime = optString("modifiedTime"),
        productType = optInt("productType", 1),
        singleNotDelivery = optInt("singleNotDelivery", 0),
        productCode = optString("productCode"),
        douyinGoodsId = douyinGoodsId.ifBlank { optString("douyinGoodsId") },
        couponCode = couponCode,
        couponRuleId = couponRuleId,
        couponRuleName = couponRuleName,
        couponLogo = couponLogo,
        attributeGroups = optJSONArray("productAttrs").toAttributeGroups()
    )
}

private fun JSONArray?.toAttributeGroups(): List<AttributeGroup> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val group = optJSONObject(index) ?: continue
            val optionsJson = group.optJSONArray("productAttrs") ?: continue
            val options = buildList {
                for (optionIndex in 0 until optionsJson.length()) {
                    val option = optionsJson.optJSONObject(optionIndex) ?: continue
                    add(
                        ProductAttribute(
                            id = option.optString("attributeId"),
                            name = option.optString("attributeName")
                        )
                    )
                }
            }.filter { it.id.isNotBlank() }
            if (options.isNotEmpty()) {
                add(AttributeGroup(group.optString("attributeName", "Spec"), options))
            }
        }
    }
}

fun MixueProduct.defaultAttributes(): List<ProductAttribute> {
    return attributeGroups.mapNotNull { group ->
        group.options.firstOrNull { it.name.contains("\u6b63\u5e38") }
            ?: group.options.firstOrNull()
    }
}

private fun JSONObject.toOrder(fallbackCode: String = ""): MixueOrder {
    val orderStatus = optInt("orderStatus", -1)
    val status = when (orderStatus) {
        10, 20 -> "Making"
        30, 40, 50 -> "Ready"
        70 -> "Done"
        80, 90 -> "Canceled"
        else -> optString("orderStatusShow", "")
    }
    val takeNo = if (has("takeNo")) optString("takeNo") else ""
    return MixueOrder(
        orderCode = optString("orderCode", fallbackCode),
        shopName = optString("shopName"),
        takeNo = takeNo,
        status = status,
        price = optInt("price", 0),
        orderStatus = orderStatus
    )
}

private fun MixueProduct.uidFor(attributes: List<ProductAttribute>): String {
    val selection = listOf(
        "",
        "",
        attributes.joinToString("/") { it.id }
    ).joinToString(",")
    val raw = "${productId}_${selection}_${couponCode}"
    return raw.md5()
}

private fun String.md5(): String {
    val bytes = MessageDigest.getInstance("MD5").digest(toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}

private fun JSONArray?.toAnyList(): List<Any?> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            add(opt(index).toPlainAny())
        }
    }
}

private fun Any?.toPlainAny(): Any? = when (this) {
    JSONObject.NULL -> null
    is JSONObject -> {
        val out = linkedMapOf<String, Any?>()
        val keys = keys()
        while (keys.hasNext()) {
            val key = keys.next()
            out[key] = opt(key).toPlainAny()
        }
        out
    }

    is JSONArray -> this.toAnyList()
    else -> this
}

private fun String.blankToNull(): String? = if (isBlank()) null else this
