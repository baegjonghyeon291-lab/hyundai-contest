package com.shimpyo.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 저장된 사고 기록에서 과실 판단에 쓰일 사실만 뽑아낸다. */
class CrashReport(val event: CrashEvent) {

    private val byKey = event.samples.groupBy { it.key }.mapValues { (_, v) -> v.sortedBy { it.t } }
    private val crash = event.crashTime

    fun series(key: SignalKey): List<Sample> = byKey[key].orEmpty()

    /** t 시점(포함) 직전의 값. */
    fun valueAt(key: SignalKey, t: Long): Double? = series(key).lastOrNull { it.t <= t }?.value

    /** 충돌 전 [withinMs] 안에서 값이 꺼짐→켜짐(또는 0→다른 값)으로 바뀐 마지막 시각. */
    private fun lastTurnedOnBefore(key: SignalKey, withinMs: Long): Long? {
        var prev = 0.0
        var at: Long? = null
        for (s in series(key)) {
            if (s.t > crash) break
            if (prev == 0.0 && s.value != 0.0 && s.t >= crash - withinMs) at = s.t
            prev = s.value
        }
        return at
    }

    val crashTimeText: String = SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.KOREA).format(Date(crash))

    val speedAtCrash: Double? = valueAt(SignalKey.SPEED_KPH, crash)

    val maxSpeed5sBefore: Double? = series(SignalKey.SPEED_KPH)
        .filter { it.t in (crash - 5_000)..crash }.maxOfOrNull { it.value }

    val brakeOnAt: Long? = lastTurnedOnBefore(SignalKey.BRAKE, 10_000)
    val brakeAtCrash: Boolean = valueAt(SignalKey.BRAKE, crash) == 1.0
    val speedAtBrake: Double? = brakeOnAt?.let { valueAt(SignalKey.SPEED_KPH, it) }

    val turnSignalAtCrash: Int = valueAt(SignalKey.TURN_SIGNAL, crash)?.toInt() ?: 0
    val turnSignalOnAt: Long? = lastTurnedOnBefore(SignalKey.TURN_SIGNAL, 30_000)

    val gearAtCrash: Int? = valueAt(SignalKey.GEAR, crash)?.toInt()
    val lowBeam: Boolean = valueAt(SignalKey.LOW_BEAM, crash) == 1.0
    val highBeam: Boolean = valueAt(SignalKey.HIGH_BEAM, crash) == 1.0
    val wiper: Boolean = valueAt(SignalKey.WIPER, crash) == 1.0
    val steeringAtCrash: Double? = valueAt(SignalKey.STEERING_DEG, crash)
    val lat: Double? = valueAt(SignalKey.LAT, crash)
    val lon: Double? = valueAt(SignalKey.LON, crash)

    fun secondsBefore(t: Long) = (crash - t) / 1000.0

    /** 기록 구간의 이동 경로 (위도, 경도). */
    fun path(): List<Pair<Double, Double>> {
        val lats = series(SignalKey.LAT)
        val lons = series(SignalKey.LON)
        return lats.mapNotNull { la -> lons.lastOrNull { it.t <= la.t }?.let { la.value to it.value } }
            .distinct()
    }

    /** 리포트 본문 줄들. (제목, 내용, 운전자에게 유리한 사실인지) */
    fun lines(): List<Triple<String, String, Boolean?>> = buildList {
        add(Triple("감지", "${event.cause} · 최대 충격 %.1f G".format(event.peakG), null))
        add(Triple("충돌 순간 속도", speedAtCrash?.let { "%.1f km/h".format(it) } ?: "기록 없음", null))
        maxSpeed5sBefore?.let { add(Triple("충돌 전 5초 최고 속도", "%.1f km/h".format(it), null)) }

        when {
            brakeOnAt != null -> add(Triple(
                "브레이크",
                "충돌 %.1f초 전 밟음 (그때 %.1f km/h → 충돌 시 %.1f km/h)".format(
                    secondsBefore(brakeOnAt), speedAtBrake ?: 0.0, speedAtCrash ?: 0.0,
                ),
                true,
            ))
            brakeAtCrash -> add(Triple("브레이크", "충돌 10초 이전부터 밟고 있음", true))
            else -> add(Triple("브레이크", "충돌 전 10초 동안 밟지 않음", false))
        }

        val dir = when (turnSignalAtCrash) { 1 -> "왼쪽"; 2 -> "오른쪽"; else -> null }
        add(
            if (dir != null) Triple(
                "방향지시등",
                "$dir 켜짐" + (turnSignalOnAt?.let { " (충돌 %.1f초 전 켬)".format(secondsBefore(it)) } ?: " (30초 이상 전부터)"),
                true,
            ) else Triple("방향지시등", "꺼짐", null),
        )

        add(Triple("전조등", when { highBeam -> "상향등 켜짐"; lowBeam -> "켜짐"; else -> "꺼짐" }, if (lowBeam || highBeam) true else null))
        add(Triple("와이퍼", if (wiper) "작동 중 (우천 추정)" else "꺼짐", null))
        add(Triple("기어", gearLabel(gearAtCrash), null))
        steeringAtCrash?.let { add(Triple("핸들 각도", "%.0f° %s".format(kotlin.math.abs(it), if (it < 0) "왼쪽" else if (it > 0) "오른쪽" else ""), null)) }
        add(Triple("위치", if (lat != null && lon != null) "%.5f, %.5f".format(lat, lon) else "기록 없음", null))
    }

    companion object {
        fun gearLabel(code: Int?) = when (code) { 0 -> "P (주차)"; 1 -> "R (후진)"; 2 -> "N (중립)"; 3 -> "D (주행)"; null -> "기록 없음"; else -> "기타" }
    }
}
