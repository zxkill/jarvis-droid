package org.zxkill.nori.skills.face_tracker

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
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import org.nori.skill.context.SkillContext
import org.zxkill.nori.R
import org.zxkill.nori.io.graphical.PersistentSkillOutput
import java.util.concurrent.Executors

/**
 * Графический вывод, показывающий поток с камеры, рамку вокруг обнаруженного
 * лица и рассчитанные углы yaw/pitch относительно центра кадра.
 * Если лицо не найдено, дополнительно используется детектор позы для
 * примерного определения положения головы.
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
        var imageSize by remember { mutableStateOf<Pair<Int, Int>?>(null) } // ширина/высота кадра
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
            // Детектор позы: на случай, когда лицо не найдено
            val poseDetector = PoseDetection.getClient(
                PoseDetectorOptions.Builder()
                    .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
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
            // Анализ каждого кадра: ищем лицо и при отсутствии пробуем найти голову
            analysis.setAnalyzer(executor) { imageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage != null) {
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    detector.process(image)
                        .addOnSuccessListener { faces ->
                            val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                            if (face != null) {
                                imageSize = Pair(image.width, image.height)
                                val box = face.boundingBox
                                val mirrored = Rect(
                                    image.width - box.right,
                                    box.top,
                                    image.width - box.left,
                                    box.bottom
                                )
                                faceBox = mirrored
                                val cx = mirrored.exactCenterX()
                                val cy = mirrored.exactCenterY()
                                val px = image.width.toFloat()
                                val py = image.height.toFloat()
                                val yaw = (cx - px / 2f) / px * FOV_DEG_X
                                val pitch = -(cy - py / 2f) / py * FOV_DEG_Y
                                offsets = Pair(yaw, pitch)
                                imageProxy.close()
                            } else {
                                poseDetector.process(image)
                                    .addOnSuccessListener { pose ->
                                        // Берём уши и нос, чтобы прикинуть положение головы
                                        val lEar = pose.getPoseLandmark(PoseLandmark.LEFT_EAR)
                                        val rEar = pose.getPoseLandmark(PoseLandmark.RIGHT_EAR)
                                        val nose = pose.getPoseLandmark(PoseLandmark.NOSE)
                                        val landmarks = listOfNotNull(lEar, rEar, nose)
                                            .filter { it.inFrameLikelihood >= POSE_MIN_VIS }
                                        if (landmarks.size >= 2) {
                                            imageSize = Pair(image.width, image.height)
                                            val xs = landmarks.map { it.position.x }
                                            val ys = landmarks.map { it.position.y }
                                            val xMin = xs.minOrNull()!!
                                            val xMax = xs.maxOrNull()!!
                                            val yMin = ys.minOrNull()!!
                                            val yMax = ys.maxOrNull()!!
                                            val mirrored = Rect(
                                                (image.width - xMax).toInt(),
                                                yMin.toInt(),
                                                (image.width - xMin).toInt(),
                                                yMax.toInt()
                                            )
                                            faceBox = mirrored
                                            val cx = mirrored.exactCenterX()
                                            val cy = mirrored.exactCenterY()
                                            val px = image.width.toFloat()
                                            val py = image.height.toFloat()
                                            val yaw = (cx - px / 2f) / px * FOV_DEG_X
                                            val pitch = -(cy - py / 2f) / py * FOV_DEG_Y
                                            offsets = Pair(yaw, pitch)
                                        } else {
                                            faceBox = null
                                            offsets = null
                                            imageSize = null
                                        }
                                    }
                                    .addOnCompleteListener { imageProxy.close() }
                            }
                        }
                        .addOnFailureListener { imageProxy.close() }
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
                poseDetector.close()
                executor.shutdown()
            }
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // Само превью камеры
            AndroidView({ previewView }, modifier = Modifier.fillMaxSize())
            // Слой для рисования рамки
            Canvas(modifier = Modifier.fillMaxSize()) {
                val box = faceBox
                val img = imageSize
                if (box != null && img != null) {
                    val (iw, ih) = img
                    val scaleX = size.width / iw
                    val scaleY = size.height / ih
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
        private const val POSE_MIN_VIS = 0.8f
    }
}
