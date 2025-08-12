package org.zxkill.nori.skills.navigation

import org.nori.skill.context.SkillContext
import org.zxkill.nori.R
import org.zxkill.nori.io.graphical.HeadlineSpeechSkillOutput
import org.zxkill.nori.util.getString

class NavigationOutput(
    private val where: String?,
) : HeadlineSpeechSkillOutput {
    override fun getSpeechOutput(ctx: SkillContext): String = if (where.isNullOrBlank()) {
        ctx.getString(R.string.skill_navigation_specify_where)
    } else {
        ctx.getString(R.string.skill_navigation_navigating_to, where)
    }
}
