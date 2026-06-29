package site.unclefish.wearmixue.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.items
import site.unclefish.wearmixue.R
import site.unclefish.wearmixue.model.LoadState
import site.unclefish.wearmixue.model.MixueOrder
import site.unclefish.wearmixue.model.MixueProduct
import site.unclefish.wearmixue.model.MixueStore
import site.unclefish.wearmixue.model.PriceQuote
import site.unclefish.wearmixue.model.ProductAttribute
import site.unclefish.wearmixue.ui.components.Body
import site.unclefish.wearmixue.ui.components.FadeInListItem
import site.unclefish.wearmixue.ui.components.ListButton
import site.unclefish.wearmixue.ui.components.ListItemColumn
import site.unclefish.wearmixue.ui.components.PrimaryButton
import site.unclefish.wearmixue.ui.components.ScalingScreenList
import site.unclefish.wearmixue.ui.components.SmallButton
import site.unclefish.wearmixue.ui.components.Title
import site.unclefish.wearmixue.ui.components.WavyLoadingIndicator
import site.unclefish.wearmixue.ui.util.formatCny
import site.unclefish.wearmixue.ui.util.localizedOrderStatus

@Composable
internal fun ProductScreen(
    store: MixueStore?,
    productsState: LoadState<List<MixueProduct>>,
    onProduct: (MixueProduct) -> Unit
) {
    val productsTitle = stringResource(R.string.products_title)
    ScalingScreenList {
        item {
            Title(store?.shopName ?: productsTitle)
        }
        when (val state = productsState) {
            LoadState.Idle -> item { FadeInListItem { Body(stringResource(R.string.products_waiting)) } }
            LoadState.Loading -> item { FadeInListItem { WavyLoadingIndicator() } }
            is LoadState.Error -> item { FadeInListItem { Body(state.message) } }
            is LoadState.Success -> items(state.value, key = { it.productId }) { product ->
                FadeInListItem {
                    ListButton(
                        title = if (product.hasCoupon) {
                            stringResource(R.string.coupon_prefix, product.productName)
                        } else {
                            product.productName
                        },
                        subtitle = "${formatCny(product.productPrice)} ${product.couponRuleName}".trim()
                    ) { onProduct(product) }
                }
            }
        }
    }
}

@Composable
internal fun ConfirmScreen(
    product: MixueProduct?,
    selectedAttributes: List<ProductAttribute>,
    quoteState: LoadState<PriceQuote>,
    orderState: LoadState<MixueOrder>,
    onAttribute: (Int, ProductAttribute) -> Unit,
    onQuote: () -> Unit,
    onSubmit: (PriceQuote) -> Unit,
    onCancelOrder: (MixueOrder) -> Unit
) {
    if (product == null) return
    var confirmSubmittedCancel by remember(product.productId) { mutableStateOf(false) }
    val couponItemText = stringResource(R.string.coupon_item)
    val regularItemText = stringResource(R.string.regular_item)
    val submittedTitleText = stringResource(R.string.submitted_title)
    val quote = (quoteState as? LoadState.Success)?.value
    var displayedQuote by remember(product.productId) { mutableStateOf<PriceQuote?>(null) }

    LaunchedEffect(quote) {
        if (quote != null) displayedQuote = quote
    }

    val animatedQuote = quote ?: displayedQuote

    ScalingScreenList {
        item {
            ListItemColumn {
                Title(product.productName)
                Body(
                    product.couponRuleName.ifBlank {
                        if (product.hasCoupon) {
                            couponItemText
                        } else {
                            regularItemText
                        }
                    }
                )
            }
        }
        product.attributeGroups.forEachIndexed { index, group ->
            item { Body(group.name) }
            group.options.chunked(2).forEach { options ->
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        options.forEach { option ->
                            SmallButton(
                                text = option.name,
                                selected = selectedAttributes.getOrNull(index)?.id == option.id,
                                modifier = Modifier.weight(1f)
                            ) { onAttribute(index, option) }
                        }
                    }
                }
            }
        }
        item { PrimaryButton(stringResource(R.string.action_quote), onClick = onQuote) }
        item {
            AnimatedVisibility(
                visible = quote != null,
                enter = fadeIn() + expandVertically(),
                exit = shrinkVertically() + fadeOut()
            ) {
                animatedQuote?.let { currentQuote ->
                    ListItemColumn {
                        Body(stringResource(R.string.pay_amount, formatCny(currentQuote.total)))
                        if (currentQuote.discountDesc.isNotBlank()) Body(currentQuote.discountDesc)
                    }
                }
            }
        }
        item {
            AnimatedVisibility(
                visible = quote != null,
                enter = fadeIn() + expandVertically(),
                exit = shrinkVertically() + fadeOut()
            ) {
                animatedQuote?.let { currentQuote ->
                    PrimaryButton(
                        text = if (currentQuote.isZero) {
                            stringResource(R.string.action_order)
                        } else {
                            stringResource(R.string.action_pay_unsupported)
                        },
                        enabled = currentQuote.isZero
                    ) {
                        if (currentQuote.isZero) onSubmit(currentQuote)
                    }
                }
            }
        }
        when (val state = quoteState) {
            LoadState.Loading -> item { WavyLoadingIndicator() }
            is LoadState.Error -> item { Body(state.message) }
            else -> Unit
        }
        when (val state = orderState) {
            LoadState.Loading -> item { WavyLoadingIndicator() }
            is LoadState.Error -> item { Body(state.message) }
            is LoadState.Success -> {
                val order = state.value
                item {
                    ListItemColumn {
                        Title(order.takeNo.ifBlank { submittedTitleText })
                        Body(order.shopName.ifBlank { order.orderCode })
                        if (order.status.isNotBlank()) Body(localizedOrderStatus(order.status))
                    }
                }
                item {
                    when {
                        confirmSubmittedCancel -> {
                            ListItemColumn {
                                Body(stringResource(R.string.cancel_order_prompt))
                                PrimaryButton(stringResource(R.string.action_confirm_cancel)) {
                                    confirmSubmittedCancel = false
                                    onCancelOrder(order)
                                }
                                PrimaryButton(stringResource(R.string.action_keep_order)) {
                                    confirmSubmittedCancel = false
                                }
                            }
                        }

                        order.canCancelFromWatch -> {
                            PrimaryButton(stringResource(R.string.action_cancel)) {
                                confirmSubmittedCancel = true
                            }
                        }

                        else -> {
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

            else -> Unit
        }
    }
}
