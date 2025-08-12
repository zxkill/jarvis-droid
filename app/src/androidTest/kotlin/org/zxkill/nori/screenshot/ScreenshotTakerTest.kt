package org.zxkill.nori.screenshot

import android.Manifest
import android.view.WindowInsets
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.datastore.core.DataStore
import androidx.test.rule.GrantPermissionRule
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.zxkill.nori.MainActivity
import org.zxkill.nori.di.SttInputDeviceWrapper
import org.zxkill.nori.di.SttInputDeviceWrapperModule
import org.zxkill.nori.di.WakeDeviceWrapper
import org.zxkill.nori.di.WakeDeviceWrapperModule
import org.zxkill.nori.eval.SkillEvaluator
import org.zxkill.nori.eval.SkillEvaluatorModule
import org.zxkill.nori.settings.datastore.Theme
import org.zxkill.nori.settings.datastore.UserSettings
import org.zxkill.nori.settings.datastore.copy
import org.zxkill.nori.skills.lyrics.LyricsInfo
import org.zxkill.nori.skills.lyrics.LyricsOutput
import org.zxkill.nori.skills.search.SearchInfo
import org.zxkill.nori.skills.search.SearchOutput
import org.zxkill.nori.skills.telephone.ConfirmCallOutput
import org.zxkill.nori.skills.telephone.ConfirmedCallOutput
import org.zxkill.nori.skills.telephone.TelephoneInfo
import org.zxkill.nori.skills.weather.WeatherInfo
import org.zxkill.nori.skills.weather.WeatherOutput
import org.zxkill.nori.ui.home.Interaction
import org.zxkill.nori.ui.home.InteractionLog
import org.zxkill.nori.ui.home.QuestionAnswer
import org.zxkill.nori.io.input.SttState
import org.zxkill.nori.skills.weather.ResolvedLengthUnit
import org.zxkill.nori.skills.weather.ResolvedTemperatureUnit
import javax.inject.Inject
import javax.inject.Singleton

@UninstallModules(
    SttInputDeviceWrapperModule::class,
    WakeDeviceWrapperModule::class,
    SkillEvaluatorModule::class,
)
@HiltAndroidTest
class ScreenshotTakerTest {
    @Module
    @InstallIn(SingletonComponent::class)
    class FakeSttInputDeviceWrapperModule {
        @Provides
        @Singleton
        fun provideInputDeviceWrapper(): SttInputDeviceWrapper {
            return FakeSttInputDeviceWrapper()
        }
    }
    @Module
    @InstallIn(SingletonComponent::class)
    class FakeWakeDeviceWrapperModule {
        @Provides
        @Singleton
        fun provideWakeDeviceWrapper(): WakeDeviceWrapper {
            return FakeWakeDeviceWrapper()
        }
    }

    @Module
    @InstallIn(SingletonComponent::class)
    class FakeSkillEvaluatorModule {
        @Provides
        @Singleton
        fun provideSkillEvaluator(): SkillEvaluator {
            return FakeSkillEvaluator()
        }
    }


    @Inject
    lateinit var sttInputDeviceWrapper: SttInputDeviceWrapper

    private val fakeSttInputDeviceWrapper: FakeSttInputDeviceWrapper get() =
        sttInputDeviceWrapper as FakeSttInputDeviceWrapper

    @Inject
    lateinit var skillEvaluator: SkillEvaluator

    private val fakeSkillEvaluator: FakeSkillEvaluator get() =
        skillEvaluator as FakeSkillEvaluator
    
    @Inject
    lateinit var dataStore: DataStore<UserSettings>


    // must run before anything else
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    // needed so that the SttButton does not show the "microphone permission needed" message
    @get:Rule(order = 1)
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    // needed to make hiding status/navigation bars instantaneous
    @get:Rule(order = 2)
    val disableAnimationsRule = DisableAnimationsRule()

    @get:Rule(order = 3)
    val composeRule = createAndroidComposeRule<MainActivity>()


    private val coilEventListener = CoilEventListener()


    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun takeScreenshots() = runTest {
        composeRule.activity.runOnUiThread {
            composeRule.activity.window.decorView.windowInsetsController!!
                .hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        }
        coilEventListener.setup(composeRule.activity)


        // screenshot 0: home screen with "Here is what I can do" and STT listening
        dataStore.updateData { it.copy { theme = Theme.THEME_DARK } }
        fakeSttInputDeviceWrapper.uiState.emit(SttState.Listening)
        composeRule.takeScreenshot("en-US", "0")

        // screenshot 1: home screen with interactions with weather and lyrics skills
        dataStore.updateData { it.copy { theme = Theme.THEME_LIGHT } }
        fakeSttInputDeviceWrapper.uiState.emit(SttState.Loaded)
        coilEventListener.resetStartedImages()
        fakeSkillEvaluator.state.value = screenshot2InteractionLog
        composeRule.onNodeWithTag("interaction_list")
            .performScrollToIndex(2) // scroll to the first interaction
        composeRule.waitUntil { coilEventListener.isIdle(startedAtLeast = 1) }
        composeRule.takeScreenshot("en-US", "1")

        // screenshot 2: home screen with interactions with telephone and search skills
        dataStore.updateData { it.copy { theme = Theme.THEME_BLACK } }
        fakeSttInputDeviceWrapper.uiState.emit(SttState.Loaded)
        coilEventListener.resetStartedImages()
        fakeSkillEvaluator.state.value = screenshot3InteractionLog
        composeRule.onNodeWithTag("interaction_list")
            .performScrollToIndex(2) // scroll to the first interaction
        composeRule.waitUntil { coilEventListener.isIdle(startedAtLeast = 2) }
        composeRule.takeScreenshot("en-US", "2")

        // screenshot 4: settings screen
        dataStore.updateData { it.copy { theme = Theme.THEME_DARK } }
        composeRule.onNodeWithTag("drawer_handle")
            .performClick() // open the drawer
        composeRule.onNodeWithTag("settings_drawer_item")
            .performClick() // open the settings screen
        composeRule.takeScreenshot("en-US", "4")

        // screenshot 3: skill settings screen
        dataStore.updateData { it.copy { theme = Theme.THEME_LIGHT } }
        composeRule.onNodeWithTag("skill_settings_item")
            .performClick() // open the skill settings screen
        composeRule.onAllNodesWithTag("expand_skill_settings_handle").apply {
            fetchSemanticsNodes().forEachIndexed { i, _ -> get(i).performClick() }
        } // expand all skill settings
        composeRule.takeScreenshot("en-US", "3")
    }

    companion object {
        private val screenshot2InteractionLog = InteractionLog(listOf(
            Interaction(WeatherInfo, listOf(
                QuestionAnswer(
                    question = "what's the weather in milan",
                    answer = WeatherOutput.Success(
                        city = "Milan",
                        description = "Few clouds",
                        iconUrl = "https://openweathermap.org/img/wn/02d@2x.png",
                        temp = 8.8,
                        tempMin = 7.2,
                        tempMax = 10.2,
                        tempString = "nine",
                        windSpeed = 1.8,
                        temperatureUnit = ResolvedTemperatureUnit.CELSIUS,
                        lengthUnit = ResolvedLengthUnit.METRIC,
                    )
                )
            )),
            Interaction(LyricsInfo, listOf(
                QuestionAnswer(
                    question = "lyrics bohemian rhapsody",
                    answer = LyricsOutput.Success(
                        title = "Bohemian Rhapsody",
                        artist = "Queen",
                        lyrics = "[Intro]\n" +
                                "Is this the real life? Is this just fantasy?\n" +
                                "Caught in a landslide, no escape from reality\n" +
                                "Open your eyes, look up to the skies and see\n" +
                                "I'm just a poor boy, I need no sympathy\n" +
                                "Because I'm easy come, easy go, little high, little low\n" +
                                "Any way the wind blows doesn't really matter to me, to me\n" +
                                "\n" +
                                "[Verse 1]\n" +
                                "Mama, just killed a man\n" +
                                "Put a gun against his head, pulled my trigger, now he's dead\n" +
                                "Mama, life had just begun\n" +
                                "But now I've gone and thrown it all away\n" +
                                "Mama, ooh, didn't mean to make you cry\n" +
                                "If I'm not back again this time tomorrow\n" +
                                "Carry on, carry on as if nothing really matters"
                    )
                )
            )),
        ), null)

        private val screenshot3InteractionLog = InteractionLog(listOf(
            Interaction(TelephoneInfo, listOf(
                QuestionAnswer(
                    question = "call michael",
                    answer = ConfirmCallOutput(
                        "Michael Smith",
                        "0123 456789"
                    )
                ),
                QuestionAnswer(
                    question = "go for it",
                    answer = ConfirmedCallOutput(
                        "0123 456789"
                    )
                ),
            )),
            Interaction(SearchInfo, listOf(
                QuestionAnswer(
                    question = "search newpipe",
                    answer = SearchOutput(listOf(
                        SearchOutput.Data(
                            title = "NewPipe - a free YouTube client",
                            description = "NewPipe is an Android app that lets you watch videos " +
                                    "and live streams from YouTube and other platforms without " +
                                    "ads or permissions. It is fast, lightweight, privacy " +
                                    "friendly and supports offline usage, background player, " +
                                    "subscriptions and more features.",
                            url = "https://newpipe.net",
                            thumbnailUrl = "https://external-content.duckduckgo.com/ip3/newpipe.net.ico"
                        ),
                        SearchOutput.Data(
                            title = "GitHub - TeamNewPipe/NewPipe: A libre lightweight streaming front-end for Android.",
                            description = "The NewPipe project aims to provide a private, " +
                                    "anonymous experience for using web-based media services. " +
                                    "Therefore, the app does not collect any data without your " +
                                    "consent. NewPipe's privacy policy explains in detail what " +
                                    "data is sent and stored when you send a crash report, or " +
                                    "leave a comment in our blog. You can find the document here.",
                            url = "https://github.com/TeamNewPipe/NewPipe",
                            thumbnailUrl = "https://external-content.duckduckgo.com/ip3/github.com.ico"
                        ),
                    ), askAgain = false)
                ),
            )),
        ), null)
    }
}
