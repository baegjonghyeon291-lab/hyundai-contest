package com.shimpyo.app

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity

/** 사고 리포트: 핵심 사실 + 위치 + 속도·브레이크·깜빡이 그래프 + 휴대폰 전송. */
class ReportActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra("id") ?: return finish()
        val event = CrashStore(this).get(id) ?: return finish()
        setContentView(buildLayout(CrashReport(event)))
    }

    private fun buildLayout(report: CrashReport): ScrollView {
        val facts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        report.lines().forEach { (title, value, favorable) ->
            val (mark, color) = when (favorable) {
                true -> "✓" to Palette.good
                false -> "!" to Palette.bad
                null -> "·" to Palette.sub
            }
            facts.addView(row(
                label(mark, 26f, color, bold = true).apply { width = 44 },
                label(title, 24f, Palette.sub).apply { width = 300 },
                label(value, 26f, Palette.text),
                gap = 8, weights = false,
            ).apply { setPadding(0, 8, 0, 8) })
        }

        val topBar = row(
            ghostButton("← 목록") { finish() },
            ghostButton("기록 삭제") {
                CrashStore(this).delete(report.event.id)
                Recorder.savedCount++
                finish()
            },
            actionButton("📱 휴대폰으로 받기") {
                startActivity(Intent(this, QrActivity::class.java).putExtra("url", Share.crashUrl(report)))
            },
            gap = 16, weights = false,
        )

        val summary = card(
            label("사고 리포트", 22f, Palette.sub),
            label(report.crashTimeText, 40f, Palette.text, bold = true),
            label("충돌 전 30초 ~ 후 10초 차량 기록 · 블랙박스 영상의 보조 자료", 20f, Palette.sub),
            LinearLayout(this).apply { space(12) },
            facts,
        )
        val route = card(
            label("사고 지점", 22f, Palette.sub),
            RouteView(this, report.path(), report.lat?.let { la -> report.lon?.let { lo -> la to lo } }).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 460).apply { topMargin = 16 }
            },
        )
        val chart = card(
            label("속도 · 브레이크 · 방향지시등", 22f, Palette.sub),
            SpeedChartView(this, report).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 480).apply { topMargin = 12 }
            },
        )
        val columns = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(summary, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f))
            addView(route, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = 24 })
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 40, 56, 40)
            addView(topBar); space(24); addView(columns); space(24); addView(chart)
        }
        return ScrollView(this).apply { setBackgroundColor(Palette.bg); addView(root) }
    }
}
