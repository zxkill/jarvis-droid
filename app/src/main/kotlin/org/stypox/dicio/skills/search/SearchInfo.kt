package org.stypox.dicio.skills.search

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import org.stypox.dicio.R

/** Информация о поисковом скилле. */
object SearchInfo : SkillInfo("search") {
    override fun name(context: Context) =
        context.getString(R.string.skill_name_search)

    override fun sentenceExample(context: Context) =
        context.getString(R.string.skill_sentence_example_search)

    @Composable
    override fun icon() =
        rememberVectorPainter(Icons.Default.Search)

    override fun isAvailable(ctx: SkillContext): Boolean = true

    override fun build(ctx: SkillContext): Skill<*> {
        return SearchSkill(this)
    }
}
