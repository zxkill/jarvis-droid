package org.zxkill.nori

import android.Manifest
import android.app.Application
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.HiltAndroidApp
import android.util.Log
import org.zxkill.nori.skills.weather.WeatherCache
import org.zxkill.nori.util.checkPermissions

// В Android есть известная проблема, связанная с allowBackup=true.
// Подробнее: https://medium.com/p/924c91bafcac
@HiltAndroidApp
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Начиная с Android 13 (TIRAMISU) требуется разрешение на показ уведомлений.
        // Для старших версий или при наличии разрешения создаём каналы уведомлений.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkPermissions(this, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            initNotificationChannels()
        }

        // Прогреваем кэш погоды при запуске приложения,
        // чтобы ответы были мгновенными
        Log.d("App", "Запускаем предварительную загрузку погоды")
        WeatherCache.preload(this)
    }

    // Создаёт каналы уведомлений, используемые приложением.
    private fun initNotificationChannels() {
        NotificationManagerCompat.from(this).createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(
                    getString(R.string.error_report_channel_id),
                    NotificationManagerCompat.IMPORTANCE_LOW
                )
                    // Человекочитаемое имя канала
                    .setName(getString(R.string.error_report_channel_name))
                    // Описание, отображаемое в настройках системы
                    .setDescription(getString(R.string.error_report_channel_description))
                    .build()
            )
        )
    }
}
