package com.databox.blackbox

import android.content.BroadcastReceiver
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.Intent

/**
 * 시연 자동화용 (디버그 빌드에서만 동작). adb shell am broadcast --user 10 -n com.databox.blackbox/.DemoReceiver -a com.databox.blackbox.DEMO --es cmd <명령>
 * (암시적 브로드캐스트는 Android 14에서 "Background execution not allowed"로 버려지므로 -n 으로 컴포넌트를 지정해야 한다)
 * 명령: report(최근 사고 리포트 열기) · valet(발레파킹 맡김 시작) · return(돌려받기) · main(메인)
 */
class DemoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val open = { cls: Class<*>, id: String? ->
            context.startActivity(Intent(context, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).apply { id?.let { putExtra("id", it) } })
        }
        when (intent.getStringExtra("cmd")) {
            "report" -> CrashStore(context).list().firstOrNull()?.let { open(ReportActivity::class.java, it.id) }
            "valet" -> Recorder.service?.startHandover(HandoverMode.VALET)
            "return" -> Recorder.service?.finishHandover { open(HandoverActivity::class.java, it.id) }
            "main" -> open(MainActivity::class.java, null)
        }
    }
}
