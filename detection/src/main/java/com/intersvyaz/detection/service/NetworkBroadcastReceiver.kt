package com.intersvyaz.detection.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class NetworkBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val networkService = Intent(context, NetworkService::class.java).also {
                it.action = Actions.START.name
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context?.startForegroundService(networkService)
            else
                context?.startService(networkService)
        }
        Log.e(TAG, "onReceive ${intent?.action}")
    }

    companion object {
        private val TAG = NetworkBroadcastReceiver::class.java.simpleName
    }
}