package site.unclefish.wearmixue.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import site.unclefish.wearmixue.R

internal fun String.maskPhoneNumber(): String {
    val digitIndexes = indices.filter { this[it].isDigit() }
    if (digitIndexes.size < 7) return this

    val maskStart = (digitIndexes.size - 8).coerceAtLeast(3)
    if (maskStart + 4 > digitIndexes.size) return this

    val chars = toCharArray()
    digitIndexes.subList(maskStart, maskStart + 4).forEach { index ->
        chars[index] = '*'
    }
    return chars.concatToString()
}

@Composable
internal fun formatCny(cents: Int): String = stringResource(R.string.currency_cny, cents / 100.0)

@Composable
internal fun localizedOrderStatus(status: String): String = when (status) {
    "Making" -> stringResource(R.string.status_making)
    "Ready" -> stringResource(R.string.status_ready)
    "Done" -> stringResource(R.string.status_done)
    "Canceled" -> stringResource(R.string.status_canceled)
    else -> status
}
