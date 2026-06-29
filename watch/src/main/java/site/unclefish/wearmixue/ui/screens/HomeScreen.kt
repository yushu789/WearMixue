package site.unclefish.wearmixue.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import site.unclefish.wearmixue.R
import site.unclefish.wearmixue.core.AuthSession
import site.unclefish.wearmixue.ui.components.Body
import site.unclefish.wearmixue.ui.components.PrimaryButton
import site.unclefish.wearmixue.ui.components.ScreenColumn
import site.unclefish.wearmixue.ui.components.Title
import site.unclefish.wearmixue.ui.util.maskPhoneNumber

@Composable
internal fun HomeScreen(
    auth: AuthSession,
    message: String,
    onStores: () -> Unit,
    onOrders: () -> Unit,
    onLogout: () -> Unit
) {
    ScreenColumn {
        Title(stringResource(R.string.app_name))
        if (auth.mobilePhone.isNotBlank()) Body(auth.mobilePhone.maskPhoneNumber())
        if (message.isNotBlank()) Body(message)
        PrimaryButton(
            text = stringResource(R.string.stores_title),
            leadingIcon = Icons.Filled.RestaurantMenu,
            onClick = onStores
        )
        PrimaryButton(
            text = stringResource(R.string.orders_title),
            leadingIcon = Icons.AutoMirrored.Filled.ReceiptLong,
            onClick = onOrders
        )
        PrimaryButton(
            text = stringResource(R.string.action_logout),
            leadingIcon = Icons.AutoMirrored.Filled.Logout,
            onClick = onLogout
        )
    }
}
