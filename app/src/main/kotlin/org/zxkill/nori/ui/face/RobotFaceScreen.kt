package org.zxkill.nori.ui.face

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.os.BatteryManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import org.nori.skill.skill.SkillInfo
import org.nori.skill.skill.Permission
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import org.nori.skill.skill.SkillOutput
import org.zxkill.nori.io.graphical.PersistentSkillOutput
import org.zxkill.nori.skills.weather.WeatherOutput
import org.zxkill.nori.io.input.InputEvent
import org.zxkill.nori.io.input.SttState
import org.zxkill.nori.io.wake.WakeService.Companion.TRIGGER_WORD
import org.zxkill.nori.ui.home.HomeScreenViewModel
import org.zxkill.nori.ui.home.SttFab
import org.zxkill.nori.ui.eyes.AnimatedEyes
import org.zxkill.nori.ui.eyes.rememberEyesState
import org.zxkill.nori.settings.datastore.UserSettings
import org.zxkill.nori.util.checkPermissions
import org.zxkill.nori.util.getNonGrantedSecurePermissions
import java.util.Locale
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import coil.compose.AsyncImage
import kotlin.math.roundToInt

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

      // Карта текущих выводов авто-скиллов (время, дата, погода и т.д.)
      val autoOutputs by viewModel.autoSkillOutputs.collectAsState()
      // Информация о включённых скиллах — нужна для отображения иконок и порядка
      val autoInfos by viewModel.skillHandler.enabledSkillsInfo.collectAsState()

    // Проверяем, выданы ли необходимые разрешения для запуска навыков.
    // Если приложение уже имеет доступ ко всем требуемым разрешениям,
    // возвращаем true, иначе выводится сообщение о недостающих разрешениях.
    val context = LocalContext.current
    viewModel.skillEvaluator.permissionRequester = { perms: List<Permission> ->
        val normal = perms.filterIsInstance<Permission.NormalPermission>().map { it.id }.toTypedArray()
        val secure = perms.filterIsInstance<Permission.SecurePermission>()
        checkPermissions(context, *normal) &&
            getNonGrantedSecurePermissions(context, secure).isEmpty()
    }

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
            if (latestOutput !is PersistentSkillOutput) {
                // Обычный вывод скрываем через заданный таймаут
                delay(displaySeconds * 1000L)
                visibleOutput = null
            }
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
                    contentAlignment = Alignment.Center,
                ) {
                    // При показе ответа скилла глаза занимают лишь половину экрана,
                    // поэтому уменьшаем их размер для визуального баланса
                    RobotEyes(compact = true)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    visibleOutput?.GraphicalOutput(viewModel.skillContext)
                }
            }
        }

        // В левом верхнем углу показываем обновляемую информацию:
        // время, дату и погоду. Такой выбор позиции позволяет
        // избежать перекрытия системными индикаторами (например, точкой микрофона)
        AutoInfoCorner(
            infos = autoInfos ?: listOf(),
            outputs = autoOutputs,
        )

        if (sttState != null) {
            SttFab(
                state = sttState!!,
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

/**
 * Компактный уголок с автообновляемой информацией.
 * Показывает дату, время и погоду в одну строку с небольшими иконками
 * в левом верхнем углу экрана.
 */
@Composable
private fun BoxScope.AutoInfoCorner(
    infos: List<SkillInfo>,
    outputs: Map<String, SkillOutput>,
) {
    // Текущие дата и время выводим числовым форматом
    val dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
    val timeStr = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

    val context = LocalContext.current
    var batteryPercent by remember { mutableStateOf(0) }
    val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    LaunchedEffect(Unit) {
        while (true) {
            batteryPercent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            delay(60_000)
        }
    }
    val batteryIcon = when (batteryPercent) {
        in 0..15 -> Icons.Default.BatteryAlert
        in 16..30 -> Icons.Default.Battery2Bar
        in 31..50 -> Icons.Default.Battery4Bar
        in 51..80 -> Icons.Default.Battery6Bar
        else -> Icons.Default.BatteryFull
    }

    // Получаем структурированный вывод погоды, если он есть
    val weather = outputs["weather"] as? WeatherOutput.Success
    val showDate = outputs.containsKey("current_date")
    val showTime = outputs.containsKey("current_time")

    Row(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showDate) {
            // Иконка и текст даты
            infos.find { it.id == "current_date" }?.let { info ->
                Icon(
                    painter = info.icon(),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = dateStr,
                color = Color.White,
                fontSize = 12.sp,
            )
        }

        if (showTime) {
            Spacer(modifier = Modifier.width(8.dp))
            // Иконка и текст времени
            infos.find { it.id == "current_time" }?.let { info ->
                Icon(
                    painter = info.icon(),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = timeStr,
                color = Color.White,
                fontSize = 12.sp,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = batteryIcon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$batteryPercent%",
            color = Color.White,
            fontSize = 12.sp,
        )

        weather?.let { data ->
            Spacer(modifier = Modifier.width(8.dp))
            // Иконка погоды берётся из сетевого источника
            AsyncImage(
                model = data.iconUrl,
                contentDescription = data.description,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            val unit = stringResource(data.temperatureUnit.unitString)
            val temp = data.temperatureUnit.convert(data.temp).roundToInt()
            Text(
                text = "$temp $unit ${data.description}",
                color = Color.White,
                fontSize = 12.sp,
            )
        }
    }
}
