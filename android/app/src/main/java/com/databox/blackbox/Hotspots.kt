package com.databox.blackbox

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** 여러 차의 급제동이 모인 지점(50m 격자). "사고 날 뻔한 곳"의 통계. */
data class Hotspot(val lat: Double, val lon: Double, val count: Int, val lastAt: Long)

/**
 * 급제동 핫스팟 서버 연동. 올리는 것은 위치·시각·직전 속도·골목 여부뿐이며 차량·사용자 식별 정보는 없다.
 */
object Hotspots {
    private const val TAG = "DataBlackBox"
    private const val BASE = "https://hyundai-contest.vercel.app/api/hotspots"
    private const val FETCH_INTERVAL_MS = 30_000L
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile var nearby: List<Hotspot> = emptyList(); private set
    @Volatile private var lastFetchAt = 0L
    @Volatile var lastError: String? = null; private set

    fun report(lat: Double, lon: Double, kphBefore: Double, inAlley: Boolean) {
        executor.execute {
            runCatching {
                val body = JSONObject().put("lat", lat).put("lon", lon).put("t", System.currentTimeMillis())
                    .put("kph", Math.round(kphBefore)).put("alley", inAlley).toString()
                val conn = (URL(BASE).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; connectTimeout = 8_000; readTimeout = 10_000; doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                conn.outputStream.use { it.write(body.toByteArray()) }
                Log.i(TAG, "급제동 지점 업로드: HTTP ${conn.responseCode}")
                lastFetchAt = 0 // 바로 다시 받아 내 기록도 반영
            }.onFailure { lastError = it.message; Log.w(TAG, "핫스팟 업로드 실패: ${it.message}") }
        }
    }

    fun refresh(lat: Double, lon: Double, radiusM: Int = 400) {
        val now = System.currentTimeMillis()
        if (now - lastFetchAt < FETCH_INTERVAL_MS) return
        lastFetchAt = now
        executor.execute {
            runCatching {
                val conn = (URL("$BASE?lat=$lat&lon=$lon&r=$radiusM").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8_000; readTimeout = 10_000
                }
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                val arr = json.getJSONArray("cells")
                nearby = (0 until arr.length()).map {
                    val c = arr.getJSONObject(it)
                    Hotspot(c.getDouble("lat"), c.getDouble("lon"), c.getInt("count"), c.optLong("last"))
                }
                lastError = null
            }.onFailure { lastError = it.message; Log.w(TAG, "핫스팟 조회 실패: ${it.message}") }
        }
    }
}
