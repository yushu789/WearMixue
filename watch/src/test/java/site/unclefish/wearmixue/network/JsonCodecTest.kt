package site.unclefish.wearmixue.network

import org.junit.Assert.assertEquals
import org.junit.Test

class JsonCodecTest {
    @Test
    fun stringifyKeepsIdsAsStrings() {
        val json = JsonCodec.stringify(
            linkedMapOf(
                "productId" to "product-test-1",
                "productAmount" to 1
            )
        )

        assertEquals("{\"productId\":\"product-test-1\",\"productAmount\":1}", json)
    }
}
