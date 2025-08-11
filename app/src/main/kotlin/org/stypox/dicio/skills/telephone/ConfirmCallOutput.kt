package org.stypox.dicio.skills.telephone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.InteractionPlan
import org.dicio.skill.skill.SkillOutput
import org.stypox.dicio.R
import org.stypox.dicio.io.graphical.Body
import org.stypox.dicio.io.graphical.Headline
import org.stypox.dicio.util.RecognizeYesNoSkill
import org.stypox.dicio.util.getString

class ConfirmCallOutput(
    private val name: String,
    private val number: String
) : SkillOutput {
    override fun getSpeechOutput(ctx: SkillContext): String =
        ctx.getString(R.string.skill_telephone_confirm_call, name)

    override fun getInteractionPlan(ctx: SkillContext): InteractionPlan {
        // Вспомогательный скилл подтверждения звонка, который ждёт ответ «да» или «нет»
        val confirmYesNoSkill = object : RecognizeYesNoSkill(TelephoneInfo) {
            override suspend fun onAnswer(
                ctx: SkillContext,
                inputData: Boolean
            ): SkillOutput {
                return if (inputData) {
                    // Пользователь согласился — совершаем звонок
                    TelephoneSkill.call(ctx.android, number)
                    ConfirmedCallOutput(number)
                } else {
                    // Пользователь отказался или ответ не распознан
                    ConfirmedCallOutput(null)
                }
            }
        }

        return InteractionPlan.ReplaceSubInteraction(
            reopenMicrophone = true,
            nextSkills = listOf(confirmYesNoSkill),
        )
    }

    @Composable
    override fun GraphicalOutput(ctx: SkillContext) {
        Column {
            Headline(text = getSpeechOutput(ctx))
            Spacer(modifier = Modifier.height(4.dp))
            Body(text = number)
        }
    }
}
