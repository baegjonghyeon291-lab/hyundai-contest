package com.databox.blackbox

/** 기록하는 차량 신호 종류. 값은 모두 숫자로 저장한다(용량 최소화). */
enum class SignalKey {
    SPEED_KPH,      // km/h, 차량 표준 API(PERF_VEHICLE_SPEED) — 계기판과 같은 출처
    GEAR,           // 0=P 1=R 2=N 3=D 4=기타
    TURN_SIGNAL,    // 0=꺼짐 1=왼쪽 2=오른쪽
    BRAKE,          // 0/1
    LOW_BEAM,       // 0/1
    HIGH_BEAM,      // 0/1
    WIPER,          // 0/1
    STEERING_DEG,   // 핸들 각도
    IMPACT_G,       // 중력 1G를 뺀 충격량
    ACCEL_X, ACCEL_Y, ACCEL_Z, // m/s², 충격 방향 추정용
    LAT, LON,
    AIRBAG,         // 0/1
}

data class Sample(val t: Long, val key: SignalKey, val value: Double)

/**
 * 최근 [windowMs] 동안의 신호만 들고 있는 링버퍼.
 * 창 밖으로 밀려난 값도 키별 마지막 값 하나는 남겨서, 구간 시작 시점의 상태를 알 수 있게 한다.
 */
class SignalBuffer(private val windowMs: Long) {
    private val samples = ArrayDeque<Sample>()
    private val lastEvicted = HashMap<SignalKey, Sample>()
    private val latest = HashMap<SignalKey, Sample>()

    @Synchronized
    fun add(key: SignalKey, value: Double, t: Long = System.currentTimeMillis()) {
        val sample = Sample(t, key, value)
        samples.addLast(sample)
        latest[key] = sample
        while (samples.isNotEmpty() && samples.first().t < t - windowMs) {
            val old = samples.removeFirst()
            lastEvicted[old.key] = old
        }
    }

    @Synchronized
    fun latest(key: SignalKey): Double? = latest[key]?.value

    /** [from, to] 구간 샘플 + 구간 시작 직전의 키별 상태. */
    @Synchronized
    fun snapshot(from: Long, to: Long): List<Sample> {
        val before = HashMap(lastEvicted)
        val inRange = ArrayList<Sample>()
        for (s in samples) {
            if (s.t < from) before[s.key] = s else if (s.t <= to) inRange += s
        }
        return before.values.map { it.copy(t = from) } + inRange
    }
}
