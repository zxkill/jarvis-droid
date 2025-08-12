package org.zxkill.nori.eval

import android.content.Context
import androidx.datastore.core.DataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.nori.skill.skill.Skill
import org.nori.skill.skill.SkillInfo
import org.zxkill.nori.di.LocaleManager
import org.zxkill.nori.di.SkillContextImpl
import org.zxkill.nori.di.SkillContextInternal
import org.zxkill.nori.settings.datastore.UserSettings
import org.zxkill.nori.settings.datastore.UserSettingsModule
import org.zxkill.nori.skills.current_date.CurrentDateInfo
import org.zxkill.nori.skills.current_time.CurrentTimeInfo
import org.zxkill.nori.skills.fallback.text.TextFallbackInfo
import org.zxkill.nori.skills.lyrics.LyricsInfo
import org.zxkill.nori.skills.navigation.NavigationInfo
import org.zxkill.nori.skills.open.OpenInfo
import org.zxkill.nori.skills.search.SearchInfo
import org.zxkill.nori.skills.telephone.TelephoneInfo
import org.zxkill.nori.skills.weather.WeatherInfo
import org.zxkill.nori.skills.face_tracker.FaceTrackerInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkillHandler @Inject constructor(
    private val dataStore: DataStore<UserSettings>,
    private val localeManager: LocaleManager,
    private val skillContext: SkillContextInternal,
) {
    // TODO improve id handling (maybe just use an int that can point to an Android resource)
    val allSkillInfoList = listOf(
        WeatherInfo,
        SearchInfo,
        LyricsInfo,
        OpenInfo,
        NavigationInfo,
        TelephoneInfo,
        FaceTrackerInfo,
        CurrentTimeInfo,
        CurrentDateInfo,
    )

    // TODO add more fallback skills (e.g. search)
    private val fallbackSkillInfoList = listOf(
        TextFallbackInfo,
    )

    private val scope = CoroutineScope(Dispatchers.Default)

    // will be null when it has not been initialized yet
    private val _enabledSkillsInfo: MutableStateFlow<List<SkillInfo>?> = MutableStateFlow(null)
    val enabledSkillsInfo: StateFlow<List<SkillInfo>?> = _enabledSkillsInfo

    private val _skillRanker = MutableStateFlow(
        // an initial dummy value, will be overwritten directly by the launched job
        SkillRanker(listOf(), buildSkillFromInfo(fallbackSkillInfoList[0]))
    )
    val skillRanker: StateFlow<SkillRanker> = _skillRanker

    init {
        scope.launch {
            localeManager.locale
                .combine(dataStore.data) { locale, data -> Pair(locale, data.enabledSkillsMap) }
                .distinctUntilChanged()
                .collectLatest { (_, enabledSkills) ->
                    // locale is not used here, because the skills directly use the sections locale

                    val newEnabledSkillsInfo = allSkillInfoList
                        .filter { enabledSkills.getOrDefault(it.id, true) }
                        .filter { it.isAvailable(skillContext) }

                    _enabledSkillsInfo.value = newEnabledSkillsInfo
                    _skillRanker.value = SkillRanker(
                        newEnabledSkillsInfo.map(::buildSkillFromInfo),
                        buildSkillFromInfo(fallbackSkillInfoList[0]),
                    )
                }
        }
    }

    private fun buildSkillFromInfo(skillInfo: SkillInfo): Skill<*> {
        return skillInfo.build(skillContext)
    }

    companion object {
        fun newForPreviews(context: Context): SkillHandler {
            return SkillHandler(
                UserSettingsModule.newDataStoreForPreviews(),
                LocaleManager.newForPreviews(context),
                SkillContextImpl.newForPreviews(context),
            )
        }
    }
}
