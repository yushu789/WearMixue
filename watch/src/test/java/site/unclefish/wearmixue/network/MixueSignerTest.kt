package site.unclefish.wearmixue.network

import org.junit.Assert.assertEquals
import org.junit.Test

class MixueSignerTest {
    @Test
    fun canonicalStringSortsKeysAndSkipsBlankValues() {
        val canonical = MixueSigner.canonicalString(
            linkedMapOf(
                "shopId" to "shop-test-1",
                "empty" to "",
                "appId" to "e08880ca53a947209c72ec774afc21bf",
                "nullValue" to null,
                "orderType" to 1
            )
        )

        assertEquals(
            "appId=e08880ca53a947209c72ec774afc21bf&orderType=1&shopId=shop-test-1",
            canonical
        )
    }

    @Test
    fun canonicalStringStringifiesNestedObjectsLikeRequestBody() {
        val product = linkedMapOf<String, Any?>(
            "productId" to "product-test-1",
            "attributeIds" to listOf("attr-normal-ice", "attr-normal-sugar"),
            "deductInfoList" to listOf(
                linkedMapOf(
                    "couponRuleId" to "coupon-rule-test-1",
                    "couponCode" to "coupon-code-test-1",
                    "channelSource" to 2
                )
            )
        )

        val canonical = MixueSigner.canonicalString(
            linkedMapOf(
                "products" to listOf(product),
                "channelType" to 5
            )
        )

        assertEquals(
            "channelType=5&products=[{\"productId\":\"product-test-1\",\"attributeIds\":[\"attr-normal-ice\",\"attr-normal-sugar\"],\"deductInfoList\":[{\"couponRuleId\":\"coupon-rule-test-1\",\"couponCode\":\"coupon-code-test-1\",\"channelSource\":2}]}]",
            canonical
        )
    }
}
