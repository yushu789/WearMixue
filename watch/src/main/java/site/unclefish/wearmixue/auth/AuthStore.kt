package site.unclefish.wearmixue.auth

import android.content.Context
import site.unclefish.wearmixue.core.AuthSession

interface SessionStore {
    fun read(): AuthSession
    fun save(session: AuthSession)
    fun clear()
}

class AuthStore(context: Context) : SessionStore {
    private val prefs = context.applicationContext.getSharedPreferences("mixue_auth", Context.MODE_PRIVATE)

    override fun read(): AuthSession = AuthSession(
        accessToken = prefs.getString(KEY_ACCESS_TOKEN, "").orEmpty(),
        customerId = prefs.getString(KEY_CUSTOMER_ID, "").orEmpty(),
        seqNum = prefs.getString(KEY_SEQ_NUM, "").orEmpty(),
        mobilePhone = prefs.getString(KEY_MOBILE_PHONE, "").orEmpty()
    )

    override fun save(session: AuthSession) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, session.accessToken)
            .putString(KEY_CUSTOMER_ID, session.customerId)
            .putString(KEY_SEQ_NUM, session.seqNum)
            .putString(KEY_MOBILE_PHONE, session.mobilePhone)
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_CUSTOMER_ID = "customer_id"
        private const val KEY_SEQ_NUM = "seq_num"
        private const val KEY_MOBILE_PHONE = "mobile_phone"
    }
}
