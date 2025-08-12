package org.zxkill.nori.io.wake

import android.Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
import android.Manifest.permission.RECORD_AUDIO
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import org.zxkill.nori.R

class BootBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Got intent ${intent.action}")

        val permissions = mutableListOf(RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions += FOREGROUND_SERVICE_MICROPHONE
        }
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(context, permission) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                Log.d(TAG, "Audio permission not granted: $permission")
                Toast.makeText(
                    context,
                    R.string.grant_microphone_permission,
                    Toast.LENGTH_LONG,
                ).show()
                return
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Log.d(TAG, "Creating notification")
                WakeService.createNotificationToStartLater(context)
            } else {
                Log.d(TAG, "Starting service")
                WakeService.start(context)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialise wake service", e)
        }
    }

    companion object {
        val TAG = BootBroadcastReceiver::class.simpleName
    }
}
