package org.stypox.dicio.skills.telephone

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.dicio.skill.standard.StandardRecognizerSkill
import org.stypox.dicio.sentences.Sentences.Telephone

/** Скилл совершения телефонных звонков по имени контакта. */
class TelephoneSkill(correspondingSkillInfo: SkillInfo, data: StandardRecognizerData<Telephone>) :
    StandardRecognizerSkill<Telephone>(correspondingSkillInfo, data) {

    override suspend fun generateOutput(ctx: SkillContext, inputData: Telephone): SkillOutput {
        val contentResolver = ctx.android.contentResolver
        val userContactName = when (inputData) {
            is Telephone.Dial -> inputData.who?.trim { it <= ' ' } ?: ""
        }
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
                && numbers.size == 1 // у контакта только один номер
                && (contacts.size <= i + 1 // следующий контакт существенно менее похож
                        || contacts[i + 1].distance - 2 > contact.distance)
            ) {
                // Очень близкое совпадение — звоним напрямую
                return ConfirmCallOutput(contact.name, numbers[0])
            }
            validContacts.add(Pair(contact.name, numbers))
            ++i
        }

        if (validContacts.size == 1
            && (validContacts[0].second.size == 1 || ctx.parserFormatter == null)
        ) {
            // Остался единственный кандидат: звоним на его номер
            val contact = validContacts[0]
            return ConfirmCallOutput(contact.first, contact.second[0])
        }

        // Если однозначного контакта нет, возвращаем список для выбора пользователем
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
