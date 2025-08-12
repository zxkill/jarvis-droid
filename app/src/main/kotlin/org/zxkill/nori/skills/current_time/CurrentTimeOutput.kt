package org.zxkill.nori.skills.current_time

import org.nori.skill.context.SkillContext
import org.zxkill.nori.R
import org.zxkill.nori.io.graphical.HeadlineSpeechSkillOutput
import org.zxkill.nori.util.getString

class CurrentTimeOutput(
    private val timeStr: String,
) : HeadlineSpeechSkillOutput {
    override fun getSpeechOutput(ctx: SkillContext): String =
        ctx.getString(R.string.skill_time_current_time, timeStr)
}
