package site.unclefish.wearmixue.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.items
import site.unclefish.wearmixue.R
import site.unclefish.wearmixue.model.LoadState
import site.unclefish.wearmixue.model.MixueOrder
import site.unclefish.wearmixue.ui.components.Body
import site.unclefish.wearmixue.ui.components.ListButton
import site.unclefish.wearmixue.ui.components.ListItemColumn
import site.unclefish.wearmixue.ui.components.PrimaryButton
import site.unclefish.wearmixue.ui.components.ScalingScreenList
import site.unclefish.wearmixue.ui.components.Title
import site.unclefish.wearmixue.ui.components.WavyLoadingIndicator
import site.unclefish.wearmixue.ui.util.formatCny
import site.unclefish.wearmixue.ui.util.localizedOrderStatus

@Composable
internal fun OrdersScreen(
    ordersState: LoadState<List<MixueOrder>>,
    orderState: LoadState<MixueOrder>,
    onRefresh: () -> Unit,
    onOrder: (MixueOrder) -> Unit
) {
    ScalingScreenList(
        isRefreshing = ordersState == LoadState.Loading,
        onRefresh = onRefresh
    ) {
        item {
            Title(stringResource(R.string.orders_title))
        }
        when (val state = ordersState) {
            LoadState.Loading -> Unit
            is LoadState.Error -> item { Body(state.message) }
            is LoadState.Success -> items(state.value) { order ->
                ListButton(
                    title = order.shopName.ifBlank { order.orderCode },
                    subtitle = listOf(localizedOrderStatus(order.status), order.takeNo)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                ) { onOrder(order) }
            }

            else -> Unit
        }
        when (val state = orderState) {
            LoadState.Loading -> if (ordersState != LoadState.Loading) item { WavyLoadingIndicator() }
            is LoadState.Error -> item { Body(state.message) }
            is LoadState.Success -> item {
                Body(stringResource(R.string.last_status, localizedOrderStatus(state.value.status)))
            }
            else -> Unit
        }
    }
}

@Composable
internal fun OrderScreen(
    order: MixueOrder?,
    orderState: LoadState<MixueOrder>,
    onRefresh: (MixueOrder) -> Unit,
    onCancelOrder: (MixueOrder) -> Unit
) {
    if (order == null) return
    var confirmCancel by remember(order.orderCode) { mutableStateOf(false) }
    ScalingScreenList(
        isRefreshing = orderState == LoadState.Loading,
        onRefresh = { onRefresh(order) }
    ) {
        item {
            ListItemColumn {
                Title(order.takeNo.ifBlank { order.orderCode })
                Body(order.shopName)
                if (order.status.isNotBlank()) Body(localizedOrderStatus(order.status))
                Body(stringResource(R.string.pay_amount, formatCny(order.price)))
            }
        }
        when (val state = orderState) {
            LoadState.Loading -> Unit
            is LoadState.Error -> item { Body(state.message) }
            is LoadState.Success -> item {
                Body(stringResource(R.string.last_status, localizedOrderStatus(state.value.status)))
            }
            else -> Unit
        }
        when {
            confirmCancel -> {
                item {
                    ListItemColumn {
                        Body(stringResource(R.string.cancel_order_prompt))
                        PrimaryButton(stringResource(R.string.action_confirm_cancel)) {
                            confirmCancel = false
                            onCancelOrder(order)
                        }
                    }
                }
                item {
                    PrimaryButton(stringResource(R.string.action_keep_order)) {
                        confirmCancel = false
                    }
                }
            }

            order.canCancelFromWatch -> {
                item {
                    PrimaryButton(stringResource(R.string.action_cancel)) {
                        confirmCancel = true
                    }
                }
            }

            else -> {
                item {
                    PrimaryButton(
                        text = if (order.isZeroPrice) {
                            stringResource(R.string.action_cannot_cancel)
                        } else {
                            stringResource(R.string.action_pay_unsupported)
                        },
                        enabled = false
                    ) {}
                }
            }
        }
    }
}
