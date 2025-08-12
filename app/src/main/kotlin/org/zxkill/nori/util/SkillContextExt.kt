package org.zxkill.nori.util

import androidx.annotation.StringRes
import org.nori.skill.context.SkillContext

fun SkillContext.getString(@StringRes resId: Int): String {
    return this.android.getString(resId)
}

fun SkillContext.getString(@StringRes resId: Int, vararg formatArgs: Any?): String {
    return this.android.getString(resId, *formatArgs)
}
