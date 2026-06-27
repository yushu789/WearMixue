package site.unclefish.wearmixue.core

/**
 * Token payload exchanged between the phone login app and the watch ordering app.
 *
 * This class is the single source of truth for the phone -> watch transfer protocol:
 * its JSON shape is produced by [toJson] on the phone and parsed by [fromJson] on the
 * watch, so any field drift becomes a compile error rather than a silent mismatch.
 */
data class AuthSession(
    val accessToken: String = "",
    val customerId: String = "",
    val seqNum: String = "",
    val mobilePhone: String = ""
) {
    val isLoggedIn: Boolean get() = accessToken.isNotBlank()
    val isUsableForOrdering: Boolean
        get() = accessToken.isNotBlank() && customerId.isNotBlank() && seqNum.isNotBlank()

    fun toJson(): String {
        val sb = StringBuilder()
        sb.append('{')
        sb.append("\"accessToken\":").append(quote(accessToken)).append(',')
        sb.append("\"customerId\":").append(quote(customerId)).append(',')
        sb.append("\"seqNum\":").append(quote(seqNum)).append(',')
        sb.append("\"mobilePhone\":").append(quote(mobilePhone))
        sb.append('}')
        return sb.toString()
    }

    companion object {
        fun fromJson(json: String): AuthSession {
            val map = parseObject(json)
            return AuthSession(
                accessToken = firstNonBlank(map, "accessToken", "access-token", "at", "token"),
                customerId = firstNonBlank(map, "customerId"),
                seqNum = firstNonBlank(map, "seqNum"),
                mobilePhone = firstNonBlank(map, "mobilePhone", "phone")
            )
        }

        /**
         * Minimal JSON object parser: extracts string values by key anywhere in the
         * payload. This handles both the flat relay protocol and the nested Mixue
         * login response shape without pulling JSON dependencies into :core.
         */
        private fun parseObject(json: String): Map<String, String> {
            val out = LinkedHashMap<String, String>()
            // Match "key" : "value" pairs by source position so escape decoding is fed
            // the exact quoted span (index arithmetic on the decoded length would
            // drift on escapes, which is what broke the earlier hand-rolled parser).
            val pattern = Regex("\"((?:\\\\.|[^\"\\\\])*)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
            for (match in pattern.findAll(json)) {
                out[decodeValue(match.groupValues[1])] = decodeValue(match.groupValues[2])
            }
            val numberPattern = Regex("\"(customerId|seqNum|mobilePhone)\"\\s*:\\s*(-?\\d+)")
            for (match in numberPattern.findAll(json)) {
                val key = decodeValue(match.groupValues[1])
                out[key] = match.groupValues[2]
            }
            return out
        }

        private fun firstNonBlank(map: Map<String, String>, vararg keys: String): String {
            return keys.firstNotNullOfOrNull { key -> map[key]?.takeIf { it.isNotBlank() } }.orEmpty()
        }

        private fun decodeValue(raw: String): String {
            val sb = StringBuilder()
            var i = 0
            while (i < raw.length) {
                val c = raw[i]
                if (c == '\\' && i + 1 < raw.length) {
                    when (raw[i + 1]) {
                        '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                        'b' -> sb.append('\b'); 'f' -> sb.append('')
                        'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                        'u' -> {
                            val hex = raw.substring(i + 2, minOf(i + 6, raw.length))
                            sb.append(hex.toInt(16).toChar())
                            i += 4
                        }
                        else -> sb.append(raw[i + 1])
                    }
                    i += 2
                } else {
                    sb.append(c); i++
                }
            }
            return sb.toString()
        }

        private fun quote(value: String): String {
            val out = StringBuilder(value.length + 2)
            out.append('"')
            for (c in value) {
                when (c) {
                    '\\' -> out.append("\\\\"); '"' -> out.append("\\\"")
                    '\b' -> out.append("\\b"); '' -> out.append("\\f")
                    '\n' -> out.append("\\n"); '\r' -> out.append("\\r"); '\t' -> out.append("\\t")
                    else -> {
                        if (c.code < 0x20) {
                            out.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                        } else {
                            out.append(c)
                        }
                    }
                }
            }
            out.append('"')
            return out.toString()
        }
    }
}
