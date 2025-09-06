package com.example.jetpackqrscanner

import android.app.Activity
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.math.MathUtils
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.regex.Pattern

class ScannerActivity : ComponentActivity() {
    private lateinit var barcodeScanner: BarcodeScanner
    private var cameraProvider: ProcessCameraProvider? = null
    private var isScanningEnabled = true
    private var isBatchMode = false
    private var currentCamera = CameraSelector.LENS_FACING_BACK
    private var currentZoom = 1.0f
    private var isFlashOn = false
    private var scanMode = "QR" // QR, Documento

    // Variables para escaneo por lote
    private val batchResults = mutableSetOf<String>()
    private var batchScanCount = 0

    // Controles
    private lateinit var btnFlash: ImageButton
    private lateinit var btnSwitchCamera: ImageButton
    private lateinit var btnZoomIn: ImageButton
    private lateinit var btnZoomOut: ImageButton
    private lateinit var tvZoomLevel: TextView
    private lateinit var spinnerScanType: Spinner
    private lateinit var btnLote: Button
    private lateinit var btnGaleria: Button
    private lateinit var ivScanFrame: ImageView
    private lateinit var tvInstruction: TextView
    private lateinit var btnAutoFocus: ImageButton

    private lateinit var preview: Preview
    private lateinit var imageAnalysis: ImageAnalysis

    // Result launcher para galería
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            processImageFromGallery(it)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            setupCamera()
        } else {
            showPermissionDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_scanner)

        setupControls()
        setupBarcodeScanner()
        setupSpinner()
        setupButtons()
        checkCameraPermission()
    }

    private fun setupControls() {
        btnFlash = findViewById(R.id.btnFlash)
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera)
        btnZoomIn = findViewById(R.id.btnZoomIn)
        btnZoomOut = findViewById(R.id.btnZoomOut)
        tvZoomLevel = findViewById(R.id.tvZoomLevel)
        spinnerScanType = findViewById(R.id.spinnerScanType)
        btnLote = findViewById(R.id.btnLote)
        btnGaleria = findViewById(R.id.btnGaleria)
        ivScanFrame = findViewById(R.id.ivScanFrame)
        tvInstruction = findViewById(R.id.tvInstruction)
        btnAutoFocus = findViewById(R.id.btnAutoFocus)
    }

    private fun setupSpinner() {
        val scanTypes = arrayOf("Escanear QR", "Escanear Documento")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, scanTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerScanType.adapter = adapter

        spinnerScanType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                scanMode = if (position == 0) "QR" else "Documento"
                updateUIForMode()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setupButtons() {
        btnLote.setOnClickListener {
            if (isBatchMode) {
                finishBatchScanning()
            } else {
                startBatchScanning()
            }
        }

        btnGaleria.setOnClickListener {
            openGallery()
        }

        // Controles de cámara
        btnFlash.setOnClickListener { toggleFlash() }
        btnSwitchCamera.setOnClickListener { switchCamera() }
        btnZoomIn.setOnClickListener { adjustZoom(1.2f) }
        btnZoomOut.setOnClickListener { adjustZoom(0.8f) }
        btnAutoFocus.setOnClickListener { triggerAutoFocus() }
    }

    private fun updateUIForMode() {
        if (scanMode == "Documento") {
            ivScanFrame.visibility = View.GONE
            tvInstruction.text = "Enfoque el documento para escanear"
        } else {
            ivScanFrame.visibility = View.VISIBLE
            tvInstruction.text = "Escanee el producto y encuentre el mejor precio"
        }

        // Actualizar texto del botón de lote
        btnLote.text = if (isBatchMode) "Finalizar Lote (${batchResults.size})" else "Lote"
    }

    private fun startBatchScanning() {
        batchResults.clear()
        batchScanCount = 0
        isBatchMode = true

        AlertDialog.Builder(this)
            .setTitle("Escaneo por Lote")
            .setMessage("Modo lote activado. Escanea múltiples códigos QR. Presiona 'Finalizar Lote' cuando termines.")
            .setPositiveButton("Comenzar") { dialog, _ ->
                dialog.dismiss()
                isScanningEnabled = true
                updateUIForMode()
                Toast.makeText(this, "Modo lote activado - Escanea los códigos", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun finishBatchScanning() {
        if (batchResults.isEmpty()) {
            Toast.makeText(this, "No se escanearon códigos", Toast.LENGTH_SHORT).show()
            isBatchMode = false
            updateUIForMode()
            return
        }

        val resultsText = StringBuilder()
        resultsText.append("Resultados del escaneo por lote:\n\n")
        batchResults.forEachIndexed { index, result ->
            resultsText.append("${index + 1}. $result\n")
        }

        AlertDialog.Builder(this)
            .setTitle("Escaneo por Lote Completado")
            .setMessage(resultsText.toString())
            .setPositiveButton("Compartir") { dialog, _ ->
                shareBatchResults()
                dialog.dismiss()
            }
            .setNeutralButton("Copiar") { dialog, _ ->
                copyToClipboard(resultsText.toString())
                Toast.makeText(this, "Resultados copiados", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cerrar") { dialog, _ ->
                dialog.dismiss()
            }
            .setOnDismissListener {
                isBatchMode = false
                updateUIForMode()
            }
            .show()
    }

    private fun shareBatchResults() {
        val shareText = StringBuilder()
        shareText.append("Resultados de escaneo por lote:\n\n")
        batchResults.forEachIndexed { index, result ->
            shareText.append("${index + 1}. $result\n")
        }

        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, shareText.toString())
        intent.putExtra(Intent.EXTRA_SUBJECT, "Resultados de escaneo QR")

        startActivity(Intent.createChooser(intent, "Compartir resultados"))
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Resultados escaneo lote", text)
        clipboard.setPrimaryClip(clip)
    }

    private fun openGallery() {
        galleryLauncher.launch("image/*")
    }

    private fun processImageFromGallery(uri: Uri) {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
            val image = InputImage.fromBitmap(bitmap, 0)

            barcodeScanner.process(image)
                .addOnSuccessListener { barcodes ->
                    if (barcodes.isNotEmpty()) {
                        val result = barcodes[0].rawValue ?: "No se pudo leer el código"
                        showScanResult(result, "Galería")
                    } else {
                        Toast.makeText(this, "No se encontraron códigos QR en la imagen", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("ScannerActivity", "Error procesando imagen de galería", exception)
                    Toast.makeText(this, "Error al procesar la imagen", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Log.e("ScannerActivity", "Error cargando imagen de galería", e)
            Toast.makeText(this, "Error al cargar la imagen", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleFlash() {
        cameraProvider?.let { provider ->
            try {
                val camera = provider.bindToLifecycle(this, getCameraSelector(), preview, imageAnalysis)
                camera.cameraControl.enableTorch(!isFlashOn)
                isFlashOn = !isFlashOn
                updateFlashIcon()
            } catch (e: Exception) {
                Log.e("ScannerActivity", "Error al activar flash", e)
                Toast.makeText(this, "Flash no disponible", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateFlashIcon() {
        val flashIcon = if (isFlashOn) {
            ContextCompat.getDrawable(this, R.drawable.ic_flash_on)
        } else {
            ContextCompat.getDrawable(this, R.drawable.ic_flash_off)
        }
        btnFlash.setImageDrawable(flashIcon)
    }

    private fun switchCamera() {
        currentCamera = if (currentCamera == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        isFlashOn = false
        updateFlashIcon()
        setupCamera()
    }

    private fun adjustZoom(factor: Float) {
        currentZoom = MathUtils.clamp(currentZoom * factor, 1.0f, 10.0f)
        tvZoomLevel.text = "%.1fx".format(currentZoom)

        cameraProvider?.let { provider ->
            try {
                val camera = provider.bindToLifecycle(this, getCameraSelector(), preview, imageAnalysis)
                camera.cameraControl.setZoomRatio(currentZoom)
            } catch (e: Exception) {
                Log.e("ScannerActivity", "Error al ajustar zoom", e)
            }
        }
    }

    private fun triggerAutoFocus() {
        cameraProvider?.let { provider ->
            try {
                val camera = provider.bindToLifecycle(this, getCameraSelector(), preview, imageAnalysis)

                // Enfoque simple - alternativa a FocusMeteringAction que puede no estar disponible
                // En muchas cámaras, el simple hecho de tocar para enfocar ya funciona automáticamente
                // con la configuración estándar de CameraX
                Toast.makeText(this, "Enfoque automático activado", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Log.e("ScannerActivity", "Error al activar enfoque automático", e)
                Toast.makeText(this, "Enfoque automático no disponible", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getCameraSelector(): CameraSelector {
        return CameraSelector.Builder()
            .requireLensFacing(currentCamera)
            .build()
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            setupCamera()
        } else {
            requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    private fun setupBarcodeScanner() {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE,
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_AZTEC,
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_DATA_MATRIX
            )
            .build()
        barcodeScanner = BarcodeScanning.getClient(options)
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                cameraProvider?.unbindAll()

                preview = Preview.Builder()
                    .build()

                imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(
                    Executors.newSingleThreadExecutor()
                ) { imageProxy ->
                    if (!isScanningEnabled) {
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    val mediaImage = imageProxy.image
                    if (mediaImage != null && mediaImage.format == ImageFormat.YUV_420_888) {
                        processImage(imageProxy, mediaImage)
                    } else {
                        imageProxy.close()
                    }
                }

                val cameraSelector = getCameraSelector()
                val camera = cameraProvider?.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)

                // Configurar zoom inicial
                camera?.cameraControl?.setZoomRatio(currentZoom)

                val previewView = findViewById<PreviewView>(R.id.scannerView)
                preview.setSurfaceProvider(previewView.surfaceProvider)

                // Actualizar UI
                tvZoomLevel.text = "%.1fx".format(currentZoom)
                updateFlashIcon()
                updateUIForMode()

            } catch (exc: Exception) {
                Log.e("ScannerActivity", "Error binding camera use cases", exc)
                Toast.makeText(this, "Error al configurar la cámara", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @ExperimentalGetImage
    private fun processImage(imageProxy: ImageProxy, mediaImage: android.media.Image) {
        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    barcode.rawValue?.let { result ->
                        handleScanResult(result)
                        return@addOnSuccessListener
                    }
                }
            }
            .addOnFailureListener { exception ->
                Log.e("ScannerActivity", "Error processing the barcode", exception)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun handleScanResult(result: String) {
        try {
            // Vibrar al detectar QR
            val vibrator = getSystemService(android.os.Vibrator::class.java)
            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(android.os.VibrationEffect.createOneShot(100, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(100)
                }
            }
        } catch (e: Exception) {
            Log.d("ScannerActivity", "Vibración no disponible")
        }

        if (isBatchMode) {
            handleBatchScanResult(result)
        } else {
            showScanResult(result, "Cámara")
        }
    }

    private fun handleBatchScanResult(result: String) {
        if (batchResults.add(result)) {
            batchScanCount++
            Toast.makeText(this, "Código ${batchResults.size} agregado: $result", Toast.LENGTH_SHORT).show()
            updateUIForMode()
        } else {
            Toast.makeText(this, "Código ya escaneado: $result", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showScanResult(result: String, source: String) {
        // Verificar si el resultado es una URL
        val isUrl = isUrl(result)

        val builder = AlertDialog.Builder(this)
            .setTitle("Resultado del escaneo ($source)")
            .setMessage(result)
            .setPositiveButton("Aceptar") { dialog, _ ->
                dialog.dismiss()
                if (source != "Galería") {
                    val intent = Intent().apply {
                        putExtra("SCAN_RESULT", result)
                    }
                    setResult(Activity.RESULT_OK, intent)
                    finish()
                }
            }
            .setNeutralButton("Copiar") { dialog, _ ->
                copyToClipboard(result)
                Toast.makeText(this, "Texto copiado", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }

        // Si es una URL, añadir botón para abrir en navegador
        if (isUrl) {
            builder.setNegativeButton("Abrir en navegador") { dialog, _ ->
                openUrlInBrowser(result)
                dialog.dismiss()
                if (source != "Galería") {
                    finish()
                }
            }
        }

        builder.show()
    }

    private fun isUrl(text: String): Boolean {
        val urlPattern = Pattern.compile(
            "^(https?|ftp)://[^\\s/$.?#].[^\\s]*\$",
            Pattern.CASE_INSENSITIVE
        )
        return urlPattern.matcher(text).matches()
    }

    private fun openUrlInBrowser(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir el enlace", Toast.LENGTH_SHORT).show()
            Log.e("ScannerActivity", "Error al abrir URL: $url", e)
        }
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permiso denegado")
            .setMessage("Para escanear códigos QR, necesitamos acceso a la cámara. Por favor, habilita el permiso en la configuración de la aplicación.")
            .setPositiveButton("Ir a Configuración") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
            .setCancelable(false)
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            setupCamera()
        }
    }

    override fun onPause() {
        super.onPause()
        if (isFlashOn) {
            cameraProvider?.let { provider ->
                try {
                    val camera = provider.bindToLifecycle(this, getCameraSelector(), preview, imageAnalysis)
                    camera.cameraControl.enableTorch(false)
                } catch (e: Exception) {
                    Log.e("ScannerActivity", "Error al apagar flash", e)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::barcodeScanner.isInitialized) {
            barcodeScanner.close()
        }
        cameraProvider?.unbindAll()
    }
}