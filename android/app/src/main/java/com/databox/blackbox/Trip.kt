package com.databox.blackbox

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** 안전벨트 안내 1건: 언제, 어느 자리에 안내했는지 (택시·카풀 운전자의 면책 근거). */
data class BeltNotice(val t: Long, val seat: String)

/** 주행 1회 기록. 30일 보관 후 자동 삭제. */
data class TripRecord(
    val id: String,
    val start: Long,
    val end: Long,
    val distanceKm: Double,
    val maxSpeedKph: Double,
    val hardBrakes: Int,
    val hardAccels: Int,
    val speed1Hz: List<Double>,
    val path: List<Pair<Double, Double>>,
    val beltNotices: List<BeltNotice>,
    val crashIds: List<String>,
) {
    val durationMin get() = (end - start) / 60_000.0
}

/** 진행 중인 주행: 통계 + 1초 간격 속도 + 벨트 안내 + 사고. */
class TripTracker : DriveStats() {
    val speed1Hz = mutableListOf<Double>()
    val beltNotices = mutableListOf<BeltNotice>()
    val crashIds = mutableListOf<String>()
    private var lastSecond = 0L

    @Synchronized
    fun onSecond(kph: Double, t: Long = System.currentTimeMillis()) {
        if (t - lastSecond < 1_000) return
        lastSecond = t
        speed1Hz += kph
    }

    @Synchronized
    fun finish() = TripRecord(
        id = "trip_$start", start = start, end = System.currentTimeMillis(),
        distanceKm = distanceKm, maxSpeedKph = maxSpeedKph,
        hardBrakes = hardBrakes, hardAccels = hardAccels,
        speed1Hz = speed1Hz.toList(), path = path.toList(),
        beltNotices = beltNotices.toList(), crashIds = crashIds.toList(),
    )
}

/** 주행 일지 저장소: gzip JSON, 30일 지난 기록은 자동 삭제. */
class TripStore(context: Context) {
    private val dir = File(context.filesDir, "trips").apply { mkdirs() }

    fun save(r: TripRecord) {
        val json = JSONObject()
            .put("id", r.id).put("start", r.start).put("end", r.end)
            .put("distanceKm", r.distanceKm).put("maxSpeedKph", r.maxSpeedKph)
            .put("hardBrakes", r.hardBrakes).put("hardAccels", r.hardAccels)
            .put("speed", JSONArray().apply { r.speed1Hz.forEach { put(Math.round(it * 10) / 10.0) } })
            .put("path", JSONArray().apply { r.path.forEach { put(JSONArray().put(it.first).put(it.second)) } })
            .put("belt", JSONArray().apply { r.beltNotices.forEach { put(JSONArray().put(it.t).put(it.seat)) } })
            .put("crashes", JSONArray(r.crashIds))
        GZIPOutputStream(File(dir, "${r.id}.json.gz").outputStream()).bufferedWriter().use { it.write(json.toString()) }
    }

    fun list(): List<TripRecord> = files().mapNotNull { runCatching { load(it) }.getOrNull() }.sortedByDescending { it.start }

    fun get(id: String) = File(dir, "$id.json.gz").takeIf { it.exists() }?.let(::load)

    fun delete(id: String) = File(dir, "$id.json.gz").delete()

    /** 보관 기간이 지난 주행 일지를 지운다. 사고 기록은 직접 지우기 전까지 남긴다. */
    fun purgeOlderThan(days: Int) {
        val cutoff = System.currentTimeMillis() - days * 24L * 3600_000L
        files().filter { f -> f.name.removePrefix("trip_").substringBefore(".").toLongOrNull()?.let { it < cutoff } ?: false }
            .forEach { it.delete() }
    }

    private fun files() = dir.listFiles { f -> f.name.endsWith(".json.gz") }.orEmpty().toList()

    private fun load(f: File): TripRecord {
        val j = JSONObject(GZIPInputStream(f.inputStream()).bufferedReader().use { it.readText() })
        val sp = j.getJSONArray("speed")
        val p = j.getJSONArray("path")
        val b = j.getJSONArray("belt")
        val c = j.getJSONArray("crashes")
        return TripRecord(
            id = j.getString("id"), start = j.getLong("start"), end = j.getLong("end"),
            distanceKm = j.getDouble("distanceKm"), maxSpeedKph = j.getDouble("maxSpeedKph"),
            hardBrakes = j.getInt("hardBrakes"), hardAccels = j.getInt("hardAccels"),
            speed1Hz = (0 until sp.length()).map { sp.getDouble(it) },
            path = (0 until p.length()).map { p.getJSONArray(it).let { a -> a.getDouble(0) to a.getDouble(1) } },
            beltNotices = (0 until b.length()).map { b.getJSONArray(it).let { a -> BeltNotice(a.getLong(0), a.getString(1)) } },
            crashIds = (0 until c.length()).map { c.getString(it) },
        )
    }

    companion object {
        const val RETENTION_DAYS = 30
    }
}
