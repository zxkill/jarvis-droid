package org.stypox.dicio.ui.eyes

import androidx.compose.animation.core.Animatable
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

    /** Текущая эмоция глаз – доступна только для чтения */
    val expression: EyeExpression
        get() = _expression

    /** Установить новую эмоцию глаз */
    fun setExpression(newExpression: EyeExpression) {
        _expression = newExpression
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
        drawEye(leftCenter, eyeSize, blink.value, state.expression, eyeColor)
        drawEye(rightCenter, eyeSize, blink.value, state.expression, eyeColor)
    }
}

/** Вспомогательная функция для рисования одного глаза */
private fun DrawScope.drawEye(
    center: Offset,
    baseSize: Float,
    blink: Float,
    expression: EyeExpression,
    eyeColor: Color,
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

    if (expression == EyeExpression.SURPRISED || expression == EyeExpression.SCARED || expression == EyeExpression.AWE) {
        drawPath(path, color = Color.Black.copy(alpha = 0.3f), style = Stroke(width = height * 0.08f))
    }
}

