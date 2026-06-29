package site.unclefish.wearmixue.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ColorScheme as ComposeColorScheme
import androidx.compose.material3.MaterialTheme as ComposeMaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ColorScheme as WearColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import site.unclefish.wearmixue.BuildConfig
import site.unclefish.wearmixue.R
import site.unclefish.wearmixue.auth.AuthStore
import site.unclefish.wearmixue.core.AuthSession
import site.unclefish.wearmixue.model.CartLine
import site.unclefish.wearmixue.model.LoadState
import site.unclefish.wearmixue.model.MixueOrder
import site.unclefish.wearmixue.model.MixueProduct
import site.unclefish.wearmixue.model.MixueStore
import site.unclefish.wearmixue.model.PriceQuote
import site.unclefish.wearmixue.model.ProductAttribute
import site.unclefish.wearmixue.network.MixueApiClient
import site.unclefish.wearmixue.network.MixueApiException
import site.unclefish.wearmixue.network.MixueRepository
import site.unclefish.wearmixue.network.MixueSigner
import site.unclefish.wearmixue.network.defaultAttributes
import site.unclefish.wearmixue.ui.navigation.AppRoute
import site.unclefish.wearmixue.ui.navigation.WearMixueNavHost

@Composable
fun WearMixueApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authStore = remember { AuthStore(context) }
    val repository = remember {
        MixueRepository(
            MixueApiClient(authStore, MixueSigner(BuildConfig.MIXUE_PRIVATE_KEY)),
            authStore
        )
    }

    var auth by remember { mutableStateOf(authStore.read()) }
    var storesState by remember { mutableStateOf<LoadState<List<MixueStore>>>(LoadState.Idle) }
    var productsState by remember { mutableStateOf<LoadState<List<MixueProduct>>>(LoadState.Idle) }
    var ordersState by remember { mutableStateOf<LoadState<List<MixueOrder>>>(LoadState.Idle) }
    var selectedStore by remember { mutableStateOf<MixueStore?>(null) }
    var selectedProduct by remember { mutableStateOf<MixueProduct?>(null) }
    var selectedOrder by remember { mutableStateOf<MixueOrder?>(null) }
    val selectedAttributes = remember { mutableStateListOf<ProductAttribute>() }
    var quoteState by remember { mutableStateOf<LoadState<PriceQuote>>(LoadState.Idle) }
    var orderState by remember { mutableStateOf<LoadState<MixueOrder>>(LoadState.Idle) }
    var message by remember { mutableStateOf("") }
    var lastError by remember { mutableStateOf("") }
    val loginCompleteMessage = stringResource(R.string.message_login_complete)
    val tokenSavedMessage = stringResource(R.string.message_token_saved)
    val orderCanceledMessage = stringResource(R.string.message_order_canceled)
    val storeLoadFailedMessage = stringResource(R.string.error_store_load_failed)
    val tokenCheckFailedMessage = stringResource(R.string.error_token_check_failed)
    val productsLoadFailedMessage = stringResource(R.string.error_products_load_failed)
    val ordersLoadFailedMessage = stringResource(R.string.error_orders_load_failed)
    val quoteFailedMessage = stringResource(R.string.error_quote_failed)
    val submitFailedMessage = stringResource(R.string.error_submit_failed)
    val cancelFailedMessage = stringResource(R.string.error_cancel_failed)
    val refreshFailedMessage = stringResource(R.string.error_refresh_failed)

    fun applyReceivedToken(session: AuthSession) {
        authStore.save(session)
        auth = authStore.read()
        message = loginCompleteMessage
        lastError = ""
    }

    fun syncAuthAfterFailure(error: Throwable, fallback: String): LoadState.Error {
        val detail = error.toReadableError(fallback)
        lastError = detail
        if (!authStore.read().isUsableForOrdering) {
            auth = AuthSession()
            selectedStore = null
            selectedProduct = null
            selectedOrder = null
        }
        return LoadState.Error(detail)
    }

    fun loadStores() {
        scope.launch {
            storesState = LoadState.Loading
            val location = context.lastKnownLocation()
            storesState = runIoCatching {
                repository.findNearbyStores(
                    location?.latitude ?: FALLBACK_LATITUDE,
                    location?.longitude ?: FALLBACK_LONGITUDE
                )
            }.fold(
                onSuccess = {
                    lastError = ""
                    LoadState.Success(it)
                },
                onFailure = { syncAuthAfterFailure(it, storeLoadFailedMessage) }
            )
        }
    }

    fun validateThenLoadStores() {
        scope.launch {
            storesState = LoadState.Loading
            runIoCatching { repository.validateToken() }.fold(
                onSuccess = { loadStores() },
                onFailure = {
                    storesState = syncAuthAfterFailure(it, tokenCheckFailedMessage)
                    message = storesState.let { state -> if (state is LoadState.Error) state.message else "" }
                }
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        loadStores()
    }

    fun loadProducts(store: MixueStore) {
        selectedStore = store
        selectedProduct = null
        quoteState = LoadState.Idle
        orderState = LoadState.Idle
        scope.launch {
            productsState = LoadState.Loading
            val result = runIoCatching {
                val orderingStore = repository.getShopInfo(store.shopId)?.withFallback(store) ?: store
                val coupons = repository.getCouponProducts(orderingStore)
                val menu = repository.getMenuProducts(orderingStore.shopId)
                val products = coupons + menu.filterNot { menuProduct ->
                    coupons.any { couponProduct -> couponProduct.productId == menuProduct.productId }
                }
                orderingStore to products
            }.fold(
                onSuccess = { (orderingStore, products) ->
                    lastError = ""
                    selectedStore = orderingStore
                    LoadState.Success(products)
                },
                onFailure = { syncAuthAfterFailure(it, productsLoadFailedMessage) }
            )
            productsState = result
        }
    }

    fun loadOrders() {
        scope.launch {
            ordersState = LoadState.Loading
            ordersState = runIoCatching { repository.getOrders() }.fold(
                onSuccess = {
                    lastError = ""
                    LoadState.Success(it)
                },
                onFailure = { syncAuthAfterFailure(it, ordersLoadFailedMessage) }
            )
        }
    }

    fun selectProduct(product: MixueProduct) {
        val initialAttributes = product.defaultAttributes()
        selectedProduct = product
        selectedAttributes.clear()
        selectedAttributes.addAll(initialAttributes)
        quoteState = LoadState.Idle
        orderState = LoadState.Idle
        val store = selectedStore ?: return
        scope.launch {
            runIoCatching { repository.getProductDetail(store, product) }
                .onSuccess { detailed ->
                    if (selectedProduct?.productId == product.productId) {
                        selectedProduct = detailed
                        if (selectedAttributes.map { it.id } == initialAttributes.map { it.id }) {
                            selectedAttributes.clear()
                            selectedAttributes.addAll(detailed.defaultAttributes())
                        }
                    }
                }
        }
    }

    fun quote() {
        val store = selectedStore ?: return
        val product = selectedProduct ?: return
        scope.launch {
            quoteState = LoadState.Loading
            quoteState = runIoCatching {
                repository.calcPrice(store, CartLine(product, selectedAttributes.toList()))
            }.fold(
                onSuccess = {
                    lastError = ""
                    LoadState.Success(it)
                },
                onFailure = { syncAuthAfterFailure(it, quoteFailedMessage) }
            )
        }
    }

    fun submitOrder(quote: PriceQuote) {
        val store = selectedStore ?: return
        val product = selectedProduct ?: return
        scope.launch {
            orderState = LoadState.Loading
            orderState = runIoCatching {
                val order = repository.saveZeroOrder(store, CartLine(product, selectedAttributes.toList()), quote)
                runCatching { repository.getOrderInfo(order.orderCode) }.getOrDefault(order)
            }.fold(
                onSuccess = { order ->
                    lastError = ""
                    LoadState.Success(order)
                },
                onFailure = { syncAuthAfterFailure(it, submitFailedMessage) }
            )
        }
    }

    fun cancelOrder(order: MixueOrder) {
        scope.launch {
            orderState = LoadState.Loading
            orderState = runIoCatching { repository.cancelZeroOrder(order.orderCode) }.fold(
                onSuccess = { canceled ->
                    lastError = ""
                    message = orderCanceledMessage.format(canceled.orderCode).trim()
                    selectedOrder = canceled
                    if (ordersState is LoadState.Success) loadOrders()
                    LoadState.Success(canceled)
                },
                onFailure = { syncAuthAfterFailure(it, cancelFailedMessage) }
            )
        }
    }

    fun refreshOrder(order: MixueOrder) {
        scope.launch {
            orderState = LoadState.Loading
            orderState = runIoCatching { repository.refreshOrder(order.orderCode) }.fold(
                onSuccess = { refreshed ->
                    lastError = ""
                    selectedOrder = refreshed
                    LoadState.Success(refreshed)
                },
                onFailure = { syncAuthAfterFailure(it, refreshFailedMessage) }
            )
        }
    }

    fun logout() {
        authStore.clear()
        auth = AuthSession()
        selectedStore = null
        selectedProduct = null
        selectedOrder = null
        storesState = LoadState.Idle
        productsState = LoadState.Idle
        ordersState = LoadState.Idle
        orderState = LoadState.Idle
        message = ""
        lastError = ""
    }

    LaunchedEffect(auth.isUsableForOrdering) {
        if (auth.isUsableForOrdering && storesState == LoadState.Idle && repository.isReady) {
            validateThenLoadStores()
        }
    }

    val composeColorScheme = ComposeMaterialTheme.colorScheme
    val wearColorScheme = remember(composeColorScheme) {
        composeColorScheme.toWearColorScheme()
    }

    MaterialTheme(
        colorScheme = dynamicColorScheme(context) ?: wearColorScheme
    ) {
        val rootRoute = when {
            !repository.isReady -> AppRoute.MissingKey
            auth.isUsableForOrdering -> AppRoute.Home
            else -> AppRoute.Login
        }

        AppScaffold(containerColor = MaterialTheme.colorScheme.background) {
            WearMixueNavHost(
                rootRoute = rootRoute,
                auth = auth,
                message = message,
                storesState = storesState,
                productsState = productsState,
                ordersState = ordersState,
                quoteState = quoteState,
                orderState = orderState,
                lastError = lastError,
                selectedStore = selectedStore,
                selectedProduct = selectedProduct,
                selectedOrder = selectedOrder,
                selectedAttributes = selectedAttributes,
                onTokenReceived = ::applyReceivedToken,
                onManualTokenSaved = { session ->
                    authStore.save(session)
                    auth = authStore.read()
                    message = tokenSavedMessage
                },
                onRefreshStores = {
                    if (context.hasLocationPermission()) {
                        loadStores()
                    } else {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                },
                onStoreSelected = ::loadProducts,
                onLoadOrders = ::loadOrders,
                onOrderSelected = { selectedOrder = it },
                onProductSelected = ::selectProduct,
                onAttributeSelected = { groupIndex, attribute ->
                    while (selectedAttributes.size <= groupIndex) selectedAttributes.add(attribute)
                    selectedAttributes[groupIndex] = attribute
                    quoteState = LoadState.Idle
                },
                onQuote = ::quote,
                onSubmitOrder = ::submitOrder,
                onCancelOrder = ::cancelOrder,
                onRefreshOrder = ::refreshOrder,
                onLogout = ::logout
            )
        }
    }
}

private fun Throwable.toReadableError(fallback: String): String = when (this) {
    is MixueApiException -> message.ifBlank { fallback }
    else -> listOf(fallback, this::class.java.simpleName, message.orEmpty())
        .filter { it.isNotBlank() }
        .joinToString(": ")
}

private suspend fun <T> runIoCatching(block: suspend () -> T): Result<T> =
    withContext(Dispatchers.IO) {
        runCatching { block() }
    }

private fun ComposeColorScheme.toWearColorScheme(): WearColorScheme = WearColorScheme(
    primary = primary,
    primaryDim = primaryContainer,
    primaryContainer = primaryContainer,
    onPrimary = onPrimary,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondary,
    secondaryDim = secondaryContainer,
    secondaryContainer = secondaryContainer,
    onSecondary = onSecondary,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary,
    tertiaryDim = tertiaryContainer,
    tertiaryContainer = tertiaryContainer,
    onTertiary = onTertiary,
    onTertiaryContainer = onTertiaryContainer,
    surfaceContainerLow = surfaceContainerLow,
    surfaceContainer = surfaceContainer,
    surfaceContainerHigh = surfaceContainerHigh,
    onSurface = onSurface,
    onSurfaceVariant = onSurfaceVariant,
    outline = outline,
    outlineVariant = outlineVariant,
    background = background,
    onBackground = onBackground,
    error = error,
    errorDim = errorContainer,
    errorContainer = errorContainer,
    onError = onError,
    onErrorContainer = onErrorContainer
)

private fun Context.hasLocationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

private fun Context.lastKnownLocation(): Location? {
    if (!hasLocationPermission()) return null
    val manager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        .mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }
        .maxByOrNull { it.time }
}

private fun MixueStore.withFallback(fallback: MixueStore): MixueStore {
    return copy(
        shopId = shopId.ifBlank { fallback.shopId },
        shopName = shopName.ifBlank { fallback.shopName },
        shopAddress = shopAddress.ifBlank { fallback.shopAddress },
        poiId = poiId.ifBlank { fallback.poiId },
        distanceText = distanceText.ifBlank { fallback.distanceText },
        deliveryType = deliveryType ?: fallback.deliveryType
    )
}

private const val FALLBACK_LATITUDE = 39.908722
private const val FALLBACK_LONGITUDE = 116.397499
