package org.zxkill.nori.skills.search

import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import org.nori.skill.context.SkillContext
import org.nori.skill.recognizer.FuzzyRecognizerSkill
import org.nori.skill.recognizer.FuzzyRecognizerSkill.Pattern
import org.nori.skill.skill.SkillInfo
import org.nori.skill.skill.SkillOutput
import org.nori.skill.skill.Specificity
import org.zxkill.nori.util.ConnectionUtils
import org.zxkill.nori.util.LocaleUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Скилл для поиска информации в интернете (DuckDuckGo).
 * Команда распознаётся по нечёткому совпадению с простыми шаблонами.
 */
class SearchSkill(correspondingSkillInfo: SkillInfo) :
    FuzzyRecognizerSkill<String>(correspondingSkillInfo, Specificity.LOW) {

    override val patterns = listOf(
        // Простейший шаблон вида "найди котов" или "поиск погода"
        Pattern(
            examples = listOf(
                "найди погоду",
                "поиск погоды",
                "посмотри информацию",
                "ищи новости"
            ),
            regex = Regex("(?:найди|поиск(?:ай)?|посмотри|ищи)\\s+(?<query>.+)"),
            builder = { match -> match!!.groups["query"]!!.value }
        )
    )

    override suspend fun generateOutput(ctx: SkillContext, inputData: String?): SkillOutput {
        val query = inputData ?: return SearchOutput(null, true)
        return SearchOutput(searchOnDuckDuckGo(ctx, query), true)
    }
}

private const val DUCK_DUCK_GO_SEARCH_URL = "https://duckduckgo.com/html/?q="

private val DUCK_DUCK_GO_SUPPORTED_LOCALES = listOf(
    "ar-es", "au-en", "at-de", "be-fr", "be-nl", "br-pt", "bg-bg", "ca-en", "ca-fr",
    "ct-ca", "cl-es", "cn-zh", "co-es", "hr-hr", "cz-cs", "dk-da", "ee-et", "fi-fi",
    "fr-fr", "de-de", "gr-el", "hk-tz", "hu-hu", "in-en", "id-en", "ie-en", "il-en",
    "it-it", "jp-jp", "kr-kr", "lv-lv", "lt-lt", "my-en", "mx-es", "nl-nl", "nz-en",
    "no-no", "pk-en", "pe-es", "ph-en", "pl-pl", "pt-pt", "ro-ro", "ru-ru", "xa-ar",
    "sg-en", "sk-sk", "sl-sl", "za-en", "es-ca", "es-es", "se-sv", "ch-de", "ch-fr",
    "tw-tz", "th-en", "tr-tr", "us-en", "us-es", "ua-uk", "uk-en", "vn-en"
)

internal fun searchOnDuckDuckGo(ctx: SkillContext, query: String): List<SearchOutput.Data> {
    // Ищем наиболее подходящую локаль для запроса
    var resolvedLocale: LocaleUtils.LocaleResolutionResult? = null
    try {
        resolvedLocale = LocaleUtils.resolveSupportedLocale(
            LocaleListCompat.create(ctx.locale), DUCK_DUCK_GO_SUPPORTED_LOCALES
        )
    } catch (_: LocaleUtils.UnsupportedLocaleException) {
    }
    val locale = resolvedLocale?.supportedLocaleString ?: ""

    // Выполняем HTTP-запрос к DuckDuckGo с нужными заголовками
    val html: String = ConnectionUtils.getPage(
        DUCK_DUCK_GO_SEARCH_URL + ConnectionUtils.urlEncode(query),
        object : HashMap<String?, String?>() {
            init {
                put(
                    "User-Agent",
                    "Mozilla/5.0 (X11; Linux x86_64; rv:135.0) Gecko/20100101 Firefox/135.0"
                )
                put(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                )
                put(
                    "Host",
                    "html.duckduckgo.com"
                )
                put("Cookie", "kl=$locale")
            }
        }
    )

    val document: Document = Jsoup.parse(html)
    val elements = document.select("div[class=links_main links_deep result__body]")
    val result: MutableList<SearchOutput.Data> = ArrayList()
    for (element in elements) {
        try {
            // Ссылка на результат находится в параметре "uddg"
            val ddgUrl = element.select("a[class=result__a]").first()!!.attr("href")
            val url = ddgUrl.toUri().getQueryParameter("uddg")!!

            result.add(
                SearchOutput.Data(
                    title = element.select("a[class=result__a]").first()!!.text(),
                    thumbnailUrl = "https:" + element.select("img[class=result__icon__img]")
                        .first()!!.attr("src"),
                    url = url,
                    description = element.select("a[class=result__snippet]").first()!!.text(),
                )
            )
        } catch (_: NullPointerException) {
        }
    }

    return result
}
