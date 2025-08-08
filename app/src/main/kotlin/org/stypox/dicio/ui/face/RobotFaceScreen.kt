package org.stypox.dicio.ui.face

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
import org.stypox.dicio.io.input.InputEvent
import org.stypox.dicio.io.input.SttState
import org.stypox.dicio.io.wake.WakeService.Companion.TRIGGER_WORD
import org.stypox.dicio.ui.home.HomeScreenViewModel
import org.stypox.dicio.ui.home.SttFab
import org.stypox.dicio.ui.eyes.AnimatedEyes
import org.stypox.dicio.ui.eyes.rememberEyesState
import org.stypox.dicio.settings.datastore.UserSettings
import java.util.Locale

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

    // Текущее состояние устройства распознавания речи
    val sttState by viewModel.sttInputDevice.uiState.collectAsState()

    // Функция запуска прослушивания и обработки команд, если они начинаются
    // с ключевого слова
    val startListening = {
        viewModel.sttInputDevice.onClick { event ->
            when (event) {
                is InputEvent.Partial -> {
                    // Промежуточный текст показываем только если пользователь
                    // уже произнёс ключевое слово.
                    val lower = event.utterance.lowercase(Locale.getDefault())
                    val trigger = TRIGGER_WORD.lowercase(Locale.getDefault())
                    if (lower.startsWith(trigger)) {
                        val trimmed = event.utterance.substring(trigger.length).trim()
                        if (trimmed.isNotEmpty()) {
                            viewModel.skillEvaluator.processInputEvent(
                                InputEvent.Partial(trimmed)
                            )
                        }
                    }
                }
                is InputEvent.Final -> {
                    // Итоговое распознавание. Проверяем наличие ключевого слова
                    // в начале фразы и передаём дальше только команду без него.
                    val utterance = event.utterances.firstOrNull()
                    val trigger = TRIGGER_WORD.lowercase(Locale.getDefault())
                    if (utterance != null) {
                        val text = utterance.first
                        val confidence = utterance.second
                        val lower = text.lowercase(Locale.getDefault())
                        if (lower.startsWith(trigger)) {
                            val command = text.substring(trigger.length).trim()
                            if (command.isNotEmpty()) {
                                viewModel.skillEvaluator.processInputEvent(
                                    InputEvent.Final(listOf(Pair(command, confidence)))
                                )
                            } else {
                                // Было сказано только ключевое слово — очищаем ввод
                                viewModel.skillEvaluator.processInputEvent(InputEvent.None)
                            }
                        } else {
                            // Фраза не содержит ключевое слово, игнорируем её
                            viewModel.skillEvaluator.processInputEvent(InputEvent.None)
                        }
                    } else {
                        viewModel.skillEvaluator.processInputEvent(InputEvent.None)
                    }
                }
                InputEvent.None -> {
                    // Пользователь молчал — передаём событие далее без изменений
                    viewModel.skillEvaluator.processInputEvent(InputEvent.None)
                }
                is InputEvent.Error -> {
                    // Ошибки передаём напрямую обработчику
                    viewModel.skillEvaluator.processInputEvent(event)
                }
            }
        }
    }

    // При появлении нового вывода показываем его на экране ограниченное время
    LaunchedEffect(latestOutput) {
        if (latestOutput != null) {
            visibleOutput = latestOutput
            delay(displaySeconds * 1000L)
            visibleOutput = null
        }
    }

    // Автоматически запускаем прослушивание, как только устройство готово.
    // После обработки команды состояние возвращается в Loaded,
    // и эта корутина снова активирует прослушивание, обеспечивая
    // непрерывную работу без дополнительных нажатий.
    LaunchedEffect(sttState) {
        if (sttState == SttState.Loaded) {
            startListening()
        }
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
            // Отображаем крупные глаза по центру, когда вывод скилла отсутствует
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
                    // При показе ответа скилла глаза занимают лишь половину экрана,
                    // поэтому уменьшаем их размер для визуального баланса
                    RobotEyes(compact = true)
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

        if (sttState != null) {
            SttFab(
                state = sttState,
                onClick = startListening,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
            )
        }
    }
}

/**
 * Отображение пары анимированных глаз. Благодаря [rememberEyesState]
 * анимации и выбранная эмоция сохраняются между перерисовками.
 */
/**
 * Обёртка вокруг [AnimatedEyes], позволяющая переключать размер глаз.
 * @param compact если `true`, глаза будут уменьшены и подойдут для режима,
 *                когда экран поделён между глазами и выводом скилла
 */
@Composable
fun RobotEyes(modifier: Modifier = Modifier, compact: Boolean = false) {
    val eyesState = rememberEyesState()

    // В зависимости от режима выбираем высоту области рисования глаз.
    // В обычном состоянии глаза крупнее (120dp), а в "компактном" режиме – меньше.
    val size = if (compact) 60.dp else 120.dp

    AnimatedEyes(
        state = eyesState,
        modifier = modifier,
        eyeSize = size,
    )
}
