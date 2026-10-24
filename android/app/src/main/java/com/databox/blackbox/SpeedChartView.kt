package com.databox.blackbox

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/**
 * 사고 전후 그래프: 속도 선 + 브레이크(빨강)·방향지시등(노랑) 구간 띠 + 충돌 시점 세로선.
 */
class SpeedChartView(context: Context, private val report: CrashReport) : View(context) {

    private val from = report.event.crashTime - RecorderService.PRE_MS
    private val to = report.event.crashTime + RecorderService.POST_MS

    private val axis = Paint().apply { color = Color.parseColor("#5B6B82"); strokeWidth = 2f; textSize = 26f }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.LTGRAY; textSize = 26f }
    private val speedLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4FC3F7"); strokeWidth = 5f; style = Paint.Style.STROKE
    }
    private val brakeBand = Paint().apply { color = Color.parseColor("#E53935") }
    private val signalBand = Paint().apply { color = Color.parseColor("#FDD835") }
    private val crashLine = Paint().apply { color = Color.parseColor("#FF5252"); strokeWidth = 4f }

    override fun onDraw(canvas: Canvas) {
        val left = 90f
        val right = width - 30f
        val top = 30f
        val bandH = 22f
        val chartBottom = height - 60f - bandH * 2 - 20f
        val maxSpeed = (report.series(SignalKey.SPEED_KPH).maxOfOrNull { it.value } ?: 0.0)
            .coerceAtLeast(20.0).let { (kotlin.math.ceil(it / 20) * 20) }

        fun x(t: Long) = left + (right - left) * ((t - from).toFloat() / (to - from))
        fun y(kph: Double) = chartBottom - (chartBottom - top) * (kph / maxSpeed).toFloat()

        // 축과 눈금
        canvas.drawLine(left, chartBottom, right, chartBottom, axis)
        canvas.drawLine(left, top, left, chartBottom, axis)
        canvas.drawText("${maxSpeed.toInt()}", 20f, top + 10f, label)
        canvas.drawText("0", 50f, chartBottom, label)
        canvas.drawText("km/h", 10f, (top + chartBottom) / 2, label)
        for (sec in -30..10 step 10) {
            val tx = x(report.event.crashTime + sec * 1000L)
            canvas.drawText(if (sec == 0) "충돌" else "${sec}초", tx - 25f, height - 15f, label)
        }

        // 속도 선 (계단형: 값은 다음 값이 올 때까지 유지)
        val speed = report.series(SignalKey.SPEED_KPH)
        if (speed.isNotEmpty()) {
            val path = Path()
            speed.forEachIndexed { i, s ->
                val px = x(s.t.coerceIn(from, to))
                if (i == 0) path.moveTo(px, y(s.value)) else path.lineTo(px, y(s.value))
            }
            path.lineTo(x(to), y(speed.last().value))
            canvas.drawPath(path, speedLine)
        }

        // 브레이크 / 방향지시등 구간 띠
        val brakeTop = chartBottom + 15f
        val signalTop = brakeTop + bandH + 8f
        drawBands(canvas, SignalKey.BRAKE, brakeTop, bandH, brakeBand, ::x)
        drawBands(canvas, SignalKey.TURN_SIGNAL, signalTop, bandH, signalBand, ::x)
        canvas.drawText("브레이크", right - 150f, brakeTop + bandH - 2f, label)
        canvas.drawText("깜빡이", right - 150f, signalTop + bandH - 2f, label)

        // 충돌 시점
        val cx = x(report.event.crashTime)
        canvas.drawLine(cx, top, cx, signalTop + bandH, crashLine)
    }

    private fun drawBands(canvas: Canvas, key: SignalKey, top: Float, h: Float, paint: Paint, x: (Long) -> Float) {
        var onSince: Long? = null
        for (s in report.series(key)) {
            if (s.value != 0.0 && onSince == null) onSince = s.t
            if (s.value == 0.0 && onSince != null) {
                canvas.drawRect(x(onSince), top, x(s.t), top + h, paint)
                onSince = null
            }
        }
        onSince?.let { canvas.drawRect(x(it), top, x(to), top + h, paint) }
    }
}
