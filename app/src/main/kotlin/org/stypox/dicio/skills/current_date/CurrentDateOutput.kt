package org.stypox.dicio.skills.current_date

import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillOutput
import org.stypox.dicio.R
import org.stypox.dicio.io.graphical.HeadlineSpeechSkillOutput
import org.stypox.dicio.util.getString

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

