package com.databox.blackbox

import android.content.Context

/** 사용자 설정. 자동 제어는 끌 수 있어야 심사(운전자 주의분산 가이드)와 실사용 모두 통과한다. */
class Settings(context: Context) {
    private val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var insurerPhone: String
        get() = p.getString("insurerPhone", "") ?: ""
        set(v) = p.edit().putString("insurerPhone", v).apply()

    var autoLights: Boolean       // 골목 진입 시 전조등·안개등 자동 점등
        get() = p.getBoolean("autoLights", true)
        set(v) = p.edit().putBoolean("autoLights", v).apply()

    var autoWindow: Boolean       // 골목 진입 시 운전석 창문 살짝 열어 바깥 소리 듣기
        get() = p.getBoolean("autoWindow", true)
        set(v) = p.edit().putBoolean("autoWindow", v).apply()

    var autoVolume: Boolean       // 골목 진입 시 미디어 볼륨 낮춤
        get() = p.getBoolean("autoVolume", true)
        set(v) = p.edit().putBoolean("autoVolume", v).apply()

    var shareHardBrakes: Boolean  // 급제동 지점 익명 공유 (핫스팟 지도)
        get() = p.getBoolean("shareHardBrakes", true)
        set(v) = p.edit().putBoolean("shareHardBrakes", v).apply()
}
