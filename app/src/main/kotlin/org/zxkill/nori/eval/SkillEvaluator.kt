package org.zxkill.nori.eval

import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.nori.skill.skill.InteractionPlan
import org.nori.skill.skill.Permission
import org.nori.skill.skill.SkillOutput
import org.zxkill.nori.di.SkillContextInternal
import org.zxkill.nori.di.SttInputDeviceWrapper
import org.zxkill.nori.io.graphical.ErrorSkillOutput
import org.zxkill.nori.io.graphical.MissingPermissionsSkillOutput
import org.zxkill.nori.io.input.InputEvent
import org.zxkill.nori.skills.fallback.text.TextFallbackOutput
import org.zxkill.nori.ui.home.Interaction
import org.zxkill.nori.ui.home.InteractionLog
import org.zxkill.nori.ui.home.PendingQuestion
import org.zxkill.nori.ui.home.QuestionAnswer
import javax.inject.Singleton

// Интерфейс, описывающий обработчик пользовательских запросов (skills).
interface SkillEvaluator {
    // Поток с журналом взаимодействий для отображения на экране.
    val state: StateFlow<InteractionLog>

    // Функция, которую активити задаёт для запроса разрешений у пользователя.
    var permissionRequester: suspend (List<Permission>) -> Boolean

    // Обрабатывает входящие события от устройства ввода речи.
    // [askToRepeat] определяет, нужно ли в случае нераспознанной команды
    // просить пользователя повторить её.
    fun processInputEvent(event: InputEvent, askToRepeat: Boolean = true)
}

class SkillEvaluatorImpl(
    private val skillContext: SkillContextInternal,
    private val skillHandler: SkillHandler,
    private val sttInputDevice: SttInputDeviceWrapper,
) : SkillEvaluator {

    private val scope = CoroutineScope(Dispatchers.Default)

    // Получаем текущий ранжировщик навыков из обработчика.
    private val skillRanker: SkillRanker
        get() = skillHandler.skillRanker.value

    // Журнал взаимодействий и ожидаемый вопрос от пользователя.
    private val _state = MutableStateFlow(
        InteractionLog(
            interactions = listOf(),
            pendingQuestion = null,
        )
    )
    override val state: StateFlow<InteractionLog> = _state

    // Должна обновляться даже при пересоздании активити, поэтому `var`.
    override var permissionRequester: suspend (List<Permission>) -> Boolean = { false }

    // Обработка событий происходит в отдельной корутине.
    override fun processInputEvent(event: InputEvent, askToRepeat: Boolean) {
        scope.launch {
            suspendProcessInputEvent(event, askToRepeat)
        }
    }

    // Основной метод, реагирующий на новое событие от микрофона.
    private suspend fun suspendProcessInputEvent(event: InputEvent, askToRepeat: Boolean) {
        when (event) {
            is InputEvent.Error -> {
                addErrorInteractionFromPending(event.throwable)
            }
            is InputEvent.Final -> {
                // Пользователь завершил фразу: сохраняем её и запускаем обработку.
                _state.value = _state.value.copy(
                    pendingQuestion = PendingQuestion(
                        userInput = event.utterances[0].first,
                        continuesLastInteraction = skillRanker.hasAnyBatches(),
                        skillBeingEvaluated = null,
                    )
                )
                evaluateMatchingSkill(event.utterances.map { it.first }, askToRepeat)
            }
            InputEvent.None -> {
                // Пользователь молчит — очищаем ожидаемый вопрос.
                _state.value = _state.value.copy(pendingQuestion = null)
            }
            is InputEvent.Partial -> {
                // Частичное распознавание: обновляем текст на экране.
                _state.value = _state.value.copy(
                    pendingQuestion = PendingQuestion(
                        userInput = event.utterance,
                        // Следующий ввод может продолжать предыдущее взаимодействие
                        // только если стек навык‑батчей не пуст.
                        continuesLastInteraction = skillRanker.hasAnyBatches(),
                        skillBeingEvaluated = null,
                    )
                )
            }
        }
    }

    // Пытается подобрать и выполнить подходящий навык для пользовательского ввода.
    private suspend fun evaluateMatchingSkill(
        utterances: List<String>,
        askToRepeat: Boolean,
    ) {
        val (chosenInput, chosenSkill) = try {
            utterances.firstNotNullOfOrNull { input: String ->
                skillRanker.getBest(skillContext, input)?.let { skillWithResult ->
                    Pair(input, skillWithResult)
                }
            } ?: Pair(utterances[0], skillRanker.getFallbackSkill(skillContext, utterances[0]))
        } catch (throwable: Throwable) {
            addErrorInteractionFromPending(throwable)
            return
        }
        val skillInfo = chosenSkill.skill.correspondingSkillInfo

        _state.value = _state.value.copy(
            pendingQuestion = PendingQuestion(
                userInput = chosenInput,
                // Если навык продолжает прошлый диалог, ранжировщик не очищает стек батчей.
                continuesLastInteraction = skillRanker.hasAnyBatches(),
                skillBeingEvaluated = skillInfo,
            )
        )

        try {
            val permissions = skillInfo.neededPermissions
            if (permissions.isNotEmpty() && !permissionRequester(permissions)) {
                // Пользователь не выдал необходимые разрешения.
                addInteractionFromPending(MissingPermissionsSkillOutput(skillInfo))
                return
            }

            // Если необходимо игнорировать фразы без распознанного навыка,
            // устанавливаем предыдущий вывод так, чтобы TextFallbackSkill
            // не попросил повторить команду.
            skillContext.previousOutput =
                _state.value.interactions.lastOrNull()?.questionsAnswers?.lastOrNull()?.answer
            if (!askToRepeat) {
                skillContext.previousOutput = TextFallbackOutput(true)
            }
            val output = chosenSkill.generateOutput(skillContext)

            val interactionPlan = output.getInteractionPlan(skillContext)
            addInteractionFromPending(output)
            output.getSpeechOutput(skillContext).let {
                if (it.isNotBlank()) {
                    withContext (Dispatchers.Main) {
                        skillContext.speechOutputDevice.speak(it)
                    }
                }
            }

            when (interactionPlan) {
                InteractionPlan.FinishInteraction -> {
                    // Диалог завершён — сбрасываем стек навыков.
                    skillRanker.removeAllBatches()
                }
                is InteractionPlan.FinishSubInteraction -> {
                    skillRanker.removeTopBatch()
                }
                is InteractionPlan.Continue -> {
                    // Продолжаем текущий набор навыков без изменений.
                }
                is InteractionPlan.StartSubInteraction -> {
                    skillRanker.addBatchToTop(interactionPlan.nextSkills)
                }
                is InteractionPlan.ReplaceSubInteraction -> {
                    skillRanker.removeTopBatch()
                    skillRanker.addBatchToTop(interactionPlan.nextSkills)
                }
            }

            if (interactionPlan.reopenMicrophone) {
                skillContext.speechOutputDevice.runWhenFinishedSpeaking {
                    sttInputDevice.tryLoad(this::processInputEvent)
                }
            }

        } catch (throwable: Throwable) {
            addErrorInteractionFromPending(throwable)
            return
        }
    }

    // Добавляет в журнал ошибку, возникшую при обработке навыков.
    private fun addErrorInteractionFromPending(throwable: Throwable) {
        Log.e(TAG, "Error while evaluating skills", throwable)
        addInteractionFromPending(ErrorSkillOutput(throwable, true))
    }

    // Переносит подготовленный ответ из pending‑состояния в журнал взаимодействий.
    private fun addInteractionFromPending(skillOutput: SkillOutput) {
        val log = _state.value
        val pendingUserInput = log.pendingQuestion?.userInput
        val pendingContinuesLastInteraction = log.pendingQuestion?.continuesLastInteraction
            ?: skillRanker.hasAnyBatches()
        val pendingSkill = log.pendingQuestion?.skillBeingEvaluated
        val questionAnswer = QuestionAnswer(pendingUserInput, skillOutput)

        _state.value = log.copy(
            interactions = log.interactions.toMutableList().also { inters ->
                if (pendingContinuesLastInteraction && inters.isNotEmpty()) {
                    inters[inters.size - 1] = inters[inters.size - 1].let { i -> i.copy(
                        questionsAnswers = i.questionsAnswers.toMutableList()
                            .apply { add(questionAnswer) }
                    ) }
                } else {
                    inters.add(
                        Interaction(
                            skill = pendingSkill,
                            questionsAnswers = listOf(questionAnswer)
                        )
                    )
                }
            },
            pendingQuestion = null,
        )
    }

    companion object {
        val TAG = SkillEvaluator::class.simpleName
    }
}

@Module
@InstallIn(SingletonComponent::class)
class SkillEvaluatorModule {
    @Provides
    @Singleton
    fun provideSkillEvaluator(
        skillContext: SkillContextInternal,
        skillHandler: SkillHandler,
        sttInputDevice: SttInputDeviceWrapper,
    ): SkillEvaluator {
        return SkillEvaluatorImpl(skillContext, skillHandler, sttInputDevice)
    }
}
