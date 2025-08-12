package org.zxkill.nori.skills.open

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.nori.skill.context.SkillContext
import org.nori.skill.skill.Skill
import org.nori.skill.skill.SkillInfo
import org.zxkill.nori.R

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
