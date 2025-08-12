package org.zxkill.nori.util

import org.nori.skill.context.SkillContext
import org.nori.skill.recognizer.FuzzyRecognizerSkill
import org.nori.skill.recognizer.FuzzyRecognizerSkill.Pattern
import org.nori.skill.skill.SkillInfo
import org.nori.skill.skill.SkillOutput
import org.nori.skill.skill.Specificity

/**
 * Базовый класс для распознавания ответов «да» или «нет».
 * Используется в диалогах подтверждения.
 */
abstract class RecognizeYesNoSkill(correspondingSkillInfo: SkillInfo) :
    FuzzyRecognizerSkill<Boolean>(correspondingSkillInfo, Specificity.LOW) {

    override val patterns = listOf(
        // Согласие: перечисляем несколько распространённых вариантов
        Pattern(
            examples = listOf("да", "ага", "конечно", "yes"),
            builder = { _ -> true }
        ),

        // Отказ
        Pattern(
            examples = listOf("нет", "неа", "no"),
            builder = { _ -> false }
        ),
    )

    /**
     * Метод базового класса [FuzzyRecognizerSkill] требует обработку nullable-значения.
     * Здесь мы преобразуем его в `Boolean`, считая отсутствие распознанного ответа
     * отказом пользователя.
     */
    final override suspend fun generateOutput(ctx: SkillContext, inputData: Boolean?): SkillOutput {
        val answer = inputData == true
        return onAnswer(ctx, answer)
    }

    /**
     * Реакция на однозначный ответ пользователя.
     * @param inputData `true` при подтверждении и `false` при отказе
     */
    protected abstract suspend fun onAnswer(ctx: SkillContext, inputData: Boolean): SkillOutput
}
