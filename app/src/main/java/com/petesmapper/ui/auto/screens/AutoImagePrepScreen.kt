package com.petesmapper.ui.auto.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.petesmapper.ui.auto.AutoImportEngine
import com.petesmapper.ui.auto.ImageBounds
import com.petesmapper.ui.auto.PerspectiveCorners
import com.petesmapper.ui.auto.viewmodel.AutoWorkflowViewModel
import java.io.File

@Composable
fun AutoImagePrepScreen(vm: AutoWorkflowViewModel) {
    val context = LocalContext.current
    val state by vm.state.collectAsState()
    val engine = remember { AutoImportEngine() }

    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var cropX by remember { mutableFloatStateOf(0f) }
    var cropY by remember { mutableFloatStateOf(0f) }
    var cropW by remember { mutableFloatStateOf(300f) }
    var cropH by remember { mutableFloatStateOf(300f) }
    val corners = remember { mutableStateListOf(Offset(20f, 20f), Offset(280f, 20f), Offset(280f, 280f), Offset(20f, 280f)) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val input = context.contentResolver.openInputStream(uri) ?: return@runCatching
            val temp = File(context.cacheDir, "auto_import_${System.currentTimeMillis()}.png")
            temp.outputStream().use { output -> input.copyTo(output) }
            vm.setNormalizedImage(engine.loadImage(temp))
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap ?: return@rememberLauncherForActivityResult
        runCatching {
            val temp = File(context.cacheDir, "auto_camera_${System.currentTimeMillis()}.png")
            temp.outputStream().use { out -> bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out) }
            vm.setNormalizedImage(engine.loadImage(temp))
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("AUTO Image Preparation")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { galleryLauncher.launch("image/*") }) { Text("Pick Image") }
            Button(onClick = { cameraLauncher.launch(null) }) { Text("Capture") }
            Button(onClick = { vm.resetFromStep(1) }) { Text("Reset Image") }
        }

        Card(Modifier.fillMaxWidth().height(320.dp)) {
            Canvas(Modifier.fillMaxSize().background(Color(0xFF101010)).pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    // drag nearest perspective corner
                    val idx = corners.indices.minByOrNull { i ->
                        val c = corners[i]; val dx = c.x - change.position.x; val dy = c.y - change.position.y; dx * dx + dy * dy
                    } ?: 0
                    corners[idx] = corners[idx] + drag
                }
            }) {
                // preview placeholder + crop rectangle + perspective corners
                drawRect(Color(0x332196F3), topLeft = Offset(cropX, cropY), size = androidx.compose.ui.geometry.Size(cropW, cropH), style = Stroke(2f))
                corners.forEach { c -> drawCircle(Color.Yellow, 8f, c) }
                if (corners.size == 4) {
                    drawLine(Color.Yellow, corners[0], corners[1], 2f)
                    drawLine(Color.Yellow, corners[1], corners[2], 2f)
                    drawLine(Color.Yellow, corners[2], corners[3], 2f)
                    drawLine(Color.Yellow, corners[3], corners[0], 2f)
                }
            }
        }

        Text("Brightness")
        Slider(value = brightness, onValueChange = { brightness = it }, valueRange = -80f..80f)
        Text("Contrast")
        Slider(value = contrast, onValueChange = { contrast = it }, valueRange = 0.5f..2.5f)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val img = state.normalizedImage ?: return@Button
                val cropped = engine.cropImage(img, ImageBounds(cropX.toInt(), cropY.toInt(), cropW.toInt(), cropH.toInt()))
                vm.setNormalizedImage(cropped)
            }) { Text("Crop") }
            Button(onClick = {
                val img = state.normalizedImage ?: return@Button
                vm.setNormalizedImage(engine.rotateImage(img, 90f))
            }) { Text("Rotate 90°") }
            Button(onClick = {
                val img = state.normalizedImage ?: return@Button
                val corrected = engine.perspectiveCorrect(
                    img,
                    PerspectiveCorners(corners[0].x, corners[0].y, corners[1].x, corners[1].y, corners[2].x, corners[2].y, corners[3].x, corners[3].y),
                    autoDetect = false,
                )
                vm.setNormalizedImage(corrected)
            }) { Text("Perspective") }
        }

        Button(onClick = {
            val img = state.normalizedImage ?: return@Button
            val normalized = engine.normalizeContrast(engine.normalizeBrightness(img, brightness), contrast)
            vm.setNormalizedImage(normalized)
        }, modifier = Modifier.fillMaxWidth()) { Text("Apply Normalize + Preview") }
    }
}
