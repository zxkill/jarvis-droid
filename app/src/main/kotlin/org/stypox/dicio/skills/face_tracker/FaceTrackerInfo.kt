package org.stypox.dicio.skills.face_tracker

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import org.stypox.dicio.R
import org.stypox.dicio.sentences.Sentences
import org.stypox.dicio.util.PERMISSION_CAMERA

/**
 * Информация о скилле слежения за лицом: здесь задаются идентификатор,
 * локализованное название, пример фразы для подсказки и требуемые разрешения.
 * Скилл использует ML Kit для детекции лица и при его отсутствии
 * пытается определить положение головы по ключевым точкам позы.
 */
object FaceTrackerInfo : SkillInfo("face_tracker") {
    // Название скилла, отображаемое пользователю
    override fun name(context: Context) =
        context.getString(R.string.skill_name_face_tracker)

    // Пример голосовой команды, которую можно произнести
    override fun sentenceExample(context: Context) =
        context.getString(R.string.skill_sentence_example_face_tracker)

    // Иконка скилла для интерфейса
    @Composable
    override fun icon() = rememberVectorPainter(Icons.Filled.Face)

    // Скилл требует доступа к камере
    override val neededPermissions = listOf(PERMISSION_CAMERA)

    override fun isAvailable(ctx: SkillContext): Boolean {
        // Скилл доступен только если для текущего языка есть фразы в Sentences
        return Sentences.FaceTracker[ctx.sentencesLanguage] != null
    }

    override fun build(ctx: SkillContext): Skill<*> {
        // Получаем данные распознавателя для текущего языка и создаём скилл
        val data = Sentences.FaceTracker[ctx.sentencesLanguage]!!
        return FaceTrackerSkill(this, data)
    }
}
