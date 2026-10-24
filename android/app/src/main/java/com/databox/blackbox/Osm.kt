package com.databox.blackbox

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 지도에서 찾은 '구조적 위험 지점'. 실시간 장애물이 아니라 사람이 튀어나올 확률이 높은 고정 지점이다. */
data class MapHazard(val kind: String, val label: String, val lat: Double, val lon: Double)

/** 현재 위치 주변 도로 정보 (OpenStreetMap). */
data class RoadContext(
    val roadClass: String?,       // residential, living_street, service, tertiary, secondary, primary …
    val roadName: String?,
    val maxspeedKph: Int?,
    val hazards: List<MapHazard>,
    val fetchedAt: Long,
) {
    /** 이면도로(골목)로 볼 도로 등급. */
    val isAlleyClass get() = roadClass in setOf("residential", "living_street", "service", "unclassified", "pedestrian")
}

/**
 * OpenStreetMap Overpass 조회. 내비가 꺼져 있어도 도로 등급·제한속도·횡단보도·정류장·학교를 알 수 있다.
 * 같은 50m 격자는 다시 묻지 않고, 전체 요청은 4초에 1번으로 제한한다.
 */
object Osm {
    private const val TAG = "DataBlackBox"
    private const val ENDPOINT = "https://overpass-api.de/api/interpreter"
    private const val MIN_INTERVAL_MS = 4_000L
    private val executor = Executors.newSingleThreadExecutor()
    private val cache = HashMap<String, RoadContext>()
    @Volatile private var lastRequestAt = 0L
    @Volatile private var inFlight = false

    /** 캐시에 있으면 즉시, 없으면 백그라운드로 받아 [onResult]를 부른다. */
    fun lookup(lat: Double, lon: Double, onResult: (RoadContext) -> Unit) {
        val key = "%.4f,%.4f".format(Math.round(lat * 2000) / 2000.0, Math.round(lon * 2000) / 2000.0) // ≈50m 격자
        cache[key]?.let { onResult(it); return }
        val now = System.currentTimeMillis()
        if (inFlight || now - lastRequestAt < MIN_INTERVAL_MS) return
        inFlight = true
        lastRequestAt = now
        executor.execute {
            try {
                val ctx = fetch(lat, lon)
                cache[key] = ctx
                onResult(ctx)
            } catch (e: Exception) {
                Log.w(TAG, "OSM 조회 실패: ${e.message}")
            } finally {
                inFlight = false
            }
        }
    }

    private fun fetch(lat: Double, lon: Double): RoadContext {
        val q = """
            [out:json][timeout:20];
            (
              way(around:14,$lat,$lon)[highway][highway!~"footway|path|steps|cycleway"];
              node(around:70,$lat,$lon)[highway=crossing];
              node(around:120,$lat,$lon)[highway=bus_stop];
              way(around:180,$lat,$lon)[amenity~"school|kindergarten|childcare"];
              node(around:180,$lat,$lon)[amenity~"school|kindergarten|childcare"];
            );
            out tags center;
        """.trimIndent()
        val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 8_000
        conn.readTimeout = 20_000
        conn.doOutput = true
        conn.setRequestProperty("User-Agent", "golmok-guard-prototype/0.1")
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.outputStream.use { it.write(("data=" + URLEncoder.encode(q, "UTF-8")).toByteArray()) }
        val body = conn.inputStream.bufferedReader().readText()
        val elements = JSONObject(body).getJSONArray("elements")

        val roadRank = listOf("living_street", "pedestrian", "service", "residential", "unclassified", "tertiary", "secondary", "primary", "trunk", "motorway")
        var bestRoad: JSONObject? = null
        val hazards = ArrayList<MapHazard>()
        for (i in 0 until elements.length()) {
            val e = elements.getJSONObject(i)
            val tags = e.optJSONObject("tags") ?: continue
            val hw = tags.optString("highway")
            val amenity = tags.optString("amenity")
            val (plat, plon) = when {
                e.has("lat") -> e.getDouble("lat") to e.getDouble("lon")
                e.has("center") -> e.getJSONObject("center").let { it.getDouble("lat") to it.getDouble("lon") }
                else -> 0.0 to 0.0
            }
            when {
                e.getString("type") == "way" && hw.isNotEmpty() && amenity.isEmpty() -> {
                    // 가장 '골목다운' 등급을 현재 도로로 본다 (큰길 옆 골목 진입 시 골목이 우선)
                    if (bestRoad == null || roadRank.indexOf(hw) < roadRank.indexOf(bestRoad!!.optString("highway"))) bestRoad = tags
                }
                hw == "crossing" -> hazards += MapHazard("crossing", "횡단보도", plat, plon)
                hw == "bus_stop" -> hazards += MapHazard("bus_stop", "버스정류장" + tags.optString("name").let { if (it.isEmpty()) "" else " ($it)" }, plat, plon)
                amenity.isNotEmpty() -> hazards += MapHazard("school", when (amenity) { "school" -> "학교"; "kindergarten" -> "유치원"; else -> "어린이집" } + tags.optString("name").let { if (it.isEmpty()) "" else " ($it)" }, plat, plon)
            }
        }
        val maxspeed = bestRoad?.optString("maxspeed")?.filter { it.isDigit() }?.toIntOrNull()
        return RoadContext(
            roadClass = bestRoad?.optString("highway")?.ifEmpty { null },
            roadName = bestRoad?.optString("name")?.ifEmpty { null },
            maxspeedKph = maxspeed,
            hazards = hazards,
            fetchedAt = System.currentTimeMillis(),
        )
    }

    /** 두 좌표 사이 거리(m). */
    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
