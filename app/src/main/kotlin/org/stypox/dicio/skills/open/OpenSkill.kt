package org.stypox.dicio.skills.open

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import org.dicio.skill.context.SkillContext
import org.dicio.skill.recognizer.FuzzyRecognizerSkill
import org.dicio.skill.recognizer.FuzzyRecognizerSkill.Pattern
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.skill.Specificity
import org.stypox.dicio.util.StringUtils

/**
 * Скилл для запуска других приложений по их названию.
 * Использует нечёткое сопоставление вводимой команды с шаблонами.
 */
class OpenSkill(correspondingSkillInfo: SkillInfo) :
    FuzzyRecognizerSkill<OpenSkill.Command>(correspondingSkillInfo, Specificity.LOW) {

    /** Данные команды, содержащие название запускаемого приложения. */
    data class Command(val app: String?)

    // Явно указываем тип списка шаблонов, чтобы Kotlin не выводил
    // отдельный подкласс команды и корректно переопределял поле
    // [patterns] из базового скилла
    override val patterns: List<Pattern<Command>> = listOf(
        // Поддерживаются конструкции вида "запусти браузер" или "открой телеграм"
        Pattern(
            example = "запусти приложение",
            regex = Regex("(?:запусти|открой)\\s+(?<app>.+)"),
            // Формируем объект [Command] из названия приложения в совпадении
            builder = { Command(it.groups["app"]?.value) }
        )
    )

    override suspend fun generateOutput(ctx: SkillContext, inputData: Command?): SkillOutput {
        // После распознавания шаблона данные не могут быть null
        val data = requireNotNull(inputData)
        // Название приложения, которое произнёс пользователь
        val userAppName = data.app?.trim { it <= ' ' }
        val packageManager: PackageManager = ctx.android.packageManager
        // Пытаемся найти наиболее похожее приложение по названию
        val applicationInfo = userAppName?.let { getMostSimilarApp(packageManager, it) }

        if (applicationInfo != null) {
            // Формируем интент для запуска найденного приложения
            val launchIntent: Intent =
                packageManager.getLaunchIntentForPackage(applicationInfo.packageName)!!
            launchIntent.action = Intent.ACTION_MAIN
            launchIntent.addCategory(Intent.CATEGORY_LAUNCHER)
            launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            ctx.android.startActivity(launchIntent)
        }

        return OpenOutput(
            appName = applicationInfo?.loadLabel(packageManager)?.toString() ?: userAppName,
            packageName = applicationInfo?.packageName,
        )
    }

    companion object {
        private fun getMostSimilarApp(
            packageManager: PackageManager,
            appName: String
        ): ApplicationInfo? {
            val resolveInfosIntent = Intent(Intent.ACTION_MAIN, null)
            resolveInfosIntent.addCategory(Intent.CATEGORY_LAUNCHER)

            @SuppressLint("QueryPermissionsNeeded") // нужно получить список всех приложений
            val resolveInfos: List<ResolveInfo> =
                packageManager.queryIntentActivities(resolveInfosIntent, 0)
            var bestDistance = Int.MAX_VALUE
            var bestApplicationInfo: ApplicationInfo? = null

            for (resolveInfo in resolveInfos) {
                try {
                    val currentApplicationInfo: ApplicationInfo = packageManager.getApplicationInfo(
                        resolveInfo.activityInfo.packageName, PackageManager.GET_META_DATA
                    )
                    val currentDistance = StringUtils.customStringDistance(
                        appName,
                        packageManager.getApplicationLabel(currentApplicationInfo).toString(),
                    )
                    if (currentDistance < bestDistance) {
                        bestDistance = currentDistance
                        bestApplicationInfo = currentApplicationInfo
                    }
                } catch (_: PackageManager.NameNotFoundException) {
                }
            }
            // Если расстояние слишком велико, считаем, что подходящего приложения нет
            return if (bestDistance > 5) null else bestApplicationInfo
        }
    }
}
