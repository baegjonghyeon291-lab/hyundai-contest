package com.databox.blackbox

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater

/**
 * 리포트를 휴대폰으로 넘기는 링크.
 * 데이터는 URL의 # 뒤(fragment)에만 담겨서 웹 서버로 전송되지 않고, 휴대폰 브라우저 안에서만 풀린다.
 */
object Share {
    private const val VIEWER = "https://hyundai-contest.vercel.app/#r="
    // QR 한 장에 안정적으로 들어가는 크기
    private const val MAX_URL_CHARS = 2200

    fun crashUrl(report: CrashReport): String {
        val e = report.event
        fun json(maxPath: Int) = JSONObject()
            .put("k", "c").put("t", e.crashTime).put("c", e.cause).put("g", round1(e.peakG))
            .put("f", JSONArray().apply {
                report.lines().forEach { (title, value, fav) ->
                    put(JSONArray().put(title).put(value).put(when (fav) { true -> 1; false -> -1; null -> 0 }))
                }
            })
            // 0.5초 간격 속도 (충돌 전 30초 ~ 후 10초)
            .put("sp", JSONArray().apply {
                for (i in -60..20) put(round1(report.valueAt(SignalKey.SPEED_KPH, e.crashTime + i * 500L) ?: 0.0))
            })
            .put("br", intervals(report, SignalKey.BRAKE))
            .put("ts", intervals(report, SignalKey.TURN_SIGNAL))
            .apply { if (report.lat != null && report.lon != null) put("loc", JSONArray().put(round5(report.lat)).put(round5(report.lon))) }
            .put("path", pathJson(report.path(), maxPath))
        return fit { json(it) }
    }

    fun handoverUrl(r: HandoverRecord): String {
        fun json(maxPath: Int) = JSONObject()
            .put("k", "h").put("m", r.mode.label).put("s", r.start).put("e", r.end)
            .put("d", round1(r.distanceKm * 10) / 10).put("mx", round1(r.maxSpeedKph))
            .put("hb", r.hardBrakes).put("ha", r.hardAccels).put("sus", r.suspiciousDistance)
            .put("why", JSONArray(r.suspicionReasons()))
            .put("ch", JSONArray().apply { r.changes().forEach { (k, v) -> put(JSONArray().put(k).put(v)) } })
            .put("path", pathJson(r.path, maxPath))
        return fit { json(it) }
    }

    /** 경로 점 수를 줄여 가며 QR 한 장에 들어가게 맞춘다. */
    private fun fit(build: (maxPath: Int) -> JSONObject): String {
        for (maxPath in listOf(80, 40, 20, 10, 0)) {
            val url = VIEWER + encode(build(maxPath).toString())
            if (url.length <= MAX_URL_CHARS) return url
        }
        return VIEWER + encode(build(0).toString())
    }

    private fun encode(text: String): String {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true) // raw deflate ↔ 브라우저 'deflate-raw'
        deflater.setInput(text.toByteArray(Charsets.UTF_8))
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf))
        return Base64.encodeToString(out.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /** 0.1초 단위 상대 시각의 켜짐 구간: [[시작, 끝, 값], ...] */
    private fun intervals(report: CrashReport, key: SignalKey) = JSONArray().apply {
        val t0 = report.event.crashTime
        fun rel(t: Long) = ((t - t0) / 100).toInt()
        var since: Long? = null
        var v = 0.0
        for (s in report.series(key)) {
            if (s.value != 0.0 && since == null) { since = s.t; v = s.value }
            if (s.value == 0.0 && since != null) { put(JSONArray().put(rel(since)).put(rel(s.t)).put(v.toInt())); since = null }
        }
        since?.let { put(JSONArray().put(rel(it)).put(rel(t0 + RecorderService.POST_MS)).put(v.toInt())) }
    }

    private fun pathJson(path: List<Pair<Double, Double>>, max: Int) = JSONArray().apply {
        if (max == 0 || path.isEmpty()) return@apply
        val step = maxOf(1, path.size / max)
        path.filterIndexed { i, _ -> i % step == 0 || i == path.lastIndex }
            .forEach { put(JSONArray().put(round5(it.first)).put(round5(it.second))) }
    }

    private fun round1(v: Double) = Math.round(v * 10) / 10.0
    private fun round5(v: Double) = Math.round(v * 100_000) / 100_000.0

    fun qr(text: String, size: Int = 720): Bitmap {
        val matrix = QRCodeWriter().encode(
            text, BarcodeFormat.QR_CODE, size, size,
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L, EncodeHintType.MARGIN to 2),
        )
        val pixels = IntArray(size * size) { if (matrix[it % size, it / size]) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565)
    }
}
