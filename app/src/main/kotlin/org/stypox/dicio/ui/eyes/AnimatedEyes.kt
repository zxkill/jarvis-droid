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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeCap
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
    /** Нейтральное спокойное выражение */
    NEUTRAL,
    /** Улыбка – верхние веки слегка опущены */
    HAPPY,
    /** Сердитый взгляд – верхние веки наклонены внутрь */
    ANGRY,
    /** Грустный взгляд – нижние веки приподняты */
    SAD,
    /** Удивление – глаза широко раскрыты */
    SURPRISED,
}

/**
 * Состояние глаз, которое позволяет изменять эмоции и направление взгляда
 * извне через удобные методы [setExpression] и [lookAt].
 */
class EyesState {
    // Текущая эмоция
    var expression by mutableStateOf(EyeExpression.NEUTRAL)
        private set

    // Куда "смотрят" зрачки. Диапазон -1..1 по каждой оси.
    var lookX by mutableStateOf(0f)
        private set
    var lookY by mutableStateOf(0f)
        private set

    /** Установить новую эмоцию глаз */
    fun setExpression(newExpression: EyeExpression) {
        expression = newExpression
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
        val eyeHeight = size.height * 0.8f * blink.value
        val leftCenter = Offset(eyeWidth * 0.5f, size.height / 2f)
        val rightCenter = Offset(eyeWidth * 1.5f, size.height / 2f)
        drawEye(leftCenter, eyeWidth, eyeHeight, pupilOffsetX, pupilOffsetY,
            state.expression, eyeColor, irisColor, pupilColor)
        drawEye(rightCenter, eyeWidth, eyeHeight, pupilOffsetX, pupilOffsetY,
            state.expression, eyeColor, irisColor, pupilColor)
    }
}

/** Вспомогательная функция для рисования одного глаза */
private fun DrawScope.drawEye(
    center: Offset,
    width: Float,
    height: Float,
    pupilOffsetX: Float,
    pupilOffsetY: Float,
    expression: EyeExpression,
    eyeColor: Color,
    irisColor: Color,
    pupilColor: Color,
) {
    // Овал глаза
    val rect = Rect(center.x - width / 2f, center.y - height / 2f,
        center.x + width / 2f, center.y + height / 2f)
    drawOval(color = eyeColor, topLeft = rect.topLeft, size = rect.size)

    // Радужка и зрачок
    val radius = min(width, height) / 4f
    val pupilCenter = Offset(
        x = center.x + pupilOffsetX * radius,
        y = center.y + pupilOffsetY * radius,
    )
    drawCircle(color = irisColor, radius = radius, center = pupilCenter)
    drawCircle(color = pupilColor, radius = radius / 2f, center = pupilCenter)

    // Рисуем веки в зависимости от эмоции
    when (expression) {
        EyeExpression.HAPPY -> {
            // Верхняя дуга, создающая "улыбку"
            drawArc(
                color = pupilColor,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = rect.topLeft,
                size = rect.size,
                style = Stroke(width = height * 0.15f, cap = StrokeCap.Round)
            )
        }
        EyeExpression.ANGRY -> {
            // Диагональная линия сверху-внутрь
            drawLine(
                color = pupilColor,
                start = Offset(rect.left, rect.top + height * 0.2f),
                end = Offset(rect.right, rect.top - height * 0.1f),
                strokeWidth = height * 0.15f,
                cap = StrokeCap.Round,
            )
        }
        EyeExpression.SAD -> {
            // Нижняя дуга "печали"
            drawArc(
                color = pupilColor,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = rect.topLeft,
                size = rect.size,
                style = Stroke(width = height * 0.15f, cap = StrokeCap.Round)
            )
        }
        EyeExpression.SURPRISED -> {
            // Толстая обводка вокруг глаза для эффекта удивления
            drawOval(
                color = pupilColor.copy(alpha = 0.3f),
                topLeft = rect.topLeft,
                size = rect.size,
                style = Stroke(width = height * 0.08f)
            )
        }
        else -> Unit
    }
}

