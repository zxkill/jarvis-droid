package org.zxkill.nori.ui.home

import org.nori.skill.skill.SkillInfo
import org.nori.skill.skill.SkillOutput

data class QuestionAnswer(
    val question: String?,
    val answer: SkillOutput,
)

data class Interaction(
    val skill: SkillInfo?,
    val questionsAnswers: List<QuestionAnswer>
)

data class PendingQuestion(
    val userInput: String,
    val continuesLastInteraction: Boolean,
    val skillBeingEvaluated: SkillInfo?,
)

data class InteractionLog(
    val interactions: List<Interaction>,
    val pendingQuestion: PendingQuestion?,
)
