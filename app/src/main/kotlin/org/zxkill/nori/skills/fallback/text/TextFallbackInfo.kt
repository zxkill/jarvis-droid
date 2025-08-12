package org.zxkill.nori.skills.fallback.text

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.nori.skill.skill.Skill
import org.nori.skill.context.SkillContext
import org.nori.skill.skill.SkillInfo
import org.zxkill.nori.R

object TextFallbackInfo : SkillInfo("text") {
    override fun name(context: Context) =
        context.getString(R.string.skill_fallback_name_text)

    override fun sentenceExample(context: Context) =
        ""

    @Composable
    override fun icon() =
        rememberVectorPainter(Icons.Default.Warning)

    override fun isAvailable(ctx: SkillContext): Boolean {
        return true
    }

    override fun build(ctx: SkillContext): Skill<*> {
        return TextFallbackSkill(TextFallbackInfo)
    }
}
