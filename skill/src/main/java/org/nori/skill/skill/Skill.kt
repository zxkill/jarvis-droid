package org.nori.skill.skill

import org.nori.skill.context.SkillContext

/**
 * Скилл — это компонент, который оценивает пользовательский ввод и
 * формирует выходные данные.
 *
 * @param InputData тип данных, извлекаемых из ввода в методе [score] и
 * передаваемых затем в [generateOutput]
 * @param correspondingSkillInfo объект [SkillInfo], которому принадлежит скилл
 * @param specificity уровень специфичности распознавания входа, см. [Specificity]
 */
abstract class Skill<InputData>(
    val correspondingSkillInfo: SkillInfo,
    val specificity: Specificity,
) {

    /**
     * Сопоставляет пользовательский текст и вычисляет объект [Score], а также
     * извлекает необходимые данные из ввода.
     *
     * @param ctx [SkillContext] для доступа к ресурсам, настройкам и окружению
     * @param input исходная строка, полученная от пользователя
     * @return пару из [Score] и извлечённых данных, которые могут понадобиться
     *         методу [generateOutput]
     */
    abstract fun score(
        ctx: SkillContext,
        input: String,
    ): Pair<Score, InputData>

    /**
     * Вызывается, если данный скилл признан лучшим кандидатом для обработки
     * пользовательского запроса. Метод получает данные, ранее возвращённые
     * [score], и формирует выходной объект [SkillOutput]. Выполняется в
     * фоновом потоке, поэтому при необходимости взаимодействия с UI следует
     * использовать корутины или другие механизмы переключения контекста.
     *
     * @param ctx [SkillContext] для доступа к ресурсам, настройкам и окружению
     * @param inputData данные, извлечённые на этапе [score]
     */
    abstract suspend fun generateOutput(
        ctx: SkillContext,
        inputData: InputData,
    ): SkillOutput
}
