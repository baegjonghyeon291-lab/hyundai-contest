package com.shimpyo.app

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 맡김 리포트: 맡긴 동안 주행 요약 + 바뀐 설정 + 경로 + 휴대폰 전송. */
class HandoverActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra("id") ?: return finish()
        val record = HandoverStore(this).get(id) ?: return finish()
        setContentView(buildLayout(record))
    }

    private fun buildLayout(r: HandoverRecord): ScrollView {
        val fmt = SimpleDateFormat("MM.dd HH:mm", Locale.KOREA)
        fun value(v: String, warn: Boolean = false) = label(v, 40f, if (warn) Palette.warn else Palette.text, bold = true)

        val topBar = row(
            ghostButton("← 목록") { finish() },
            ghostButton("기록 삭제") {
                HandoverStore(this).delete(r.id)
                Recorder.savedCount++
                finish()
            },
            actionButton("📱 휴대폰으로 받기") {
                startActivity(Intent(this, QrActivity::class.java).putExtra("url", Share.handoverUrl(r)))
            },
            gap = 16, weights = false,
        )

        val verdict = if (r.suspiciousDistance)
            label("⚠️ ${r.mode.label}치고 이동 거리가 깁니다 (%.1f km). 맡긴 동안 운행됐을 수 있어요.".format(r.distanceKm), 26f, Palette.warn, bold = true)
        else label("✓ 맡긴 동안 특이한 운행은 없었습니다.", 26f, Palette.good, bold = true)

        val changes = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val list = r.changes()
        if (list.isEmpty()) changes.addView(label("바뀐 설정 없음", 24f, Palette.sub))
        list.forEach { (name, diff) -> changes.addView(row(
            label(name, 24f, Palette.sub).apply { width = 340 }, label(diff, 26f, Palette.text), weights = false,
        ).apply { setPadding(0, 6, 0, 6) }) }

        val summary = card(
            label("맡김 리포트 · ${r.mode.icon} ${r.mode.label}", 22f, Palette.sub),
            label("${fmt.format(Date(r.start))} ~ ${fmt.format(Date(r.end))}", 36f, Palette.text, bold = true),
            LinearLayout(this).apply { space(12) },
            verdict,
            LinearLayout(this).apply { space(20) },
            row(
                tile("주행 거리", value("%.1f km".format(r.distanceKm), r.suspiciousDistance)),
                tile("맡긴 시간", value("%.0f분".format(r.durationMin))),
                tile("최고 속도", value("%.0f km/h".format(r.maxSpeedKph), r.maxSpeedKph > 80)),
            ),
            LinearLayout(this).apply { space(16) },
            row(
                tile("급제동", value("${r.hardBrakes}회", r.hardBrakes > 0)),
                tile("급가속", value("${r.hardAccels}회", r.hardAccels > 0)),
                tile("바뀐 설정", value("${list.size}개", list.isNotEmpty())),
            ),
            LinearLayout(this).apply { space(24) },
            label("바뀐 설정", 22f, Palette.sub),
            changes,
        )
        val route = card(
            label("이동 경로", 22f, Palette.sub),
            RouteView(this, r.path, null).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 560).apply { topMargin = 16 }
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
            addView(topBar); space(24); addView(columns)
        }
        return ScrollView(this).apply { setBackgroundColor(Palette.bg); addView(root) }
    }
}
