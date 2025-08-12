package org.zxkill.nori.skills.lyrics

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.nori.skill.context.SkillContext
import org.nori.skill.skill.Skill
import org.nori.skill.skill.SkillInfo
import org.zxkill.nori.R
object LyricsInfo : SkillInfo("lyrics") {
    override fun name(context: Context) =
        context.getString(R.string.skill_name_lyrics)

    override fun sentenceExample(context: Context) =
        context.getString(R.string.skill_sentence_example_lyrics)

    @Composable
    override fun icon() =
        rememberVectorPainter(Icons.Default.MusicNote)

    override fun isAvailable(ctx: SkillContext): Boolean = true

    override fun build(ctx: SkillContext): Skill<*> {
        return LyricsSkill(this)
    }
}
