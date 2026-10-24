package com.databox.blackbox

import ai.pleos.playground.navi.constants.CautionPointType
import ai.pleos.playground.navi.constants.TurnType
import java.util.Calendar
import kotlin.math.abs

/** 골목 모드에서 운전자에게 알릴 사건. */
enum class AlleyEvent { ENTER_SOON, ENTER, EXIT, OVERSPEED, DANGER, HAZARD, HOTSPOT }

data class HazardAhead(val id: String, val label: String, val distanceM: Int)

/** 매초 계산되는 골목 상태. 화면 표시와 차량 제어가 이 값만 본다. */
data class AlleyState(
    val inAlley: Boolean = false,
    val enterSoon: Boolean = false,
    val targetKph: Int = 30,
    val level: Int = 0,                 // 0 일반도로 · 1 골목(기준 이내) · 2 기준 초과 · 3 위험
    val reasons: List<String> = emptyList(),
    val roadName: String? = null,
    val hazardsAhead: List<HazardAhead> = emptyList(),
    val hotspotCount: Int = 0,
    val night: Boolean = false,
    val rain: Boolean = false,
    val schoolTime: Boolean = false,
)

/**
 * 골목 진입 판단 + 위험도 계산.
 * 입력: 지도(OSM) 도로 등급, 내비 주의지점·다음 회전·제한속도, 급제동 핫스팟, 차량 신호(속도·깜빡이·핸들·전조등·와이퍼).
 * 센서(레이더·카메라) 없이 '구조적으로 위험한 순간'을 잡는 것이 목표다.
 */
class AlleyEngine {

    // ── 외부에서 갱신하는 입력 ──
    @Volatile var road: RoadContext? = null
    @Volatile var cautions: Map<CautionPointType, Int> = emptyMap()   // 종류 → 남은 거리(m)
    @Volatile var nextTurn: Pair<TurnType, Double>? = null             // 다음 회전, 남은 거리(m)
    @Volatile var naviSpeedLimit: Int? = null
    @Volatile var hotspots: List<Hotspot> = emptyList()

    var state = AlleyState(); private set

    private var belowScoreTicks = 0
    private var enterSoonAnnouncedAt = 0L
    private var lastOverspeedAt = 0L
    private val hazardAnnouncedAt = HashMap<String, Long>()
    private val hotspotAnnouncedAt = HashMap<String, Long>()

    fun tick(
        lat: Double?, lon: Double?, kph: Double, turnSignal: Int, steeringDeg: Double,
        lowBeam: Boolean, wiper: Boolean, now: Long = System.currentTimeMillis(),
    ): Pair<AlleyState, List<AlleyEvent>> {
        val events = ArrayList<AlleyEvent>()
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val weekday = cal.get(Calendar.DAY_OF_WEEK) in Calendar.MONDAY..Calendar.FRIDAY
        val night = lowBeam || hour >= 19 || hour < 6
        val schoolTime = weekday && (hour in 7..8 || hour in 13..16)
        val r = road
        val c = cautions

        // ── 골목 점수 ──
        val reasons = ArrayList<String>()
        var score = 0
        if (r?.isAlleyClass == true) { score += 2; reasons += "이면도로" + (r.roadName?.let { " · $it" } ?: "") }
        if (r?.maxspeedKph != null && r.maxspeedKph <= 30) { score += 1; reasons += "제한속도 ${r.maxspeedKph}" }
        naviSpeedLimit?.let { if (it in 1..30) { score += 1; reasons += "내비 제한속도 $it" } }
        c[CautionPointType.NARROW_ROAD]?.let { if (it <= 30) { score += 2; reasons += "좁은 길" } }
        c[CautionPointType.SCHOOL_ZONE]?.let { if (it <= 30) { score += 2; reasons += "어린이보호구역" } }
        c[CautionPointType.SPEED_BUMP]?.let { if (it <= 30) { score += 1; reasons += "과속방지턱" } }
        c[CautionPointType.ACCIDENT_PRONE_AREA]?.let { if (it <= 50) { score += 1; reasons += "사고다발구역" } }

        val wasIn = state.inAlley
        val inAlley = if (score >= 2) { belowScoreTicks = 0; true } else {
            // 교차로 지나며 잠깐 큰길 등급이 잡혀도 3초는 골목으로 유지
            belowScoreTicks++
            wasIn && belowScoreTicks < 3
        }

        // ── 진입 임박 ──
        val turning = turnSignal != 0 && abs(steeringDeg) > 25
        val turnAhead = nextTurn?.let { (t, d) -> d <= 60 && t in TURNS }
        val cautionAhead = listOf(CautionPointType.NARROW_ROAD, CautionPointType.SCHOOL_ZONE).any { c[it]?.let { d -> d in 31..80 } == true }
        val enterSoon = !inAlley && (turning || turnAhead == true || cautionAhead)
        if (enterSoon && now - enterSoonAnnouncedAt > 30_000) { enterSoonAnnouncedAt = now; events += AlleyEvent.ENTER_SOON }

        // ── 기준 속도 ──
        val schoolNear = r?.hazards?.any { h -> h.kind == "school" && lat != null && lon != null && Osm.distanceM(lat, lon, h.lat, h.lon) <= 150 } == true
        val target = when {
            c[CautionPointType.SCHOOL_ZONE]?.let { it <= 30 } == true -> 20
            r?.roadClass == "living_street" || r?.roadClass == "pedestrian" -> 20
            r?.maxspeedKph != null && r.maxspeedKph <= 20 -> 20
            schoolTime && schoolNear -> 20
            else -> 30
        }
        if (schoolTime && schoolNear) reasons += "등하교 시간"
        if (night) reasons += "야간"
        if (wiper) reasons += "우천"

        // ── 전방 구조적 위험 지점 ──
        val hazardsAhead = ArrayList<HazardAhead>()
        if (lat != null && lon != null && r != null) {
            for (h in r.hazards) {
                val d = Osm.distanceM(lat, lon, h.lat, h.lon).toInt()
                val limit = when (h.kind) { "crossing" -> 60; "bus_stop" -> 80; else -> 150 }
                if (d <= limit) hazardsAhead += HazardAhead("${h.kind}:${h.lat},${h.lon}", h.label, d)
            }
            hazardsAhead.sortBy { it.distanceM }
        }

        // ── 급제동 핫스팟 ──
        var hotspotCount = 0
        if (lat != null && lon != null) for (h in hotspots) {
            if (Osm.distanceM(lat, lon, h.lat, h.lon) <= 60) hotspotCount += h.count
        }
        // 격자 여러 개가 겹쳐도 안내는 90초에 한 번만
        if (hotspotCount >= 2 && now - (hotspotAnnouncedAt["any"] ?: 0L) > 90_000) {
            hotspotAnnouncedAt["any"] = now
            events += AlleyEvent.HOTSPOT
        }

        // ── 위험 단계 ──
        val over = kph - target
        val level = when {
            !inAlley -> 0
            hazardsAhead.isNotEmpty() && kph > target -> 3
            over > 10 -> 3
            over > 0 -> 2
            else -> 1
        }

        if (inAlley && !wasIn) events += AlleyEvent.ENTER
        if (!inAlley && wasIn) events += AlleyEvent.EXIT
        if (inAlley && level >= 2 && level > state.level && now - lastOverspeedAt > 8_000) {
            lastOverspeedAt = now
            events += if (level == 3) AlleyEvent.DANGER else AlleyEvent.OVERSPEED
        }
        if (inAlley && kph > target) for (h in hazardsAhead) {
            if (now - (hazardAnnouncedAt[h.id] ?: 0L) > 60_000) { hazardAnnouncedAt[h.id] = now; events += AlleyEvent.HAZARD; break }
        }

        state = AlleyState(
            inAlley = inAlley, enterSoon = enterSoon, targetKph = target, level = level,
            reasons = reasons.distinct(), roadName = r?.roadName, hazardsAhead = hazardsAhead,
            hotspotCount = hotspotCount, night = night, rain = wiper, schoolTime = schoolTime,
        )
        return state to events
    }

    companion object {
        private val TURNS = setOf(TurnType.TURN_LEFT, TurnType.TURN_RIGHT, TurnType.TURN_SHARP_L, TurnType.TURN_SHARP_R, TurnType.TURN_KEEP_L, TurnType.TURN_KEEP_R)
    }
}
