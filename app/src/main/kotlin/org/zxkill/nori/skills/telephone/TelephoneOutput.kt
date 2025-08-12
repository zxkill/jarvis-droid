package org.zxkill.nori.skills.telephone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.nori.skill.skill.Skill
import org.nori.skill.context.SkillContext
import org.nori.skill.skill.InteractionPlan
import org.nori.skill.skill.SkillOutput
import org.zxkill.nori.R
import org.zxkill.nori.io.graphical.Headline
import org.zxkill.nori.util.getString

class TelephoneOutput(
    private val contacts: List<Pair<String, List<String>>>,
) : SkillOutput {
    override fun getSpeechOutput(ctx: SkillContext): String = if (contacts.isEmpty()) {
        ctx.getString(R.string.skill_telephone_unknown_contact)
    } else {
        ctx.getString(R.string.skill_telephone_found_contacts, contacts.size)
    }

    override fun getInteractionPlan(ctx: SkillContext): InteractionPlan {
        val nextSkills = mutableListOf<Skill<*>>(
            ContactChooserName(
                // when saying the name, there is no way to distinguish between
                // different numbers, so just use the first one
                contacts.map { Pair(it.first, it.second[0]) }
            )
        )

        if (ctx.parserFormatter != null) {
            nextSkills.add(
                ContactChooserIndex(
                    contacts.flatMap { contact ->
                        contact.second.map { number ->
                            Pair(contact.first, number)
                        }
                    }
                )
            )
        }

        return InteractionPlan.StartSubInteraction(
            reopenMicrophone = true,
            nextSkills = nextSkills,
        )
    }

    @Composable
    override fun GraphicalOutput(ctx: SkillContext) {
        if (contacts.isEmpty()) {
            Headline(text = getSpeechOutput(ctx))
        } else {
            Column {
                for (i in contacts.indices) {
                    if (i != 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Text(
                        text = contacts[i].first,
                        style = MaterialTheme.typography.titleMedium,
                    )

                    for (number in contacts[i].second) {
                        Text(
                            text = number,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
