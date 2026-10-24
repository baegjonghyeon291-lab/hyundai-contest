package com.databox.blackbox

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.LinearLayout
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 사고 직후 대응: 보험사·112 전화, 위치 복사. 가장 당황한 순간에 두 번 누를 일이 없게 만든다. */
object Emergency {

    fun dial(context: Context, number: String) {
        val clean = number.filter { it.isDigit() || it == '+' }
        if (clean.isEmpty()) { Toast.makeText(context, "설정에서 보험사 번호를 먼저 입력하세요", Toast.LENGTH_LONG).show(); return }
        runCatching {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Toast.makeText(context, "전화 앱을 열 수 없습니다: ${it.message}", Toast.LENGTH_LONG).show() }
    }

    fun copyLocation(context: Context, report: CrashReport) {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA)
        val text = buildString {
            append("[사고 위치] ${fmt.format(Date(report.event.crashTime))}\n")
            if (report.lat != null && report.lon != null) {
                append("위도 %.5f, 경도 %.5f\n".format(report.lat, report.lon))
                append("https://map.kakao.com/link/map/사고지점,%.5f,%.5f\n".format(report.lat, report.lon))
            } else append("위치 기록 없음\n")
            append("충돌 속도 ${report.speedAtCrash?.let { "%.0f km/h".format(it) } ?: "-"} · ${report.event.cause}")
        }
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("사고 위치", text))
        Toast.makeText(context, "사고 위치와 시각을 복사했습니다", Toast.LENGTH_SHORT).show()
    }

    /** 리포트 화면·메인 사고 카드에 공통으로 붙는 버튼 줄. */
    fun buttons(context: Context, report: CrashReport): LinearLayout {
        val settings = Settings(context)
        return context.row(
            context.actionButton("🚨 보험사 전화", Palette.bad) { dial(context, settings.insurerPhone) },
            context.actionButton("112 신고", Palette.bad) { dial(context, "112") },
            context.ghostButton("📍 위치 복사") { copyLocation(context, report) },
            gap = 14, weights = false,
        )
    }
}
