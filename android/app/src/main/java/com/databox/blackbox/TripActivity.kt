package com.databox.blackbox

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 주행 일지 상세: 주행 요약 + 전체 속도 그래프 + 경로 + 벨트 안내 이력 + 이 주행의 사고. */
class TripActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra("id") ?: return finish()
        val trip = TripStore(this).get(id) ?: return finish()
        setContentView(buildLayout(trip))
    }

    private fun buildLayout(t: TripRecord): ScrollView {
        val fmt = SimpleDateFormat("MM.dd HH:mm", Locale.KOREA)
        val clock = SimpleDateFormat("HH:mm:ss", Locale.KOREA)
        fun value(v: String, warn: Boolean = false) = label(v, 40f, if (warn) Palette.warn else Palette.text, bold = true)

        val topBar = row(
            ghostButton("← 목록") { finish() },
            ghostButton("일지 삭제") { TripStore(this).delete(t.id); Recorder.savedCount++; finish() },
            gap = 16, weights = false,
        )

        val belts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (t.beltNotices.isEmpty()) belts.addView(label("안내 없음 (모두 착용)", 24f, Palette.sub))
        t.beltNotices.forEach { belts.addView(label("${clock.format(Date(it.t))}  ${it.seat} 착용 안내함", 24f, Palette.text)) }

        val crashes = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        t.crashIds.forEach { cid ->
            CrashStore(this).get(cid)?.let { e ->
                crashes.addView(ghostButton("💥 ${clock.format(Date(e.crashTime))} 사고 리포트 보기") {
                    startActivity(Intent(this, ReportActivity::class.java).putExtra("id", cid))
                })
            }
        }

        val summary = card(
            label("주행 일지 · ${TripStore.RETENTION_DAYS}일 보관", 22f, Palette.sub),
            label("${fmt.format(Date(t.start))} ~ ${fmt.format(Date(t.end))}", 36f, Palette.text, bold = true),
            LinearLayout(this).apply { space(20) },
            row(
                tile("주행 거리", value("%.1f km".format(t.distanceKm))),
                tile("주행 시간", value("%.0f분".format(t.durationMin))),
                tile("최고 속도", value("%.0f km/h".format(t.maxSpeedKph))),
            ),
            LinearLayout(this).apply { space(16) },
            row(
                tile("급제동", value("${t.hardBrakes}회", t.hardBrakes > 0)),
                tile("급가속", value("${t.hardAccels}회", t.hardAccels > 0)),
                tile("벨트 안내", value("${t.beltNotices.size}회", t.beltNotices.isNotEmpty())),
            ),
            LinearLayout(this).apply { space(24) },
            label("안전벨트 안내 기록", 22f, Palette.sub),
            belts,
            LinearLayout(this).apply { space(16) },
            crashes,
        )
        val route = card(
            label("주행 경로", 22f, Palette.sub),
            RouteView(this, t.path, null).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 520).apply { topMargin = 16 }
            },
        )
        val chart = card(
            label("속도 (전체 주행)", 22f, Palette.sub),
            TripChartView(this, t).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 360).apply { topMargin = 12 }
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

/** 주행 전체 1초 간격 속도 + 벨트 안내 시점(노란 점). */
class TripChartView(context: Context, private val trip: TripRecord) : View(context) {
    private val axis = Paint().apply { color = Palette.line; strokeWidth = 2f }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.sub; textSize = 24f }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.accent; strokeWidth = 4f; style = Paint.Style.STROKE }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.warn }

    override fun onDraw(canvas: Canvas) {
        val sp = trip.speed1Hz
        val left = 90f; val right = width - 20f; val top = 20f; val bottom = height - 50f
        canvas.drawLine(left, bottom, right, bottom, axis)
        if (sp.size < 2) { canvas.drawText("기록이 짧습니다", left, height / 2f, label); return }
        val max = (sp.max().coerceAtLeast(20.0) / 20).let { kotlin.math.ceil(it) * 20 }
        fun x(i: Int) = left + (right - left) * i / (sp.size - 1)
        fun y(v: Double) = (bottom - (bottom - top) * v / max).toFloat()
        val path = Path().apply { moveTo(x(0), y(sp[0])); for (i in 1 until sp.size) lineTo(x(i), y(sp[i])) }
        canvas.drawPath(path, line)
        canvas.drawText("${max.toInt()}", 20f, top + 20f, label)
        canvas.drawText("0", 50f, bottom, label)
        canvas.drawText("출발", left, height - 10f, label)
        canvas.drawText("도착", right - 60f, height - 10f, label)
        trip.beltNotices.forEach {
            val i = ((it.t - trip.start) / 1000).toInt().coerceIn(0, sp.lastIndex)
            canvas.drawCircle(x(i), y(sp[i]), 10f, dot)
        }
    }
}
