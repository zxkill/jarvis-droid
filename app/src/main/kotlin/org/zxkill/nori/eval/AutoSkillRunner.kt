package org.zxkill.nori.eval

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.nori.skill.skill.AutoRunnable
import org.nori.skill.skill.SkillInfo
import org.nori.skill.skill.SkillOutput
import org.zxkill.nori.di.SkillContextInternal
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Служебный класс, который периодически запускает скиллы,
 * реализующие интерфейс [AutoRunnable]. Каждый скилл выполняется
 * в собственной корутине, а результаты сохраняются в [StateFlow],
 * чтобы интерфейс мог подписаться на обновления.
 */
@Singleton
class AutoSkillRunner @Inject constructor(
    private val skillHandler: SkillHandler,
    private val skillContext: SkillContextInternal,
) {
    // Общий скоуп для всех фоновых задач
    private val scope = CoroutineScope(Dispatchers.Default)
    // Активные корутины, запущенные для каждого скилла
    private val jobs = mutableMapOf<String, Job>()
    // Карта последних выводов каждого авто-скилла
    private val _outputs = MutableStateFlow<Map<String, SkillOutput>>(emptyMap())
    /** Поток с актуальными результатами всех авто-скиллов. */
    val outputs: StateFlow<Map<String, SkillOutput>> = _outputs

    init {
        // При каждом изменении списка активных скиллов перезапускаем задачи,
        // чтобы учесть новые или отключённые возможности.
        scope.launch {
            skillHandler.enabledSkillsInfo.filterNotNull().collectLatest { infos ->
                restartJobs(infos)
            }
        }
    }

    /**
     * Отменяет ранее запущенные задачи и создаёт новые для каждого
     * скилла, поддерживающего автоматический запуск.
     */
    private fun restartJobs(infos: List<SkillInfo>) {
        // Останавливаем старые корутины
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        _outputs.value = emptyMap()
        for (info in infos) {
            val skill = info.build(skillContext)
            if (skill is AutoRunnable) {
                // Для каждого авто-скилла запускаем бесконечный цикл
                jobs[info.id] = scope.launch {
                    while (true) {
                        val output = skill.autoOutput(skillContext)
                        // Обновляем карту выводов. Старые значения перезаписываются.
                        _outputs.update { it + (info.id to output) }
                        delay(skill.autoUpdateIntervalMillis)
                    }
                }
            }
        }
    }

    /** Отменяет все запущенные корутины и очищает ресурсы. */
    fun cancel() {
        scope.cancel()
    }
}

