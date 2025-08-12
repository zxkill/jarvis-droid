package org.zxkill.nori.screenshot

import kotlinx.coroutines.flow.MutableStateFlow
import org.nori.skill.skill.Permission
import org.zxkill.nori.eval.SkillEvaluator
import org.zxkill.nori.io.input.InputEvent
import org.zxkill.nori.ui.home.InteractionLog

class FakeSkillEvaluator : SkillEvaluator {
    override val state: MutableStateFlow<InteractionLog> = MutableStateFlow(
        InteractionLog(
            interactions = listOf(),
            pendingQuestion = null,
        )
    )

    override var permissionRequester: suspend (List<Permission>) -> Boolean = { true }

    override fun processInputEvent(event: InputEvent, askToRepeat: Boolean) {
    }
}
