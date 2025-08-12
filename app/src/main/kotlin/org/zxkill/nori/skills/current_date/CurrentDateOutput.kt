package org.zxkill.nori.skills.current_date

import org.nori.skill.context.SkillContext
import org.nori.skill.skill.SkillOutput
import org.zxkill.nori.R
import org.zxkill.nori.io.graphical.HeadlineSpeechSkillOutput
import org.zxkill.nori.util.getString

class CurrentDateOutput(
    private val type: Type,
    private val value: String
) : HeadlineSpeechSkillOutput {
    enum class Type { DAY, YEAR, MONTH }

    override fun getSpeechOutput(ctx: SkillContext): String = when (type) {
        Type.DAY -> ctx.getString(R.string.skill_date_today, value)
        Type.YEAR -> ctx.getString(R.string.skill_date_year, value)
        Type.MONTH -> ctx.getString(R.string.skill_date_month, value)
    }
}

