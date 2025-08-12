package org.zxkill.nori.skills.telephone

import org.nori.numbers.unit.Number
import org.nori.skill.context.SkillContext
import org.nori.skill.skill.AlwaysBestScore
import org.nori.skill.skill.AlwaysWorstScore
import org.nori.skill.skill.Score
import org.nori.skill.skill.Skill
import org.nori.skill.skill.SkillOutput
import org.nori.skill.skill.Specificity

class ContactChooserIndex internal constructor(private val contacts: List<Pair<String, String>>) :
    Skill<Int>(TelephoneInfo, Specificity.HIGH) {

    override fun score(
        ctx: SkillContext,
        input: String
    ): Pair<Score, Int> {
        val index = ctx.parserFormatter!!
            .extractNumber(input)
            .preferOrdinal(true)
            .mixedWithText
            .asSequence()
            .filter { obj -> (obj as? Number)?.isInteger == true }
            .map { obj -> (obj as Number).integerValue().toInt() }
            .firstOrNull() ?: 0
        return Pair(
            if (index <= 0 || index > contacts.size) AlwaysWorstScore else AlwaysBestScore,
            index
        )
    }

    override suspend fun generateOutput(ctx: SkillContext, inputData: Int): SkillOutput {
        if (inputData > 0 && inputData <= contacts.size) {
            val contact = contacts[inputData - 1]
            return ConfirmCallOutput(contact.first, contact.second)
        } else {
            // impossible situation
            return ConfirmedCallOutput(null)
        }
    }
}
