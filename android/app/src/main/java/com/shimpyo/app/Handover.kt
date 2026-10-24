package com.shimpyo.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 맡김 모드 종류. */
enum class HandoverMode(val label: String, val icon: String) {
    VALET("발레파킹", "🅿️"),
    SERVICE("정비소", "🔧"),
    DRIVER("대리운전", "🚗"),
    FAMILY("가족·지인", "👪"),
}

/** 차량 설정 스냅샷 (맡길 때 vs 돌려받을 때 비교용). */
data class CarSettings(
    val seatForeAft: Int? = null,
    val seatBackrest: Int? = null,
    val seatHeight: Int? = null,
    val hvacTempC: Float? = null,
)

/** 맡김 기록 1건. */
data class HandoverRecord(
    val id: String,
    val mode: HandoverMode,
    val start: Long,
    val end: Long,
    val distanceKm: Double,
    val maxSpeedKph: Double,
    val hardBrakes: Int,
    val hardAccels: Int,
    val before: CarSettings,
    val after: CarSettings,
    val path: List<Pair<Double, Double>>,
) {
    val durationMin get() = (end - start) / 60_000.0

    /** 바뀐 설정 목록: (항목, "전 → 후"). */
    fun changes(): List<Pair<String, String>> = buildList {
        fun <T> diff(name: String, a: T?, b: T?, fmt: (T) -> String) {
            if (a != null && b != null && a != b) add(name to "${fmt(a)} → ${fmt(b)}")
        }
        diff("운전석 앞뒤 위치", before.seatForeAft, after.seatForeAft) { "$it" }
        diff("운전석 등받이 각도", before.seatBackrest, after.seatBackrest) { "$it" }
        diff("운전석 높이", before.seatHeight, after.seatHeight) { "$it" }
        diff("에어컨 온도", before.hvacTempC, after.hvacTempC) { "%.1f℃".format(it) }
    }

    /** 맡긴 용도에 비해 주행 거리가 의심스러운지. */
    val suspiciousDistance: Boolean
        get() = when (mode) {
            HandoverMode.VALET -> distanceKm > 2.0
            HandoverMode.SERVICE -> distanceKm > 5.0
            else -> false
        }
}

/**
 * 맡긴 동안 실시간 누적: 차속을 시간으로 적분해 거리 계산, 1초 속도 변화로 급가속·급제동 판단.
 */
class HandoverTracker(val mode: HandoverMode, val before: CarSettings) {
    val start = System.currentTimeMillis()
    var distanceKm = 0.0; private set
    var maxSpeedKph = 0.0; private set
    var hardBrakes = 0; private set
    var hardAccels = 0; private set
    val path = mutableListOf<Pair<Double, Double>>()

    private var lastT = 0L
    private var lastKph = 0.0
    private val recent = ArrayDeque<Pair<Long, Double>>()
    private var lastHardEventAt = 0L

    @Synchronized
    fun onSpeed(kph: Double, t: Long = System.currentTimeMillis()) {
        if (lastT != 0L) distanceKm += lastKph * (t - lastT) / 3_600_000.0
        lastT = t
        lastKph = kph
        if (kph > maxSpeedKph) maxSpeedKph = kph

        recent.addLast(t to kph)
        while (recent.size > 1 && t - recent.first().first > 1_000) recent.removeFirst()
        val (t0, v0) = recent.first()
        if (t - t0 >= 700 && t - lastHardEventAt > HARD_EVENT_COOLDOWN_MS) {
            val perSec = (kph - v0) * 1000.0 / (t - t0)
            if (perSec <= -HARD_KPH_PER_SEC) { hardBrakes++; lastHardEventAt = t }
            if (perSec >= HARD_KPH_PER_SEC) { hardAccels++; lastHardEventAt = t }
        }
    }

    @Synchronized
    fun onLocation(lat: Double, lon: Double) {
        val last = path.lastOrNull()
        // 약 20m 이상 움직였을 때만 경로 점 추가
        if (last == null || kotlin.math.abs(last.first - lat) + kotlin.math.abs(last.second - lon) > 0.0002) path += lat to lon
    }

    @Synchronized
    fun finish(after: CarSettings) = HandoverRecord(
        id = "handover_$start",
        mode = mode, start = start, end = System.currentTimeMillis(),
        distanceKm = distanceKm, maxSpeedKph = maxSpeedKph,
        hardBrakes = hardBrakes, hardAccels = hardAccels,
        before = before, after = after, path = path.toList(),
    )

    companion object {
        // 1초에 12km/h 이상 변화 ≈ 0.34G: 일반적인 급가속·급제동 기준
        const val HARD_KPH_PER_SEC = 12.0
        private const val HARD_EVENT_COOLDOWN_MS = 3_000L
    }
}

class HandoverStore(context: Context) {
    private val dir = File(context.filesDir, "handovers").apply { mkdirs() }

    fun save(r: HandoverRecord) {
        val json = JSONObject()
            .put("id", r.id).put("mode", r.mode.name).put("start", r.start).put("end", r.end)
            .put("distanceKm", r.distanceKm).put("maxSpeedKph", r.maxSpeedKph)
            .put("hardBrakes", r.hardBrakes).put("hardAccels", r.hardAccels)
            .put("before", settingsJson(r.before)).put("after", settingsJson(r.after))
            .put("path", JSONArray().apply { r.path.forEach { put(JSONArray().put(it.first).put(it.second)) } })
        File(dir, "${r.id}.json").writeText(json.toString())
    }

    fun list(): List<HandoverRecord> = dir.listFiles { f -> f.extension == "json" }.orEmpty()
        .mapNotNull { runCatching { load(it) }.getOrNull() }.sortedByDescending { it.start }

    fun get(id: String) = File(dir, "$id.json").takeIf { it.exists() }?.let(::load)

    fun delete(id: String) = File(dir, "$id.json").delete()

    private fun load(f: File): HandoverRecord {
        val j = JSONObject(f.readText())
        val p = j.getJSONArray("path")
        return HandoverRecord(
            id = j.getString("id"), mode = HandoverMode.valueOf(j.getString("mode")),
            start = j.getLong("start"), end = j.getLong("end"),
            distanceKm = j.getDouble("distanceKm"), maxSpeedKph = j.getDouble("maxSpeedKph"),
            hardBrakes = j.getInt("hardBrakes"), hardAccels = j.getInt("hardAccels"),
            before = settings(j.getJSONObject("before")), after = settings(j.getJSONObject("after")),
            path = (0 until p.length()).map { p.getJSONArray(it).let { a -> a.getDouble(0) to a.getDouble(1) } },
        )
    }

    private fun settingsJson(s: CarSettings) = JSONObject().apply {
        s.seatForeAft?.let { put("seatForeAft", it) }
        s.seatBackrest?.let { put("seatBackrest", it) }
        s.seatHeight?.let { put("seatHeight", it) }
        s.hvacTempC?.let { put("hvacTempC", it.toDouble()) }
    }

    private fun settings(j: JSONObject) = CarSettings(
        seatForeAft = j.optIntOrNull("seatForeAft"),
        seatBackrest = j.optIntOrNull("seatBackrest"),
        seatHeight = j.optIntOrNull("seatHeight"),
        hvacTempC = if (j.has("hvacTempC")) j.getDouble("hvacTempC").toFloat() else null,
    )

    private fun JSONObject.optIntOrNull(key: String) = if (has(key)) getInt(key) else null
}
