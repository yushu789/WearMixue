package site.unclefish.wearmixue.network

object JsonCodec {
    fun stringify(value: Any?): String = when (value) {
        null -> "null"
        is String -> quote(value)
        is Boolean -> value.toString()
        is Int, is Long, is Float, is Double -> value.toString()
        is Number -> value.toString()
        is Map<*, *> -> value.entries.joinToString(prefix = "{", postfix = "}", separator = ",") { entry ->
            quote(entry.key.toString()) + ":" + stringify(entry.value)
        }
        is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]", separator = ",") { stringify(it) }
        is Array<*> -> value.joinToString(prefix = "[", postfix = "]", separator = ",") { stringify(it) }
        else -> quote(value.toString())
    }

    fun quote(value: String): String {
        val out = StringBuilder(value.length + 2)
        out.append('"')
        for (char in value) {
            when (char) {
                '\\' -> out.append("\\\\")
                '"' -> out.append("\\\"")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> {
                    if (char.code < 0x20) {
                        out.append("\\u")
                        out.append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        out.append(char)
                    }
                }
            }
        }
        out.append('"')
        return out.toString()
    }
}
