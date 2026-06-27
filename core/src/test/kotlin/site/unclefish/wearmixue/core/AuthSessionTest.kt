package site.unclefish.wearmixue.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthSessionTest {
    @Test
    fun roundTripPreservesAllFields() {
        val original = AuthSession(
            accessToken = "eyJ0eXAi.token.part",
            customerId = "customer-test-1",
            seqNum = "seq-123",
            mobilePhone = "phone-test-1"
        )
        val parsed = AuthSession.fromJson(original.toJson())
        assertEquals(original, parsed)
    }

    @Test
    fun roundTripHandlesBlanksQuotesAndChinese() {
        val original = AuthSession(
            accessToken = "a\"b\\c",
            customerId = "",
            seqNum = "中文符号",
            mobilePhone = ""
        )
        val parsed = AuthSession.fromJson(original.toJson())
        assertEquals(original, parsed)
    }

    @Test
    fun isLoggedInReflectsAccessToken() {
        assertTrue(AuthSession(accessToken = "x").isLoggedIn)
        assertFalse(AuthSession(accessToken = "").isLoggedIn)
        assertFalse(AuthSession().isLoggedIn)
    }

    @Test
    fun orderingSessionRequiresCustomerAndSeqNum() {
        assertTrue(AuthSession(accessToken = "x", customerId = "c", seqNum = "s").isUsableForOrdering)
        assertFalse(AuthSession(accessToken = "x", customerId = "c").isUsableForOrdering)
        assertFalse(AuthSession(accessToken = "x", seqNum = "s").isUsableForOrdering)
        assertFalse(AuthSession(customerId = "c", seqNum = "s").isUsableForOrdering)
    }

    @Test
    fun fromJsonToleratesMissingFields() {
        val parsed = AuthSession.fromJson("""{"accessToken":"only"}""")
        assertEquals("only", parsed.accessToken)
        assertEquals("", parsed.customerId)
    }

    @Test
    fun fromJsonCanExtractLoginResponseShape() {
        val parsed = AuthSession.fromJson(
            """
            {
              "code": 0,
              "data": {
                "accessToken": "token-from-ks",
                "customerInfo": {
                  "customerId": "customer-test-1",
                  "mobilePhone": "phone-test-1",
                  "seqNum": "seq-test-1"
                }
              }
            }
            """.trimIndent()
        )

        assertEquals("token-from-ks", parsed.accessToken)
        assertEquals("customer-test-1", parsed.customerId)
        assertEquals("seq-test-1", parsed.seqNum)
        assertEquals("phone-test-1", parsed.mobilePhone)
        assertTrue(parsed.isUsableForOrdering)
    }

    @Test
    fun fromJsonAcceptsAtAliasAndNumericIdentityFields() {
        val parsed = AuthSession.fromJson(
            """
            {
              "at": "token-from-header-name",
              "customerId": 1001,
              "seqNum": 2002,
              "phone": "phone-test-1"
            }
            """.trimIndent()
        )

        assertEquals("token-from-header-name", parsed.accessToken)
        assertEquals("1001", parsed.customerId)
        assertEquals("2002", parsed.seqNum)
        assertEquals("phone-test-1", parsed.mobilePhone)
        assertTrue(parsed.isUsableForOrdering)
    }
}
