package org.zxkill.nori.settings

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.nori.skill.context.SkillContext
import org.nori.skill.skill.SkillInfo
import org.zxkill.nori.di.SkillContextInternal
import org.zxkill.nori.settings.datastore.UserSettings
import org.zxkill.nori.eval.SkillHandler
import org.zxkill.nori.util.toStateFlowDistinctBlockingFirst
import javax.inject.Inject


@HiltViewModel
class SkillSettingsViewModel @Inject constructor(
    application: Application,
    private val dataStore: DataStore<UserSettings>,
    val skillContext: SkillContextInternal,
    private val skillHandler: SkillHandler,
) : AndroidViewModel(application) {

    val skills: List<SkillInfo> get() = skillHandler.allSkillInfoList

    // run blocking because the settings screen cannot start if settings have not been loaded yet
    val enabledSkills = dataStore.data
        .map { it.enabledSkillsMap }
        .toStateFlowDistinctBlockingFirst(viewModelScope)

    fun setSkillEnabled(id: String, state: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .putEnabledSkills(id, state)
                    .build()
            }
        }
    }
}
