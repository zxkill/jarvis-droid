package org.stypox.dicio.skills.lyrics

import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.dicio.skill.standard.StandardRecognizerSkill
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.stypox.dicio.sentences.Sentences.Lyrics
import org.stypox.dicio.util.ConnectionUtils
import org.stypox.dicio.util.RegexUtils
import org.unbescape.javascript.JavaScriptEscape
import org.unbescape.json.JsonEscape
import java.util.regex.Pattern

/** Скилл получения текста песен с сервиса Genius. */
class LyricsSkill(correspondingSkillInfo: SkillInfo, data: StandardRecognizerData<Lyrics>)
    : StandardRecognizerSkill<Lyrics>(correspondingSkillInfo, data) {

    /**
     * Подключается к Genius для получения текста песни.
     * В будущем можно добавить поддержку других сервисов.
     */
    override suspend fun generateOutput(ctx: SkillContext, inputData: Lyrics): SkillOutput {
        // Извлекаем название песни из запроса
        val songName: String = when (inputData) {
            is Lyrics.Query -> inputData.song ?: return LyricsOutput.Failed(title = "")
        }
        val search: JSONObject = ConnectionUtils.getPageJson(
            GENIUS_SEARCH_URL + ConnectionUtils.urlEncode(songName) + "&count=1"
        )
        val searchHits: JSONArray = search.getJSONObject("response").getJSONArray("sections")
            .getJSONObject(0).getJSONArray("hits")
        if (searchHits.length() == 0) {
            return LyricsOutput.Failed(title = songName)
        }

        val song: JSONObject = searchHits.getJSONObject(0).getJSONObject("result")
        var lyricsHtml: String = ConnectionUtils.getPage(
            GENIUS_LYRICS_URL + song.getInt("id") + "/embed.js"
        )
        lyricsHtml = RegexUtils.matchGroup(LYRICS_PATTERN, lyricsHtml, 1)
        lyricsHtml = JsonEscape.unescapeJson(JavaScriptEscape.unescapeJavaScript(lyricsHtml))
        val lyricsDocument: Document = Jsoup.parse(lyricsHtml)
        val elements = lyricsDocument.select("div[class=rg_embed_body]")
        elements.select("br").append("{#%)")

        return LyricsOutput.Success(
            title = song.getString("title"),
            artist = song.getJSONObject("primary_artist").getString("name"),
            lyrics = RegexUtils.replaceAll(NEWLINE_PATTERN, elements.text(), "\n"),
        )
    }

    companion object {
        // замените "songs" на "multi", чтобы получать результаты всех типов, а не только песни
        private const val GENIUS_SEARCH_URL = "https://genius.com/api/search/songs?q="
        private const val GENIUS_LYRICS_URL = "https://genius.com/songs/"
        private val LYRICS_PATTERN =
            Pattern.compile("document\\.write\\(JSON\\.parse\\('(.+)'\\)\\)")
        private val NEWLINE_PATTERN = Pattern.compile("\\s*(\\\\n)?\\s*\\{#%\\)\\s*")
    }
}
