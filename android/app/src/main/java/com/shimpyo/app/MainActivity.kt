package com.shimpyo.app

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener

/**
 * 연결 확인용 테스트 화면.
 * 차량 값 읽기(주행거리·기어·방향지시등·설정 온도)와 쓰기(운전석 설정 온도)를 한 화면에서 확인한다.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var vehicle: Vehicle
    private val handler = Handler(Looper.getMainLooper())
    // 공조 구역 번호는 차종마다 달라서 capability 조회로 받아온다. 받기 전엔 운전석 기본값.
    private var powerArea = SeatArea(SeatValue.ROW_1_LEFT)
    private var tempArea = SeatArea(SeatValue.ROW_1_LEFT)

    private lateinit var distanceView: TextView
    private lateinit var gearView: TextView
    private lateinit var turnSignalView: TextView
    private lateinit var tempView: TextView
    private lateinit var logView: TextView

    private var targetTemp: Float? = null

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = ui { gearView.text = "기어: ${gearLabel(gear)}" }
        override fun onFailed(e: Exception) = log("기어 구독 실패: ${e.message}")
    }

    private val turnSignalListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(state: TurnSignalValue?) =
            ui { turnSignalView.text = "방향지시등: ${turnSignalLabel(state)}" }
        override fun onFailed(e: Exception) = log("방향지시등 구독 실패: ${e.message}")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildLayout())

        vehicle = Vehicle(this)
        vehicle.initialize()
        log("차량 서비스 연결 중…")

        // initialize()는 비동기로 연결되므로 잠시 후 첫 조회와 구독을 시작한다.
        handler.postDelayed({
            loadHvacAreas()
            refresh()
            vehicle.getGear().registerGearSelection(gearListener)
            vehicle.getTurnSignal().registerTurnSignalState(turnSignalListener)
        }, 1500)
    }

    override fun onDestroy() {
        vehicle.getGear().unregisterGearSelection(gearListener)
        vehicle.getTurnSignal().unregisterTurnSignalState(turnSignalListener)
        vehicle.release()
        super.onDestroy()
    }

    private fun loadHvacAreas() {
        vehicle.getHVAC().checkHvacPowerCapabilities(
            { areas: List<SeatArea> ->
                areas.firstOrNull()?.let { powerArea = it }
                log("에어컨 전원 지원 구역: ${areas.size}개")
            },
            { e -> log("에어컨 전원 지원 확인 실패: ${e.message}") },
        )
        vehicle.getHVAC().checkHvacTemperatureCapabilities(
            { bounds: Map<SeatArea, AreaBounds<Float>> ->
                bounds.keys.firstOrNull()?.let { tempArea = it }
                bounds.values.firstOrNull()?.let { log("온도 범위: ${it.minValue} ~ ${it.maxValue} ℃") }
                handler.post { refresh() }
            },
            { e -> log("온도 지원 확인 실패: ${e.message}") },
        )
    }

    private fun refresh() {
        vehicle.getOdometer().getCurrentDistanceDriven(
            { km -> ui { distanceView.text = "총 주행거리: %.1f km".format(km) } },
            { e -> log("주행거리 조회 실패: ${e.message}") },
        )
        vehicle.getGear().getCurrentGearSelectionState(
            { gear -> ui { gearView.text = "기어: ${gearLabel(gear)}" } },
            { e -> log("기어 조회 실패: ${e.message}") },
        )
        vehicle.getTurnSignal().getCurrentTurnSignalState(
            { state -> ui { turnSignalView.text = "방향지시등: ${turnSignalLabel(state)}" } },
            { e -> log("방향지시등 조회 실패: ${e.message}") },
        )
        vehicle.getHVAC().getHvacTemperature(
            tempArea, TemperatureUnit.CELSIUS,
            { temp ->
                targetTemp = temp
                ui { tempView.text = "운전석 설정 온도: %.1f ℃".format(temp) }
            },
            { e -> log("온도 조회 실패: ${e.message}") },
        )
        log("차량 값 조회 요청 완료")
    }

    private fun powerOnHvac() {
        vehicle.getHVAC().setHvacPower(
            powerArea, true,
            { _, _ ->
                log("에어컨 켜기 요청 완료")
                handler.postDelayed({ refresh() }, 800)
            },
            { e -> log("에어컨 켜기 실패: ${e.message}") },
        )
    }

    private fun changeTemp(delta: Float) {
        val current = targetTemp ?: return log("현재 온도를 아직 못 읽었어요")
        val next = current + delta
        vehicle.getHVAC().setHvacTemperature(
            tempArea, next, TemperatureUnit.CELSIUS,
            { _, _ ->
                log("온도 변경 요청: %.1f ℃".format(next))
                // 변경 요청 완료 ≠ 실제 반영. 다시 읽어서 확인한다.
                handler.postDelayed({ refresh() }, 500)
            },
            { e -> log("온도 변경 실패: ${e.message}") },
        )
    }

    private fun buildLayout(): LinearLayout {
        fun label(size: Float) = TextView(this).apply {
            textSize = size
            setTextColor(Color.WHITE)
            setPadding(0, 12, 0, 12)
        }
        fun button(text: String, onClick: () -> Unit) = Button(this).apply {
            this.text = text
            textSize = 26f
            setOnClickListener { onClick() }
        }

        distanceView = label(34f).apply { text = "총 주행거리: -" }
        gearView = label(34f).apply { text = "기어: -" }
        turnSignalView = label(34f).apply { text = "방향지시등: -" }
        tempView = label(34f).apply { text = "운전석 설정 온도: -" }
        logView = label(20f).apply { setTextColor(Color.LTGRAY) }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(button("에어컨 켜기") { powerOnHvac() })
            addView(button("온도 −1℃") { changeTemp(-1f) })
            addView(button("온도 +1℃") { changeTemp(1f) })
            addView(button("새로고침") { refresh() })
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setBackgroundColor(Color.parseColor("#0B1A2E"))
            setPadding(80, 60, 80, 60)
            addView(label(48f).apply { text = "안녕하세요, 쉼표 테스트 앱입니다" })
            addView(distanceView)
            addView(gearView)
            addView(turnSignalView)
            addView(tempView)
            addView(buttons)
            addView(logView)
        }
    }

    private fun gearLabel(gear: VehicleGear?) = when (gear) {
        VehicleGear.GEAR_PARK -> "P (주차)"
        VehicleGear.GEAR_REVERSE -> "R (후진)"
        VehicleGear.GEAR_NEUTRAL -> "N (중립)"
        VehicleGear.GEAR_DRIVE -> "D (주행)"
        null -> "알 수 없음"
        else -> gear.name
    }

    private fun turnSignalLabel(state: TurnSignalValue?) = when (state) {
        TurnSignalValue.LEFT -> "왼쪽 ◀"
        TurnSignalValue.RIGHT -> "오른쪽 ▶"
        TurnSignalValue.OFF -> "꺼짐"
        null -> "알 수 없음"
    }

    private fun ui(block: () -> Unit) = runOnUiThread(block)

    private fun log(message: String) = ui {
        logView.text = (message + "\n" + logView.text).lines().take(6).joinToString("\n")
    }
}
