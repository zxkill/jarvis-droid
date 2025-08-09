package org.stypox.dicio.io.graphical

import org.dicio.skill.skill.SkillOutput

/**
 * Маркерный интерфейс для [SkillOutput], которые должны оставаться на экране
 * бесконечно и не скрываться автоматически через таймаут. Такой вывод
 * заменяется только явным показом другого [SkillOutput].
 */
interface PersistentSkillOutput : SkillOutput
