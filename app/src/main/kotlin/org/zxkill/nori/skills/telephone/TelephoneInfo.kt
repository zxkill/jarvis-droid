package org.zxkill.nori.skills.telephone

import android.Manifest
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.fragment.app.Fragment
import org.nori.skill.skill.Skill
import org.nori.skill.context.SkillContext
import org.nori.skill.skill.Permission
import org.nori.skill.skill.SkillInfo
import org.zxkill.nori.R
import org.zxkill.nori.util.PERMISSION_CALL_PHONE
import org.zxkill.nori.util.PERMISSION_READ_CONTACTS

object TelephoneInfo : SkillInfo("telephone") {
    override fun name(context: Context) =
        context.getString(R.string.skill_name_telephone)

    override fun sentenceExample(context: Context) =
        context.getString(R.string.skill_sentence_example_telephone)

    @Composable
    override fun icon() =
        rememberVectorPainter(Icons.Default.Call)

    override val neededPermissions: List<Permission>
            = listOf(PERMISSION_READ_CONTACTS, PERMISSION_CALL_PHONE)

    override fun isAvailable(ctx: SkillContext): Boolean = true

    override fun build(ctx: SkillContext): Skill<*> {
        return TelephoneSkill(this)
    }
}
