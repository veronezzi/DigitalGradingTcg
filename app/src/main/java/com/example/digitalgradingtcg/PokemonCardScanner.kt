package com.example.digitalgradingtcg

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageFormat
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
@OptIn(ExperimentalMaterial3Api::class, ExperimentalGetImage::class, ExperimentalPermissionsApi::class)
@Composable
fun PokemonCardScanner() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Estados para controle da UI
    var cardNumber by remember { mutableStateOf("Aponte para uma carta Pokémon") }
    var detectionStatus by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf("Centralize a carta no retângulo") }
    var showPermissionDialog by remember { mutableStateOf(false) }

    // Configurações da câmera e ML Kit
    val textRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)
    var isCardLocked by remember { mutableStateOf(false) }


    val cameraController = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            // Configurações otimizadas para leitura de texto
            setImageAnalysisAnalyzer(
                ContextCompat.getMainExecutor(context),
                PokemonCardAnalyzer(textRecognizer) { text ->
                    if (!isCardLocked) {
                        val result = analyzeCardText(text)
                        cardNumber = result.first
                        feedbackMessage = result.second
                        detectionStatus = result.first.startsWith("Nº: ") ||
                                result.first.startsWith("Possível Pokémon: ")
                        if (detectionStatus) isCardLocked = true
                    }
                }
            )
            setZoomRatio(1.5f)
            bindToLifecycle(lifecycleOwner)
        }
    }

    LaunchedEffect(cameraPermissionState.status) {
        when {
            cameraPermissionState.status.isGranted -> {

                cameraController.setImageAnalysisAnalyzer(
                    cameraExecutor,
                    PokemonCardAnalyzer(textRecognizer) { text ->
                        if (!isCardLocked) {
                            val result = analyzeCardText(text)
                            cardNumber = result.first
                            feedbackMessage = result.second
                            detectionStatus = result.first.startsWith("Nº: ")
                            if (detectionStatus) isCardLocked = true
                        }
                    }
                )

            }
            cameraPermissionState.status.shouldShowRationale -> {
                showPermissionDialog = true
            }
            else -> {
                cameraPermissionState.launchPermissionRequest()
            }
        }
    }

    // Diálogo de permissão
    if (showPermissionDialog) {
        PermissionRationaleDialog(
            onRequestPermission = {
                showPermissionDialog = false
                cameraPermissionState.launchPermissionRequest()
            },
            onDismiss = { showPermissionDialog = false }
        )
    }

    // Layout principal
    Box(modifier = Modifier.fillMaxSize()) {
        if (cameraPermissionState.status.isGranted) {
            // Visualização da câmera
            AndroidView(
                factory = { context ->
                    PreviewView(context).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        controller = cameraController
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Overlay com retângulo de reconhecimento central
            CardDetectionOverlay(
                detectionStatus = detectionStatus,
                feedbackMessage = feedbackMessage,
                cardNumber = cardNumber,
                onUnlock = {
                    isCardLocked = false
                    cardNumber = "Aponte para uma carta Pokémon"
                    feedbackMessage = "Centralize a carta no retângulo"
                    detectionStatus = false
                }
            )

        } else {
            PermissionRequiredScreen {
                cameraPermissionState.launchPermissionRequest()
            }
        }
    }
}

@Composable
private fun CardDetectionOverlay(
    detectionStatus: Boolean,
    feedbackMessage: String,
    cardNumber: String,
    onUnlock: () -> Unit
) {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.8f)
                .aspectRatio(3f / 4f)
                .border(
                    width = 4.dp,
                    color = if (detectionStatus) Color.Green else Color.Yellow.copy(alpha = 0.7f),
                    shape = MaterialTheme.shapes.medium
                )
        )

        Text(
            text = feedbackMessage,
            style = MaterialTheme.typography.titleLarge.copy(
                color = Color.White,
                background = Color.Black.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp)
        )

        if (detectionStatus) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = cardNumber,
                        style = MaterialTheme.typography.displaySmall.copy(
                            color = Color.Green,
                            background = Color.Black.copy(alpha = 0.7f)
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onUnlock) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Desbloquear leitura",
                            tint = Color.Red
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(onClick = {
                    val query = cardNumber.removePrefix("Nº: ").replace(" ", "")
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://www.google.com/search?q=$query + pokemon")
                    )
                    context.startActivity(intent)
                }) {
                    Icon(Icons.Default.Search, contentDescription = "Buscar")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Acessar carta")
                }
            }
        }
    }
}

@Composable
private fun PermissionRationaleDialog(
    onRequestPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permissão necessária") },
        text = { Text("Para escanear cartas Pokémon, precisamos acessar sua câmera.") },
        confirmButton = {
            Button(onClick = onRequestPermission) {
                Text("Permitir")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun PermissionRequiredScreen(
    onRequestPermission: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "Permissão da câmera necessária",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = "Este app precisa da câmera para escanear cartas Pokémon",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            Button(onClick = onRequestPermission) {
                Text("Conceder permissão")
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
class PokemonCardAnalyzer(
    private val textRecognizer: TextRecognizer,
    private val onResult: (String) -> Unit
) : ImageAnalysis.Analyzer {
    override fun analyze(imageProxy: ImageProxy) {
        try {
            val mediaImage = imageProxy.image ?: return
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            // Processar apenas a área central (onde está o retângulo)
            val bitmap = imageProxy.toBitmap()
            val height = bitmap.height
            val width = bitmap.width

            // Área correspondente ao retângulo na tela (80% central)
            val left = (width * 0.1f).toInt()
            val top = (height * 0.2f).toInt()
            val right = (width * 0.9f).toInt()
            val bottom = (height * 0.8f).toInt()

            val region = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
            val processedBitmap = enhanceImage(region)

            val image = InputImage.fromBitmap(processedBitmap, rotationDegrees)

            textRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    onResult(visionText.text)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                    bitmap.recycle()
                    processedBitmap.recycle()
                    region.recycle()
                }
        } catch (e: Exception) {
            Log.e("PokemonAnalyzer", "Erro na análise", e)
            imageProxy.close()
        }
    }


private fun enhanceImage(bitmap: Bitmap): Bitmap {
    val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val paint = Paint().apply {
        // Aumentar contraste e saturação
        colorFilter = ColorMatrixColorFilter(
            ColorMatrix().apply {
                // Ajuste mais agressivo para melhorar texto
                set(
                    floatArrayOf(
                        1.5f, 0f, 0f, 0f, -50f,
                        0f, 1.5f, 0f, 0f, -50f,
                        0f, 0f, 1.5f, 0f, -50f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
        )
    }
    canvas.drawBitmap(bitmap, 0f, 0f, paint)
    return result
}
}



private fun analyzeCardText(text: String): Pair<String, String> {
    // Padrões mais abrangentes para diferentes formatos de cartas Pokémon
    val patterns = listOf(
        """\b(\d{1,3})\s*[/\\]\s*(\d{1,3})\b""".toRegex(),
        """\b(\d{1,3})\s*of\s*(\d{1,3})\b""".toRegex(),
        """\bno\s*\.?\s*(\d{1,3})\b""".toRegex(),
        """\b(\d{1,3})\s*-\s*(\d{1,3})\b""".toRegex()
    )

    for (pattern in patterns) {
        pattern.find(text.trim())?.let { match ->
            val num1 = match.groupValues[1].padStart(3, '0')
            val num2 = if (match.groupValues.size > 2) match.groupValues[2].padStart(3, '0') else "???"
            return Pair("Nº: $num1/$num2", "Carta detectada!")
        }
    }

    // Verificar se há texto que parece nome de Pokémon
    val pokemonName = detectPokemonName(text)
    if (pokemonName != null) {
        return Pair("Possível Pokémon: $pokemonName", "Nome reconhecido")
    }

    return Pair("Número não encontrado", "Centralize o canto inferior da carta")
}

private fun detectPokemonName(text: String): String? {
    val commonPokemon = listOf("Pikachu", "Charizard", "Mewtwo", "Eevee", "Bulbasaur", "Squirtle" ,"Munkidori")

    return text.split("\n").firstOrNull { line ->
        commonPokemon.any { pokemon ->
            line.contains(pokemon, ignoreCase = true)
        }
    }
}

@OptIn(ExperimentalGetImage::class)
fun ImageProxy.toBitmap(): Bitmap {
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize).apply {
        yBuffer.get(this, 0, ySize)
        vBuffer.get(this, ySize, vSize)
        uBuffer.get(this, ySize + vSize, uSize)
    }

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream().apply {
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 75, this)
    }
    return BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
}