package com.farmcare.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.farmcare.app.FarmViewModel
import com.farmcare.app.ui.theme.*
import com.farmcare.app.util.QR_PREFIX
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@Composable
fun ScanScreen(vm: FarmViewModel, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current; val d = vm.s.collectAsState().value.d
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }
    var error by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }; var manual by remember { mutableStateOf("") }
    var cameraFailed by remember { mutableStateOf(false) }

    fun handle(raw: String) {
        if (busy) return
        if (!raw.startsWith(QR_PREFIX) || raw.length <= QR_PREFIX.length + 8) { error = "This is not a FarmCare animal tag."; return }
        busy = true; error = null
        vm.resolveQr(raw.removePrefix(QR_PREFIX)) { id, e -> busy = false; if (id != null) onOpen(id) else error = e }
    }
    AppScaffold("Scan QR", tab = true) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    granted && !cameraFailed -> CameraPreview({ cameraFailed = true }) { handle(it) }
                    granted -> Text("The camera could not be started. Use manual search below.", Modifier.padding(24.dp), color = Danger)
                    else -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Camera access is needed to scan animal tags.", style = MaterialTheme.typography.titleMedium)
                        Text("FarmCare uses the camera only while this screen is open and does not save photos.")
                        if (asked) Text("Permission was denied. You can allow it in the app's system settings, or search by ID below.", color = Warning)
                        Button({ launcher.launch(Manifest.permission.CAMERA) }, colors = ButtonDefaults.buttonColors(containerColor = Forest)) { Text("Allow camera") }
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                error?.let { Text(it, color = Danger) }
                Text("Manual search", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(manual, { manual = it.uppercase() }, Modifier.weight(1f), singleLine = true, placeholder = { Text("e.g. G001") })
                    Button({ val a = d.animals.find { it.code.equals(manual.trim(), true) }; if (a != null) onOpen(a.id) else error = "No animal with ID \"${manual.trim()}\" was found." },
                        enabled = manual.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = Forest)) { Text("Find") }
                }
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraPreview(onError: () -> Unit, onCode: (String) -> Unit) {
    val ctx = LocalContext.current; val owner = LocalLifecycleOwner.current; val latest by rememberUpdatedState(onCode)
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()) }
    val main = remember { ContextCompat.getMainExecutor(ctx) }
    DisposableEffect(Unit) { onDispose { scanner.close(); executor.shutdown() } }
    AndroidView(modifier = Modifier.fillMaxSize(), factory = { c ->
        val view = PreviewView(c); val future = ProcessCameraProvider.getInstance(c)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor) { proxy ->
                    val img = proxy.image
                    if (img == null) proxy.close() else scanner.process(InputImage.fromMediaImage(img, proxy.imageInfo.rotationDegrees))
                        .addOnSuccessListener { codes -> codes.firstOrNull()?.rawValue?.let { v -> main.execute { latest(v) } } }
                        .addOnCompleteListener { proxy.close() }
                }
                provider.unbindAll(); provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) { onError() }
        }, main)
        view
    })
}
