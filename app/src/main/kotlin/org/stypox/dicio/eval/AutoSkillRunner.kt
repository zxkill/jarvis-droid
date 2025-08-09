package org.stypox.dicio.eval

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
import org.dicio.skill.skill.AutoRunnable
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.stypox.dicio.di.SkillContextInternal
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs skills that implement [AutoRunnable] on a schedule and exposes their outputs.
 */
@Singleton
class AutoSkillRunner @Inject constructor(
    private val skillHandler: SkillHandler,
    private val skillContext: SkillContextInternal,
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val jobs = mutableMapOf<String, Job>()
    private val _outputs = MutableStateFlow<Map<String, SkillOutput>>(emptyMap())
    val outputs: StateFlow<Map<String, SkillOutput>> = _outputs

    init {
        scope.launch {
            skillHandler.enabledSkillsInfo.filterNotNull().collectLatest { infos ->
                restartJobs(infos)
            }
        }
    }

    private fun restartJobs(infos: List<SkillInfo>) {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        _outputs.value = emptyMap()
        for (info in infos) {
            val skill = info.build(skillContext)
            if (skill is AutoRunnable) {
                jobs[info.id] = scope.launch {
                    while (true) {
                        val output = skill.autoOutput(skillContext)
                        _outputs.update { it + (info.id to output) }
                        delay(skill.autoUpdateIntervalMillis)
                    }
                }
            }
        }
    }

    fun cancel() {
        scope.cancel()
    }
}

