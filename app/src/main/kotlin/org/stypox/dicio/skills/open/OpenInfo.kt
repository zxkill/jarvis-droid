package org.stypox.dicio.skills.open

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import org.stypox.dicio.R

/**
 * Информация о скилле открытия приложений.
 */
object OpenInfo : SkillInfo("open") {
    override fun name(context: Context) =
        context.getString(R.string.skill_name_open)

    override fun sentenceExample(context: Context) =
        context.getString(R.string.skill_sentence_example_open)

    @Composable
    override fun icon() =
        rememberVectorPainter(Icons.AutoMirrored.Filled.OpenInNew)

    override fun isAvailable(ctx: SkillContext) = true

    override fun build(ctx: SkillContext): Skill<*> {
        return OpenSkill(this)
    }
}
