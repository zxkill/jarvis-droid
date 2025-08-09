package org.dicio.skill.skill

import org.dicio.skill.context.SkillContext

/**
 * Interface for skills that can provide output automatically without a user query.
 * Implementations can specify how frequently the information should be refreshed
 * and return the [SkillOutput] that should be displayed.
 */
interface AutoRunnable {
    /** How often this skill should update the output, in milliseconds. */
    val autoUpdateIntervalMillis: Long

    /** Generate output without any user interaction. */
    suspend fun autoOutput(ctx: SkillContext): SkillOutput
}

