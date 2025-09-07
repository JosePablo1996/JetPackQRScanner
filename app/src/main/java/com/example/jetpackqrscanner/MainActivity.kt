package com.example.jetpackqrscanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    // Estados mejorados con nombres más descriptivos
    private var scannedResultText by mutableStateOf("")
    private var showSplashScreen by mutableStateOf(true)
    private var cameraPermissionDenied by mutableStateOf(false)

    // Constantes para códigos de resultado y claves de intent
    companion object {
        const val SCAN_RESULT_KEY = "SCAN_RESULT"
        const val SCANNER_REQUEST_CODE = 1001
    }

    // Launcher para iniciar la actividad del escáner
    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleScanResult(result.resultCode, result.data)
    }

    // Launcher para permisos de cámara
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        handlePermissionResult(isGranted)
    }

    private fun handleScanResult(resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK) {
            val scannedResult = data?.getStringExtra(SCAN_RESULT_KEY).orEmpty()
            if (scannedResult.isNotBlank()) {
                scannedResultText = scannedResult
                showToast("Escaneo exitoso!")
            } else {
                showToast("No se pudo leer el código QR")
            }
        } else if (resultCode == RESULT_CANCELED) {
            showToast("Escaneo cancelado")
        }
    }

    private fun handlePermissionResult(isGranted: Boolean) {
        if (isGranted) {
            cameraPermissionDenied = false
            openScanner()
        } else {
            cameraPermissionDenied = true
            showToast("Permiso de cámara denegado. Puede activarlo en Configuración > Aplicaciones")
        }
    }

    private fun openScanner() {
        val intent = Intent(this, ScannerActivity::class.java)
        scannerLauncher.launch(intent)
    }

    private fun checkCameraPermission() {
        when {
            hasCameraPermission() -> {
                openScanner()
            }
            shouldShowPermissionRationale() -> {
                showToast("Se necesita el permiso de la cámara para escanear códigos QR")
                requestCameraPermission()
            }
            else -> {
                requestCameraPermission()
            }
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun shouldShowPermissionRationale(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
    }

    private fun requestCameraPermission() {
        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun clearResult() {
        scannedResultText = ""
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Restaurar estado después de rotación o recreación
        if (savedInstanceState != null) {
            scannedResultText = savedInstanceState.getString("resultText", "")
            showSplashScreen = savedInstanceState.getBoolean("showSplash", true)
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showSplashScreen) {
                        SplashScreen {
                            showSplashScreen = false
                        }
                    } else {
                        QRScannerApp(
                            resultText = scannedResultText,
                            onScanClick = { checkCameraPermission() },
                            onClearClick = { clearResult() },
                            cameraPermissionDenied = cameraPermissionDenied
                        )
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("resultText", scannedResultText)
        outState.putBoolean("showSplash", showSplashScreen)
    }
}

@Composable
fun SplashScreen(onSplashEnd: () -> Unit) {
    var animationState by remember { mutableStateOf(0) }
    val offsetY by animateDpAsState(
        targetValue = if (animationState >= 1) 0.dp else (-100).dp,
        animationSpec = tween(durationMillis = 1000, easing = EaseInOut),
        label = "splashAnimation"
    )

    LaunchedEffect(Unit) {
        delay(500) // Espera inicial
        animationState = 1
        delay(1500) // Duración del splash
        onSplashEnd()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFF6B3B),
                        Color(0xFFFF3366)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo animado
            AnimatedVisibility(
                visible = animationState >= 1,
                enter = scaleIn(
                    animationSpec = SpringSpec(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn()
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_qr_scanner_pro),
                    contentDescription = "QR Scanner Logo",
                    modifier = Modifier
                        .size(120.dp)
                        .padding(16.dp),
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Texto animado
            AnimatedVisibility(
                visible = animationState >= 1,
                enter = slideInVertically(
                    animationSpec = tween(durationMillis = 800),
                    initialOffsetY = { it }
                ) + fadeIn()
            ) {
                Text(
                    text = "QR SCANNER PRO",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.offset(y = offsetY)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedVisibility(
                visible = animationState >= 1,
                enter = fadeIn(animationSpec = tween(durationMillis = 1000, delayMillis = 500))
            ) {
                Text(
                    text = "Escáner profesional de códigos QR",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRScannerApp(
    resultText: String,
    onScanClick: () -> Unit,
    onClearClick: () -> Unit,
    cameraPermissionDenied: Boolean = false
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "QR SCANNER PRO",
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFFF3366),
                    titleContentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = visible,
                enter = scaleIn() + fadeIn(animationSpec = tween(durationMillis = 800))
            ) {
                FloatingActionButton(
                    onClick = onScanClick,
                    containerColor = Color(0xFFFF3366),
                    shape = CircleShape,
                    modifier = Modifier
                        .size(80.dp)
                        .shadow(16.dp, CircleShape)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_scan_main),
                        contentDescription = "Escanear QR",
                        modifier = Modifier.size(36.dp),
                        tint = Color.White
                    )
                }
            }
        }
    ) { innerPadding ->
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(durationMillis = 1000))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFFF6B3B),
                                Color(0xFFFF3366)
                            )
                        )
                    ),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Mensaje de error de permisos
                if (cameraPermissionDenied) {
                    AnimatedVisibility(
                        visible = visible,
                        enter = slideInVertically() + fadeIn()
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0x66FF0000) // Rojo semitransparente
                            )
                        ) {
                            Text(
                                text = "Permiso de cámara denegado. La aplicación no puede escanear sin este permiso.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                // Header con imagen mejorado
                AnimatedVisibility(
                    visible = visible,
                    enter = scaleIn(
                        animationSpec = SpringSpec(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ) + fadeIn()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White
                        )
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_qr_scanner_pro),
                                contentDescription = "QR Icon",
                                modifier = Modifier
                                    .size(100.dp)
                                    .padding(8.dp),
                                tint = Color(0xFFFF3366)
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Text(
                                text = "ESCÁNER DE CÓDIGOS QR",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = Color(0xFFFF3366),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Escanea cualquier código QR para obtener información instantánea de forma rápida y segura",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = Color.DarkGray,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Resultado del escaneo
                if (resultText.isNotBlank()) {
                    AnimatedVisibility(
                        visible = visible,
                        enter = slideInVertically() + fadeIn()
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "RESULTADO DEL ESCANEO",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF3366),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = resultText,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color.DarkGray
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                Button(
                                    onClick = onClearClick,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFF3366),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = android.R.drawable.ic_menu_delete),
                                        contentDescription = "Limpiar",
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("LIMPIAR RESULTADO", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    // Mensaje cuando no hay resultado
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(animationSpec = tween(durationMillis = 800, delayMillis = 300))
                    ) {
                        Text(
                            text = "Presiona el botón de escaneo para comenzar",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.9f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                // Botón de escaneo alternativo
                AnimatedVisibility(
                    visible = visible,
                    enter = scaleIn() + fadeIn(animationSpec = tween(durationMillis = 800, delayMillis = 500))
                ) {
                    Button(
                        onClick = onScanClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFFFF3366)
                        ),
                        shape = RoundedCornerShape(18.dp),
                        enabled = !cameraPermissionDenied,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 40.dp)
                            .height(60.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_scan_main),
                            contentDescription = "Escanear",
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            "ESCANEAR CÓDIGO QR",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(60.dp))

                // Indicador de seguridad
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(animationSpec = tween(durationMillis = 1000, delayMillis = 700))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_shield),
                            contentDescription = "Seguridad",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Escaneo seguro - Tus datos están protegidos",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}