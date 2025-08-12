package org.nori.skill.skill

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import org.nori.skill.MockSkillInfo

class SkillInfoTest : StringSpec({
    "get needed permissions" {
        val skillInfo: SkillInfo = MockSkillInfo

        withClue("Needed permissions should be empty by default") {
            skillInfo.neededPermissions.shouldBeEmpty()
        }
    }
})
