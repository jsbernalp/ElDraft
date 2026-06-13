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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRScannerScreen(
    convocatoryId: String,
    isOrganizer: Boolean,
    onScanComplete: () -> Unit,
    onBack: () -> Unit,
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

    // Color del marco según el estado del escaneo.
    val reticleColor = when (scanState) {
        is ScanUiState.Success -> Color(0xFF2E7D32)
        is ScanUiState.Error -> MaterialTheme.colorScheme.error
        else -> Color.White
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Cámara a pantalla completa (o mensaje de permiso).
        when {
            !hasCameraPermission -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.QrCodeScanner,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(56.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Necesitamos permiso de cámara para escanear el código.",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 40.dp),
                    )
                }
            }
            else -> CameraPreview(onQrDetected = { qr -> viewModel.submitScan(qr) })
        }

        // Overlay: scrim oscuro con ventana central + marco con esquinas.
        if (hasCameraPermission) {
            ScannerOverlay(reticleColor = reticleColor)
        }

        // Header flotante sobre la cámara.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .safeContentPadding()
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = Color.White,
                    )
                }
                Text(
                    "Escanea el código",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        // Zona inferior: tarjeta de instrucciones paso a paso + estado del escaneo.
        // Quien muestra el QR es el OTRO rol: el organizador escanea el QR de un
        // convocado y viceversa.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeContentPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val s = scanState) {
                is ScanUiState.Sending -> StatusPill("Validando…", showSpinner = true)
                is ScanUiState.Success -> StatusPill("¡Asistencia registrada!", color = Color(0xFF2E7D32))
                is ScanUiState.Error -> {
                    StatusPill(s.message, color = MaterialTheme.colorScheme.error)
                    Button(
                        onClick = { viewModel.resetScan() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Reintentar") }
                }
                // Mientras escanea, mostrar la guía paso a paso.
                ScanUiState.Scanning -> ScanInstructions(
                    otherRole = if (isOrganizer) "un jugador convocado" else "el organizador",
                )
            }
        }
    }
}

/**
 * Scrim oscuro a pantalla completa con una "ventana" central transparente y un
 * marco de esquinas que indica dónde apuntar el QR. Incluye una línea de
 * escaneo animada que recorre la ventana.
 */
@Composable
private fun BoxScope.ScannerOverlay(reticleColor: Color) {
    val transition = rememberInfiniteTransition(label = "scanline")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sweep",
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val side = size.minDimension * 0.68f
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f
        val corner = 28f

        // Scrim oscuro con recorte de la ventana central.
        drawRect(color = Color.Black.copy(alpha = 0.55f))
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = Size(side, side),
            cornerRadius = CornerRadius(corner, corner),
            blendMode = BlendMode.Clear,
        )

        // Esquinas del marco (4 ángulos en L).
        val len = side * 0.12f
        val stroke = 6f
        val c = reticleColor
        fun corner(x: Float, y: Float, dx: Int, dy: Int) {
            drawLine(c, Offset(x, y), Offset(x + len * dx, y), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(c, Offset(x, y), Offset(x, y + len * dy), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        corner(left, top, 1, 1)
        corner(left + side, top, -1, 1)
        corner(left, top + side, 1, -1)
        corner(left + side, top + side, -1, -1)

        // Línea de escaneo animada dentro de la ventana.
        val lineY = top + side * sweep
        drawLine(
            color = c.copy(alpha = 0.8f),
            start = Offset(left + len, lineY),
            end = Offset(left + side - len, lineY),
            strokeWidth = 3f,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * Tarjeta de instrucciones paso a paso para el escaneo. Como cualquier
 * participante puede generar y escanear, aclara a quién pedirle el QR
 * ([otherRole]) y que esa persona debe tocar "Mostrar QR".
 */
@Composable
private fun ScanInstructions(
    otherRole: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = 0.55f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "¿Cómo registro la asistencia?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            StepLine(number = "1", text = "Pídele a $otherRole que toque “Mostrar QR”")
            StepLine(number = "2", text = "Apunta la cámara al código")
        }
    }
}

@Composable
private fun StepLine(number: String, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.92f),
        )
    }
}

@Composable
private fun StatusPill(
    text: String,
    showSpinner: Boolean = false,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
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
            Text(text, color = color, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
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
