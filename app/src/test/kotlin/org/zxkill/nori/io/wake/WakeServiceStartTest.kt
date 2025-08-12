package org.zxkill.nori.io.wake

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.test.mock.MockContext
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import org.zxkill.nori.io.wake.WakeService
import java.util.concurrent.atomic.AtomicBoolean

class WakeServiceStartTest : StringSpec({
    "start uses startForegroundService when not started" {
        val ctx = object : MockContext() {
            var startServiceCalled = false
            var startForegroundServiceCalled = false
            override fun startService(service: Intent?): ComponentName? {
                startServiceCalled = true
                return null
            }
            override fun startForegroundService(service: Intent?): ComponentName? {
                startForegroundServiceCalled = true
                return null
            }
            override fun checkSelfPermission(permission: String): Int = PERMISSION_GRANTED
        }

        setServiceStarted(false)
        WakeService.start(ctx)

        ctx.startForegroundServiceCalled.shouldBeTrue()
        ctx.startServiceCalled.shouldBeFalse()
    }

    "start uses startService when already started" {
        val ctx = object : MockContext() {
            var startServiceCalled = false
            var startForegroundServiceCalled = false
            override fun startService(service: Intent?): ComponentName? {
                startServiceCalled = true
                return null
            }
            override fun startForegroundService(service: Intent?): ComponentName? {
                startForegroundServiceCalled = true
                return null
            }
            override fun checkSelfPermission(permission: String): Int = PERMISSION_GRANTED
        }

        setServiceStarted(true)
        WakeService.start(ctx)

        ctx.startServiceCalled.shouldBeTrue()
        ctx.startForegroundServiceCalled.shouldBeFalse()
    }
})

private fun setServiceStarted(value: Boolean) {
    val field = WakeService::class.java.getDeclaredField("serviceStarted")
    field.isAccessible = true
    (field.get(null) as AtomicBoolean).set(value)
}
