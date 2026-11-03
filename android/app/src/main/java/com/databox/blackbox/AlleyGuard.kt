package com.databox.blackbox

import ai.pleos.playground.navi.data.CautionPoint
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import android.content.Context
import android.media.AudioManager
import android.util.Log

/**
 * 골목지킴이 본체.
 * 신호를 모아 [AlleyEngine]에 넣고, 결과에 따라 음성 안내와 차량 제어(조명·무드등·창문·볼륨·김서림)를 한다.
 * 모든 제어는 설정에서 끌 수 있고, 골목을 벗어나면 원래 상태로 되돌린다.
 */
class AlleyGuard(
    private val context: Context,
    private val vehicle: Vehicle,
    private val say: (String) -> Unit,
    private val warn: (String) -> Unit,
    private val notify: (title: String, text: String, urgent: Boolean) -> Unit = { _, _, _ -> },
) {
    val engine = AlleyEngine()
    private val settings = Settings(context)
    private val buffer get() = Recorder.buffer

    // 차량 신호 (서비스가 갱신)
    @Volatile var lat: Double? = null
    @Volatile var lon: Double? = null
    @Volatile var speedKph = 0.0
    @Volatile var turnSignal = 0
    @Volatile var steeringDeg = 0.0
    @Volatile var lowBeam = false
    @Volatile var wiper = false
    @Volatile var gearPark = true

    private var navi: NaviHelper? = null
    private val driverSeat = SeatArea(SeatValue.ROW_1_LEFT)
    private val driverWindow = WindowArea(WindowValue.ROW_1_LEFT)
    private val windshield = WindowArea(WindowValue.FRONT_WINDSHIELD)
    private var windowMax = 100

    // 골목 진입 전 상태 (벗어날 때 복원)
    private var applied = false
    private var prevLowBeam: LightState? = null
    private var prevFog: LightState? = null
    private var prevCabin: LightState? = null
    private var prevWindow: Int? = null
    private var prevVolume: Int? = null
    private var defrostApplied = false

    // 급제동 감지 (핫스팟 업로드용)
    private val recent = ArrayDeque<Pair<Long, Double>>()
    private var lastHardBrakeAt = 0L

    private val doorListeners = mutableListOf<() -> Unit>()
    private val doorOpenAnnouncedAt = HashMap<String, Long>()

    // ── 시작/종료 ────────────────────────────────────────────────────────

    fun start() {
        runCatching { startNavi() }.onFailure { warn("내비 연동 실패: ${it.message}") }
        runCatching {
            vehicle.getWindow().checkWindowPositionCapabilities(
                { bounds: Map<WindowArea, AreaBounds<Int>> -> bounds[driverWindow]?.let { windowMax = it.maxValue } },
                { e -> warn("창문 범위 확인 실패: ${e.message}") },
            )
        }
        runCatching { watchRearDoors() }.onFailure { warn("뒷문 감시 실패: ${it.message}") }
    }

    fun stop() {
        restore()
        doorListeners.forEach { runCatching { it() } }
        runCatching { navi?.release() }
    }

    private fun startNavi() {
        val n = NaviHelper(context)
        n.addListener(object : NaviHelperEventListener {
            override fun onCautionPointInfo(points: List<CautionPoint>) {
                engine.cautions = points.associate { it.type to it.remainDistance }
            }
            override fun onTBTInfo(tbt: List<TBTInfo>) {
                engine.nextTurn = tbt.firstOrNull()?.let { it.type to it.distance }
            }
            override fun onCurrentRoadSpeedLimit(limit: Int) {
                engine.naviSpeedLimit = limit.takeIf { it > 0 }
            }
        })
        n.initialize({ Log.i(TAG, "내비 연동 준비됨") }, { e -> warn("내비 초기화 실패: ${e.message}") })
        navi = n
    }

    // ── 입력 ─────────────────────────────────────────────────────────────

    fun onPosition(lat: Double, lon: Double) {
        this.lat = lat; this.lon = lon
        // 조회 실패로 새 도로 정보를 못 받아도, 예전 지점에서 120m 넘게 멀어졌으면 '모름'으로 둔다 (골목 이탈이 걸리도록)
        engine.road?.let { if (Osm.distanceM(lat, lon, it.lat, it.lon) > 120) engine.road = null }
        Osm.lookup(lat, lon) { engine.road = it }
        Hotspots.refresh(lat, lon)
        engine.hotspots = Hotspots.nearby
    }

    fun onSpeed(kph: Double) {
        speedKph = kph
        val now = System.currentTimeMillis()
        recent.addLast(now to kph)
        while (recent.size > 1 && now - recent.first().first > 1_000) recent.removeFirst()
        val (t0, v0) = recent.first()
        if (now - t0 >= 700 && now - lastHardBrakeAt > 5_000) {
            val perSec = (kph - v0) * 1000.0 / (now - t0)
            if (perSec <= -DriveStats.HARD_KPH_PER_SEC) {
                lastHardBrakeAt = now
                val la = lat; val lo = lon
                if (settings.shareHardBrakes && la != null && lo != null) Hotspots.report(la, lo, v0, engine.state.inAlley)
            }
        }
    }

    // ── 매초 ─────────────────────────────────────────────────────────────

    fun tick(now: Long) {
        val (s, events) = engine.tick(lat, lon, speedKph, turnSignal, steeringDeg, lowBeam, wiper, now)
        Recorder.alleyState = s
        buffer.add(SignalKey.ALLEY, if (s.inAlley) 1.0 else 0.0, now)
        buffer.add(SignalKey.ALLEY_TARGET, s.targetKph.toDouble(), now)
        buffer.add(SignalKey.ALLEY_LEVEL, s.level.toDouble(), now)

        for (e in events) when (e) {
            AlleyEvent.ENTER_SOON -> announce("곧 골목길입니다. 브레이크에 발을 올리고 ${s.targetKph} 이하로 준비하세요.", now)
            AlleyEvent.ENTER -> {
                val extra = buildList {
                    if (s.schoolTime) add("등하교 시간이에요")
                    if (s.night) add("야간이라 보행자가 늦게 보여요")
                    if (s.rain) add("비가 와서 제동거리가 길어요")
                }.joinToString(" ")
                announce("골목길 진입. ${s.targetKph} 이하로 서행하세요. $extra".trim(), now)
                apply(s)
            }
            AlleyEvent.EXIT -> { announce("골목을 벗어났습니다.", now); restore() }
            AlleyEvent.OVERSPEED -> { announce("기준 속도 ${s.targetKph}을 넘었어요. 속도를 줄이세요.", now); ambient(RED) }
            AlleyEvent.DANGER -> {
                val h = s.hazardsAhead.firstOrNull()
                announce(if (h != null) "위험! ${h.label} ${h.distanceM}미터 앞. 즉시 감속하세요." else "위험! 골목에서 과속입니다. 즉시 감속하세요.", now)
                ambient(RED)
            }
            AlleyEvent.HAZARD -> s.hazardsAhead.firstOrNull()?.let { announce("${it.label} ${it.distanceM}미터 앞. 사람이 나올 수 있어요.", now) }
            AlleyEvent.HOTSPOT -> announce("이 구간에서 다른 운전자들이 최근 ${s.hotspotCount}번 급제동했어요. 조심하세요.", now)
        }
        if (applied && s.level == 1 && events.isEmpty()) ambient(AMBER)
    }

    private fun announce(text: String, now: Long) {
        say(text)
        buffer.add(SignalKey.ALLEY_WARNED, 1.0, now)
        Recorder.lastAlleyMessage = text
        // 주행 중엔 앱 화면이 가려지므로 헤드업 알림으로도 보여 준다 (음성과 같은 내용, 큰 글자 한 줄)
        val s = engine.state
        val title = when {
            s.inAlley && s.level >= 3 -> "⚠ 위험 · 즉시 감속"
            s.inAlley && s.level == 2 -> "골목 · 기준 ${s.targetKph} 초과"
            s.inAlley -> "골목 모드 · 기준 ${s.targetKph}"
            else -> "골목지킴이"
        }
        notify(title, text, s.level >= 2)
    }

    // ── 차량 제어 ─────────────────────────────────────────────────────────

    private fun apply(s: AlleyState) {
        if (applied) return
        applied = true
        val light = vehicle.getLight()
        if (settings.autoLights) runCatching {
            light.getCurrentLowBeamLightState({ st -> prevLowBeam = st
                if (st != LightState.ON) light.setLowBeamLightSwitch(LightSwitch.ON, { }, { e -> warn("전조등: ${e.message}") })
            }, { e -> warn("전조등 상태: ${e.message}") })
            light.getCurrentFrontFogLightState({ st -> prevFog = st
                if (st != LightState.ON) light.setFrontFogLightSwitch(LightSwitch.ON, { }, { e -> warn("안개등: ${e.message}") })
            }, { e -> warn("안개등 상태: ${e.message}") })
            if (s.night) light.getCurrentCabinLightState({ st -> prevCabin = st
                if (st == LightState.ON) light.setCabinLightSwitch(LightSwitch.OFF, { }, { e -> warn("실내등: ${e.message}") })
            }, { e -> warn("실내등 상태: ${e.message}") })
            buffer.add(SignalKey.AUTO_LIGHTS, 1.0)
            // 제어가 실제 반영됐는지 되읽어 기록 (에뮬레이터·차종별 지원 확인용)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                light.getCurrentLowBeamLightState({ st -> Log.i(TAG, "전조등 되읽기: $st") }, { })
                vehicle.getWindow().getCurrentWindowPosition(driverWindow, { p -> Log.i(TAG, "창문 되읽기: $p / max $windowMax") }, { })
            }, 1500)
        }
        ambient(AMBER)
        // 비 올 때(와이퍼 작동)는 창문을 열지 않는다
        if (settings.autoWindow && !s.rain) runCatching {
            vehicle.getWindow().getCurrentWindowPosition(driverWindow, { pos -> prevWindow = pos
                val crack = maxOf(1, windowMax / 10)
                if ((pos ?: 0) < crack) vehicle.getWindow().setWindowPosition(driverWindow, crack, { _, _ -> }, { e -> warn("창문: ${e.message}") })
            }, { e -> warn("창문 상태: ${e.message}") })
        }
        if (settings.autoVolume) runCatching {
            val am = context.getSystemService(AudioManager::class.java)
            val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            prevVolume = cur
            am.setStreamVolume(AudioManager.STREAM_MUSIC, cur / 2, 0)
        }.onFailure { warn("볼륨: ${it.message}") }
        if (s.rain) runCatching {
            vehicle.getHVAC().setHvacDefroster(windshield, true, { _, _ -> defrostApplied = true }, { e -> warn("김서림 제거: ${e.message}") })
        }
    }

    private fun restore() {
        if (!applied) return
        applied = false
        val light = vehicle.getLight()
        runCatching {
            prevLowBeam?.let { if (it != LightState.ON) light.setLowBeamLightSwitch(LightSwitch.OFF, { }, { }) }
            prevFog?.let { if (it != LightState.ON) light.setFrontFogLightSwitch(LightSwitch.OFF, { }, { }) }
            prevCabin?.let { if (it == LightState.ON) light.setCabinLightSwitch(LightSwitch.ON, { }, { }) }
            ambientArea?.let { light.setAmbientLightSwitch(it, LightSwitch.AUTOMATIC, { _, _ -> }, { }) }
            lastAmbient = null
        }
        runCatching { prevWindow?.let { vehicle.getWindow().setWindowPosition(driverWindow, it, { _, _ -> }, { }) } }
        runCatching { prevVolume?.let { context.getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_MUSIC, it, 0) } }
        if (defrostApplied) runCatching { vehicle.getHVAC().setHvacDefroster(windshield, false, { _, _ -> }, { }) }
        prevLowBeam = null; prevFog = null; prevCabin = null; prevWindow = null; prevVolume = null; defrostApplied = false
        buffer.add(SignalKey.AUTO_LIGHTS, 0.0)
    }

    // 무드등 구역은 차종마다 달라 capability로 받는다. 같은 색은 반복 전송하지 않는다.
    private var ambientArea: SeatArea? = null
    private var ambientChecked = false
    private var lastAmbient: Int? = null

    private fun ambient(color: Int) {
        if (lastAmbient == color) return
        val light = vehicle.getLight()
        if (!ambientChecked) {
            ambientChecked = true
            runCatching {
                light.checkAmbientLightBrightnessColorCapabilities(
                    { _: CapabilityConfig, areas: List<SeatArea> -> ambientArea = areas.firstOrNull(); if (ambientArea != null) ambient(color) },
                    { e -> warn("무드등 미지원: ${e.message}") },
                )
            }
            return
        }
        val area = ambientArea ?: return
        lastAmbient = color
        runCatching {
            light.setAmbientLightSwitch(area, LightSwitch.ON, { _, _ -> }, { })
            light.setAmbientLightBrightnessColor(area, color, { _, _ -> }, { e -> warn("무드등: ${e.message}") })
        }
    }

    // ── 하차 측면 안내 ───────────────────────────────────────────────────

    private fun watchRearDoors() {
        for ((value, name) in listOf(DoorValue.ROW_2_LEFT to "뒷좌석 왼쪽", DoorValue.ROW_2_RIGHT to "뒷좌석 오른쪽")) {
            val area = DoorArea(value)
            val l = object : DoorPositionListener {
                override fun onPositionUpdated(pos: Int?) { if ((pos ?: 0) > 0) onRearDoorOpened(name) }
                override fun onFailed(e: Exception) = warn("뒷문($name): ${e.message}")
            }
            vehicle.getDoor().registerDoorPosition(area, l)
            doorListeners += { vehicle.getDoor().unregisterDoorPosition(area, l) }
        }
    }

    private fun onRearDoorOpened(name: String) {
        val now = System.currentTimeMillis()
        if (!gearPark || engine.road?.isAlleyClass != true) return
        if (name !in Recorder.occupiedSeats) return
        if (now - (doorOpenAnnouncedAt[name] ?: 0L) < 60_000) return
        doorOpenAnnouncedAt[name] = now
        say("골목길입니다. 뒤에서 오는 자전거나 오토바이를 확인하고 내리세요.")
        Recorder.lastAlleyMessage = "하차 안내: $name"
    }

    companion object {
        private const val TAG = "DataBlackBox"
        private const val AMBER = 0xFFFFB300.toInt()
        private const val RED = 0xFFFF1744.toInt()
    }
}
