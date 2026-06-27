package site.unclefish.wearmixue.network

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import site.unclefish.wearmixue.auth.SessionStore
import site.unclefish.wearmixue.core.AuthSession
import site.unclefish.wearmixue.model.CartLine
import site.unclefish.wearmixue.model.MixueProduct
import site.unclefish.wearmixue.model.MixueStore
import site.unclefish.wearmixue.model.PriceQuote
import site.unclefish.wearmixue.model.ProductAttribute

class MixueRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var sessionStore: FakeSessionStore
    private lateinit var repository: MixueRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        sessionStore = FakeSessionStore()
        repository = MixueRepository(
            MixueApiClient(
                sessionStore = sessionStore,
                signer = FakeSigner(),
                httpClient = localOnlyHttpClient(),
                baseUrl = server.url("/").toString().trimEnd('/')
            ),
            sessionStore
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun calcPriceBodyKeepsProductAndCouponDeductInfo() = runBlocking {
        enqueueConfig()
        server.enqueue(jsonResponse("""{"code":0,"data":{"total":0,"products":[]}}"""))

        repository.calcPrice(testStore(), CartLine(testCouponProduct(), listOf(ProductAttribute("attr-normal-ice", "normal"))))

        server.takeRequest()
        val request = server.takeRequest()
        assertEquals("/activity/v2/app/shoppingCart/calcPrice", request.path)

        val body = JSONObject(request.body.readUtf8())
        assertEquals("customer-test-1", body.getString("customerId"))
        assertEquals(5, body.getInt("channelType"))

        val product = body.getJSONArray("products").getJSONObject(0)
        assertEquals("product-test-1", product.getString("productId"))
        assertEquals("coupon-code-test-1", product.getString("douYinOrderId"))
        assertEquals(32, product.getString("uid").length)

        val deductInfo = product.getJSONArray("deductInfoList").getJSONObject(0)
        assertEquals("coupon-rule-test-1", deductInfo.getString("couponRuleId"))
        assertEquals("coupon-code-test-1", deductInfo.getString("couponCode"))
        assertEquals(2, deductInfo.getInt("channelSource"))
    }

    @Test
    fun validateTokenUsesMiniappTokenCheckEndpoint() = runBlocking {
        enqueueConfig()
        server.enqueue(jsonResponse("""{"code":0,"data":true}"""))

        assertTrue(repository.validateToken())

        val configRequest = server.takeRequest()
        assertEquals("/activity/v1/app/config", configRequest.path)
        assertNull(configRequest.getHeader("access-token"))
        val request = server.takeRequest()
        assertEquals("/activity/v1/app/isTokenValid?sign=test-sign&appId=e08880ca53a947209c72ec774afc21bf", request.path?.substringBefore("&t="))
    }

    @Test
    fun configTimestampMayBeReturnedAtRoot() = runBlocking {
        server.enqueue(jsonResponse("""{"timestamp":1000}"""))
        server.enqueue(jsonResponse("""{"code":0,"data":true}"""))

        assertTrue(repository.validateToken())

        assertEquals("/activity/v1/app/config", server.takeRequest().path)
        assertEquals("/activity/v1/app/isTokenValid?sign=test-sign&appId=e08880ca53a947209c72ec774afc21bf", server.takeRequest().path?.substringBefore("&t="))
    }

    @Test
    fun couponListBodyUsesKuaishouStringChannelFields() = runBlocking {
        enqueueConfig()
        server.enqueue(
            jsonResponse(
                """
                {"code":0,"data":{"list":[{"couponCode":"coupon-code-test-1","couponRuleId":"coupon-rule-test-1","couponRuleNameMx":"Coupon","skuId":"sku-1","products":[{"productStatus":1,"productId":"product-test-1","productName":"Jasmine Milk Green","productLogo":"","productPrice":700,"modifiedTime":"2026-06-24 00:00:00","productType":1,"productAttrs":[]}]}]}}
                """.trimIndent()
            )
        )

        val products = repository.getCouponProducts(testStore())

        assertEquals(1, products.size)
        server.takeRequest()
        val request = server.takeRequest()
        assertEquals("/activity/v1/app/shop/menu/usecoupon/kuaishou/coupon/list", request.path)

        val body = JSONObject(request.body.readUtf8())
        assertEquals("5", body.getString("channelType"))
        assertEquals("5", body.getString("priceCategoryChannelType"))
        assertEquals(10, body.getInt("limit"))
    }

    @Test
    fun couponProductsSkipUnavailableCoupons() = runBlocking {
        enqueueConfig()
        server.enqueue(
            jsonResponse(
                """
                {"code":0,"data":{"list":[{"availableNow":0,"canUseInShop":1,"couponCode":"bad","couponRuleId":"bad-rule","products":[{"productStatus":1,"productId":"bad-product","productName":"Bad","productPrice":700}]},{"availableNow":1,"canUseInShop":1,"couponCode":"coupon-code-test-1","couponRuleId":"coupon-rule-test-1","products":[{"productStatus":1,"productId":"product-test-1","productName":"Jasmine Milk Green","productPrice":700}]}]}}
                """.trimIndent()
            )
        )

        val products = repository.getCouponProducts(testStore())

        assertEquals(1, products.size)
        assertEquals("product-test-1", products.first().productId)
    }

    @Test
    fun couponProductsFollowPagination() = runBlocking {
        enqueueConfig()
        server.enqueue(
            jsonResponse(
                """
                {"code":0,"data":{"currPage":1,"totalPage":2,"list":[{"availableNow":1,"canUseInShop":1,"couponCode":"coupon-1","couponRuleId":"rule-1","products":[{"productStatus":1,"productId":"product-1","productName":"First","productPrice":700}]}]}}
                """.trimIndent()
            )
        )
        server.enqueue(
            jsonResponse(
                """
                {"code":0,"data":{"currPage":2,"totalPage":2,"list":[{"availableNow":1,"canUseInShop":1,"couponCode":"coupon-2","couponRuleId":"rule-2","products":[{"productStatus":1,"productId":"product-2","productName":"Second","productPrice":800}]}]}}
                """.trimIndent()
            )
        )

        val products = repository.getCouponProducts(testStore())

        assertEquals(2, products.size)
        server.takeRequest()
        val firstPage = JSONObject(server.takeRequest().body.readUtf8())
        val secondPage = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(1, firstPage.getInt("page"))
        assertEquals(2, secondPage.getInt("page"))
        assertEquals(10, secondPage.getInt("limit"))
    }

    @Test
    fun shopInfoKeepsPoiIdAndDefaultsToPickupDeliveryType() = runBlocking {
        enqueueConfig()
        server.enqueue(
            jsonResponse(
                """
                {"code":0,"data":{"shopId":"shop-test-1","shopName":"Mixue","regionName":"Region","shopAddress":"Street","poiId":"poi-test-1","distance":"808m"}}
                """.trimIndent()
            )
        )

        val store = repository.getShopInfo("shop-test-1")

        assertEquals("poi-test-1", store?.poiId)
        assertEquals("808m", store?.distanceText)
        assertEquals(1, store?.deliveryType)
    }

    @Test
    fun saveZeroOrderBodyUsesKuaishouEndpointAndZeroOrderFields() = runBlocking {
        enqueueConfig()
        server.enqueue(jsonResponse("""{"code":0,"data":{"orderCode":"order-test-1"}}"""))

        val order = repository.saveZeroOrder(
            store = testStore(),
            line = CartLine(testCouponProduct(), emptyList()),
            quote = PriceQuote(originPrice = 700, discountPrice = 700, total = 0, discountDesc = "", rawProducts = emptyList())
        )

        assertEquals("order-test-1", order.orderCode)
        server.takeRequest()
        val request = server.takeRequest()
        assertEquals("/activity/v1/app/ksOrder/save", request.path)

        val body = JSONObject(request.body.readUtf8())
        assertEquals(5, body.getInt("channelType"))
        assertEquals(1, body.getInt("orderType"))
        assertEquals(5, body.getInt("priceCategoryChannelType"))
        assertEquals("coupon-code-test-1", body.getString("couponCode"))
        assertEquals("customer-test-1", body.getString("customerId"))
        assertEquals(1, body.getInt("deliveryType"))
        assertTrue(body.getBoolean("order"))
        assertEquals(0, body.getInt("tableware"))
        assertEquals(1, body.getInt("noBag"))
    }

    @Test
    fun saveZeroOrderMergesQuoteFieldsWithoutDroppingCouponDeductInfo() = runBlocking {
        enqueueConfig()
        server.enqueue(jsonResponse("""{"code":0,"data":{"orderCode":"order-test-1"}}"""))

        repository.saveZeroOrder(
            store = testStore(),
            line = CartLine(testCouponProduct(), listOf(ProductAttribute("attr-normal-ice", "normal"))),
            quote = PriceQuote(
                originPrice = 700,
                discountPrice = 700,
                total = 0,
                discountDesc = "",
                rawProducts = listOf(
                    linkedMapOf(
                        "cartProductViewId" to "cart-view-test-1",
                        "productVoucherList" to listOf(
                            linkedMapOf(
                                "couponCode" to "coupon-code-test-1",
                                "couponRuleId" to "coupon-rule-test-1",
                                "channelSource" to 2
                            )
                        ),
                        "price" to 0,
                        "originPrice" to 700,
                        "productPrice" to 700,
                        "discountPrice" to 700
                    )
                )
            )
        )

        server.takeRequest()
        val request = server.takeRequest()
        val product = JSONObject(request.body.readUtf8()).getJSONArray("products").getJSONObject(0)

        assertEquals("cart-view-test-1", product.getString("cartProductViewId"))
        assertEquals(0, product.getInt("price"))
        assertEquals("coupon-code-test-1", product.getString("douYinOrderId"))
        assertEquals("coupon-code-test-1", product.getJSONArray("deductInfoList").getJSONObject(0).getString("couponCode"))
        assertEquals("coupon-code-test-1", product.getJSONArray("productVoucherList").getJSONObject(0).getString("couponCode"))
    }

    @Test
    fun productDetailUsesMiniappDetailEndpoint() = runBlocking {
        enqueueConfig()
        server.enqueue(jsonResponse("""{"code":0,"data":{"productId":"product-test-1","productType":1}}"""))

        val product = repository.getProductDetail(testStore(), testCouponProduct())

        assertEquals("product-test-1", product.productId)
        server.takeRequest()
        val request = server.takeRequest()
        assertEquals("/activity/v2/app/shop/product/detail", request.path)
        val body = JSONObject(request.body.readUtf8())
        assertEquals("product-test-1", body.getString("productId"))
        assertEquals("shop-test-1", body.getString("shopId"))
        assertEquals(1, body.getInt("orderType"))
    }

    @Test
    fun menuProductsParseArrayDataAndAttributes() = runBlocking {
        enqueueConfig()
        server.enqueue(
            jsonResponse(
                """
                {"code":0,"data":[{"productCategoryId":"cat-1","products":[{"productStatus":1,"productId":"product-test-2","productName":"Yuzu Tea","productLogo":"","productPrice":800,"modifiedTime":"2026-05-22 10:10:34","productType":1,"productAttrs":[{"attributeId":"attr-temperature","attributeName":"Temperature","productAttrs":[{"attributeId":"attr-normal-ice","attributeName":"Normal ice"}]},{"attributeId":"attr-sugar","attributeName":"Sugar","productAttrs":[{"attributeId":"attr-normal-sugar","attributeName":"Normal sugar"}]}]},{"productStatus":2,"productId":"offline","productName":"Offline","productPrice":800}]}]}
                """.trimIndent()
            )
        )

        val products = repository.getMenuProducts(testStore().shopId)

        assertEquals(1, products.size)
        assertEquals("product-test-2", products.first().productId)
        assertEquals(2, products.first().attributeGroups.size)
        assertEquals("Temperature", products.first().attributeGroups.first().name)
    }

    @Test
    fun saveZeroOrderRejectsNonZeroQuoteBeforeNetwork() {
        val error = assertThrows(MixueApiException::class.java) {
            runBlocking {
                repository.saveZeroOrder(
                    store = testStore(),
                    line = CartLine(testCouponProduct(), emptyList()),
                    quote = PriceQuote(originPrice = 700, discountPrice = 0, total = 700, discountDesc = "", rawProducts = emptyList())
                )
            }
        }

        assertEquals("This order is not CNY 0. Watch payment is not supported.", error.message)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun cancelZeroOrderBodyUsesRefundEndpoint() = runBlocking {
        enqueueConfig()
        server.enqueue(jsonResponse("""{"code":0,"data":{"orderCode":"order-test-1"}}"""))

        val order = repository.cancelZeroOrder("order-test-1")

        assertEquals("Canceled", order.status)
        server.takeRequest()
        val request = server.takeRequest()
        assertEquals("/activity/v1/app/ksOrder/refund", request.path)

        val body = JSONObject(request.body.readUtf8())
        assertEquals("customer-test-1", body.getString("customerId"))
        assertEquals("order-test-1", body.getString("orderCode"))
        assertEquals(0, body.getInt("refundReason"))
        assertEquals(0, body.getInt("refundReasonType"))
    }

    @Test
    fun orderListParsesZeroPriceForWatchCancelDecision() = runBlocking {
        enqueueConfig()
        server.enqueue(
            jsonResponse(
                """
                {"code":0,"data":{"list":[{"orderCode":"order-test-1","shopName":"Mixue","takeNo":1909,"orderStatus":30,"price":0}]}}
                """.trimIndent()
            )
        )

        val orders = repository.getOrders()

        assertEquals(1, orders.size)
        assertTrue(orders.first().isZeroPrice)
        assertTrue(orders.first().canCancelFromWatch)
        assertEquals("Ready", orders.first().status)
        assertEquals(30, orders.first().orderStatus)
    }

    @Test
    fun completedZeroOrderCannotBeCanceledFromWatch() = runBlocking {
        enqueueConfig()
        server.enqueue(
            jsonResponse(
                """
                {"code":0,"data":{"list":[{"orderCode":"order-test-1","shopName":"Mixue","takeNo":1909,"orderStatus":70,"price":0}]}}
                """.trimIndent()
            )
        )

        val orders = repository.getOrders()

        assertTrue(orders.first().isZeroPrice)
        assertFalse(orders.first().canCancelFromWatch)
        assertEquals("Done", orders.first().status)
    }

    @Test
    fun refreshOrderUsesKuaishouRefreshEndpoint() = runBlocking {
        enqueueConfig()
        server.enqueue(
            jsonResponse(
                """
                {"code":0,"data":{"orderCode":"order-test-1","shopName":"Mixue","takeNo":1909,"orderStatus":40,"price":0}}
                """.trimIndent()
            )
        )

        val order = repository.refreshOrder("order-test-1")

        assertEquals("Ready", order.status)
        assertEquals("order-test-1", order.orderCode)
        server.takeRequest()
        val request = server.takeRequest()
        assertEquals("/activity/v1/app/ksOrder/refresh", request.path)
        val body = JSONObject(request.body.readUtf8())
        assertEquals("order-test-1", body.getString("orderCode"))
    }

    @Test
    fun apiClientKeepsSessionAndReportsEndpointOn401() {
        enqueueConfig()
        server.enqueue(jsonResponse("""{"code":401,"msg":"expired"}"""))

        val error = assertThrows(MixueApiException::class.java) {
            runBlocking {
                repository.getOrders()
            }
        }

        assertEquals(401, error.code)
        assertEquals("POST /activity/v1/app/ksOrder/list", error.path)
        assertEquals("POST /activity/v1/app/ksOrder/list code=401 msg=expired", error.message)
        assertFalse(sessionStore.cleared)
    }

    private fun enqueueConfig() {
        server.enqueue(jsonResponse("""{"code":0,"data":{"timestamp":1000}}"""))
    }

    private fun localOnlyHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val host = chain.request().url.host
                check(host == "localhost" || host == "127.0.0.1") {
                    "Unit tests must not call external APIs: $host"
                }
                chain.proceed(chain.request())
            })
            .build()
    }

    private fun jsonResponse(body: String): MockResponse {
        return MockResponse()
            .setHeader("content-type", "application/json")
            .setBody(body)
    }

    private fun testStore(): MixueStore {
        return MixueStore(
            shopId = "shop-test-1",
            shopName = "Mixue",
            shopAddress = "Test address",
            poiId = "poi-1",
            deliveryType = 1
        )
    }

    private fun testCouponProduct(): MixueProduct {
        return MixueProduct(
            productId = "product-test-1",
            productName = "Jasmine Milk Green",
            productLogo = "",
            productPrice = 700,
            modifiedTime = "2026-06-24 00:00:00",
            productType = 1,
            douyinGoodsId = "sku-product-test-1",
            couponCode = "coupon-code-test-1",
            couponRuleId = "coupon-rule-test-1",
            couponRuleName = "Kuaishou coupon"
        )
    }

    private class FakeSessionStore : SessionStore {
        var cleared = false
        private var session = AuthSession(
            accessToken = "token-1",
            customerId = "customer-test-1",
            seqNum = "seq-test-1",
            mobilePhone = "phone-test-1"
        )

        override fun read(): AuthSession = session

        override fun save(session: AuthSession) {
            this.session = session
            cleared = false
        }

        override fun clear() {
            session = AuthSession()
            cleared = true
        }
    }

    private class FakeSigner : RequestSigner {
        override val isConfigured: Boolean = true

        override fun sign(params: Map<String, Any?>): String = "test-sign"
    }
}
