package org.zxkill.nori.settings

import android.app.Application
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material3.OutlinedTextField
import androidx.hilt.navigation.compose.hiltViewModel
import org.zxkill.nori.R
import org.zxkill.nori.settings.datastore.InputDevice
import org.zxkill.nori.settings.datastore.Language
import org.zxkill.nori.settings.datastore.SpeechOutputDevice
import org.zxkill.nori.settings.datastore.SttPlaySound
import org.zxkill.nori.settings.datastore.Theme
import org.zxkill.nori.settings.datastore.UserSettingsModule.Companion.newDataStoreForPreviews
import org.zxkill.nori.settings.datastore.WakeDevice
import org.zxkill.nori.settings.ui.SettingsCategoryTitle
import org.zxkill.nori.settings.ui.SettingsItem
import org.zxkill.nori.ui.theme.AppTheme


@Composable
fun MainSettingsScreen(
    navigationIcon: @Composable () -> Unit,
    navigateToSkillSettings: () -> Unit,
    viewModel: MainSettingsViewModel = hiltViewModel(),
) {
    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = navigationIcon
            )
        }
    ) {
        MainSettingsScreen(
            navigateToSkillSettings = navigateToSkillSettings,
            viewModel = viewModel,
            modifier = Modifier.padding(it),
        )
    }
}

@Composable
private fun MainSettingsScreen(
    navigateToSkillSettings: () -> Unit,
    viewModel: MainSettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settingsState.collectAsState()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) {
            viewModel.addOwwUserWakeFile(it)
        }
    }

    LazyColumn(modifier) {
        /* GENERAL SETTINGS */
        item { SettingsCategoryTitle(stringResource(R.string.pref_general), topPadding = 4.dp) }
        item {
            languageSetting().Render(
                when (val language = settings.language) {
                    Language.UNRECOGNIZED -> Language.LANGUAGE_SYSTEM
                    else -> language
                },
                viewModel::setLanguage,
            )
        }
        item {
            themeSetting().Render(
                when (val theme = settings.theme) {
                    Theme.UNRECOGNIZED -> Theme.THEME_SYSTEM
                    else -> theme
                },
                viewModel::setTheme,
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            item {
                dynamicColors().Render(
                    settings.dynamicColors,
                    viewModel::setDynamicColors
                )
            }
        }
        item {
            // Пользователь может задать, сколько секунд показывать ответ
            var text by remember(settings.skillOutputDisplaySeconds) {
                mutableStateOf(settings.skillOutputDisplaySeconds.toString())
            }
            SettingsItem(
                title = stringResource(R.string.pref_skill_output_display_time_title),
                icon = Icons.Default.Timer,
                description = stringResource(R.string.pref_skill_output_display_time_summary),
                content = {
                    OutlinedTextField(
                        value = text,
                        onValueChange = {
                            // Оставляем только цифры
                            text = it.filter { ch -> ch.isDigit() }
                            val value = text.toIntOrNull() ?: 0
                            viewModel.setSkillOutputDisplaySeconds(value)
                        },
                        singleLine = true,
                        modifier = Modifier.width(80.dp)
                    )
                }
            )
        }
        item {
            SettingsItem(
                title = stringResource(R.string.pref_skills_title),
                icon = Icons.Default.Extension,
                description = stringResource(R.string.pref_skills_summary),
                modifier = Modifier
                    .clickable(onClick = navigateToSkillSettings)
                    .testTag("skill_settings_item")
            )
        }

        /* INPUT AND OUTPUT METHODS */
        item { SettingsCategoryTitle(stringResource(R.string.pref_io)) }
        item {
            inputDevice().Render(
                when (val inputDevice = settings.inputDevice) {
                    InputDevice.UNRECOGNIZED,
                    InputDevice.INPUT_DEVICE_UNSET -> InputDevice.INPUT_DEVICE_VOSK
                    else -> inputDevice
                },
                viewModel::setInputDevice,
            )
        }
        val wakeDevice = when (val device = settings.wakeDevice) {
            WakeDevice.UNRECOGNIZED,
            WakeDevice.WAKE_DEVICE_UNSET -> WakeDevice.WAKE_DEVICE_OWW
            else -> device
        }
        item {
            wakeDevice().Render(
                wakeDevice,
                viewModel::setWakeDevice,
            )
        }
        if (wakeDevice == WakeDevice.WAKE_DEVICE_OWW) {
            /* OpenWakeWord-specific settings */
            item {
                val isHeyNori by viewModel.isHeyNori.collectAsState(true)
                if (isHeyNori) {
                    // the wake word is "Hey Nori", so there is no custom model at the moment
                    SettingsItem(
                        modifier = Modifier.clickable { importLauncher.launch(arrayOf("*/*")) },
                        title = stringResource(R.string.pref_wake_custom_import),
                        icon = Icons.Default.UploadFile,
                        description = stringResource(R.string.pref_wake_custom_import_summary_oww),
                    )
                } else {
                    // a custom model is currently set, give the option to remove it
                    SettingsItem(
                        modifier = Modifier.clickable { viewModel.removeOwwUserWakeFile() },
                        title = stringResource(R.string.pref_wake_custom_delete),
                        icon = Icons.Default.DeleteSweep,
                        description = stringResource(R.string.pref_wake_custom_delete_summary),
                    )
                }
            }
        }
        item {
            speechOutputDevice().Render(
                when (val speechOutputDevice = settings.speechOutputDevice) {
                    SpeechOutputDevice.UNRECOGNIZED,
                    SpeechOutputDevice.SPEECH_OUTPUT_DEVICE_UNSET ->
                        SpeechOutputDevice.SPEECH_OUTPUT_DEVICE_ANDROID_TTS
                    else -> speechOutputDevice
                },
                viewModel::setSpeechOutputDevice,
            )
        }
        item {
            sttPlaySound().Render(
                when (val sttPlaySound = settings.sttPlaySound) {
                    SttPlaySound.UNRECOGNIZED,
                    SttPlaySound.STT_PLAY_SOUND_UNSET -> SttPlaySound.STT_PLAY_SOUND_NOTIFICATION
                    else -> sttPlaySound
                },
                viewModel::setSttPlaySound
            )
        }
        item {
            sttAutoFinish().Render(
                settings.autoFinishSttPopup,
                viewModel::setAutoFinishSttPopup
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Preview(locale = "ru")
@Composable
private fun MainSettingsScreenPreview() {
    AppTheme {
        Surface(
            color = MaterialTheme.colorScheme.background
        ) {
            MainSettingsScreen(
                navigateToSkillSettings = {},
                viewModel = MainSettingsViewModel(
                    application = Application(),
                    wakeDeviceWrapper = null,
                    dataStore = newDataStoreForPreviews(),
                ),
            )
        }
    }
}

@Preview(locale = "ru")
@Composable
private fun MainSettingsScreenWithTopBarPreview() {
    AppTheme {
        Surface(
            color = MaterialTheme.colorScheme.background
        ) {
            MainSettingsScreen(
                navigationIcon = {
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                navigateToSkillSettings = {},
                viewModel = MainSettingsViewModel(
                    application = Application(),
                    wakeDeviceWrapper = null,
                    dataStore = newDataStoreForPreviews()
                )
            )
        }
    }
}
