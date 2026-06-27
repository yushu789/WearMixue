package site.unclefish.wearmixue.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import site.unclefish.wearmixue.BuildConfig
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
import site.unclefish.wearmixue.network.QrEncoder
import site.unclefish.wearmixue.network.TokenHttpServer
import site.unclefish.wearmixue.network.defaultAttributes
import site.unclefish.wearmixue.network.NetworkUtil
import site.unclefish.wearmixue.R

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
    var showManualLogin by remember { mutableStateOf(false) }
    var showQrLogin by remember { mutableStateOf(false) }
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
        showQrLogin = false
        showManualLogin = false
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

    LaunchedEffect(auth.isUsableForOrdering) {
        if (auth.isUsableForOrdering && storesState == LoadState.Idle && repository.isReady) {
            validateThenLoadStores()
        }
    }

    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                !repository.isReady -> MissingKeyScreen()
                !auth.isUsableForOrdering -> {
                    when {
                        showQrLogin -> QrLoginScreen(
                            onToken = ::applyReceivedToken,
                            onBack = { showQrLogin = false }
                        )
                        showManualLogin -> ManualLoginScreen(
                            onSave = { session ->
                                authStore.save(session)
                                auth = authStore.read()
                                showManualLogin = false
                                message = tokenSavedMessage
                            },
                            onBack = { showManualLogin = false }
                        )
                        else -> LoginScreen(
                            onQr = { showQrLogin = true },
                            onManual = { showManualLogin = true }
                        )
                    }
                }

                selectedOrder != null -> OrderScreen(
                    order = selectedOrder,
                    orderState = orderState,
                    onRefresh = ::refreshOrder,
                    onBack = { selectedOrder = null },
                    onCancelOrder = ::cancelOrder
                )

                selectedStore == null -> StoreScreen(
                    auth = auth,
                    storesState = storesState,
                    message = message,
                    lastError = lastError,
                    onRefresh = {
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
                    onStore = ::loadProducts,
                    onOrders = ::loadOrders,
                    ordersState = ordersState,
                    orderState = orderState,
                    onOrder = { selectedOrder = it },
                    onLogout = {
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
                )

                selectedProduct == null -> ProductScreen(
                    store = selectedStore,
                    productsState = productsState,
                    onBack = { selectedStore = null },
                    onProduct = ::selectProduct
                )

                else -> ConfirmScreen(
                    product = selectedProduct,
                    selectedAttributes = selectedAttributes,
                    quoteState = quoteState,
                    orderState = orderState,
                    onAttribute = { groupIndex, attribute ->
                        while (selectedAttributes.size <= groupIndex) selectedAttributes.add(attribute)
                        selectedAttributes[groupIndex] = attribute
                        quoteState = LoadState.Idle
                    },
                    onBack = { selectedProduct = null },
                    onQuote = ::quote,
                    onSubmit = ::submitOrder,
                    onCancelOrder = ::cancelOrder
                )
            }
        }
    }
}

@Composable
private fun MissingKeyScreen() {
    ScreenColumn {
        Title(stringResource(R.string.screen_missing_key_title))
        Body(stringResource(R.string.screen_missing_key_body))
    }
}

@Composable
private fun LoginScreen(onQr: () -> Unit, onManual: () -> Unit) {
    ScreenColumn {
        Title(stringResource(R.string.screen_login_title))
        Body(stringResource(R.string.screen_login_body))
        PrimaryButton(stringResource(R.string.action_qr_login), onClick = onQr)
        PrimaryButton(stringResource(R.string.action_manual_token), onClick = onManual)
    }
}

@Composable
private fun QrLoginScreen(
    onToken: (AuthSession) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val server = remember { TokenHttpServer(scope) }

    // Observable so the UI recomposes once the server has bound its port and the
    // WiFi/IP probe resolves. Using plain remember{} left the screen stuck on the
    // loading spinner because port=-1 never updated after the async start().
    var port by remember { mutableStateOf(-1) }
    var onWifi by remember { mutableStateOf(false) }
    var ipv4 by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(server) {
        server.start()
        // start() binds the socket synchronously; read the assigned port now.
        port = server.port
        onWifi = NetworkUtil.isOnWifi(context)
        ipv4 = NetworkUtil.wifiIpv4(context)
        server.incomingTokens.collect { session -> onToken(session) }
    }
    DisposableEffect(Unit) {
        onDispose { server.stop() }
    }

    ScreenColumn {
        Title(stringResource(R.string.screen_scan_login_title))
        when {
            port <= 0 -> CircularProgressIndicator()
            !onWifi || ipv4.isNullOrBlank() -> {
                Body(stringResource(R.string.qr_connect_wifi))
                Body(stringResource(R.string.qr_bluetooth_unreachable))
            }
            else -> {
                val url = "http://$ipv4:$port/token"
                val bitmap = remember(url) { QrEncoder.encode(url, 320) }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = url,
                        modifier = Modifier.size(160.dp)
                    )
                }
                Body(url)
                Body(stringResource(R.string.qr_scan_with_phone_app))
            }
        }
        PrimaryButton(stringResource(R.string.action_back), onClick = onBack)
    }
}

@Composable
private fun ManualLoginScreen(
    onSave: (AuthSession) -> Unit,
    onBack: () -> Unit
) {
    var accessToken by remember { mutableStateOf("") }
    var customerId by remember { mutableStateOf("") }
    var seqNum by remember { mutableStateOf("") }
    var mobilePhone by remember { mutableStateOf("") }
    var loginJson by remember { mutableStateOf("") }
    var parseMessage by remember { mutableStateOf("") }
    val parseSuccessMessage = stringResource(R.string.manual_parse_success)
    val parseMissingFieldsMessage = stringResource(R.string.manual_parse_missing_fields)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item { Title(stringResource(R.string.manual_token_title)) }
        item { Body(stringResource(R.string.manual_token_body)) }
        item {
            OutlinedTextField(
                value = loginJson,
                onValueChange = { loginJson = it },
                label = { Text(stringResource(R.string.manual_login_json_label)) },
                singleLine = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            PrimaryButton(
                text = stringResource(R.string.manual_parse_json),
                enabled = loginJson.isNotBlank()
            ) {
                val parsed = runCatching { AuthSession.fromJson(loginJson) }.getOrNull()
                if (parsed != null && parsed.isUsableForOrdering) {
                    accessToken = parsed.accessToken
                    customerId = parsed.customerId
                    seqNum = parsed.seqNum
                    mobilePhone = parsed.mobilePhone
                    parseMessage = parseSuccessMessage
                } else {
                    parseMessage = parseMissingFieldsMessage
                }
            }
        }
        if (parseMessage.isNotBlank()) {
            item { Body(parseMessage) }
        }
        item {
            OutlinedTextField(
                value = accessToken,
                onValueChange = { accessToken = it },
                label = { Text(stringResource(R.string.manual_access_token_label)) },
                singleLine = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = customerId,
                onValueChange = { customerId = it },
                label = { Text(stringResource(R.string.manual_customer_id_label)) },
                singleLine = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = seqNum,
                onValueChange = { seqNum = it },
                label = { Text(stringResource(R.string.manual_seq_num_label)) },
                singleLine = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = mobilePhone,
                onValueChange = { mobilePhone = it },
                label = { Text(stringResource(R.string.manual_phone_label)) },
                singleLine = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            PrimaryButton(
                text = stringResource(R.string.action_save),
                enabled = accessToken.isNotBlank() && customerId.isNotBlank() && seqNum.isNotBlank()
            ) {
                onSave(
                    AuthSession(
                        accessToken = accessToken.trim(),
                        customerId = customerId.trim(),
                        seqNum = seqNum.trim(),
                        mobilePhone = mobilePhone.trim()
                    )
                )
            }
        }
        item { PrimaryButton(stringResource(R.string.action_back), onClick = onBack) }
        item { Body(stringResource(R.string.manual_token_hint)) }
    }
}

@Composable
private fun StoreScreen(
    auth: AuthSession,
    storesState: LoadState<List<MixueStore>>,
    message: String,
    lastError: String,
    ordersState: LoadState<List<MixueOrder>>,
    orderState: LoadState<MixueOrder>,
    onRefresh: () -> Unit,
    onStore: (MixueStore) -> Unit,
    onOrders: () -> Unit,
    onOrder: (MixueOrder) -> Unit,
    onLogout: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Title(stringResource(R.string.stores_title))
            if (auth.mobilePhone.isNotBlank()) Body(auth.mobilePhone)
            if (message.isNotBlank()) Body(message)
        }
        if (lastError.isNotBlank()) {
            item {
                Title(stringResource(R.string.debug_title))
                Body(lastError, maxLines = 12)
            }
        }
        item { PrimaryButton(stringResource(R.string.action_refresh), onClick = onRefresh) }
        when (val state = storesState) {
            LoadState.Idle -> item { Body(stringResource(R.string.stores_waiting_location)) }
            LoadState.Loading -> item { CircularProgressIndicator() }
            is LoadState.Error -> item { Body(state.message) }
            is LoadState.Success -> items(state.value) { store ->
                ListButton(
                    title = store.shopName,
                    subtitle = listOf(store.distanceText, store.shopAddress).filter { it.isNotBlank() }.joinToString(" ")
                ) { onStore(store) }
            }
        }
        item { PrimaryButton(stringResource(R.string.orders_title), onClick = onOrders) }
        when (val state = ordersState) {
            LoadState.Loading -> item { CircularProgressIndicator() }
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
            LoadState.Loading -> item { CircularProgressIndicator() }
            is LoadState.Error -> item { Body(state.message) }
            is LoadState.Success -> item {
                Body(stringResource(R.string.last_status, localizedOrderStatus(state.value.status)))
            }
            else -> Unit
        }
        item { PrimaryButton(stringResource(R.string.action_logout), onClick = onLogout) }
    }
}

@Composable
private fun OrderScreen(
    order: MixueOrder?,
    orderState: LoadState<MixueOrder>,
    onRefresh: (MixueOrder) -> Unit,
    onBack: () -> Unit,
    onCancelOrder: (MixueOrder) -> Unit
) {
    if (order == null) return
    var confirmCancel by remember(order.orderCode) { mutableStateOf(false) }
    ScreenColumn {
        Title(order.takeNo.ifBlank { order.orderCode })
        Body(order.shopName)
        if (order.status.isNotBlank()) Body(localizedOrderStatus(order.status))
        Body(stringResource(R.string.pay_amount, formatCny(order.price)))
        when (val state = orderState) {
            LoadState.Loading -> CircularProgressIndicator()
            is LoadState.Error -> Body(state.message)
            is LoadState.Success -> Body(
                stringResource(R.string.last_status, localizedOrderStatus(state.value.status))
            )
            else -> Unit
        }
        PrimaryButton(stringResource(R.string.action_refresh)) { onRefresh(order) }
        when {
            confirmCancel -> {
                Body(stringResource(R.string.cancel_order_prompt))
                PrimaryButton(stringResource(R.string.action_confirm_cancel)) {
                    confirmCancel = false
                    onCancelOrder(order)
                }
                PrimaryButton(stringResource(R.string.action_keep_order)) {
                    confirmCancel = false
                }
            }

            order.canCancelFromWatch -> {
                PrimaryButton(stringResource(R.string.action_cancel)) {
                    confirmCancel = true
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
        PrimaryButton(stringResource(R.string.action_back), onClick = onBack)
    }
}

@Composable
private fun ProductScreen(
    store: MixueStore?,
    productsState: LoadState<List<MixueProduct>>,
    onBack: () -> Unit,
    onProduct: (MixueProduct) -> Unit
) {
    val productsTitle = stringResource(R.string.products_title)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Title(store?.shopName ?: productsTitle)
            PrimaryButton(stringResource(R.string.action_back), onClick = onBack)
        }
        when (val state = productsState) {
            LoadState.Idle -> item { Body(stringResource(R.string.products_waiting)) }
            LoadState.Loading -> item { CircularProgressIndicator() }
            is LoadState.Error -> item { Body(state.message) }
            is LoadState.Success -> items(state.value) { product ->
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

@Composable
private fun ConfirmScreen(
    product: MixueProduct?,
    selectedAttributes: List<ProductAttribute>,
    quoteState: LoadState<PriceQuote>,
    orderState: LoadState<MixueOrder>,
    onAttribute: (Int, ProductAttribute) -> Unit,
    onBack: () -> Unit,
    onQuote: () -> Unit,
    onSubmit: (PriceQuote) -> Unit,
    onCancelOrder: (MixueOrder) -> Unit
) {
    if (product == null) return
    var confirmSubmittedCancel by remember(product.productId) { mutableStateOf(false) }
    val couponItemText = stringResource(R.string.coupon_item)
    val regularItemText = stringResource(R.string.regular_item)
    val defaultSelectionText = stringResource(R.string.default_selection)
    val submittedTitleText = stringResource(R.string.submitted_title)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
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
        item {
            Body(selectedAttributes.joinToString("/") { it.name }.ifBlank { defaultSelectionText })
        }
        item { PrimaryButton(stringResource(R.string.action_quote), onClick = onQuote) }
        when (val state = quoteState) {
            LoadState.Loading -> item { CircularProgressIndicator() }
            is LoadState.Error -> item { Body(state.message) }
            is LoadState.Success -> {
                val quote = state.value
                item {
                    Body(stringResource(R.string.pay_amount, formatCny(quote.total)))
                    if (quote.discountDesc.isNotBlank()) Body(quote.discountDesc)
                }
                item {
                    PrimaryButton(
                        text = if (quote.isZero) {
                            stringResource(R.string.action_order)
                        } else {
                            stringResource(R.string.action_pay_unsupported)
                        },
                        enabled = quote.isZero
                    ) {
                        if (quote.isZero) onSubmit(quote)
                    }
                }
            }

            else -> Unit
        }
        when (val state = orderState) {
            LoadState.Loading -> item { CircularProgressIndicator() }
            is LoadState.Error -> item { Body(state.message) }
            is LoadState.Success -> {
                val order = state.value
                item {
                    Title(order.takeNo.ifBlank { submittedTitleText })
                    Body(order.shopName.ifBlank { order.orderCode })
                    if (order.status.isNotBlank()) Body(localizedOrderStatus(order.status))
                }
                item {
                    when {
                        confirmSubmittedCancel -> {
                            Body(stringResource(R.string.cancel_order_prompt))
                            PrimaryButton(stringResource(R.string.action_confirm_cancel)) {
                                confirmSubmittedCancel = false
                                onCancelOrder(order)
                            }
                            PrimaryButton(stringResource(R.string.action_keep_order)) {
                                confirmSubmittedCancel = false
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
        item { PrimaryButton(stringResource(R.string.action_back), onClick = onBack) }
    }
}

@Composable
private fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

@Composable
private fun Title(text: String) {
    Text(
        text = text,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun Body(text: String, maxLines: Int = 3) {
    Text(
        text = text,
        maxLines = maxLines,
        overflow = if (maxLines > 3) TextOverflow.Clip else TextOverflow.Ellipsis
    )
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

@Composable
private fun PrimaryButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SmallButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp)
    ) {
        Text(
            text = if (selected) stringResource(R.string.selected_prefix, text) else text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ListButton(title: String, subtitle: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(text = subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
            } else {
                Spacer(Modifier.height(1.dp))
            }
        }
    }
}

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

@Composable
private fun formatCny(cents: Int): String = stringResource(R.string.currency_cny, cents / 100.0)

@Composable
private fun localizedOrderStatus(status: String): String = when (status) {
    "Making" -> stringResource(R.string.status_making)
    "Ready" -> stringResource(R.string.status_ready)
    "Done" -> stringResource(R.string.status_done)
    "Canceled" -> stringResource(R.string.status_canceled)
    else -> status
}

private const val FALLBACK_LATITUDE = 39.908722
private const val FALLBACK_LONGITUDE = 116.397499
