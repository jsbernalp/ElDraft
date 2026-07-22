package com.eldraft.android.ui.screens

import com.eldraft.android.ui.theme.ElDraftTheme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eldraft.android.R
import com.eldraft.android.ui.attendance.AttendanceViewModel
import com.eldraft.android.ui.components.BackTopBar
import com.eldraft.android.ui.components.ScreenHeader
import com.eldraft.android.util.generateQrBitmap
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRGeneratorScreen(
    convocatoryId: String,
    onBack: () -> Unit,
    viewModel: AttendanceViewModel = koinViewModel(),
) {
    val state by viewModel.qrState.collectAsStateWithLifecycle()

    LaunchedEffect(convocatoryId) { viewModel.loadQr(convocatoryId) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = { BackTopBar(onBack = onBack) },
    ) { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = ElDraftTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(ElDraftTheme.spacing.sm))
        ScreenHeader(
            title = stringResource(R.string.qr_generator_title),
            subtitle = stringResource(R.string.qr_generator_subtitle),
            horizontalAlignment = Alignment.CenterHorizontally,
        )

        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(280.dp)
                .clip(ElDraftTheme.shape.md)
                .background(Color.White)
                .padding(ElDraftTheme.spacing.lg),
            contentAlignment = Alignment.Center,
        ) {
            val qr = state.qrCode
            when {
                state.error != null -> Text(
                    state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                qr == null -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                else -> {
                    val bitmap = remember(qr) { generateQrBitmap(qr) }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.qr_generator_content_description),
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(stringResource(R.string.qr_generator_draw_error), color = Color.Black)
                    }
                }
            }
        }

        Spacer(Modifier.height(ElDraftTheme.spacing.xl))

        if (state.qrCode != null) {
            Text(
                stringResource(R.string.qr_generator_expires_in, formatMmSs(state.secondsLeft)),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.qr_generator_renews),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = ElDraftTheme.alpha.textMuted),
            )
        }

        Spacer(Modifier.weight(1f))
    }
    }
}

private fun formatMmSs(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}
