package com.databox.blackbox

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 지도에서 찾은 '구조적 위험 지점'. 실시간 장애물이 아니라 사람이 튀어나올 확률이 높은 고정 지점이다. */
data class MapHazard(val kind: String, val label: String, val lat: Double, val lon: Double)

/** 현재 위치 주변 도로 정보 (OpenStreetMap, 서버 캐시 경유). */
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
 * 도로 정보 조회. 차 안에서 외부 지도 서버를 직접 치지 않고 우리 서버(/api/road)를 거친다.
 * 서버가 Overpass 미러 여러 곳을 시도하고 55m 격자로 캐시하므로 응답이 빠르고 끊김이 적다.
 * 같은 격자는 다시 묻지 않고, 전체 요청은 3초에 1번으로 제한한다.
 */
object Osm {
    private const val TAG = "DataBlackBox"
    private const val ENDPOINT = "https://hyundai-contest.vercel.app/api/road"
    private const val MIN_INTERVAL_MS = 3_000L
    private val executor = Executors.newSingleThreadExecutor()
    private val cache = HashMap<String, RoadContext>()
    @Volatile private var lastRequestAt = 0L
    @Volatile private var inFlight = false
    @Volatile var lastError: String? = null; private set

    /** 캐시에 있으면 즉시, 없으면 백그라운드로 받아 [onResult]를 부른다. */
    fun lookup(lat: Double, lon: Double, onResult: (RoadContext) -> Unit) {
        val key = "${Math.round(lat * 2000)}_${Math.round(lon * 2000)}" // ≈55m 격자 (서버와 동일)
        cache[key]?.let { onResult(it); return }
        val now = System.currentTimeMillis()
        if (inFlight || now - lastRequestAt < MIN_INTERVAL_MS) return
        inFlight = true
        lastRequestAt = now
        executor.execute {
            try {
                val ctx = fetch(lat, lon)
                cache[key] = ctx
                lastError = null
                onResult(ctx)
            } catch (e: Exception) {
                lastError = e.message
                Log.w(TAG, "도로 정보 조회 실패: ${e.message}")
            } finally {
                inFlight = false
            }
        }
    }

    private fun fetch(lat: Double, lon: Double): RoadContext {
        val conn = (URL("$ENDPOINT?lat=$lat&lon=$lon").openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "golmok-guard-car/0.1")
        }
        if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
        val j = JSONObject(conn.inputStream.bufferedReader().readText())
        val hz = j.optJSONArray("hazards")
        val hazards = (0 until (hz?.length() ?: 0)).map {
            val h = hz!!.getJSONObject(it)
            MapHazard(h.getString("kind"), h.getString("label"), h.getDouble("lat"), h.getDouble("lon"))
        }
        return RoadContext(
            roadClass = j.optString("roadClass").ifEmpty { null }?.takeIf { it != "null" },
            roadName = j.optString("roadName").ifEmpty { null }?.takeIf { it != "null" },
            maxspeedKph = if (j.isNull("maxspeedKph")) null else j.optInt("maxspeedKph").takeIf { it > 0 },
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
