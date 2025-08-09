package org.stypox.dicio.skills.face_tracker

import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.dicio.skill.standard.StandardRecognizerSkill
import org.stypox.dicio.io.graphical.HeadlineSpeechSkillOutput
import org.stypox.dicio.R
import org.stypox.dicio.sentences.Sentences.FaceTracker

/**
 * Скилл, который по голосовой команде запускает или останавливает
 * вывод слежения за лицом.
 */
class FaceTrackerSkill(
    correspondingSkillInfo: SkillInfo,
    data: StandardRecognizerData<FaceTracker>,
) : StandardRecognizerSkill<FaceTracker>(correspondingSkillInfo, data) {

    override suspend fun generateOutput(ctx: SkillContext, inputData: FaceTracker): SkillOutput {
        return when (inputData) {
            // Команда «включи слежение» – показываем постоянный вывод с камеры
            is FaceTracker.Start -> FaceTrackerOutput()
            // Команда «выключи слежение» – возвращаем только голосовое сообщение
            is FaceTracker.Stop -> object : HeadlineSpeechSkillOutput {
                override fun getSpeechOutput(ctx: SkillContext) =
                    ctx.android.getString(R.string.skill_face_tracking_disabled)
            }
        }
    }
}

