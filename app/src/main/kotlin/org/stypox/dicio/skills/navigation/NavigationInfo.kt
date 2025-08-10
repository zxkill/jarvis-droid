package org.stypox.dicio.skills.navigation

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.fragment.app.Fragment
import org.dicio.skill.skill.Skill
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.stypox.dicio.R
object NavigationInfo : SkillInfo("navigation") {
    override fun name(context: Context) =
        context.getString(R.string.skill_name_navigation)

    override fun sentenceExample(context: Context) =
        context.getString(R.string.skill_sentence_example_navigation)

    @Composable
    override fun icon() =
        rememberVectorPainter(Icons.Default.Directions)

    override fun isAvailable(ctx: SkillContext): Boolean = true

    override fun build(ctx: SkillContext): Skill<*> {
        return NavigationSkill(this)
    }
}
