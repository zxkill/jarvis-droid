package org.zxkill.nori.settings

import android.app.Application
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.zxkill.nori.di.WakeDeviceWrapper
import org.zxkill.nori.io.wake.oww.OpenWakeWordDevice
import org.zxkill.nori.settings.datastore.InputDevice
import org.zxkill.nori.settings.datastore.Language
import org.zxkill.nori.settings.datastore.SpeechOutputDevice
import org.zxkill.nori.settings.datastore.SttPlaySound
import org.zxkill.nori.settings.datastore.Theme
import org.zxkill.nori.settings.datastore.UserSettings
import org.zxkill.nori.settings.datastore.WakeDevice
import org.zxkill.nori.util.toStateFlowDistinctBlockingFirst
import javax.inject.Inject

@HiltViewModel
class MainSettingsViewModel @Inject constructor(
    application: Application,
    private val wakeDeviceWrapper: WakeDeviceWrapper?,
    private val dataStore: DataStore<UserSettings>
) : AndroidViewModel(application) {
    // run blocking because the settings screen cannot start if settings have not been loaded yet
    val settingsState = dataStore.data
        .toStateFlowDistinctBlockingFirst(viewModelScope)

    private fun updateData(transform: (UserSettings.Builder) -> Unit) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .apply(transform)
                    .build()
            }
        }
    }

    val isHeyNori: StateFlow<Boolean> = wakeDeviceWrapper?.isHeyNori ?: MutableStateFlow(true)

    fun addOwwUserWakeFile(uri: Uri) {
        viewModelScope.launch {
            OpenWakeWordDevice.addUserWakeFile(getApplication(), uri)
            wakeDeviceWrapper?.reinitializeToReleaseResources()
        }
    }

    fun removeOwwUserWakeFile() {
        viewModelScope.launch {
            OpenWakeWordDevice.removeUserWakeFile(getApplication())
            wakeDeviceWrapper?.reinitializeToReleaseResources()
        }
    }

    fun setLanguage(value: Language) =
        updateData { it.setLanguage(value) }
    fun setTheme(value: Theme) =
        updateData { it.setTheme(value) }
    fun setDynamicColors(value: Boolean) =
        updateData { it.setDynamicColors(value) }
    fun setInputDevice(value: InputDevice) =
        updateData { it.setInputDevice(value) }
    fun setWakeDevice(value: WakeDevice) =
        updateData { it.setWakeDevice(value) }
    fun setSpeechOutputDevice(value: SpeechOutputDevice) =
        updateData { it.setSpeechOutputDevice(value) }
    fun setSttPlaySound(value: SttPlaySound) =
        updateData { it.setSttPlaySound(value) }
    // Устанавливаем время отображения результата скилла
    fun setSkillOutputDisplaySeconds(value: Int) =
        updateData { it.setSkillOutputDisplaySeconds(value) }
    fun setAutoFinishSttPopup(value: Boolean) =
        updateData { it.setAutoFinishSttPopup(value) }
}
