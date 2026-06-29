package site.unclefish.wearmixue.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import site.unclefish.wearmixue.R
import site.unclefish.wearmixue.core.AuthSession
import site.unclefish.wearmixue.network.NetworkUtil
import site.unclefish.wearmixue.network.QrEncoder
import site.unclefish.wearmixue.network.TokenHttpServer
import site.unclefish.wearmixue.ui.components.Body
import site.unclefish.wearmixue.ui.components.PrimaryButton
import site.unclefish.wearmixue.ui.components.ScalingScreenList
import site.unclefish.wearmixue.ui.components.ScreenColumn
import site.unclefish.wearmixue.ui.components.Title
import site.unclefish.wearmixue.ui.components.WavyLoadingIndicator

@Composable
internal fun MissingKeyScreen() {
    ScreenColumn {
        Title(stringResource(R.string.screen_missing_key_title))
        Body(stringResource(R.string.screen_missing_key_body))
    }
}

@Composable
internal fun LoginScreen(onQr: () -> Unit, onManual: () -> Unit) {
    ScreenColumn {
        Title(stringResource(R.string.screen_login_title))
        Body(stringResource(R.string.screen_login_body))
        PrimaryButton(stringResource(R.string.action_qr_login), onClick = onQr)
        PrimaryButton(stringResource(R.string.action_manual_token), onClick = onManual)
    }
}

@Composable
internal fun QrLoginScreen(
    onToken: (AuthSession) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val server = remember { TokenHttpServer(scope) }

    var port by remember { mutableStateOf(-1) }
    var onWifi by remember { mutableStateOf(false) }
    var ipv4 by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(server) {
        server.start()
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
            port <= 0 -> WavyLoadingIndicator()
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
    }
}

@Composable
internal fun ManualLoginScreen(
    onSave: (AuthSession) -> Unit
) {
    var accessToken by remember { mutableStateOf("") }
    var customerId by remember { mutableStateOf("") }
    var seqNum by remember { mutableStateOf("") }
    var mobilePhone by remember { mutableStateOf("") }
    var loginJson by remember { mutableStateOf("") }
    var parseMessage by remember { mutableStateOf("") }
    val parseSuccessMessage = stringResource(R.string.manual_parse_success)
    val parseMissingFieldsMessage = stringResource(R.string.manual_parse_missing_fields)

    ScalingScreenList {
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
        item { Body(stringResource(R.string.manual_token_hint)) }
    }
}
