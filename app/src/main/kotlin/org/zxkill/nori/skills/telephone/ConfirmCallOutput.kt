package org.zxkill.nori.skills.telephone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.nori.skill.context.SkillContext
import org.nori.skill.skill.InteractionPlan
import org.nori.skill.skill.SkillOutput
import org.zxkill.nori.R
import org.zxkill.nori.io.graphical.Body
import org.zxkill.nori.io.graphical.Headline
import org.zxkill.nori.util.RecognizeYesNoSkill
import org.zxkill.nori.util.getString

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
