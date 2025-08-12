package org.zxkill.nori.util

import org.nori.skill.context.SkillContext
import org.nori.skill.skill.AlwaysBestScore
import org.nori.skill.skill.Score
import org.nori.skill.skill.Skill
import org.nori.skill.skill.SkillInfo
import org.nori.skill.skill.Specificity

abstract class RecognizeEverythingSkill(correspondingSkillInfo: SkillInfo) :
    Skill<String>(correspondingSkillInfo, Specificity.LOW) {
    override fun score(
        ctx: SkillContext,
        input: String
    ): Pair<Score, String> {
        return Pair(AlwaysBestScore, input)
    }
}
