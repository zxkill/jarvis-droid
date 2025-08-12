package org.zxkill.nori.skills.lyrics

import org.nori.skill.context.SkillContext
import org.nori.skill.recognizer.FuzzyRecognizerSkill
import org.nori.skill.recognizer.FuzzyRecognizerSkill.Pattern as SkillPattern
import org.nori.skill.skill.SkillInfo
import org.nori.skill.skill.SkillOutput
import org.nori.skill.skill.Specificity
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.zxkill.nori.util.ConnectionUtils
import org.zxkill.nori.util.RegexUtils
import org.unbescape.javascript.JavaScriptEscape
import org.unbescape.json.JsonEscape
import java.util.regex.Pattern

/**
 * Скилл получения текста песен с сервиса Genius.
 * Распознаёт команды вида "текст песни Imagine".
 */
class LyricsSkill(correspondingSkillInfo: SkillInfo) :
    FuzzyRecognizerSkill<String>(correspondingSkillInfo, Specificity.LOW) {

    // Используем псевдоним [SkillPattern], чтобы отличать его от java.util.regex.Pattern.
    override val patterns: List<SkillPattern<String>> = listOf(
        SkillPattern(
            examples = listOf(
                "текст песни imagine",
                "слова песни imagine",
                "покажи текст imagine"
            ),
            regex = Regex("(?:текст (?:песни|песенки)|слова песни)\\s+(?<song>.+)"),
            builder = { match -> match!!.groups["song"]!!.value }
        )
    )

    /**
     * Подключается к Genius для получения текста песни.
     * В будущем можно добавить поддержку других сервисов.
     */
    override suspend fun generateOutput(ctx: SkillContext, inputData: String?): SkillOutput {
        val songName = inputData ?: return LyricsOutput.Failed(title = "")
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
        // Используем полное имя класса, чтобы избежать конфликта с [SkillPattern]
        // и явно указать, что это регулярные выражения Java.
        private val LYRICS_PATTERN =
            java.util.regex.Pattern.compile("document\\.write\\(JSON\\.parse\\('(.+)'\\)\\)")
        private val NEWLINE_PATTERN =
            java.util.regex.Pattern.compile("\\s*(\\\\n)?\\s*\\{#%\\)\\s*")
    }
}
