package org.zxkill.nori.skills.current_date

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.nori.skill.context.SkillContext
import org.nori.skill.skill.Skill
import org.nori.skill.skill.SkillInfo
import org.zxkill.nori.R
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

