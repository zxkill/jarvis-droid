package org.zxkill.nori.skills.telephone

import org.nori.skill.context.SkillContext
import org.nori.skill.skill.AlwaysBestScore
import org.nori.skill.skill.AlwaysWorstScore
import org.nori.skill.skill.Score
import org.nori.skill.skill.Skill
import org.nori.skill.skill.SkillOutput
import org.nori.skill.skill.Specificity
import org.zxkill.nori.util.StringUtils

class ContactChooserName internal constructor(private val contacts: List<Pair<String, String>>) :
    // use a low specificity to prefer the index-based contact chooser
    Skill<Pair<String, String>?>(TelephoneInfo, Specificity.LOW) {

    override fun score(
        ctx: SkillContext,
        input: String
    ): Pair<Score, Pair<String, String>?> {
        val trimmedInput = input.trim { it <= ' ' }

        val bestContact = contacts
            .map { nameNumberPair ->
                Pair(
                    nameNumberPair,
                    StringUtils.contactStringDistance(trimmedInput, nameNumberPair.first)
                )
            }
            .filter { pair -> pair.second < -7 }
            .minByOrNull { a -> a.second }
            ?.first

        return Pair(
            if (bestContact == null) AlwaysWorstScore else AlwaysBestScore,
            bestContact
        )
    }

    override suspend fun generateOutput(ctx: SkillContext, inputData: Pair<String, String>?): SkillOutput {
        return inputData?.let {
            ConfirmCallOutput(it.first, it.second)
        }
            // impossible situation
            ?: ConfirmedCallOutput(null)
    }
}
