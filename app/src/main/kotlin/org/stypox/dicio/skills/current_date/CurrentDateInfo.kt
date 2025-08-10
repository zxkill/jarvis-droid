package org.stypox.dicio.skills.current_date

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import org.stypox.dicio.R
object CurrentDateInfo : SkillInfo("current_date") {
    override fun name(context: Context) =
        context.getString(R.string.skill_name_current_date)

    override fun sentenceExample(context: Context) =
        context.getString(R.string.skill_sentence_example_current_date)

    @Composable
    override fun icon() =
        rememberVectorPainter(Icons.Default.Event)

    override fun isAvailable(ctx: SkillContext): Boolean = true

    override fun build(ctx: SkillContext): Skill<*> {
        return CurrentDateSkill(this)
    }
}

