package org.stypox.dicio.skills.face_tracker

import org.dicio.skill.context.SkillContext
import org.dicio.skill.recognizer.FuzzyRecognizerSkill
import org.dicio.skill.recognizer.FuzzyRecognizerSkill.Pattern
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.skill.Specificity
import org.stypox.dicio.R
import org.stypox.dicio.io.graphical.HeadlineSpeechSkillOutput

/**
 * Скилл, который по голосовой команде запускает или останавливает
 * вывод слежения за лицом.
 */
class FaceTrackerSkill(
    correspondingSkillInfo: SkillInfo,
) : FuzzyRecognizerSkill<FaceTrackerSkill.Command>(correspondingSkillInfo, Specificity.LOW) {

    /** Возможные команды для данного скилла. */
    sealed class Command {
        object Start : Command()
        object Stop : Command()
    }

    // Набор команд, которые распознаёт данный скилл. Тип указан явно, чтобы
    // избежать выведения Pattern<*> при смешении разных подклассов Command.
    override val patterns: List<Pattern<Command>> = listOf(
        Pattern(
            example = "запусти трекинг лица",
            regex = Regex("^(?:запусти|включи)\\s+(?:трек.*лица|слежение лица)$"),
            builder = { Command.Start }
        ),
        Pattern(
            example = "останови трекинг лица",
            regex = Regex("^(?:останови|выключи)\\s+(?:трек.*лица|слежение лица)$"),
            builder = { Command.Stop }
        ),
    )

    override suspend fun generateOutput(ctx: SkillContext, inputData: Command?): SkillOutput {
        return when (requireNotNull(inputData)) {
            // Команда «включи слежение» – показываем постоянный вывод с камеры
            Command.Start -> FaceTrackerOutput()
            // Команда «выключи слежение» – возвращаем только голосовое сообщение
            Command.Stop -> object : HeadlineSpeechSkillOutput {
                override fun getSpeechOutput(ctx: SkillContext) =
                    ctx.android.getString(R.string.skill_face_tracking_disabled)
            }
        }
    }
}
