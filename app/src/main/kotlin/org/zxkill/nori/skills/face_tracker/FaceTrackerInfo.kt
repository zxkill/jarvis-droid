package org.zxkill.nori.skills.face_tracker

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.nori.skill.context.SkillContext
import org.nori.skill.skill.Skill
import org.nori.skill.skill.SkillInfo
import org.zxkill.nori.R
import org.zxkill.nori.util.PERMISSION_CAMERA

/**
 * Информация о скилле слежения за лицом.
 */
object FaceTrackerInfo : SkillInfo("face_tracker") {
    override fun name(context: Context) =
        context.getString(R.string.skill_name_face_tracker)

    override fun sentenceExample(context: Context) =
        context.getString(R.string.skill_sentence_example_face_tracker)

    @Composable
    override fun icon() = rememberVectorPainter(Icons.Filled.Face)

    // Скиллу требуется доступ к камере устройства
    override val neededPermissions = listOf(PERMISSION_CAMERA)

    override fun isAvailable(ctx: SkillContext) = true

    override fun build(ctx: SkillContext): Skill<*> {
        return FaceTrackerSkill(this)
    }
}
