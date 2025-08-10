package org.stypox.dicio.util

import org.dicio.skill.context.SkillContext
import org.dicio.skill.recognizer.FuzzyRecognizerSkill
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.skill.Specificity

/**
 * Базовый класс для распознавания ответов «да» или «нет».
 * Используется в диалогах подтверждения.
 */
abstract class RecognizeYesNoSkill(correspondingSkillInfo: SkillInfo) :
    FuzzyRecognizerSkill<Boolean>(correspondingSkillInfo, Specificity.LOW) {

    override val patterns = listOf(
        // Несколько вариантов согласия
        Pattern(
            example = "да",
            regex = Regex("^(?:да|ага|конечно|yes)$"),
            builder = { true }
        ),
        // И варианты отказа
        Pattern(
            example = "нет",
            regex = Regex("^(?:нет|неа|no)$"),
            builder = { false }
        )
    )

    abstract override suspend fun generateOutput(ctx: SkillContext, inputData: Boolean): SkillOutput
}
