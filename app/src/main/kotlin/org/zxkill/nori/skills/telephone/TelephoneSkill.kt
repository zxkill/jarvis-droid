package org.zxkill.nori.skills.telephone

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.nori.skill.context.SkillContext
import org.nori.skill.recognizer.FuzzyRecognizerSkill
import org.nori.skill.recognizer.FuzzyRecognizerSkill.Pattern
import org.nori.skill.skill.SkillInfo
import org.nori.skill.skill.SkillOutput
import org.nori.skill.skill.Specificity

/** Скилл совершения телефонных звонков по имени контакта. */
class TelephoneSkill(correspondingSkillInfo: SkillInfo) :
    FuzzyRecognizerSkill<String>(correspondingSkillInfo, Specificity.LOW) {

    override val patterns = listOf(
        Pattern(
            examples = listOf(
                "позвони маме",
                "набер маму",
                "сделай звонок маме"
            ),
            regex = Regex("(?:позвони|набер[и]?|позвонить|сделай звонок)\\s+(?<who>.+)"),
            builder = { match -> match!!.groups["who"]!!.value }
        )
    )

    override suspend fun generateOutput(ctx: SkillContext, inputData: String?): SkillOutput {
        val userContactName = inputData?.trim { it <= ' ' } ?: ""
        val contentResolver = ctx.android.contentResolver
        val contacts = Contact.getFilteredSortedContacts(contentResolver, userContactName)
        val validContacts = ArrayList<Pair<String, List<String>>>()

        var i = 0
        while (validContacts.size < 5 && i < contacts.size) {
            val contact = contacts[i]
            val numbers = contact.getNumbers(contentResolver)
            if (numbers.isEmpty()) {
                ++i
                continue
            }
            if (validContacts.isEmpty()
                && contact.distance < 3
                && numbers.size == 1
                && (contacts.size <= i + 1
                        || contacts[i + 1].distance - 2 > contact.distance)
            ) {
                return ConfirmCallOutput(contact.name, numbers[0])
            }
            validContacts.add(Pair(contact.name, numbers))
            ++i
        }

        if (validContacts.size == 1
            && (validContacts[0].second.size == 1 || ctx.parserFormatter == null)
        ) {
            val contact = validContacts[0]
            return ConfirmCallOutput(contact.first, contact.second[0])
        }

        return TelephoneOutput(validContacts)
    }

    companion object {
        fun call(context: Context, number: String?) {
            val callIntent = Intent(Intent.ACTION_CALL)
            callIntent.data = Uri.parse("tel:$number")
            callIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(callIntent)
        }
    }
}
