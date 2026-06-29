package site.unclefish.wearmixue.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.items
import site.unclefish.wearmixue.R
import site.unclefish.wearmixue.model.LoadState
import site.unclefish.wearmixue.model.MixueStore
import site.unclefish.wearmixue.ui.components.Body
import site.unclefish.wearmixue.ui.components.FadeInListItem
import site.unclefish.wearmixue.ui.components.ListButton
import site.unclefish.wearmixue.ui.components.ListItemColumn
import site.unclefish.wearmixue.ui.components.ScalingScreenList
import site.unclefish.wearmixue.ui.components.Title
import site.unclefish.wearmixue.ui.components.WavyLoadingIndicator

@Composable
internal fun StoreScreen(
    storesState: LoadState<List<MixueStore>>,
    isRefreshing: Boolean,
    lastError: String,
    onRefresh: () -> Unit,
    onStore: (MixueStore) -> Unit
) {
    ScalingScreenList(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh
    ) {
        item {
            Title(stringResource(R.string.stores_title))
        }
        if (lastError.isNotBlank()) {
            item {
                ListItemColumn {
                    Title(stringResource(R.string.debug_title))
                    Body(lastError, maxLines = 12)
                }
            }
        }
        when (val state = storesState) {
            LoadState.Idle -> item { FadeInListItem { Body(stringResource(R.string.stores_waiting_location)) } }
            LoadState.Loading -> item { FadeInListItem { WavyLoadingIndicator() } }
            is LoadState.Error -> item { FadeInListItem { Body(state.message) } }
            is LoadState.Success -> items(state.value, key = { it.shopId }) { store ->
                FadeInListItem {
                    ListButton(
                        title = store.shopName,
                        subtitle = listOf(store.distanceText, store.shopAddress)
                            .filter { it.isNotBlank() }
                            .joinToString(" ")
                    ) { onStore(store) }
                }
            }
        }
    }
}
