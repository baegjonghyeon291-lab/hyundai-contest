package com.databox.blackbox

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 차 시동(시스템 부팅) 시 앱을 열지 않아도 기록을 시작한다. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (!Recorder.running) context.startForegroundService(Intent(context, RecorderService::class.java))
        }
    }
}
