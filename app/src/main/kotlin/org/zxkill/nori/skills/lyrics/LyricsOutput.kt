package org.zxkill.nori.skills.lyrics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.nori.skill.context.SkillContext
import org.nori.skill.skill.SkillOutput
import org.zxkill.nori.R
import org.zxkill.nori.io.graphical.Body
import org.zxkill.nori.io.graphical.Headline
import org.zxkill.nori.io.graphical.HeadlineSpeechSkillOutput
import org.zxkill.nori.io.graphical.Subtitle
import org.zxkill.nori.util.getString

sealed interface LyricsOutput : SkillOutput {
    data class Success(
        val title: String,
        val artist: String,
        val lyrics: String,
    ) : LyricsOutput {
        override fun getSpeechOutput(ctx: SkillContext): String = ctx.getString(
            R.string.skill_lyrics_found_song_by_artist, title, artist
        )

        @Composable
        override fun GraphicalOutput(ctx: SkillContext) {
            Column {
                Headline(text = title)
                Subtitle(text = artist)
                Spacer(modifier = Modifier.height(12.dp))
                Body(text = lyrics)
            }
        }
    }

    data class Failed(
        val title: String,
    ) : HeadlineSpeechSkillOutput, LyricsOutput {
        override fun getSpeechOutput(ctx: SkillContext): String = ctx.getString(
            R.string.skill_lyrics_song_not_found, title
        )
    }
}
