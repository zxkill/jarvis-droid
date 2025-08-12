package org.zxkill.nori.ui.eyes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.random.Random
import kotlin.math.abs
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
 * Состояние глаз, позволяющее изменять эмоции извне
 * через удобный метод [setExpression].
 */
class EyesState {
    // Текущая эмоция хранится во внутреннем стейте
    private var _expression by mutableStateOf(EyeExpression.NORMAL)

    // Направление взгляда по осям X и Y (диапазон -1..1)
    private var _lookX by mutableStateOf(0f)
    private var _lookY by mutableStateOf(0f)

    /** Текущая эмоция глаз – доступна только для чтения */
    val expression: EyeExpression
        get() = _expression

    /** Текущее направление взгляда по горизонтали */
    val lookX: Float
        get() = _lookX

    /** Текущее направление взгляда по вертикали */
    val lookY: Float
        get() = _lookY

    /** Установить новую эмоцию глаз */
    fun setExpression(newExpression: EyeExpression) {
        _expression = newExpression
    }

    /** Повернуть взгляд в указанном направлении (-1..1) */
    fun lookAt(x: Float, y: Float) {
        _lookX = x.coerceIn(-1f, 1f)
        _lookY = y.coerceIn(-1f, 1f)
    }
}

/** Создаёт и запоминает состояние глаз */
@Composable
fun rememberEyesState(): EyesState = remember { EyesState() }

/**
 * Основной компонент, рисующий пару глаз с анимацией моргания.
 * @param state состояние глаз, позволяющее изменять эмоции
 * @param modifier модификатор для размещения компонента
 * @param eyeColor цвет глаз
 * @param spacingRatio коэффициент расстояния между глазами
 * @param eyeSize высота области рисования глаза. Меняя этот параметр,
 *               можно масштабировать глаза без изменения логики рисования.
*/
@Composable
fun AnimatedEyes(
    state: EyesState,
    modifier: Modifier = Modifier,
    eyeColor: Color = Color.White,
    spacingRatio: Float = 0.5f,
    eyeSize: Dp = 120.dp,
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

    // Автоматическая смена эмоций каждые 30 секунд
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            val values = EyeExpression.values()
            state.setExpression(values[Random.nextInt(values.size)])
        }
    }

    // Анимация "блуждающего" взгляда
    val lookX = remember { Animatable(0f) }
    val lookY = remember { Animatable(0f) }

    // Плавно переводим взгляд к значениям из состояния
    LaunchedEffect(state.lookX) {
        lookX.animateTo(state.lookX, tween(durationMillis = 500))
    }
    LaunchedEffect(state.lookY) {
        lookY.animateTo(state.lookY, tween(durationMillis = 500))
    }

    // Периодически задаём новое направление взгляда
    LaunchedEffect(Unit) {
        while (true) {
            delay(4_000L)
            val x = Random.nextInt(-50, 51) / 100f
            val y = Random.nextInt(-50, 51) / 100f
            state.lookAt(x, y)
        }
    }

    // Небольшие колебания размеров глаз, чтобы сделать взгляд живее
    val idleTransition = rememberInfiniteTransition()
    val widthJitter by idleTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Reverse,
        ),
    )
    val heightJitter by idleTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse,
        ),
    )

    // Основное полотно для рисования глаз. Высота задаётся параметром [eyeSize],
    // что позволяет менять масштаб глаз в различных режимах экрана.
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(eyeSize)
    ) {
        val eyeSize = size.height
        val gap = eyeSize * spacingRatio
        val offset = eyeSize / 2f + gap / 2f
        val centerY = size.height / 2f
        val centerX = size.width / 2f
        val leftCenter = Offset(centerX - offset, centerY)
        val rightCenter = Offset(centerX + offset, centerY)
        val lx = lookX.value
        val ly = lookY.value
        drawEye(leftCenter, eyeSize, blink.value, state.expression, eyeColor, lx, ly, true, widthJitter, heightJitter)
        drawEye(rightCenter, eyeSize, blink.value, state.expression, eyeColor, lx, ly, false, widthJitter, heightJitter)
    }
}

/** Вспомогательная функция для рисования одного глаза */
private fun DrawScope.drawEye(
    center: Offset,
    baseSize: Float,
    blink: Float,
    expression: EyeExpression,
    eyeColor: Color,
    lookX: Float,
    lookY: Float,
    isLeft: Boolean,
    widthJitter: Float,
    heightJitter: Float,
) {
    val cfg = expression.config()
    val scale = baseSize / 40f
    // Перевод коэффициентов из esp32-eyes к нашим координатам
    val moveX = -25f * lookX * scale
    val moveY = 20f * lookY * scale
    val scaleYCommon = 1f - abs(lookY) * 0.4f
    val scaleYx = 1f + (if (isLeft) 1f else -1f) * lookX * 0.2f
    val verticalScale = scaleYCommon * scaleYx

    val width = cfg.width * scale * widthJitter
    val height = cfg.height * scale * blink * verticalScale * heightJitter
    val radiusTop = cfg.radiusTop * scale * blink * verticalScale * heightJitter
    val radiusBottom = cfg.radiusBottom * scale * blink * verticalScale * heightJitter
    val centerWithOffset = Offset(
        center.x + cfg.offsetX * scale + moveX,
        center.y + cfg.offsetY * scale + moveY
    )
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

    if (expression == EyeExpression.SURPRISED || expression == EyeExpression.SCARED || expression == EyeExpression.AWE) {
        drawPath(path, color = Color.Black.copy(alpha = 0.3f), style = Stroke(width = height * 0.08f))
    }
}

