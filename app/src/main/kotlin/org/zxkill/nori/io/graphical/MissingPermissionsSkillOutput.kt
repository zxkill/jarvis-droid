package org.zxkill.nori.io.graphical

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.nori.skill.context.SkillContext
import org.nori.skill.skill.SkillInfo
import org.nori.skill.skill.SkillOutput
import org.zxkill.nori.R
import org.zxkill.nori.settings.SkillSettingsItemPermissionLine
import org.zxkill.nori.util.commaJoinPermissions
import org.zxkill.nori.util.getString

class MissingPermissionsSkillOutput(
    private val skill: SkillInfo
) : SkillOutput {
    override fun getSpeechOutput(ctx: SkillContext): String =
        ctx.getString(
            R.string.eval_missing_permissions,
            skill.name(ctx.android),
            commaJoinPermissions(ctx.android, skill.neededPermissions)
        )

    @Composable
    override fun GraphicalOutput(ctx: SkillContext) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = skill.icon(),
                contentDescription = null,
                modifier = Modifier
                    .padding(8.dp)
                    .size(40.dp)
            )

            SkillSettingsItemPermissionLine(
                skill = skill,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
