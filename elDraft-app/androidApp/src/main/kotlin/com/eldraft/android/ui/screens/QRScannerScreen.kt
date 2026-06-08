package com.eldraft.android.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eldraft.android.ui.attendance.AttendanceViewModel
import com.eldraft.android.ui.attendance.ScanUiState
import com.eldraft.android.util.QrCodeAnalyzer
import org.koin.androidx.compose.koinViewModel
import java.util.concurrent.Executors

@Composable
fun QRScannerScreen(
    convocatoryId: String,
    onScanComplete: () -> Unit,
    viewModel: AttendanceViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Al validar con éxito, espera un momento y vuelve atrás.
    LaunchedEffect(scanState) {
        if (scanState is ScanUiState.Success) {
            kotlinx.coroutines.delay(1200)
            onScanComplete()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Escanea el código del partido",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(24.dp),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            when {
                !hasCameraPermission -> Text(
                    "Necesitamos permiso de cámara para escanear el código.",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
                else -> CameraPreview(
                    onQrDetected = { qr -> viewModel.submitScan(qr) },
                )
            }

            // Overlay de estado
            when (val s = scanState) {
                is ScanUiState.Sending -> StatusOverlay("Validando…", showSpinner = true)
                is ScanUiState.Success -> StatusOverlay("✓ ¡Asistencia registrada!", color = Color(0xFF2E7D32))
                is ScanUiState.Error -> StatusOverlay(s.message, color = MaterialTheme.colorScheme.error)
                ScanUiState.Scanning -> Unit
            }
        }

        if (scanState is ScanUiState.Error) {
            Button(
                onClick = { viewModel.resetScan() },
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
            ) { Text("Reintentar") }
        }
    }
}

@Composable
private fun BoxScope.StatusOverlay(
    text: String,
    showSpinner: Boolean = false,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showSpinner) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
            }
            Text(text, color = color, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun CameraPreview(onQrDetected: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(analysisExecutor, QrCodeAnalyzer(onQrDetected))
                    }
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                } catch (_: Exception) {
                    // Si el bind falla (p. ej. sin cámara), la UI seguirá mostrando negro.
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
}
