package org.stypox.dicio.util

import org.dicio.skill.context.SkillContext
import org.dicio.skill.recognizer.FuzzyRecognizerSkill
import org.dicio.skill.recognizer.FuzzyRecognizerSkill.Pattern
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
        // Согласие: перечисляем несколько распространённых вариантов
        Pattern(example = "да", builder = { _ -> true }),
        Pattern(example = "ага", builder = { _ -> true }),
        Pattern(example = "конечно", builder = { _ -> true }),
        Pattern(example = "yes", builder = { _ -> true }),

        // Отказ
        Pattern(example = "нет", builder = { _ -> false }),
        Pattern(example = "неа", builder = { _ -> false }),
        Pattern(example = "no", builder = { _ -> false }),
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
