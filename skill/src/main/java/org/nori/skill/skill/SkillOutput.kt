package org.nori.skill.skill

import androidx.compose.runtime.Composable
import org.nori.skill.context.SkillContext

interface SkillOutput {
    fun getSpeechOutput(ctx: SkillContext): String

    fun getInteractionPlan(ctx: SkillContext): InteractionPlan = InteractionPlan.FinishInteraction

    @Composable
    fun GraphicalOutput(ctx: SkillContext)
}
