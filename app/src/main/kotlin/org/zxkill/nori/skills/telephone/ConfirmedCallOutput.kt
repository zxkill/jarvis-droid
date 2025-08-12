package org.zxkill.nori.skills.telephone

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.nori.skill.context.SkillContext
import org.nori.skill.skill.SkillOutput
import org.zxkill.nori.R
import org.zxkill.nori.io.graphical.Headline
import org.zxkill.nori.util.getString

class ConfirmedCallOutput(
    private val number: String?,
) : SkillOutput {
    override fun getSpeechOutput(ctx: SkillContext): String = if (number == null) {
        ctx.getString(R.string.skill_telephone_not_calling)
    } else {
        "" // do not speak anything since a call has just started
    }

    @Composable
    override fun GraphicalOutput(ctx: SkillContext) {
        Headline(
            text = if (number == null) {
                stringResource(R.string.skill_telephone_not_calling)
            } else {
                stringResource(R.string.skill_telephone_calling, number)
            }
        )
    }
}
