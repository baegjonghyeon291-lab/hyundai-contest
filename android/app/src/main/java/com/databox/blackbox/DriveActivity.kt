package com.databox.blackbox

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * 주행 중 화면 (Distraction Optimized).
 * 큰 글자 몇 개만 보여 준다: 골목 모드 여부·현재 속도·기준 속도·다음 위험 지점·마지막 안내.
 * 목록·설정·입력은 없고, 주행 제한이 풀리면 메인 화면으로 돌아간다.
 */
class DriveActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var car: Car? = null
    private lateinit var mode: TextView
    private lateinit var speed: TextView
    private lateinit var target: TextView
    private lateinit var hazard: TextView
    private lateinit var message: TextView
    private lateinit var root: LinearLayout

    private val tick = object : Runnable {
        override fun run() { render(); handler.postDelayed(this, 300) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildLayout())
        runCatching {
            val c = Car.createCar(this)
            car = c
            (c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager).registerListener { r ->
                if (!r.isRequiresDistractionOptimization) finish()
            }
        }
    }

    override fun onResume() { super.onResume(); handler.post(tick) }
    override fun onPause() { handler.removeCallbacks(tick); super.onPause() }
    override fun onDestroy() { runCatching { car?.disconnect() }; super.onDestroy() }

    private fun render() {
        val a = Recorder.alleyState
        val kph = Recorder.buffer.latest(SignalKey.SPEED_KPH) ?: 0.0
        val tint = when (a.level) { 3 -> Palette.bad; 2 -> Palette.warn; 1 -> Palette.good; else -> if (a.enterSoon) Palette.warn else Palette.sub }
        mode.text = when {
            a.inAlley && a.level >= 3 -> "위험 · 즉시 감속"
            a.inAlley && a.level == 2 -> "골목 · 기준 속도 초과"
            a.inAlley -> "골목 · 서행 중"
            a.enterSoon -> "곧 골목 · 준비"
            else -> "일반 도로"
        }
        mode.setTextColor(tint)
        root.setBackgroundColor(if (a.level >= 2) Color.argb(255, 60 + Color.red(tint) / 5, 20 + Color.green(tint) / 8, 24) else Palette.bg)
        speed.text = "%.0f".format(kph)
        speed.setTextColor(if (a.inAlley && kph > a.targetKph) Palette.bad else Palette.text)
        target.text = if (a.inAlley || a.enterSoon) "기준 ${a.targetKph}" else ""
        hazard.text = a.hazardsAhead.firstOrNull()?.let { "${it.label} ${it.distanceM}m" }
            ?: if (a.hotspotCount > 0) "급제동 지점 · 최근 ${a.hotspotCount}건" else a.reasons.take(2).joinToString(" · ")
        message.text = Recorder.lastAlleyMessage ?: ""
    }

    private fun buildLayout(): LinearLayout {
        mode = label("일반 도로", 44f, Palette.sub, bold = true).apply { gravity = Gravity.CENTER }
        speed = label("0", 150f, Palette.text, bold = true).apply { gravity = Gravity.CENTER }
        target = label("", 40f, Palette.sub, bold = true).apply { gravity = Gravity.CENTER }
        hazard = label("", 36f, Palette.warn, bold = true).apply { gravity = Gravity.CENTER }
        message = label("", 28f, Palette.accent).apply { gravity = Gravity.CENTER }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Palette.bg)
            setPadding(80, 40, 80, 40)
            addView(label("골목지킴이", 24f, Palette.sub).apply { gravity = Gravity.CENTER })
            addView(mode); space(10); addView(speed)
            addView(label("km/h", 26f, Palette.sub).apply { gravity = Gravity.CENTER })
            addView(target); space(24); addView(hazard); space(16); addView(message)
        }
        return root
    }
}
