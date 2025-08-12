package org.zxkill.nori.skills.fallback.text

import org.nori.skill.context.SkillContext
import org.nori.skill.skill.InteractionPlan
import org.zxkill.nori.R
import org.zxkill.nori.io.graphical.HeadlineSpeechSkillOutput
import org.zxkill.nori.util.getString

class TextFallbackOutput(
    val askToRepeat: Boolean
) : HeadlineSpeechSkillOutput {
    override fun getSpeechOutput(ctx: SkillContext): String = ctx.getString(
        if (askToRepeat) R.string.eval_no_match_repeat
        else R.string.eval_no_match
    )

    // this makes it so that the evaluator will open the microphone again, but the skill provided
    // will never actually match, so the previous batch of skills will be used instead
    override fun getInteractionPlan(ctx: SkillContext) =
        InteractionPlan.Continue(reopenMicrophone = askToRepeat)
}
