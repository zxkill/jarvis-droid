package org.stypox.dicio.skills.face_tracker

import android.graphics.Rect
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import org.dicio.skill.context.SkillContext
import org.stypox.dicio.R
import org.stypox.dicio.io.graphical.PersistentSkillOutput
import java.util.concurrent.Executors

/**
 * Графический вывод, показывающий поток с камеры, рамку вокруг обнаруженного
 * лица и рассчитанные углы yaw/pitch относительно центра кадра.
 */
class FaceTrackerOutput : PersistentSkillOutput {
    override fun getSpeechOutput(ctx: SkillContext): String =
        ctx.android.getString(R.string.skill_face_tracking_enabled)

    @Composable
    override fun GraphicalOutput(ctx: SkillContext) {
        val lifecycleOwner = LocalLifecycleOwner.current // нужен для привязки камеры
        val context = LocalContext.current
        val previewView = remember { PreviewView(context) } // View для вывода камеры
        var faceBox by remember { mutableStateOf<Rect?>(null) } // текущая рамка лица
        var offsets by remember { mutableStateOf<Pair<Float, Float>?>(null) } // yaw/pitch

        // Асинхронно получаем провайдера камеры
        val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

        DisposableEffect(Unit) {
            // Отдельный поток для обработки изображений
            val executor = Executors.newSingleThreadExecutor()
            // Клиент ML Kit для детекции лиц
            val detector = FaceDetection.getClient(
                FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .build()
            )
            // Превью камеры
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            // Поток анализа изображений
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            // Анализ каждого кадра: ищем лицо и вычисляем смещения
            analysis.setAnalyzer(executor) { imageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage != null) {
                    // Преобразуем кадр в формат ML Kit с учётом ориентации
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    detector.process(image)
                        .addOnSuccessListener { faces ->
                            // Берём самое крупное лицо в кадре
                            val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                            if (face != null) {
                                faceBox = face.boundingBox
                                val cx = face.boundingBox.exactCenterX()
                                val cy = face.boundingBox.exactCenterY()
                                // Защищаемся от нулевой ширины/высоты превью
                                val px = previewView.width.toFloat().takeIf { it > 0f } ?: 1f
                                val py = previewView.height.toFloat().takeIf { it > 0f } ?: 1f
                                // Перевод координат в углы поворота камеры
                                val yaw = (cx - px / 2f) / px * FOV_DEG_X
                                val pitch = -(cy - py / 2f) / py * FOV_DEG_Y
                                offsets = Pair(yaw, pitch)
                            } else {
                                // Лицо не найдено
                                faceBox = null
                                offsets = null
                            }
                        }
                        .addOnCompleteListener { imageProxy.close() } // освобождаем кадр
                } else {
                    imageProxy.close()
                }
            }

            // Привязываем превью и анализ к жизненному циклу экрана
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                analysis
            )

            onDispose {
                cameraProvider.unbindAll()
                detector.close()
                executor.shutdown()
            }
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // Само превью камеры
            AndroidView({ previewView }, modifier = Modifier.fillMaxSize())
            // Слой для рисования рамки
            Canvas(modifier = Modifier.fillMaxSize()) {
                val box = faceBox
                if (box != null && previewView.width != 0 && previewView.height != 0) {
                    val scaleX = size.width / previewView.width
                    val scaleY = size.height / previewView.height
                    drawRect(
                        color = Color.Green,
                        topLeft = Offset(box.left * scaleX, box.top * scaleY),
                        size = Size(box.width() * scaleX, box.height() * scaleY),
                        style = Stroke(width = 5f)
                    )
                }
            }
            // Текст со смещениями относительно центра кадра
            val off = offsets
            if (off != null) {
                Text(
                    text = String.format("yaw=%+.1f pitch=%+.1f", off.first, off.second),
                    color = Color.Green,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }

    companion object {
        private const val FOV_DEG_X = 62f
        private const val FOV_DEG_Y = 38f
    }
}
