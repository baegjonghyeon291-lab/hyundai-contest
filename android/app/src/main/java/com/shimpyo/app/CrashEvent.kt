package com.shimpyo.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 저장된 사고 1건: 충돌 전후 차량 신호 전체. */
data class CrashEvent(
    val id: String,
    val crashTime: Long,
    val cause: String,          // "충격 감지" / "에어백 전개"
    val peakG: Double,
    val samples: List<Sample>,
)

/** 사고 기록을 앱 내부 저장소에 JSON 파일로 보관한다. 차 밖으로 나가지 않음. */
class CrashStore(context: Context) {
    private val dir = File(context.filesDir, "crashes").apply { mkdirs() }

    fun save(event: CrashEvent) {
        val json = JSONObject()
            .put("id", event.id)
            .put("crashTime", event.crashTime)
            .put("cause", event.cause)
            .put("peakG", event.peakG)
            .put("samples", JSONArray().apply {
                event.samples.forEach { put(JSONArray().put(it.t).put(it.key.name).put(it.value)) }
            })
        File(dir, "${event.id}.json").writeText(json.toString())
    }

    fun list(): List<CrashEvent> =
        dir.listFiles { f -> f.extension == "json" }.orEmpty()
            .mapNotNull { runCatching { load(it) }.getOrNull() }
            .sortedByDescending { it.crashTime }

    fun get(id: String): CrashEvent? = File(dir, "$id.json").takeIf { it.exists() }?.let(::load)

    fun delete(id: String) = File(dir, "$id.json").delete()

    private fun load(file: File): CrashEvent {
        val json = JSONObject(file.readText())
        val arr = json.getJSONArray("samples")
        val samples = (0 until arr.length()).map {
            val s = arr.getJSONArray(it)
            Sample(s.getLong(0), SignalKey.valueOf(s.getString(1)), s.getDouble(2))
        }
        return CrashEvent(
            id = json.getString("id"),
            crashTime = json.getLong("crashTime"),
            cause = json.getString("cause"),
            peakG = json.getDouble("peakG"),
            samples = samples,
        )
    }
}
