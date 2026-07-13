package com.vasilisneo.trackstar.ui.components

// Live QR scanner used by the Scan tab of the QR connect sheet. Ports iOS's QRScannerView
// (AVCaptureSession + Vision) onto CameraX + ZXing: a full-bleed camera preview whose frames are
// decoded on a background executor, firing onCode for each successfully-read QR. The caller
// decides what a scanned string means (a coach reads an athlete's email; an athlete reads a
// trackstar://invite/{token} link) and guards against acting on it twice.

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

@Composable
fun QrCameraScanner(
    onCode: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }
    // Ask on first entry if we don't already hold the permission.
    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (hasPermission) {
            CameraPreview(onCode = onCode, modifier = Modifier.fillMaxSize())
        } else {
            PermissionPrompt(onEnable = { launcher.launch(Manifest.permission.CAMERA) })
        }
    }
}

@Composable
private fun CameraPreview(onCode: (String) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    // Latest callback, so the long-lived analyzer always calls through to the current lambda
    // without rebinding the camera on every recomposition.
    val latestOnCode by androidx.compose.runtime.rememberUpdatedState(onCode)

    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            bindCamera(ctx, lifecycleOwner, previewView, analysisExecutor) { latestOnCode(it) }
            previewView
        },
    )
}

private fun bindCamera(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    executor: java.util.concurrent.Executor,
    onCode: (String) -> Unit,
) {
    val future = ProcessCameraProvider.getInstance(context)
    future.addListener({
        val provider = future.get()
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(executor, QrAnalyzer(onCode))
        provider.unbindAll()
        runCatching {
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }
    }, ContextCompat.getMainExecutor(context))
}

// Decodes the luminance plane of each frame with ZXing. Fires onCode for every successful read;
// the caller debounces/guards acting on it. Frames with no QR (the common case) are cheap misses.
private class QrAnalyzer(private val onCode: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
    }

    override fun analyze(image: ImageProxy) {
        try {
            val buffer = image.planes[0].buffer
            val data = ByteArray(buffer.remaining()).also { buffer.get(it) }
            val source = PlanarYUVLuminanceSource(
                data, image.width, image.height, 0, 0, image.width, image.height, false
            )
            val bitmap = BinaryBitmap(HybridBinarizer(source))
            val result = runCatching { reader.decodeWithState(bitmap) }.getOrNull()
            result?.text?.let(onCode)
        } catch (_: Exception) {
            // Ignore undecodable frames.
        } finally {
            reader.reset()
            image.close()
        }
    }
}

@Composable
private fun PermissionPrompt(onEnable: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Camera access needed", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(
                "Allow camera access to scan a QR code.",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color.White)
                    .clickable(onClick = onEnable)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                Text("Enable Camera", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
            }
        }
    }
}
