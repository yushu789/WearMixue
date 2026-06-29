package site.unclefish.wearmixue.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import site.unclefish.wearmixue.core.AuthSession
import site.unclefish.wearmixue.model.LoadState
import site.unclefish.wearmixue.model.MixueOrder
import site.unclefish.wearmixue.model.MixueProduct
import site.unclefish.wearmixue.model.MixueStore
import site.unclefish.wearmixue.model.PriceQuote
import site.unclefish.wearmixue.model.ProductAttribute
import site.unclefish.wearmixue.ui.screens.ConfirmScreen
import site.unclefish.wearmixue.ui.screens.HomeScreen
import site.unclefish.wearmixue.ui.screens.LoginScreen
import site.unclefish.wearmixue.ui.screens.LogoutConfirmScreen
import site.unclefish.wearmixue.ui.screens.ManualLoginScreen
import site.unclefish.wearmixue.ui.screens.MissingKeyScreen
import site.unclefish.wearmixue.ui.screens.OrderScreen
import site.unclefish.wearmixue.ui.screens.OrdersScreen
import site.unclefish.wearmixue.ui.screens.ProductScreen
import site.unclefish.wearmixue.ui.screens.QrLoginScreen
import site.unclefish.wearmixue.ui.screens.StoreScreen

@Composable
internal fun WearMixueNavHost(
    rootRoute: String,
    auth: AuthSession,
    message: String,
    storesState: LoadState<List<MixueStore>>,
    productsState: LoadState<List<MixueProduct>>,
    ordersState: LoadState<List<MixueOrder>>,
    quoteState: LoadState<PriceQuote>,
    orderState: LoadState<MixueOrder>,
    lastError: String,
    selectedStore: MixueStore?,
    selectedProduct: MixueProduct?,
    selectedOrder: MixueOrder?,
    selectedAttributes: List<ProductAttribute>,
    onTokenReceived: (AuthSession) -> Unit,
    onManualTokenSaved: (AuthSession) -> Unit,
    onRefreshStores: () -> Unit,
    onStoreSelected: (MixueStore) -> Unit,
    onLoadOrders: () -> Unit,
    onOrderSelected: (MixueOrder) -> Unit,
    onProductSelected: (MixueProduct) -> Unit,
    onAttributeSelected: (Int, ProductAttribute) -> Unit,
    onQuote: () -> Unit,
    onSubmitOrder: (PriceQuote) -> Unit,
    onCancelOrder: (MixueOrder) -> Unit,
    onRefreshOrder: (MixueOrder) -> Unit,
    onLogout: () -> Unit
) {
    val currentAuth = rememberUpdatedState(auth)
    val currentMessage = rememberUpdatedState(message)
    val currentStoresState = rememberUpdatedState(storesState)
    val currentProductsState = rememberUpdatedState(productsState)
    val currentOrdersState = rememberUpdatedState(ordersState)
    val currentQuoteState = rememberUpdatedState(quoteState)
    val currentOrderState = rememberUpdatedState(orderState)
    val currentLastError = rememberUpdatedState(lastError)
    val currentSelectedStore = rememberUpdatedState(selectedStore)
    val currentSelectedProduct = rememberUpdatedState(selectedProduct)
    val currentSelectedOrder = rememberUpdatedState(selectedOrder)
    val currentSelectedAttributes = rememberUpdatedState(selectedAttributes)
    val currentOnTokenReceived = rememberUpdatedState(onTokenReceived)
    val currentOnManualTokenSaved = rememberUpdatedState(onManualTokenSaved)
    val currentOnRefreshStores = rememberUpdatedState(onRefreshStores)
    val currentOnStoreSelected = rememberUpdatedState(onStoreSelected)
    val currentOnLoadOrders = rememberUpdatedState(onLoadOrders)
    val currentOnOrderSelected = rememberUpdatedState(onOrderSelected)
    val currentOnProductSelected = rememberUpdatedState(onProductSelected)
    val currentOnAttributeSelected = rememberUpdatedState(onAttributeSelected)
    val currentOnQuote = rememberUpdatedState(onQuote)
    val currentOnSubmitOrder = rememberUpdatedState(onSubmitOrder)
    val currentOnCancelOrder = rememberUpdatedState(onCancelOrder)
    val currentOnRefreshOrder = rememberUpdatedState(onRefreshOrder)
    val currentOnLogout = rememberUpdatedState(onLogout)

    key(rootRoute) {
        val navController = rememberSwipeDismissableNavController()

        SwipeDismissableNavHost(
            navController = navController,
            startDestination = rootRoute,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(AppRoute.MissingKey) {
                MissingKeyScreen()
            }

            composable(AppRoute.Login) {
                LoginScreen(
                    onQr = { navController.navigate(AppRoute.QrLogin) },
                    onManual = { navController.navigate(AppRoute.ManualLogin) }
                )
            }

            composable(AppRoute.QrLogin) {
                QrLoginScreen(
                    onToken = currentOnTokenReceived.value
                )
            }

            composable(AppRoute.ManualLogin) {
                ManualLoginScreen(
                    onSave = currentOnManualTokenSaved.value
                )
            }

            composable(AppRoute.Home) {
                HomeScreen(
                    auth = currentAuth.value,
                    message = currentMessage.value,
                    onStores = { navController.navigate(AppRoute.Stores) },
                    onOrders = {
                        currentOnLoadOrders.value()
                        navController.navigate(AppRoute.Orders)
                    },
                    onLogout = { navController.navigate(AppRoute.LogoutConfirm) }
                )
            }

            composable(AppRoute.LogoutConfirm) {
                LogoutConfirmScreen(
                    onCancel = { navController.popBackStack() },
                    onConfirm = currentOnLogout.value
                )
            }

            composable(AppRoute.Stores) {
                StoreScreen(
                    storesState = currentStoresState.value,
                    isRefreshing = currentStoresState.value == LoadState.Loading,
                    lastError = currentLastError.value,
                    onRefresh = currentOnRefreshStores.value,
                    onStore = { store ->
                        currentOnStoreSelected.value(store)
                        navController.navigate(AppRoute.Products)
                    }
                )
            }

            composable(AppRoute.Orders) {
                OrdersScreen(
                    ordersState = currentOrdersState.value,
                    orderState = currentOrderState.value,
                    onRefresh = currentOnLoadOrders.value,
                    onOrder = { order ->
                        currentOnOrderSelected.value(order)
                        navController.navigate(AppRoute.Order)
                    }
                )
            }

            composable(AppRoute.Products) {
                ProductScreen(
                    store = currentSelectedStore.value,
                    productsState = currentProductsState.value,
                    onProduct = { product ->
                        currentOnProductSelected.value(product)
                        navController.navigate(AppRoute.Confirm)
                    }
                )
            }

            composable(AppRoute.Confirm) {
                ConfirmScreen(
                    product = currentSelectedProduct.value,
                    selectedAttributes = currentSelectedAttributes.value,
                    quoteState = currentQuoteState.value,
                    orderState = currentOrderState.value,
                    onAttribute = currentOnAttributeSelected.value,
                    onQuote = currentOnQuote.value,
                    onSubmit = currentOnSubmitOrder.value,
                    onCancelOrder = currentOnCancelOrder.value
                )
            }

            composable(AppRoute.Order) {
                OrderScreen(
                    order = currentSelectedOrder.value,
                    orderState = currentOrderState.value,
                    onRefresh = currentOnRefreshOrder.value,
                    onCancelOrder = currentOnCancelOrder.value
                )
            }
        }
    }
}
