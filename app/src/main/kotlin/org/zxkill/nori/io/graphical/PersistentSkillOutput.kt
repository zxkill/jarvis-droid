package org.zxkill.nori.io.graphical

import org.nori.skill.skill.SkillOutput

/**
 * Маркерный интерфейс для [SkillOutput], которые должны оставаться на экране
 * бесконечно и не скрываться автоматически через таймаут. Такой вывод
 * заменяется только явным показом другого [SkillOutput].
 */
interface PersistentSkillOutput : SkillOutput
