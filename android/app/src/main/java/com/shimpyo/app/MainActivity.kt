package com.shimpyo.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** 메인: 기록 상태(실시간 신호) + 저장된 사고 목록. */
class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var statusView: TextView
    private lateinit var liveView: TextView
    private lateinit var listBox: LinearLayout
    private var shownCount = -1

    private val tick = object : Runnable {
        override fun run() {
            renderLive()
            if (Recorder.savedCount != shownCount) renderList()
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

    private fun renderLive() {
        val b = Recorder.buffer
        val pending = Recorder.pending
        statusView.text = when {
            pending != null -> "🔴 사고 감지 — 충돌 후 상황 기록 중…"
            Recorder.running -> "● 기록 중"
            else -> "○ 기록 꺼짐"
        }
        statusView.setTextColor(if (pending != null) Color.parseColor("#FF5252") else Color.parseColor("#66BB6A"))

        val turn = when (b.latest(SignalKey.TURN_SIGNAL)?.toInt()) { 1 -> "◀ 왼쪽"; 2 -> "오른쪽 ▶"; else -> "꺼짐" }
        liveView.text = listOf(
            "속도  " + (b.latest(SignalKey.SPEED_KPH)?.let { "%.1f km/h".format(it) } ?: "-"),
            "기어  " + CrashReport.gearLabel(b.latest(SignalKey.GEAR)?.toInt()),
            "깜빡이  $turn",
            "브레이크  " + if (b.latest(SignalKey.BRAKE) == 1.0) "밟음" else "-",
            "전조등  " + if (b.latest(SignalKey.LOW_BEAM) == 1.0) "켜짐" else "꺼짐",
            "충격  %.2f G  (사고 기준 %.1f G)".format(Recorder.liveImpactG, RecorderService.IMPACT_THRESHOLD_G),
        ).joinToString("\n")
    }

    private fun renderList() {
        shownCount = Recorder.savedCount
        listBox.removeAllViews()
        val events = CrashStore(this).list()
        if (events.isEmpty()) {
            listBox.addView(text("저장된 사고 기록이 없습니다.", 28f, Color.GRAY))
            return
        }
        events.forEach { e ->
            val r = CrashReport(e)
            listBox.addView(Button(this).apply {
                text = "${r.crashTimeText}   ${e.cause} %.1fG   %s".format(
                    e.peakG, r.speedAtCrash?.let { "%.0f km/h".format(it) } ?: "",
                )
                textSize = 26f
                isAllCaps = false
                setOnClickListener {
                    startActivity(Intent(this@MainActivity, ReportActivity::class.java).putExtra("id", e.id))
                }
            })
        }
    }

    private fun buildLayout(): ScrollView {
        statusView = text("", 36f, Color.WHITE)
        liveView = text("", 30f, Color.WHITE).apply { setLineSpacing(8f, 1f) }
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text("실시간 차량 상태", 28f, Color.LTGRAY))
            addView(liveView)
        }
        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 0, 0, 0)
            addView(text("사고 기록", 28f, Color.LTGRAY))
            addView(listBox)
        }
        val columns = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(right, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B1A2E"))
            setPadding(70, 50, 70, 50)
            addView(text("데이터 블랙박스", 46f, Color.WHITE))
            addView(text("영상이 못 남기는 내 차의 순간을 숫자로 남깁니다", 24f, Color.GRAY))
            addView(statusView)
            addView(columns)
        }
        return ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0B1A2E")); addView(root) }
    }

    private fun text(value: String, size: Float, color: Int) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        setPadding(0, 10, 0, 10)
    }
}
