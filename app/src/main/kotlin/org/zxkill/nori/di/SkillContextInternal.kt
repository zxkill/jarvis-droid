package org.zxkill.nori.di

import org.nori.skill.context.SkillContext
import org.nori.skill.skill.SkillOutput

interface SkillContextInternal : SkillContext {
    // allows modifying this value
    override var previousOutput: SkillOutput?
}