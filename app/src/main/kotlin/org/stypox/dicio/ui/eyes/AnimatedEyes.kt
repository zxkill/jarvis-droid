package org.stypox.dicio.ui.eyes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * Перечисление доступных эмоций глаза.
 * Каждая эмоция определяет форму век и прочие элементы рисунка.
 */
enum class EyeExpression {
    NORMAL,
    ANGRY,
    GLEE,
    HAPPY,
    SAD,
    WORRIED,
    FOCUSED,
    ANNOYED,
    SURPRISED,
    SKEPTIC,
    FRUSTRATED,
    UNIMPRESSED,
    SLEEPY,
    SUSPICIOUS,
    SQUINT,
    FURIOUS,
    SCARED,
    AWE,
}

// Описание параметров формы глаз для каждой эмоции
private data class EyeConfig(
    val offsetX: Float,
    val offsetY: Float,
    val width: Float,
    val height: Float,
    val slopeTop: Float,
    val slopeBottom: Float,
    val radiusTop: Float,
    val radiusBottom: Float,
)

// Карта настроек эмоций на основе пресетов из esp32-eyes
private fun EyeExpression.config(): EyeConfig = when (this) {
    EyeExpression.NORMAL -> EyeConfig(0f, 0f, 40f, 40f, 0f, 0f, 8f, 8f)
    EyeExpression.HAPPY -> EyeConfig(0f, 0f, 40f, 10f, 0f, 0f, 10f, 0f)
    EyeExpression.GLEE -> EyeConfig(0f, 0f, 40f, 8f, 0f, 0f, 8f, 0f)
    EyeExpression.SAD -> EyeConfig(0f, 0f, 40f, 15f, -0.5f, 0f, 1f, 10f)
    EyeExpression.WORRIED -> EyeConfig(0f, 0f, 40f, 25f, -0.1f, 0f, 6f, 10f)
    EyeExpression.FOCUSED -> EyeConfig(0f, 0f, 40f, 14f, 0.2f, 0f, 3f, 1f)
    EyeExpression.ANNOYED -> EyeConfig(0f, 0f, 40f, 12f, 0f, 0f, 0f, 10f)
    EyeExpression.SURPRISED -> EyeConfig(-2f, 0f, 45f, 45f, 0f, 0f, 16f, 16f)
    EyeExpression.SKEPTIC -> EyeConfig(0f, -6f, 40f, 26f, 0.3f, 0f, 1f, 10f)
    EyeExpression.FRUSTRATED -> EyeConfig(3f, -5f, 40f, 12f, 0f, 0f, 0f, 10f)
    EyeExpression.UNIMPRESSED -> EyeConfig(3f, 0f, 40f, 12f, 0f, 0f, 1f, 10f)
    EyeExpression.SLEEPY -> EyeConfig(0f, -2f, 40f, 14f, -0.5f, -0.5f, 3f, 3f)
    EyeExpression.SUSPICIOUS -> EyeConfig(0f, 0f, 40f, 22f, 0f, 0f, 8f, 3f)
    EyeExpression.SQUINT -> EyeConfig(-10f, -3f, 35f, 35f, 0f, 0f, 8f, 8f)
    EyeExpression.ANGRY -> EyeConfig(-3f, 0f, 40f, 20f, 0.3f, 0f, 2f, 12f)
    EyeExpression.FURIOUS -> EyeConfig(-2f, 0f, 40f, 30f, 0.4f, 0f, 2f, 8f)
    EyeExpression.SCARED -> EyeConfig(-3f, 0f, 40f, 40f, -0.1f, 0f, 12f, 8f)
    EyeExpression.AWE -> EyeConfig(2f, 0f, 45f, 35f, -0.1f, 0.1f, 12f, 12f)
}

/**
 * Состояние глаз, которое позволяет изменять эмоции и направление взгляда
 * извне через удобные методы [setExpression] и [lookAt].
 */
class EyesState {
    // Текущая эмоция хранится во внутреннем стейте
    private var _expression by mutableStateOf(EyeExpression.NORMAL)

    /** Текущая эмоция глаз – доступна только для чтения */
    val expression: EyeExpression
        get() = _expression

    // Куда "смотрят" зрачки. Диапазон -1..1 по каждой оси.
    var lookX by mutableStateOf(0f)
        private set
    var lookY by mutableStateOf(0f)
        private set

    /** Установить новую эмоцию глаз */
    fun setExpression(newExpression: EyeExpression) {
        _expression = newExpression
    }

    /** Повернуть взгляд в указанную сторону. Значения ограничиваются диапазоном -1..1 */
    fun lookAt(x: Float, y: Float) {
        lookX = x.coerceIn(-1f, 1f)
        lookY = y.coerceIn(-1f, 1f)
    }
}

/** Создаёт и запоминает состояние глаз */
@Composable
fun rememberEyesState(): EyesState = remember { EyesState() }

/**
 * Основной компонент, рисующий пару глаз с анимациями моргания и движения зрачков.
 * @param state состояние глаз, позволяющее изменять эмоции
 * @param modifier модификатор для размещения компонента
 * @param eyeColor цвет "белка" глаз
 * @param irisColor цвет радужки
 * @param pupilColor цвет зрачка
 */
@Composable
fun AnimatedEyes(
    state: EyesState,
    modifier: Modifier = Modifier,
    eyeColor: Color = Color.White,
    irisColor: Color = Color(0xFF40C4FF),
    pupilColor: Color = Color.Black,
) {
    // Анимация моргания: значение 1 – глаза открыты, 0 – закрыты
    val blink = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        while (true) {
            // случайная задержка перед морганием
            delay(Random.nextLong(3000L, 7000L))
            blink.animateTo(0f, tween(durationMillis = 80))
            blink.animateTo(1f, tween(durationMillis = 120))
        }
    }

    // Плавное движение зрачков при изменении направления взгляда
    val pupilOffsetX by animateFloatAsState(state.lookX)
    val pupilOffsetY by animateFloatAsState(state.lookY)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        val eyeWidth = size.width / 2f
        val leftCenter = Offset(eyeWidth * 0.5f, size.height / 2f)
        val rightCenter = Offset(eyeWidth * 1.5f, size.height / 2f)
        drawEye(leftCenter, eyeWidth, blink.value, pupilOffsetX, pupilOffsetY,
            state.expression, eyeColor, irisColor, pupilColor)
        drawEye(rightCenter, eyeWidth, blink.value, pupilOffsetX, pupilOffsetY,
            state.expression, eyeColor, irisColor, pupilColor)
    }
}

/** Вспомогательная функция для рисования одного глаза */
private fun DrawScope.drawEye(
    center: Offset,
    baseSize: Float,
    blink: Float,
    pupilOffsetX: Float,
    pupilOffsetY: Float,
    expression: EyeExpression,
    eyeColor: Color,
    irisColor: Color,
    pupilColor: Color,
) {
    val cfg = expression.config()
    val scale = baseSize / 40f
    var width = cfg.width * scale
    var height = cfg.height * scale * blink
    val radiusTop = cfg.radiusTop * scale * blink
    val radiusBottom = cfg.radiusBottom * scale * blink
    val centerWithOffset = Offset(center.x + cfg.offsetX * scale, center.y + cfg.offsetY * scale)
    val deltaTop = height * cfg.slopeTop / 2f
    val deltaBottom = height * cfg.slopeBottom / 2f

    val topLeft = Offset(centerWithOffset.x - width / 2f, centerWithOffset.y - height / 2f - deltaTop)
    val topRight = Offset(centerWithOffset.x + width / 2f, centerWithOffset.y - height / 2f + deltaTop)
    val bottomRight = Offset(centerWithOffset.x + width / 2f, centerWithOffset.y + height / 2f + deltaBottom)
    val bottomLeft = Offset(centerWithOffset.x - width / 2f, centerWithOffset.y + height / 2f - deltaBottom)

    val path = Path().apply {
        moveTo(topLeft.x, topLeft.y + radiusTop)
        quadraticBezierTo(topLeft.x, topLeft.y, topLeft.x + radiusTop, topLeft.y)
        lineTo(topRight.x - radiusTop, topRight.y)
        quadraticBezierTo(topRight.x, topRight.y, topRight.x, topRight.y + radiusTop)
        lineTo(bottomRight.x, bottomRight.y - radiusBottom)
        quadraticBezierTo(
            bottomRight.x, bottomRight.y, bottomRight.x - radiusBottom, bottomRight.y
        )
        lineTo(bottomLeft.x + radiusBottom, bottomLeft.y)
        quadraticBezierTo(
            bottomLeft.x, bottomLeft.y, bottomLeft.x, bottomLeft.y - radiusBottom
        )
        close()
    }

    drawPath(path, color = eyeColor)

    clipPath(path) {
        val r = min(width, height) / 4f
        val pupilCenter = Offset(
            x = centerWithOffset.x + pupilOffsetX * r,
            y = centerWithOffset.y + pupilOffsetY * r,
        )
        drawCircle(color = irisColor, radius = r, center = pupilCenter)
        drawCircle(color = pupilColor, radius = r / 2f, center = pupilCenter)
    }

    if (expression == EyeExpression.SURPRISED || expression == EyeExpression.SCARED || expression == EyeExpression.AWE) {
        drawPath(path, color = pupilColor.copy(alpha = 0.3f), style = Stroke(width = height * 0.08f))
    }
}

