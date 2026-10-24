package com.shimpyo.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.cos

/**
 * 지도 타일 없이 경로 모양만 그린다 (차 안에선 오프라인이어도 동작해야 함).
 * 실제 지도는 휴대폰으로 넘긴 리포트에서 보여준다.
 */
class RouteView(context: Context, private val path: List<Pair<Double, Double>>, private val mark: Pair<Double, Double>?) : View(context) {

    private val bg = Paint().apply { color = Color.parseColor("#13243B") }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4FC3F7"); strokeWidth = 6f; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND
    }
    private val startDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#66BB6A") }
    private val endDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF5252") }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.LTGRAY; textSize = 24f }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 24f, 24f, bg)
        val points = path.ifEmpty { listOfNotNull(mark) }
        if (points.isEmpty()) {
            canvas.drawText("위치 기록 없음", 30f, height / 2f, text)
            return
        }
        // 위도에 따른 경도 축소를 반영해 실제 비율로 그림
        val k = cos(Math.toRadians(points.first().first))
        val xs = points.map { it.second * k }
        val ys = points.map { it.first }
        val span = maxOf(xs.max() - xs.min(), ys.max() - ys.min(), 0.0005)
        val pad = 40f
        val scale = (minOf(width, height) - pad * 2) / span
        val cx = (xs.max() + xs.min()) / 2
        val cy = (ys.max() + ys.min()) / 2
        fun px(i: Int) = (width / 2f + (xs[i] - cx) * scale).toFloat()
        fun py(i: Int) = (height / 2f - (ys[i] - cy) * scale).toFloat()

        if (points.size > 1) {
            val p = Path().apply { moveTo(px(0), py(0)); for (i in 1 until points.size) lineTo(px(i), py(i)) }
            canvas.drawPath(p, line)
            canvas.drawCircle(px(0), py(0), 12f, startDot)
        }
        canvas.drawCircle(px(points.lastIndex), py(points.lastIndex), 14f, endDot)
        canvas.drawText("%.5f, %.5f".format(points.last().first, points.last().second), 24f, height - 20f, text)
    }
}
