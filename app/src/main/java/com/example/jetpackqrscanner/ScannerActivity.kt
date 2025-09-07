package com.example.jetpackqrscanner

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.ToneGenerator
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.os.VibrationEffect
import android.util.Log
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.math.MathUtils
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

class ScannerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ScannerScreen(
                    onBackPressed = { finish() },
                    onQrCodeScanned = { result ->
                        showScanResult(result)
                    }
                )
            }
        }
    }

    private fun showScanResult(result: String) {
        val intent = Intent().apply {
            putExtra("SCAN_RESULT", result)
        }
        setResult(RESULT_OK, intent)
        finish()
    }
}

// Data class para guardar resultados con timestamp
data class ScanResult(
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isUrl: Boolean = false
) {
    fun formattedTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formattedDate(): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

// Función para emitir un pitido más distintivo y corto
fun playSound(context: Context) {
    try {
        val toneG = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        // Usamos un tono más claro y rápido para la alerta
        toneG.startTone(ToneGenerator.TONE_CDMA_PIP)
        toneG.release()
    } catch (e: Exception) {
        Log.e("ScannerActivity", "Error al reproducir sonido", e)
    }
}

// Función para vibrar el dispositivo de forma más notoria
fun vibrate(context: Context) {
    val vibrator = context.getSystemService<Vibrator>()
    vibrator?.let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Aumentamos la duración de la vibración a 200ms
            it.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            it.vibrate(200)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onBackPressed: () -> Unit,
    onQrCodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var isFlashOn by remember { mutableStateOf(false) }
    var currentZoom by remember { mutableStateOf(1.0f) }
    var currentCamera by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var isBatchMode by remember { mutableStateOf(false) }
    var batchResults by remember { mutableStateOf(emptyList<ScanResult>()) }
    var cameraError by remember { mutableStateOf(false) }
    var showResultDialog by remember { mutableStateOf(false) }
    var scannedResult by remember { mutableStateOf("") }
    var isUrl by remember { mutableStateOf(false) }
    var scanType by remember { mutableStateOf("QR/Barcode") }
    var expanded by remember { mutableStateOf(false) }
    var showBatchDialog by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
            if (!granted) {
                scope.launch {
                    snackbarHostState.showSnackbar("Permiso de cámara denegado")
                }
            }
        }
    )

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                processImageFromGallery(context, it) { result ->
                    scannedResult = result
                    isUrl = isUrl(result)
                    showResultDialog = true
                }
            }
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (showResultDialog) {
        ScanResultDialog(
            result = scannedResult,
            isUrl = isUrl,
            onDismiss = { showResultDialog = false },
            onOpenInBrowser = {
                openUrlInBrowser(context, scannedResult)
                showResultDialog = false
                if (!isBatchMode) {
                    onBackPressed()
                }
            },
            onCopyToClipboard = {
                copyToClipboard(context, scannedResult)
                scope.launch {
                    snackbarHostState.showSnackbar("Texto copiado al portapapeles")
                }
                showResultDialog = false
                if (!isBatchMode) {
                    onBackPressed()
                }
            },
            onShare = {
                shareResult(context, scannedResult)
            },
            onAccept = {
                showResultDialog = false
                if (!isBatchMode) {
                    onQrCodeScanned(scannedResult)
                }
            }
        )
    }

    // Lógica para mostrar el diálogo por lotes
    if (showBatchDialog) {
        BatchResultsDialog(
            results = batchResults,
            onDismiss = { showBatchDialog = false },
            onCopyAll = {
                val allResults = batchResults.joinToString(separator = "\n") { it.content }
                copyToClipboard(context, allResults)
                scope.launch {
                    snackbarHostState.showSnackbar("Todos los resultados copiados")
                }
            },
            onClearAll = {
                batchResults = emptyList()
                scope.launch {
                    snackbarHostState.showSnackbar("Historial limpiado")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Escáner Avanzado")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { expanded = true }) {
                        Icon(
                            imageVector = when (scanType) {
                                "QR/Barcode" -> Icons.Filled.QrCode
                                else -> Icons.Filled.DocumentScanner
                            },
                            contentDescription = "Tipo de escaneo"
                        )
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("QR/Barcode") },
                            onClick = {
                                scanType = "QR/Barcode"
                                expanded = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.QrCode,
                                    contentDescription = null
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Escanear documento") },
                            onClick = {
                                scanType = "Document"
                                expanded = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.DocumentScanner,
                                    contentDescription = null
                                )
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (cameraError) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Error al inicializar la cámara",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(16.dp)
                        )
                        Button(
                            onClick = {
                                cameraError = false
                                scope.launch {
                                    kotlinx.coroutines.delay(500)
                                    cameraError = false
                                }
                            }
                        ) {
                            Text("Reintentar")
                        }
                    }
                } else if (hasCameraPermission) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        ) {
                            CameraPreview(
                                isFlashOn = isFlashOn,
                                currentZoom = currentZoom,
                                currentCamera = currentCamera,
                                onQrCodeScanned = { result ->
                                    playSound(context)
                                    vibrate(context)
                                    if (isBatchMode) {
                                        val newResult = ScanResult(result, isUrl = isUrl(result))
                                        batchResults = batchResults + newResult
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Resultado ${batchResults.size} guardado")
                                        }
                                    } else {
                                        scannedResult = result
                                        isUrl = isUrl(result)
                                        showResultDialog = true
                                    }
                                },
                                onCameraError = { error ->
                                    cameraError = true
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Error de cámara: $error")
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            // Marco visual mejorado
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 40.dp, vertical = 80.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                // Marco con esquinas decorativas
                                Box(
                                    modifier = Modifier
                                        .size(280.dp)
                                        .border(
                                            border = BorderStroke(3.dp, Color.White.copy(alpha = 0.9f)),
                                            shape = RoundedCornerShape(24.dp)
                                        )
                                        .clip(RoundedCornerShape(24.dp))
                                )

                                // Esquinas decorativas
                                // Esquina superior izquierda
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .align(Alignment.TopStart)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .align(Alignment.TopStart)
                                            .border(
                                                BorderStroke(4.dp, MaterialTheme.colorScheme.primary),
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                    )
                                }

                                // Esquina superior derecha
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .align(Alignment.TopEnd)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .align(Alignment.TopEnd)
                                            .border(
                                                BorderStroke(4.dp, MaterialTheme.colorScheme.primary),
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                    )
                                }

                                // Esquina inferior izquierda
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .align(Alignment.BottomStart)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .align(Alignment.BottomStart)
                                            .border(
                                                BorderStroke(4.dp, MaterialTheme.colorScheme.primary),
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                    )
                                }

                                // Esquina inferior derecha
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .align(Alignment.BottomEnd)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .align(Alignment.BottomEnd)
                                            .border(
                                                BorderStroke(4.dp, MaterialTheme.colorScheme.primary),
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                    )
                                }

                                // Texto de guía
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                ) {
                                    Spacer(modifier = Modifier.height(120.dp))
                                    Text(
                                        text = "Alinea el código dentro del marco",
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .background(
                                                Color.Black.copy(alpha = 0.5f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "El escaneo es automático",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 14.sp,
                                        modifier = Modifier
                                            .background(
                                                Color.Black.copy(alpha = 0.4f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // Fila de botones con diseño mejorado
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Botón de flash
                            IconButton(
                                onClick = { isFlashOn = !isFlashOn },
                                modifier = Modifier
                                    .size(56.dp)
                                    .shadow(8.dp, CircleShape)
                                    .background(
                                        if (isFlashOn) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.secondaryContainer,
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = if (isFlashOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                                    contentDescription = "Flash",
                                    tint = if (isFlashOn) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Botón de cambiar cámara
                            IconButton(
                                onClick = {
                                    currentCamera = if (currentCamera == CameraSelector.LENS_FACING_BACK) {
                                        CameraSelector.LENS_FACING_FRONT
                                    } else {
                                        CameraSelector.LENS_FACING_BACK
                                    }
                                    isFlashOn = false
                                },
                                modifier = Modifier
                                    .size(56.dp)
                                    .shadow(8.dp, CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FlipCameraAndroid,
                                    contentDescription = "Cambiar cámara",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Botón de zoom in
                            IconButton(
                                onClick = { currentZoom = MathUtils.clamp(currentZoom * 1.2f, 1.0f, 10.0f) },
                                modifier = Modifier
                                    .size(56.dp)
                                    .shadow(8.dp, CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "Zoom in",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Botón de zoom out
                            IconButton(
                                onClick = { currentZoom = MathUtils.clamp(currentZoom * 0.8f, 1.0f, 10.0f) },
                                modifier = Modifier
                                    .size(56.dp)
                                    .shadow(8.dp, CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Remove,
                                    contentDescription = "Zoom out",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Botón de galería
                            IconButton(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        galleryLauncher.launch("image/*")
                                    } else {
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                                            galleryLauncher.launch("image/*")
                                        } else {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Permiso de almacenamiento necesario")
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(56.dp)
                                    .shadow(8.dp, CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Collections,
                                    contentDescription = "Galería",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Botón de modo lote
                            IconButton(
                                onClick = {
                                    if (isBatchMode) {
                                        if (batchResults.isNotEmpty()) {
                                            showBatchDialog = true
                                        } else {
                                            scope.launch { snackbarHostState.showSnackbar("No hay resultados guardados.") }
                                        }
                                    }
                                    isBatchMode = !isBatchMode
                                },
                                modifier = Modifier
                                    .size(56.dp)
                                    .shadow(8.dp, CircleShape)
                                    .background(
                                        if (isBatchMode) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.secondaryContainer,
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.List,
                                    contentDescription = "Modo lote",
                                    tint = if (isBatchMode) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        if (isBatchMode) {
                            Text(
                                text = "Modo lote: ${batchResults.size} resultados guardados",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                            )
                                        ),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(12.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Se necesita permiso de cámara",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(16.dp)
                        )
                        Button(
                            onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }
                        ) {
                            Text("Solicitar permiso")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScanResultDialog(
    result: String,
    isUrl: Boolean,
    onDismiss: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onCopyToClipboard: () -> Unit,
    onShare: () -> Unit,
    onAccept: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Escaneo exitoso",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Escaneo exitoso",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (isUrl) {
                    ContentTypeBadge(
                        icon = Icons.Filled.Link,
                        text = "Enlace web",
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Text(
                    text = "Contenido:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(8.dp))

                SelectionContainer {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Text(
                            text = result,
                            modifier = Modifier
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 20.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    if (isUrl) {
                        ActionButtonImproved(
                            icon = Icons.Filled.OpenInBrowser,
                            text = "Abrir",
                            onClick = onOpenInBrowser,
                            backgroundColor = MaterialTheme.colorScheme.primary,
                            textColor = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    ActionButtonImproved(
                        icon = Icons.Filled.ContentCopy,
                        text = "Copiar",
                        onClick = onCopyToClipboard,
                        backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                        textColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    ActionButtonImproved(
                        icon = Icons.Filled.Share,
                        text = "Compartir",
                        onClick = onShare,
                        backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                        textColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onAccept,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 2.dp
                    )
                ) {
                    Text(
                        text = "Aceptar",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun BatchResultsDialog(
    results: List<ScanResult>,
    onDismiss: () -> Unit,
    onCopyAll: () -> Unit,
    onClearAll: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.List,
                        contentDescription = "Resultados por lotes",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Historial de Escaneos (${results.size})",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    if (results.isEmpty()) {
                        Text(
                            text = "No hay resultados guardados",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp)
                        )
                    } else {
                        results.forEachIndexed { index, scanResult ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${index + 1}.",
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.width(30.dp)
                                        )
                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = scanResult.content,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "${scanResult.formattedDate()} ${scanResult.formattedTime()}",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = onClearAll,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        enabled = results.isNotEmpty()
                    ) {
                        Text("Limpiar")
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Button(
                        onClick = onCopyAll,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        enabled = results.isNotEmpty()
                    ) {
                        Text("Copiar todo")
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cerrar")
                    }
                }
            }
        }
    }
}

@Composable
fun ActionButtonImproved(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(76.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .background(backgroundColor, CircleShape)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = textColor,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(70.dp)
        )
    }
}

@Composable
fun ContentTypeBadge(icon: ImageVector, text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun CameraPreview(
    isFlashOn: Boolean,
    currentZoom: Float,
    currentCamera: Int,
    onQrCodeScanned: (String) -> Unit,
    onCameraError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var barcodeScanner by remember { mutableStateOf<BarcodeScanner?>(null) }
    val isAnalyzerRunning = remember { AtomicBoolean(false) }

    DisposableEffect(isFlashOn, currentZoom, currentCamera) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = Executors.newSingleThreadExecutor()

        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_CODE_93,
                Barcode.FORMAT_CODABAR,
                Barcode.FORMAT_DATA_MATRIX,
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_ITF,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_PDF417,
                Barcode.FORMAT_AZTEC
            )
            .build()
        barcodeScanner = BarcodeScanning.getClient(options)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                    if (isAnalyzerRunning.get()) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    isAnalyzerRunning.set(true)
                    processImageProxy(barcodeScanner, imageProxy, onQrCodeScanned)
                    isAnalyzerRunning.set(false)
                }

                val preview = Preview.Builder().build()
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(currentCamera)
                    .build()

                preview.setSurfaceProvider(previewView?.surfaceProvider)

                try {
                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )

                    camera.cameraControl.enableTorch(isFlashOn)
                    camera.cameraControl.setZoomRatio(currentZoom)

                } catch (e: Exception) {
                    Log.e("CameraPreview", "Error al configurar cámara", e)
                    onCameraError(e.message ?: "Error desconocido")
                }
            } catch (e: Exception) {
                Log.e("CameraPreview", "Error al obtener camera provider", e)
                onCameraError(e.message ?: "Error al obtener proveedor de cámara")
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            isAnalyzerRunning.set(true)
            barcodeScanner?.close()
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()
            } catch (e: Exception) {
                Log.e("CameraPreview", "Error al liberar cámara en onDispose", e)
            }
        }
    }

    AndroidView(
        factory = { context ->
            PreviewView(context).also {
                previewView = it
            }
        },
        modifier = modifier
    )
}

@androidx.camera.core.ExperimentalGetImage
fun processImageProxy(
    barcodeScanner: BarcodeScanner?,
    imageProxy: androidx.camera.core.ImageProxy,
    onQrCodeScanned: (String) -> Unit
) {
    try {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            barcodeScanner?.process(image)
                ?.addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        barcode.rawValue?.let { result ->
                            onQrCodeScanned(result)
                            return@addOnSuccessListener
                        }
                    }
                }
                ?.addOnFailureListener { e ->
                    Log.e("BarcodeScanner", "Error al escanear", e)
                }
                ?.addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    } catch (e: Exception) {
        Log.e("processImageProxy", "Error procesando imagen", e)
        imageProxy.close()
    }
}

// Función para verificar si un texto es una URL
fun isUrl(text: String): Boolean {
    val urlPattern = Pattern.compile(
        "^(https?|ftp)://[^\\s/$.?#].[^\\s]*\$",
        Pattern.CASE_INSENSITIVE
    )
    return urlPattern.matcher(text).matches() ||
            text.startsWith("www.") ||
            (text.contains(".") && !text.contains("://"))
}

// Función para procesar imágenes desde la galería
fun processImageFromGallery(
    context: Context,
    uri: Uri,
    onQrCodeScanned: (String) -> Unit
) {
    try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        val image = InputImage.fromBitmap(bitmap, 0)
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_CODE_93,
                Barcode.FORMAT_CODABAR,
                Barcode.FORMAT_DATA_MATRIX,
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_ITF,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_PDF417,
                Barcode.FORMAT_AZTEC
            )
            .build()
        val scanner = BarcodeScanning.getClient(options)

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                if (barcodes.isNotEmpty()) {
                    barcodes[0].rawValue?.let { result ->
                        onQrCodeScanned(result)
                    }
                } else {
                    Log.d("GalleryScanner", "No se encontraron códigos")
                }
            }
            .addOnFailureListener { e ->
                Log.e("GalleryScanner", "Error al procesar imagen", e)
            }
    } catch (e: Exception) {
        Log.e("GalleryScanner", "Error al leer imagen", e)
    }
}

// Función para abrir URL en navegador
fun openUrlInBrowser(context: Context, url: String) {
    try {
        var finalUrl = url
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            finalUrl = "https://$finalUrl"
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl))
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e("ScannerActivity", "Error al abrir URL: $url", e)
    }
}

// Función para copiar al portapapeles
fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Resultado escaneado", text))
}

// Función para compartir resultado
fun shareResult(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir resultado"))
}