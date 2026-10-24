package com.shimpyo.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 메인: 기록 상태 + 실시간 신호 + 맡김 모드 + 기록 목록(사고/맡김 탭). */
class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var speed: TextView
    private lateinit var gear: TextView
    private lateinit var signal: TextView
    private lateinit var brake: TextView
    private lateinit var lights: TextView
    private lateinit var impact: TextView
    private lateinit var handoverBox: LinearLayout
    private lateinit var listBox: LinearLayout
    private lateinit var tabCrash: TextView
    private lateinit var tabHandover: TextView
    private var showCrashes = true
    private var shownCount = -1
    private var handoverShown: HandoverTracker? = null
    private var handoverRendered = false
    private var handoverTitle: TextView? = null
    private var handoverStats: TextView? = null

    private val tick = object : Runnable {
        override fun run() {
            renderLive()
            if (Recorder.savedCount != shownCount) renderList()
            if (Recorder.handover !== handoverShown || !handoverRendered) renderHandover()
            updateHandoverStats()
            handler.postDelayed(this, 300)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildLayout())
        requestPermissionsThenStart()
    }

    override fun onResume() {
        super.onResume()
        renderList()
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    private fun requestPermissionsThenStart() {
        val needed = listOf(
            "android.car.permission.CAR_SPEED",
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
        ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isEmpty()) startRecorder() else requestPermissions(needed.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        // 일부가 거부돼도 가능한 신호로 기록은 시작한다
        startRecorder()
    }

    private fun startRecorder() {
        if (!Recorder.running) startForegroundService(Intent(this, RecorderService::class.java))
    }

    // ── 실시간 ───────────────────────────────────────────────────────────

    private fun renderLive() {
        val b = Recorder.buffer
        val (text, color) = when {
            Recorder.pending != null -> "● 사고 감지 — 충돌 후 상황 기록 중" to Palette.bad
            Recorder.handover != null -> "● 기록 중 · ${Recorder.handover!!.mode.label} 맡김 모드" to Palette.warn
            Recorder.running -> "● 상시 기록 중" to Palette.good
            else -> "○ 기록 꺼짐" to Palette.sub
        }
        status.text = text
        status.setTextColor(color)

        speed.text = b.latest(SignalKey.SPEED_KPH)?.let { "%.0f".format(it) } ?: "-"
        gear.text = when (b.latest(SignalKey.GEAR)?.toInt()) { 0 -> "P"; 1 -> "R"; 2 -> "N"; 3 -> "D"; else -> "-" }
        signal.text = when (b.latest(SignalKey.TURN_SIGNAL)?.toInt()) { 1 -> "◀ 왼쪽"; 2 -> "오른쪽 ▶"; else -> "꺼짐" }
        signal.setTextColor(if ((b.latest(SignalKey.TURN_SIGNAL) ?: 0.0) != 0.0) Palette.warn else Palette.text)
        val braking = b.latest(SignalKey.BRAKE) == 1.0
        brake.text = if (braking) "밟음" else "-"
        brake.setTextColor(if (braking) Palette.bad else Palette.text)
        lights.text = when {
            b.latest(SignalKey.HIGH_BEAM) == 1.0 -> "상향"
            b.latest(SignalKey.LOW_BEAM) == 1.0 -> "켜짐"
            else -> "꺼짐"
        }
        impact.text = "%.2f G".format(Recorder.liveImpactG)
        impact.setTextColor(if (Recorder.liveImpactG >= RecorderService.IMPACT_THRESHOLD_G) Palette.bad else Palette.text)
    }

    // ── 맡김 모드 ─────────────────────────────────────────────────────────

    private fun renderHandover() {
        val h = Recorder.handover
        handoverShown = h
        handoverRendered = true
        handoverBox.removeAllViews()
        if (h == null) {
            handoverBox.addView(label("차를 남에게 맡길 때 켜 두세요", 22f, Palette.sub))
            handoverBox.space(16)
            HandoverMode.entries.chunked(2).forEachIndexed { i, pair ->
                if (i > 0) handoverBox.space(14)
                handoverBox.addView(row(*pair.map { mode ->
                    ghostButton("${mode.icon} ${mode.label}") { Recorder.service?.startHandover(mode) }
                }.toTypedArray(), gap = 14))
            }
        } else {
            handoverTitle = label("", 28f, Palette.warn, bold = true)
            handoverStats = label("", 24f, Palette.text)
            handoverBox.addView(handoverTitle)
            handoverBox.addView(handoverStats)
            updateHandoverStats()
            handoverBox.space(16)
            handoverBox.addView(row(actionButton("돌려받기 · 리포트 보기", Palette.warn) {
                Recorder.service?.finishHandover { record ->
                    startActivity(Intent(this, HandoverActivity::class.java).putExtra("id", record.id))
                }
            }, weights = false))
        }
    }

    private fun updateHandoverStats() {
        val h = Recorder.handover ?: return
        val title = handoverTitle ?: return
        val min = (System.currentTimeMillis() - h.start) / 60_000
        title.text = "${h.mode.icon} ${h.mode.label}에 맡김 · ${min}분 경과"
        handoverStats?.text = "주행 %.2f km · 최고 %.0f km/h · 급제동 %d · 급가속 %d".format(
            h.distanceKm, h.maxSpeedKph, h.hardBrakes, h.hardAccels,
        )
    }

    // ── 기록 목록 ─────────────────────────────────────────────────────────

    private fun renderList() {
        shownCount = Recorder.savedCount
        tabCrash.background = if (showCrashes) rounded(Palette.cardHi, 18f) else null
        tabHandover.background = if (!showCrashes) rounded(Palette.cardHi, 18f) else null
        listBox.removeAllViews()
        val fmt = SimpleDateFormat("MM.dd HH:mm:ss", Locale.KOREA)
        if (showCrashes) {
            val events = CrashStore(this).list()
            if (events.isEmpty()) listBox.addView(label("사고 기록이 없습니다. 충격이 감지되면 자동으로 저장됩니다.", 22f, Palette.sub))
            events.forEach { e ->
                val r = CrashReport(e)
                listBox.addView(listItem(
                    "💥 ${fmt.format(Date(e.crashTime))}",
                    "${e.cause} %.1fG · ${r.speedAtCrash?.let { "%.0f km/h".format(it) } ?: "-"}".format(e.peakG),
                ) { startActivity(Intent(this, ReportActivity::class.java).putExtra("id", e.id)) })
            }
        } else {
            val records = HandoverStore(this).list()
            if (records.isEmpty()) listBox.addView(label("맡김 기록이 없습니다.", 22f, Palette.sub))
            records.forEach { r ->
                listBox.addView(listItem(
                    "${r.mode.icon} ${r.mode.label} · ${fmt.format(Date(r.start))}",
                    "%.1f km · %.0f분%s".format(r.distanceKm, r.durationMin, if (r.suspiciousDistance) " · ⚠️ 이동 의심" else ""),
                ) { startActivity(Intent(this, HandoverActivity::class.java).putExtra("id", r.id)) })
            }
        }
    }

    private fun listItem(title: String, sub: String, onClick: () -> Unit) = card(
        label(title, 26f, Palette.text, bold = true), label(sub, 22f, Palette.sub), pad = 26, color = Palette.cardHi,
    ).apply {
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = 16 }
    }

    // ── 레이아웃 ─────────────────────────────────────────────────────────

    private fun buildLayout(): ScrollView {
        fun big() = label("-", 40f, Palette.text, bold = true)
        status = label("", 26f, Palette.good, bold = true)
        speed = big(); gear = big(); signal = big(); brake = big(); lights = big(); impact = big()
        handoverBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tabCrash = label("사고 기록", 26f, Palette.text, bold = true).apply {
            setPadding(30, 12, 30, 12); setOnClickListener { showCrashes = true; renderList() }
        }
        tabHandover = label("맡김 기록", 26f, Palette.text, bold = true).apply {
            setPadding(30, 12, 30, 12); setOnClickListener { showCrashes = false; renderList() }
        }

        val live = card(
            label("실시간 차량 상태", 22f, Palette.sub),
            LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; space(16) },
            row(tile("속도 km/h", speed), tile("기어", gear), tile("방향지시등", signal)),
            LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; space(16) },
            row(tile("브레이크", brake), tile("전조등", lights), tile("충격 (기준 2.5G)", impact)),
        )
        val handover = card(label("맡김 모드", 22f, Palette.sub), handoverBox)
        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(live); space(24); addView(handover)
        }
        val right = card(row(tabCrash, tabHandover, gap = 8, weights = false), LinearLayout(this).apply { space(16) }, listBox)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label("데이터 블랙박스", 44f, Palette.text, bold = true))
            addView(label("영상이 못 남기는 내 차의 순간을 숫자로 남깁니다", 22f, Palette.sub))
            space(10)
            addView(status)
        }
        val columns = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.15f))
            addView(right, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = 28 })
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 44, 56, 44)
            addView(header); space(28); addView(columns)
        }
        return ScrollView(this).apply { setBackgroundColor(Palette.bg); addView(root) }
    }
}
