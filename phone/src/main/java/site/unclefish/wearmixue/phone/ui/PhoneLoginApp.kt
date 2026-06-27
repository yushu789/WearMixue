package site.unclefish.wearmixue.phone.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.launch
import site.unclefish.wearmixue.core.AuthSession
import site.unclefish.wearmixue.phone.R
import site.unclefish.wearmixue.phone.net.WatchTokenClient

private enum class LoginStage { Scanning, Input, Sending, Done }

@Composable
fun PhoneLoginApp() {
    val scope = rememberCoroutineScope()
    val tokenClient = remember { WatchTokenClient() }
    val emptyWatchUrlMessage = stringResource(R.string.phone_status_watch_url_empty)
    val missingSessionMessage = stringResource(R.string.phone_status_session_missing)
    val sendingMessage = stringResource(R.string.phone_status_sending)
    val sentMessage = stringResource(R.string.phone_status_token_sent)
    val failedToReachMessage = stringResource(R.string.phone_status_failed_to_reach)
    val scannedMessage = stringResource(R.string.phone_status_watch_url_scanned)

    var watchUrl by remember { mutableStateOf("") }
    var sessionJson by remember { mutableStateOf("") }
    var stage by remember { mutableStateOf(LoginStage.Scanning) }
    var status by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        when (stage) {
            LoginStage.Scanning -> QrScanStage(
                onScanned = { code ->
                    watchUrl = code.trim()
                    status = scannedMessage
                    stage = LoginStage.Input
                },
                onCancel = {
                    if (watchUrl.isNotBlank()) {
                        stage = LoginStage.Input
                    }
                }
            )

            LoginStage.Input -> RelayInputStage(
                sessionJson = sessionJson,
                onSessionJsonChange = { sessionJson = it },
                status = status,
                onScanAgain = { stage = LoginStage.Scanning },
                onSend = {
                    val target = watchUrl.trim()
                    val session = runCatching { AuthSession.fromJson(sessionJson) }.getOrNull()
                    if (target.isBlank()) {
                        status = emptyWatchUrlMessage
                        stage = LoginStage.Scanning
                        return@RelayInputStage
                    }
                    if (session == null || !session.isUsableForOrdering) {
                        status = missingSessionMessage
                        return@RelayInputStage
                    }
                    scope.launch {
                        stage = LoginStage.Sending
                        status = sendingMessage
                        val ok = tokenClient.sendToken(target, session)
                        status = if (ok) {
                            sentMessage
                        } else {
                            failedToReachMessage.format(target)
                        }
                        stage = if (ok) LoginStage.Done else LoginStage.Input
                    }
                }
            )

            LoginStage.Sending -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(status)
            }

            LoginStage.Done -> DoneStage(
                status = status,
                onAgain = {
                    watchUrl = ""
                    sessionJson = ""
                    status = ""
                    stage = LoginStage.Scanning
                }
            )
        }
    }
}

@Composable
private fun QrScanStage(
    onScanned: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val imageNoQrMessage = stringResource(R.string.phone_scan_image_no_qr)
    val imageFailedMessage = stringResource(R.string.phone_scan_image_failed)
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraError by remember { mutableStateOf("") }
    var imageScanStatus by remember { mutableStateOf("") }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            imageScanStatus = ""
            scanQrCodeFromImage(
                context = context,
                uri = uri,
                noQrMessage = imageNoQrMessage,
                failedMessage = imageFailedMessage,
                onScanned = onScanned,
                onStatus = { imageScanStatus = it }
            )
        }
    }
    val pickImage = {
        imagePicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            CameraQrScanner(
                onScanned = onScanned,
                onCameraError = { cameraError = it },
                modifier = Modifier.fillMaxSize()
            )
            ScanOverlay(
                cameraError = cameraError,
                imageScanStatus = imageScanStatus,
                onPickImage = pickImage,
                onCancel = onCancel
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.phone_scan_permission_needed))
                Spacer(Modifier.height(12.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text(stringResource(R.string.phone_scan_action_grant_permission))
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = pickImage) {
                    Icon(
                        imageVector = Icons.Filled.ImageSearch,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.phone_action_scan_image))
                }
                if (imageScanStatus.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(imageScanStatus)
                }
            }
        }
    }
}

@Composable
private fun ScanOverlay(
    cameraError: String,
    imageScanStatus: String,
    onPickImage: () -> Unit,
    onCancel: () -> Unit
) {
    val helperText = cameraError.ifBlank {
        imageScanStatus.ifBlank { stringResource(R.string.phone_scan_hint) }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.48f))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.phone_scan_title),
                color = Color.White
            )
            Text(
                text = helperText,
                color = Color.White
            )
        }
        Column(
            modifier = Modifier.padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = onPickImage) {
                Icon(
                    imageVector = Icons.Filled.ImageSearch,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.phone_action_scan_image))
            }
            OutlinedButton(onClick = onCancel) {
                Text(stringResource(R.string.phone_scan_action_cancel))
            }
        }
    }
}

@Composable
private fun CameraQrScanner(
    onScanned: (String) -> Unit,
    onCameraError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(FORMAT_QR_CODE)
                .build()
        )
    }
    val scanned = remember { AtomicBoolean(false) }

    AndroidView(
        factory = { PreviewView(it).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } },
        modifier = modifier
    ) { previewView ->
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                runCatching {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(
                                executor,
                                QrCodeAnalyzer(
                                    scanner = scanner,
                                    scanned = scanned,
                                    mainHandler = mainHandler,
                                    onScanned = onScanned
                                )
                            )
                        }

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                }.onFailure { error ->
                    onCameraError(
                        context.getString(
                            R.string.phone_scan_camera_failed,
                            error.localizedMessage ?: error.javaClass.simpleName
                        )
                    )
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            }
            scanner.close()
            executor.shutdown()
        }
    }
}

private class QrCodeAnalyzer(
    private val scanner: BarcodeScanner,
    private val scanned: AtomicBoolean,
    private val mainHandler: Handler,
    private val onScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val value = barcodes.firstUsableQrValue()
                if (value != null && scanned.compareAndSet(false, true)) {
                    mainHandler.post { onScanned(value) }
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}

private fun scanQrCodeFromImage(
    context: Context,
    uri: Uri,
    noQrMessage: String,
    failedMessage: String,
    onScanned: (String) -> Unit,
    onStatus: (String) -> Unit
) {
    val image = runCatching { InputImage.fromFilePath(context, uri) }
        .getOrElse { error ->
            onStatus(failedMessage.format(error.localizedMessage ?: error.javaClass.simpleName))
            return
        }
    val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(FORMAT_QR_CODE)
            .build()
    )

    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            val value = barcodes.firstUsableQrValue()
            if (value == null) {
                onStatus(noQrMessage)
            } else {
                onScanned(value)
            }
        }
        .addOnFailureListener { error ->
            onStatus(failedMessage.format(error.localizedMessage ?: error.javaClass.simpleName))
        }
        .addOnCompleteListener {
            scanner.close()
        }
}

private fun List<Barcode>.firstUsableQrValue(): String? {
    return firstNotNullOfOrNull { barcode ->
        barcode.rawValue
            ?.trim()
            ?.takeIf { barcode.format == FORMAT_QR_CODE && it.isNotBlank() }
    }
}

@Composable
private fun RelayInputStage(
    sessionJson: String,
    onSessionJsonChange: (String) -> Unit,
    status: String,
    onScanAgain: () -> Unit,
    onSend: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.phone_title_auth_relay))
        Text(stringResource(R.string.phone_subtitle_auth_relay))
        if (status.isNotBlank()) Text(status)
        OutlinedButton(onClick = onScanAgain) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Text(stringResource(R.string.phone_action_scan_qr))
        }
        OutlinedTextField(
            value = sessionJson,
            onValueChange = onSessionJsonChange,
            label = { Text(stringResource(R.string.phone_label_session_json)) },
            minLines = 6,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onSend,
            enabled = sessionJson.isNotBlank()
        ) { Text(stringResource(R.string.phone_action_send_to_watch)) }
    }
}

@Composable
private fun DoneStage(status: String, onAgain: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(status.ifBlank { stringResource(R.string.phone_status_done) })
        Button(onClick = onAgain) { Text(stringResource(R.string.phone_action_login_again)) }
    }
}
