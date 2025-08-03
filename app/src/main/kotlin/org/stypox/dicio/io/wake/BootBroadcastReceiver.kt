package org.stypox.dicio.io.wake

import android.Manifest.permission.RECORD_AUDIO
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

class BootBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Got intent ${intent.action}")

        if (ContextCompat.checkSelfPermission(context, RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Audio permission not granted")
            return
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
