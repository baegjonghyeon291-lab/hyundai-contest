package com.databox.blackbox

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.SeatAirbagDeployedListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.OnInitializeListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 시동 중 상시 기록 서비스.
 * 모든 차량 신호를 [Recorder.buffer]에 쌓고, 충격·에어백으로 사고를 감지하면 전후 구간을 저장한다.
 */
class RecorderService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var vehicle: Vehicle
    private lateinit var location: FusedLocation
    private lateinit var sensorManager: SensorManager
    private var car: Car? = null
    private var carProps: CarPropertyManager? = null
    private val buffer get() = Recorder.buffer

    private var lastAccelSavedAt = 0L
    private var lastCrashAt = 0L
    private val airbagAreas = mutableListOf<SeatArea>()
    private val frontWiper = WindowArea(WindowValue.FRONT_WINDSHIELD)

    // 시트·공조 구역 번호는 차종마다 달라서 capability로 받아 둔다
    private var seatArea: SeatArea? = null
    private var hvacArea: SeatArea? = null

    // 충격 방향 자동 보정
    private lateinit var calibration: MountCalibration
    private val recentSpeed = ArrayDeque<Pair<Long, Double>>()
    private var lastCalibratedAt = 0L
    @Volatile private var peakVector: Vec3? = null

    // 주행 일지: P 상태가 이 시간 이상 이어지면 주행 종료
    private var parkedSince: Long? = null

    // 안전벨트 안내: 좌석별 탑승·착용 상태
    private val occupied = HashMap<String, Boolean>()
    private val buckled = HashMap<String, Boolean>()
    private val lastBeltNoticeAt = HashMap<String, Long>()
    private val seatListeners = mutableListOf<() -> Unit>()

    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false

    private val ticker = object : Runnable {
        override fun run() {
            runCatching { onEverySecond() }.onFailure { warn("주기 작업 오류: ${it.message}") }
            handler.postDelayed(this, 1_000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // 시스템이 서비스를 정리해도 다시 살아나 기록을 이어 간다
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotice()
        Recorder.running = true
        Recorder.service = this
        calibration = MountCalibration(this)
        Recorder.calibrationSamples = calibration.samples
        TripStore(this).purgeOlderThan(TripStore.RETENTION_DAYS)
        initTts()

        vehicle = Vehicle(this).also { it.initialize() }
        location = FusedLocation(this).also { it.initialize() }
        // initialize()는 비동기 연결이라 잠시 후 구독한다.
        // 신호 하나가 실패해도 기록 전체가 멈추지 않게 각각 따로 감싼다.
        handler.postDelayed({
            runCatching { subscribeVehicle() }.onFailure { warn("차량 신호 구독 실패: ${it.message}") }
            runCatching { subscribeLocation() }.onFailure { warn("위치 구독 실패: ${it.message}") }
            runCatching { subscribeSeats() }.onFailure { warn("좌석 구독 실패: ${it.message}") }
        }, 1500)
        subscribeSpeed()
        subscribeAccelerometer()
        handler.postDelayed(ticker, 2_000)
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        endTrip()
        calibration.save()
        tts?.release()
        seatListeners.forEach { runCatching { it() } }
        Recorder.running = false
        Recorder.service = null
        sensorManager.unregisterListener(accelListener)
        carProps?.unregisterCallback(speedCallback)
        car?.disconnect()
        runCatching {
            vehicle.getGear().unregisterGearSelection(gearListener)
            vehicle.getTurnSignal().unregisterTurnSignalState(turnSignalListener)
            vehicle.getBrake().unregisterBrakePedalStatus(brakeListener)
            vehicle.getLight().unregisterLowBeamLightState(lowBeamListener)
            vehicle.getLight().unregisterHighBeamLightState(highBeamListener)
            vehicle.getWiper().unregisterWiperState(frontWiper, wiperListener)
            vehicle.getSteeringWheel().unregisterSteeringWheelAngle(steeringListener)
            airbagAreas.forEach { vehicle.getSafety().unregisterSeatAirbagDeployed(it, airbagListener) }
            location.unregisterFusedLocationCallback(locationListener)
            getSystemService(android.location.LocationManager::class.java).removeUpdates(gpsListener)
        }
        vehicle.release()
        location.release()
        super.onDestroy()
    }

    // ── 차속: 차량 표준 API (바퀴 속도 기반, GPS보다 정확) ──────────────────────

    private val speedCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val mps = (value.value as? Float) ?: return
            val kph = mps * 3.6
            buffer.add(SignalKey.SPEED_KPH, kph)
            Recorder.handover?.onSpeed(kph)
            Recorder.trip?.onSpeed(kph)
            learnMountDirection(kph)
        }
        override fun onErrorEvent(propertyId: Int, areaId: Int) = warn("차속 수신 오류")
    }

    private fun subscribeSpeed() {
        runCatching {
            val c = Car.createCar(this)
            car = c
            carProps = (c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager).also {
                it.registerCallback(speedCallback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_FASTEST)
            }
        }.onFailure { warn("차속 구독 실패: ${it.message}") }
    }

    // ── Pleos Vehicle SDK 신호 ────────────────────────────────────────────

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            buffer.add(SignalKey.GEAR, gearCode(gear))
            if (gear == VehicleGear.GEAR_PARK) {
                if (parkedSince == null) parkedSince = System.currentTimeMillis()
            } else if (gear != null && gear != VehicleGear.GEAR_UNKNOWN) {
                parkedSince = null
                if (Recorder.trip == null) Recorder.trip = TripTracker()
            }
        }
        override fun onFailed(e: Exception) = warn("기어: ${e.message}")
    }
    private val turnSignalListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(state: TurnSignalValue?) = buffer.add(
            SignalKey.TURN_SIGNAL,
            when (state) { TurnSignalValue.LEFT -> 1.0; TurnSignalValue.RIGHT -> 2.0; else -> 0.0 },
        )
        override fun onFailed(e: Exception) = warn("방향지시등: ${e.message}")
    }
    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(pressed: Boolean?) = buffer.add(SignalKey.BRAKE, onOff(pressed == true))
        override fun onFailed(e: Exception) = warn("브레이크: ${e.message}")
    }
    private val lowBeamListener = object : LightStateListener {
        override fun onLightStateUpdated(state: LightState?) = buffer.add(SignalKey.LOW_BEAM, onOff(state == LightState.ON))
        override fun onFailed(e: Exception) = warn("전조등: ${e.message}")
    }
    private val highBeamListener = object : LightStateListener {
        override fun onLightStateUpdated(state: LightState?) = buffer.add(SignalKey.HIGH_BEAM, onOff(state == LightState.ON))
        override fun onFailed(e: Exception) = warn("상향등: ${e.message}")
    }
    private val wiperListener = object : WiperStateListener {
        override fun onWiperStateUpdated(state: WiperState?) = buffer.add(SignalKey.WIPER, onOff(state == WiperState.ON))
        override fun onFailed(e: Exception) = warn("와이퍼: ${e.message}")
    }
    private val steeringListener = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(deg: Float?) { deg?.let { buffer.add(SignalKey.STEERING_DEG, it.toDouble()) } }
        override fun onFailed(e: Exception) = warn("핸들 각도: ${e.message}")
    }
    private val airbagListener = object : SeatAirbagDeployedListener {
        override fun onSeatAirbagDeployUpdated(deployed: Boolean?) {
            buffer.add(SignalKey.AIRBAG, onOff(deployed == true))
            if (deployed == true) onCrash("에어백 전개", Recorder.buffer.latest(SignalKey.IMPACT_G) ?: 0.0)
        }
        override fun onFailed(e: Exception) = warn("에어백: ${e.message}")
    }

    private fun subscribeVehicle() {
        vehicle.getGear().registerGearSelection(gearListener)
        vehicle.getTurnSignal().registerTurnSignalState(turnSignalListener)
        vehicle.getBrake().registerBrakePedalStatus(brakeListener)
        vehicle.getLight().registerLowBeamLightState(lowBeamListener)
        vehicle.getLight().registerHighBeamLightState(highBeamListener)
        vehicle.getWiper().registerWiperState(frontWiper, wiperListener)
        vehicle.getSteeringWheel().registerSteeringWheelAngle(steeringListener)
        vehicle.getSafety().checkAirbagDeployedCapabilities(
            { _: CapabilityConfig, areas: List<SeatArea> ->
                airbagAreas += areas
                areas.forEach { vehicle.getSafety().registerSeatAirbagDeployed(it, airbagListener) }
            },
            { e -> warn("에어백 미지원: ${e.message}") },
        )
        vehicle.getSeat().checkSeatForeAftPositionCapabilities(
            { _: CapabilityConfig, bounds: Map<SeatArea, AreaBounds<Int>> ->
                // 운전석이 포함된 구역을 우선 사용
                seatArea = bounds.keys.firstOrNull { SeatValue.ROW_1_LEFT in it.values } ?: bounds.keys.firstOrNull()
                Log.i(TAG, "시트 구역: " + bounds.keys.joinToString { a -> a.values.joinToString("+") })
            },
            { e -> warn("시트 위치 미지원: ${e.message}") },
        )
        vehicle.getHVAC().checkHvacTemperatureCapabilities(
            { bounds: Map<SeatArea, AreaBounds<Float>> -> hvacArea = bounds.keys.firstOrNull() },
            { e -> warn("공조 온도 미지원: ${e.message}") },
        )
    }

    // ── 맡김 모드 ─────────────────────────────────────────────────────────

    fun startHandover(mode: HandoverMode) {
        readSettings { before -> Recorder.handover = HandoverTracker(mode, before) }
    }

    fun finishHandover(onSaved: (HandoverRecord) -> Unit) {
        val tracker = Recorder.handover ?: return
        readSettings { after ->
            val record = tracker.finish(after)
            HandoverStore(this).save(record)
            Recorder.handover = null
            Recorder.savedCount++
            onSaved(record)
        }
    }

    /** 시트 위치 3종 + 공조 온도를 읽는다. 지원 안 되는 값은 비워 두고, 1.5초 안에 응답이 없으면 있는 값만으로 진행. */
    private fun readSettings(done: (CarSettings) -> Unit) {
        var s = CarSettings()
        var finished = false
        val finish = Runnable { if (!finished) { finished = true; Log.i(TAG, "설정 스냅샷: $s (시트 구역 $seatArea)"); done(s) } }
        var remaining = 4
        fun got(update: (CarSettings) -> CarSettings) = handler.post {
            s = update(s)
            if (--remaining == 0) finish.run()
        }
        val skip: (Exception) -> Unit = { e -> Log.w(TAG, "설정 읽기 실패: ${e.message}"); got { it } }
        val seat = seatArea
        if (seat == null) repeat(3) { got { it } } else {
            vehicle.getSeat().getCurrentSeatForeAftPosition(seat, { v -> got { it.copy(seatForeAft = v) } }, skip)
            vehicle.getSeat().getCurrentSeatBackrestAnglePosition(seat, { v -> got { it.copy(seatBackrest = v) } }, skip)
            vehicle.getSeat().getCurrentSeatHeightPosition(seat, { v -> got { it.copy(seatHeight = v) } }, skip)
        }
        val hvac = hvacArea
        if (hvac == null) got { it } else vehicle.getHVAC().getHvacTemperature(
            hvac, TemperatureUnit.CELSIUS, { v -> got { it.copy(hvacTempC = v) } }, skip,
        )
        handler.postDelayed(finish, 1_500)
    }

    // ── 위치 ─────────────────────────────────────────────────────────────

    private val locationListener = object : FusedLocationListener {
        override fun onChanged(data: LocationData?) {
            data ?: return
            onPosition(data.latitude, data.longitude)
        }
        override fun onError(e: Exception) = warn("위치: ${e.message}")
    }

    // Pleos 위치가 안 들어오는 환경(에뮬레이터 등)을 위해 표준 GPS도 함께 받는다
    private val gpsListener = android.location.LocationListener { loc -> onPosition(loc.latitude, loc.longitude) }

    private fun onPosition(lat: Double, lon: Double) {
        buffer.add(SignalKey.LAT, lat)
        buffer.add(SignalKey.LON, lon)
        Recorder.handover?.onLocation(lat, lon)
        Recorder.trip?.onLocation(lat, lon)
    }

    private fun subscribeLocation() {
        if (location.isSupported()) location.registerFusedLocationCallback(locationListener)
        else warn("Pleos 위치 서비스 미지원")
        val lm = getSystemService(android.location.LocationManager::class.java)
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            lm.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER, 1_000L, 0f, gpsListener, Looper.getMainLooper())
        } else warn("위치 권한 없음")
    }

    // ── 충격 감지: 가속도 센서 ──────────────────────────────────────────────

    private val accelListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val (x, y, z) = event.values
            val impactG = abs(sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH - 1.0)
            val now = System.currentTimeMillis()
            val vec = Vec3(x.toDouble(), y.toDouble(), z.toDouble())
            Recorder.liveImpactG = impactG
            calibration.onAccel(vec)
            // 사고 구간에서 가장 센 충격 순간의 방향을 남긴다
            if (impactG >= IMPACT_THRESHOLD_G) {
                val p = peakVector
                if (p == null || abs(p.length / SensorManager.GRAVITY_EARTH - 1.0) < impactG) peakVector = vec
            }
            // 평상시엔 20Hz로만 저장, 충격 순간은 놓치지 않게 즉시 저장
            if (now - lastAccelSavedAt >= ACCEL_SAVE_INTERVAL_MS || impactG >= IMPACT_THRESHOLD_G) {
                lastAccelSavedAt = now
                buffer.add(SignalKey.IMPACT_G, impactG, now)
                buffer.add(SignalKey.ACCEL_X, x.toDouble(), now)
                buffer.add(SignalKey.ACCEL_Y, y.toDouble(), now)
                buffer.add(SignalKey.ACCEL_Z, z.toDouble(), now)
            }
            if (impactG >= IMPACT_THRESHOLD_G) onCrash("충격 감지", impactG)
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private fun subscribeAccelerometer() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?.let { sensorManager.registerListener(accelListener, it, SensorManager.SENSOR_DELAY_GAME) }
            ?: warn("가속도 센서 없음")
    }

    // ── 사고 저장 ─────────────────────────────────────────────────────────

    @Synchronized
    private fun onCrash(cause: String, g: Double) {
        val now = System.currentTimeMillis()
        // 한 사고에서 충격이 여러 번 잡혀도 1건으로 묶는다
        if (now - lastCrashAt < CRASH_COOLDOWN_MS) {
            Recorder.pending?.let { if (g > it.peakG) Recorder.pending = it.copy(peakG = g) }
            return
        }
        lastCrashAt = now
        Recorder.pending = PendingCrash(now, cause, g)
        Log.i(TAG, "사고 감지: $cause ${"%.1f".format(g)}G")
        // 충돌 후 상황까지 담기 위해 POST_MS 뒤에 저장
        handler.postDelayed({
            val p = Recorder.pending ?: return@postDelayed
            val event = CrashEvent(
                id = "crash_${p.time}",
                crashTime = p.time,
                cause = p.cause,
                peakG = p.peakG,
                samples = buffer.snapshot(p.time - PRE_MS, p.time + POST_MS),
                direction = peakVector?.let { calibration.classify(it) },
                notes = beltNotesAt(p.time),
            )
            CrashStore(this).save(event)
            Recorder.trip?.crashIds?.add(event.id)
            peakVector = null
            Recorder.pending = null
            Recorder.savedCount++
        }, POST_MS + 200)
    }

    // ── 충격 방향 보정: 평소 가감속으로 '앞쪽'을 학습 ──────────────────────────────

    private fun learnMountDirection(kph: Double) {
        val now = System.currentTimeMillis()
        recentSpeed.addLast(now to kph)
        while (recentSpeed.size > 1 && now - recentSpeed.first().first > 1_000) recentSpeed.removeFirst()
        val (t0, v0) = recentSpeed.first()
        if (now - t0 < 600 || now - lastCalibratedAt < 300) return
        val rate = (kph - v0) * 1000.0 / (now - t0)
        val straight = abs(buffer.latest(SignalKey.STEERING_DEG) ?: 0.0) < 15
        // 충격 순간은 제외 (보정이 아니라 사고)
        if (abs(rate) >= 4 && straight && Recorder.liveImpactG < 1.0) {
            calibration.onLongitudinal(rate)
            lastCalibratedAt = now
            Recorder.calibrationSamples = calibration.samples
        }
    }

    // ── 주행 일지 ─────────────────────────────────────────────────────────

    private fun endTrip() {
        val trip = Recorder.trip ?: return
        Recorder.trip = null
        // 시동만 걸었다 끈 기록은 남기지 않는다
        if (trip.distanceKm < 0.05 && trip.crashIds.isEmpty()) return
        TripStore(this).save(trip.finish())
        Recorder.savedCount++
    }

    private fun onEverySecond() {
        val now = System.currentTimeMillis()
        val kph = buffer.latest(SignalKey.SPEED_KPH) ?: 0.0
        Recorder.trip?.onSecond(kph, now)
        parkedSince?.let { if (now - it >= TRIP_END_PARK_MS) { endTrip(); parkedSince = null } }
        checkSeatBelts(now, kph)
    }

    // ── 안전벨트 안내 ─────────────────────────────────────────────────────

    private fun subscribeSeats() {
        val seat = vehicle.getSeat()
        seat.checkSeatOccupancyCapabilities(
            { areas: List<SeatArea> ->
                areas.forEach { area ->
                    val name = seatName(area)
                    val l = object : SeatOccupancyListener {
                        override fun onSeatOccupancyUpdated(v: Boolean?) {
                            // Vehicle SDK 2.3.2는 VHAL 값 1을 true로 바꾸는데, 표준 SEAT_OCCUPANCY에서 1은 VACANT(빈자리)·2가 OCCUPIED다.
                            // 에뮬레이터 콘솔로 탑승시킨 좌석이 false로 오는 것까지 확인 → SDK 값을 뒤집어 '탑승'으로 쓴다.
                            occupied[name] = v == false
                        }
                        override fun onFailed(e: Exception) = warn("탑승 감지($name): ${e.message}")
                    }
                    seat.registerSeatOccupancy(area, l)
                    seatListeners += { seat.unregisterSeatOccupancy(area, l) }
                }
            },
            { e -> warn("탑승 감지 미지원: ${e.message}") },
        )
        seat.checkSeatBeltBuckledCapabilities(
            { areas: List<SeatArea> ->
                areas.forEach { area ->
                    val name = seatName(area)
                    val l = object : SeatBeltBuckledStateListener {
                        override fun onSeatBeltBuckledStateUpdated(v: Boolean?) { v?.let { buckled[name] = it } }
                        override fun onFailed(e: Exception) = warn("벨트 감지($name): ${e.message}")
                    }
                    seat.registerSeatBeltBuckledState(area, l)
                    seatListeners += { seat.unregisterSeatBeltBuckledState(area, l) }
                }
            },
            { e -> warn("벨트 감지 미지원: ${e.message}") },
        )
    }

    /** 주행 중 탑승했는데 벨트를 안 맨 좌석이 있으면 1분마다 음성 안내하고 기록한다. */
    private fun checkSeatBelts(now: Long, kph: Double) {
        val unbuckled = unbuckledSeats()
        Recorder.unbuckledSeats = unbuckled
        if (kph < BELT_CHECK_MIN_KPH) return
        for (name in unbuckled) {
            if (now - (lastBeltNoticeAt[name] ?: 0L) < BELT_REPEAT_MS) continue
            lastBeltNoticeAt[name] = now
            val notice = BeltNotice(now, name)
            Recorder.trip?.beltNotices?.add(notice)
            Recorder.lastBeltNotice = notice
            say("$name 안전벨트를 매 주세요")
            Log.i(TAG, "벨트 안내: $name")
        }
    }

    private fun unbuckledSeats() = occupied.filter { it.value }.keys.filter { buckled[it] != true }.sorted()

    /** 사고 리포트용: 충돌 시점 벨트 미착용 좌석과 그 전에 안내했는지. */
    private fun beltNotesAt(crashTime: Long): List<String> = unbuckledSeats().map { name ->
        val last = Recorder.trip?.beltNotices?.lastOrNull { it.seat == name && it.t <= crashTime }
        if (last != null) "$name 미착용 — 충돌 %s 전 안내함".format(agoText(crashTime - last.t))
        else "$name 미착용 (안내 전)"
    }

    private fun agoText(ms: Long): String {
        val s = ms / 1000
        return if (s >= 60) "${s / 60}분 ${s % 60}초" else "${s}초"
    }

    private fun seatName(area: SeatArea): String = when (area.values.firstOrNull()) {
        SeatValue.ROW_1_LEFT -> "운전석"
        SeatValue.ROW_1_RIGHT -> "조수석"
        SeatValue.ROW_2_LEFT -> "뒷좌석 왼쪽"
        SeatValue.ROW_2_CENTER -> "뒷좌석 가운데"
        SeatValue.ROW_2_RIGHT -> "뒷좌석 오른쪽"
        else -> "3열 좌석"
    }

    // ── 음성 안내 (Pleos TTS, 기기 내장 모드) ───────────────────────────────

    private fun initTts() {
        runCatching {
            tts = TextToSpeech(this, Mode.ON_DEVICE).also { t ->
                t.initialize(object : OnInitializeListener {
                    override fun onSuccess() {
                        runCatching { t.setLocale(java.util.Locale.KOREA) }
                        ttsReady = true
                    }
                    override fun onFailure(type: ErrorType) = warn("음성 안내 초기화 실패: $type")
                })
            }
        }.onFailure { warn("음성 안내 미지원: ${it.message}") }
    }

    private fun say(text: String) {
        if (ttsReady) runCatching { tts?.speak(text, false) }.onFailure { warn("음성 안내 실패: ${it.message}") }
    }

    private fun startForegroundWithNotice() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "주행 기록", NotificationManager.IMPORTANCE_LOW))
        val notice = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("데이터 블랙박스 기록 중")
            .setOngoing(true)
            .build()
        startForeground(1, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    private fun warn(message: String) {
        Log.w(TAG, message)
        Recorder.lastWarning = message
    }

    private fun onOff(on: Boolean) = if (on) 1.0 else 0.0

    private fun gearCode(gear: VehicleGear?) = when (gear) {
        VehicleGear.GEAR_PARK -> 0.0
        VehicleGear.GEAR_REVERSE -> 1.0
        VehicleGear.GEAR_NEUTRAL -> 2.0
        VehicleGear.GEAR_DRIVE -> 3.0
        else -> 4.0
    }

    companion object {
        private const val TAG = "DataBlackBox"
        private const val CHANNEL = "recorder"
        // 일반 주행·과속방지턱은 1G 미만, 접촉사고도 보통 2~4G 이상
        const val IMPACT_THRESHOLD_G = 2.5
        const val PRE_MS = 30_000L
        const val POST_MS = 10_000L
        private const val CRASH_COOLDOWN_MS = 15_000L
        private const val ACCEL_SAVE_INTERVAL_MS = 50L
        private const val TRIP_END_PARK_MS = 60_000L
        private const val BELT_CHECK_MIN_KPH = 5.0
        private const val BELT_REPEAT_MS = 60_000L
    }
}

data class PendingCrash(val time: Long, val cause: String, val peakG: Double)

/** 서비스와 화면이 공유하는 상태. */
object Recorder {
    // 사고 전 30초 + 후 10초 + 여유
    val buffer = SignalBuffer(windowMs = 60_000)
    @Volatile var running = false
    @Volatile var liveImpactG = 0.0
    @Volatile var pending: PendingCrash? = null
    @Volatile var savedCount = 0
    @Volatile var lastWarning: String? = null
    @Volatile var service: RecorderService? = null
    @Volatile var handover: HandoverTracker? = null
    @Volatile var trip: TripTracker? = null
    @Volatile var calibrationSamples = 0
    @Volatile var unbuckledSeats: List<String> = emptyList()
    @Volatile var lastBeltNotice: BeltNotice? = null
}
