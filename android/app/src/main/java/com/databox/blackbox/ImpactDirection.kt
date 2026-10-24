package com.databox.blackbox

import android.content.Context
import kotlin.math.abs
import kotlin.math.sqrt

data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Double) = Vec3(x * k, y * k, z * k)
    infix fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    val length get() = sqrt(this dot this)
    fun unit() = if (length < 1e-9) this else this * (1 / length)
}

/** 충격 방향 판정 결과. */
data class ImpactDirection(val label: String, val forwardG: Double, val lateralG: Double)

/**
 * 단말기가 차에 어떤 방향으로 달려 있는지 몰라도 되도록 자동 보정한다.
 * - 위쪽: 가속도 센서 평균(중력 방향)
 * - 앞쪽: 차속이 늘 때(가속)·줄 때(감속) 생기는 수평 가속도 방향을 누적
 * 보정이 끝나면 충돌 순간 가속도를 차 기준 앞뒤·좌우로 나눠 어디서 받혔는지 추정한다.
 */
class MountCalibration(context: Context) {
    private val prefs = context.getSharedPreferences("mount_calibration", Context.MODE_PRIVATE)
    private var up = Vec3(prefs.dbl("ux", 0.0), prefs.dbl("uy", 0.0), prefs.dbl("uz", 9.81))
    private var forwardSum = Vec3(prefs.dbl("fx", 0.0), prefs.dbl("fy", 0.0), prefs.dbl("fz", 0.0))
    var samples = prefs.getInt("n", 0); private set
    @Volatile var lastAccel = up; private set

    val calibrated get() = samples >= MIN_SAMPLES

    @Synchronized
    fun onAccel(a: Vec3) {
        lastAccel = a
        // 충격·급가감속 중이 아닐 때만 중력 평균에 반영
        if (abs(a.length - 9.81) < 1.5) up = up * (1 - UP_ALPHA) + a * UP_ALPHA
    }

    /** 차속 변화율(km/h per s)이 충분히 클 때 호출: +면 가속, -면 감속. */
    @Synchronized
    fun onLongitudinal(kphPerSec: Double) {
        val u = up.unit()
        val horizontal = lastAccel - u * (lastAccel dot u)
        if (horizontal.length < MIN_HORIZONTAL) return
        forwardSum = forwardSum + horizontal.unit() * (if (kphPerSec > 0) 1.0 else -1.0)
        samples++
        if (samples % 10 == 0) save()
    }

    @Synchronized
    fun classify(peak: Vec3): ImpactDirection? {
        if (!calibrated) return null
        val u = up.unit()
        val fwd = (forwardSum - u * (forwardSum dot u)).unit()
        val right = fwd cross u
        val h = peak - u * (peak dot u)
        val f = (h dot fwd) / 9.81
        val r = (h dot right) / 9.81
        // 받힌 쪽의 반대 방향으로 차가 밀린다
        val label = if (abs(f) >= abs(r)) {
            if (f < 0) "앞쪽에서 충격 (전방)" else "뒤쪽에서 충격 (후방)"
        } else {
            if (r > 0) "왼쪽에서 충격 (좌측면)" else "오른쪽에서 충격 (우측면)"
        }
        return ImpactDirection(label, f, r)
    }

    @Synchronized
    fun save() = prefs.edit()
        .putFloat("ux", up.x.toFloat()).putFloat("uy", up.y.toFloat()).putFloat("uz", up.z.toFloat())
        .putFloat("fx", forwardSum.x.toFloat()).putFloat("fy", forwardSum.y.toFloat()).putFloat("fz", forwardSum.z.toFloat())
        .putInt("n", samples).apply()

    private fun android.content.SharedPreferences.dbl(key: String, def: Double) = getFloat(key, def.toFloat()).toDouble()

    companion object {
        private const val MIN_SAMPLES = 15
        private const val UP_ALPHA = 0.02
        private const val MIN_HORIZONTAL = 0.6 // m/s², 약 0.06G
    }
}
