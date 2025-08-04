package org.stypox.dicio.ui.face

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import org.dicio.skill.skill.SkillOutput
import org.stypox.dicio.ui.home.HomeScreenViewModel
import org.stypox.dicio.settings.datastore.UserSettings

/**
 * Экран с лицом робота. Показывает два глаза и выводит ответы скиллов.
 */
@Composable
fun RobotFaceScreen(
    // Компонент открытия меню, скрываемый в левом верхнем углу
    navigationIcon: @Composable () -> Unit,
    viewModel: HomeScreenViewModel = hiltViewModel(),
) {
    // Последний вывод скилла
    val interactionLog by viewModel.skillEvaluator.state.collectAsState()
    val latestOutput = interactionLog.interactions.lastOrNull()
        ?.questionsAnswers?.lastOrNull()?.answer

    // Настройки пользователя, где хранится длительность отображения
    val settings by viewModel.dataStore.data.collectAsState(initial = UserSettings.getDefaultInstance())
    val displaySeconds = if (settings.skillOutputDisplaySeconds > 0) settings.skillOutputDisplaySeconds else 10

    // Состояние видимого вывода
    var visibleOutput by remember { mutableStateOf<SkillOutput?>(null) }

    // При появлении нового вывода показываем его на экране ограниченное время
    LaunchedEffect(latestOutput) {
        if (latestOutput != null) {
            visibleOutput = latestOutput
            delay(displaySeconds * 1000L)
            visibleOutput = null
        }
    }

    // При входе на экран сразу запускаем прослушивание
    LaunchedEffect(Unit) {
        viewModel.sttInputDevice.onClick(viewModel.skillEvaluator::processInputEvent)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Невидимая зона для открытия бокового меню
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(48.dp)
                .alpha(0f)
        ) {
            navigationIcon()
        }

        if (visibleOutput == null) {
            // Слушаем пользователя — глаза по центру
            RobotEyes(modifier = Modifier.align(Alignment.Center))
        } else {
            // Делим экран: глаза слева, вывод скилла справа
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    RobotEyes()
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    visibleOutput?.GraphicalOutput(viewModel.skillContext)
                }
            }
        }
    }
}

/**
 * Простейшее отображение глаз — два белых круга на чёрном фоне.
 */
@Composable
fun RobotEyes(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(80.dp)) {
            drawCircle(Color.White)
        }
        Canvas(modifier = Modifier.size(80.dp)) {
            drawCircle(Color.White)
        }
    }
}
