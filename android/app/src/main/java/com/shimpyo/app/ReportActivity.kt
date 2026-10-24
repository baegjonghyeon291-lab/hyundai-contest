package com.shimpyo.app

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** 사고 리포트: 요약 사실 + 속도·브레이크·깜빡이 그래프. */
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
            val mark = when (favorable) { true -> "✅ "; false -> "⚠️ "; null -> "· " }
            facts.addView(text("$mark$title   $value", 28f, Color.WHITE))
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Button(this@ReportActivity).apply { text = "← 목록"; textSize = 24f; setOnClickListener { finish() } })
            addView(Button(this@ReportActivity).apply {
                text = "기록 삭제"; textSize = 24f
                setOnClickListener {
                    CrashStore(this@ReportActivity).delete(report.event.id)
                    Recorder.savedCount++
                    finish()
                }
            })
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B1A2E"))
            setPadding(70, 40, 70, 40)
            addView(buttons)
            addView(text("사고 리포트  ${report.crashTimeText}", 40f, Color.WHITE))
            addView(text("충돌 전 30초 ~ 후 10초 차량 기록 · 블랙박스 영상의 보조 자료", 22f, Color.GRAY))
            addView(facts)
            addView(SpeedChartView(this@ReportActivity, report), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 520))
        }
        return ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0B1A2E")); addView(root) }
    }

    private fun text(value: String, size: Float, color: Int) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        setPadding(0, 8, 0, 8)
    }
}
