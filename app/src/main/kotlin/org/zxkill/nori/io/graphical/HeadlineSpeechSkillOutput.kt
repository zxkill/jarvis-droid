package org.zxkill.nori.io.graphical

import androidx.compose.runtime.Composable
import org.nori.skill.context.SkillContext
import org.nori.skill.skill.SkillOutput

/**
 * A [SkillOutput] where the graphical output is just a headline text with the speech output.
 */
interface HeadlineSpeechSkillOutput : SkillOutput {
    @Composable
    override fun GraphicalOutput(ctx: SkillContext) {
        Headline(text = getSpeechOutput(ctx))
    }
}
