package org.zxkill.nori.ui.util

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.tooling.preview.datasource.CollectionPreviewParameterProvider
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import org.nori.skill.context.SkillContext
import org.nori.skill.skill.SkillInfo
import org.nori.skill.skill.SkillOutput
import org.zxkill.nori.io.input.SttState
import org.zxkill.nori.io.wake.WakeState
import org.zxkill.nori.skills.fallback.text.TextFallbackOutput
import org.zxkill.nori.skills.lyrics.LyricsInfo
import org.zxkill.nori.skills.navigation.NavigationInfo
import org.zxkill.nori.skills.navigation.NavigationOutput
import org.zxkill.nori.skills.telephone.ConfirmCallOutput
import org.zxkill.nori.skills.telephone.ConfirmedCallOutput
import org.zxkill.nori.skills.telephone.TelephoneInfo
import org.zxkill.nori.skills.weather.WeatherInfo
import org.zxkill.nori.skills.face_tracker.FaceTrackerInfo
import org.zxkill.nori.ui.home.Interaction
import org.zxkill.nori.ui.home.InteractionLog
import org.zxkill.nori.ui.home.PendingQuestion
import org.zxkill.nori.ui.home.QuestionAnswer
import java.io.IOException


class UserInputPreviews : CollectionPreviewParameterProvider<String>(listOf(
    "",
    "When",
    "What's the weather?",
    LoremIpsum(50).values.first(),
))

class SkillInfoPreviews : CollectionPreviewParameterProvider<SkillInfo>(listOf(
    WeatherInfo,
    TelephoneInfo,
    FaceTrackerInfo,
    object : SkillInfo("test") {
        override fun name(context: Context) = "Long name lorem ipsum dolor sit amet, consectetur"
        override fun sentenceExample(context: Context) = "Long sentence ".repeat(20)
        @Composable override fun icon() = rememberVectorPainter(Icons.Default.Extension)
        override fun isAvailable(ctx: SkillContext) = true
        override fun build(ctx: SkillContext) = error("not-implemented preview-only")
    },
))

class SkillOutputPreviews : CollectionPreviewParameterProvider<SkillOutput>(listOf(
    TextFallbackOutput(askToRepeat = true),
))

class InteractionLogPreviews : CollectionPreviewParameterProvider<InteractionLog>(listOf(
    InteractionLog(
        listOf(),
        null,
    ),
    InteractionLog(
        listOf(),
        PendingQuestion(
            userInput = "What's the weather?",
            continuesLastInteraction = true,
            skillBeingEvaluated = null,
        ),
    ),
    InteractionLog(
        listOf(),
        PendingQuestion(
            userInput = LoremIpsum(50).values.first(),
            continuesLastInteraction = false,
            skillBeingEvaluated = SkillInfoPreviews().values.first(),
        ),
    ),
    InteractionLog(
        listOf(
            Interaction(
                skill = NavigationInfo,
                questionsAnswers = listOf(
                    QuestionAnswer("Take me to Paris", NavigationOutput("Paris"))
                )
            ),
        ),
        PendingQuestion(
            userInput = "Twenty",
            continuesLastInteraction = true,
            skillBeingEvaluated = null,
        ),
    ),
    InteractionLog(
        listOf(
            Interaction(
                skill = TelephoneInfo,
                questionsAnswers = listOf(
                    QuestionAnswer("call mom", ConfirmCallOutput("Mom", "1234567890")),
                    QuestionAnswer("yes", ConfirmedCallOutput("1234567890")),
                )
            )
        ),
        PendingQuestion(
            userInput = "lyrics i'm working on a dream",
            continuesLastInteraction = false,
            skillBeingEvaluated = LyricsInfo,
        ),
    ),
))

class SttStatesPreviews : CollectionPreviewParameterProvider<SttState>(listOf(
    SttState.NoMicrophonePermission,
    SttState.NotInitialized,
    SttState.NotAvailable,
    SttState.NotDownloaded,
    SttState.Downloading(Progress(0, 3, 987654, 0)),
    SttState.Downloading(Progress(5, 0, 987654, 0)),
    SttState.Downloading(Progress(0, 1, 987654, 1234567)),
    SttState.ErrorDownloading(IOException("ErrorDownloading exception")),
    SttState.Downloaded,
    SttState.Unzipping(Progress(0, 0, 765432, 0)),
    SttState.Unzipping(Progress(2, 3, 3365432, 9876543)),
    SttState.ErrorUnzipping(Exception("ErrorUnzipping exception")),
    SttState.NotLoaded,
    SttState.Loading(true),
    SttState.Loading(false),
    SttState.ErrorLoading(Exception("ErrorLoading exception")),
    SttState.Loaded,
    SttState.Listening,
    SttState.WaitingForResult,
))

class WakeStatesPreviews : CollectionPreviewParameterProvider<WakeState>(listOf(
    WakeState.NotDownloaded,
    WakeState.Downloading(Progress(0, 0, 987654, 0)),
    WakeState.Downloading(Progress(1, 2, 987654, 1234567)),
    WakeState.ErrorDownloading(Exception("ErrorDownloading exception")),
    WakeState.Loading,
    WakeState.ErrorLoading(Exception("ErrorLoading exception")),
    WakeState.Loaded,
))
