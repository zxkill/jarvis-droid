package org.stypox.dicio.skills.current_time

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Watch
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import org.stypox.dicio.R
object CurrentTimeInfo : SkillInfo("current_time") {
    override fun name(context: Context) =
        context.getString(R.string.skill_name_current_time)

    override fun sentenceExample(context: Context) =
        context.getString(R.string.skill_sentence_example_current_time)

    @Composable
    override fun icon() =
        rememberVectorPainter(Icons.Default.Watch)

    override fun isAvailable(ctx: SkillContext): Boolean = true

    override fun build(ctx: SkillContext): Skill<*> {
        return CurrentTimeSkill(this)
    }
}
