package org.zxkill.nori.skills.navigation

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.fragment.app.Fragment
import org.nori.skill.skill.Skill
import org.nori.skill.context.SkillContext
import org.nori.skill.skill.SkillInfo
import org.zxkill.nori.R
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
