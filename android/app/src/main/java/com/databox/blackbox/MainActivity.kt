package com.databox.blackbox

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 메인: 골목지킴이 상태 + 사고 직후 대응 + 블랙박스 실시간 + 맡김 모드 + 기록 탭 + 설정. */
class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var settings: Settings

    private lateinit var status: TextView
    private lateinit var beltBanner: TextView
    private lateinit var crashCard: LinearLayout

    // 골목지킴이
    private lateinit var alleyCard: LinearLayout
    private lateinit var modeView: TextView
    private lateinit var alleySpeed: TextView
    private lateinit var alleyTarget: TextView
    private lateinit var alleyLevel: TextView
    private lateinit var reasonsView: TextView
    private lateinit var hazardsView: TextView
    private lateinit var msgView: TextView

    // 블랙박스 실시간
    private lateinit var gear: TextView
    private lateinit var signal: TextView
    private lateinit var brake: TextView
    private lateinit var lights: TextView
    private lateinit var impact: TextView
    private lateinit var calibView: TextView

    private lateinit var handoverBox: LinearLayout
    private lateinit var listBox: LinearLayout
    private lateinit var tabCrash: TextView
    private lateinit var tabHandover: TextView
    private lateinit var tabTrip: TextView
    private var tab = Tab.CRASH
    private var shownCount = -1
    private var handoverShown: HandoverTracker? = null
    private var handoverRendered = false
    private var handoverTitle: TextView? = null
    private var handoverStats: TextView? = null
    private var dismissedCrashId: String? = null

    private enum class Tab { CRASH, HANDOVER, TRIP }

    private val tick = object : Runnable {
        override fun run() {
            renderLive()
            if (Recorder.savedCount != shownCount) { renderList(); renderCrashCard() }
            if (Recorder.handover !== handoverShown || !handoverRendered) renderHandover()
            updateHandoverStats()
            handler.postDelayed(this, 300)
        }
    }

    private var car: android.car.Car? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        setContentView(buildLayout())
        requestPermissionsThenStart()
        watchDrivingRestrictions()
    }

    /** 주행 제한(CarUxRestrictions)이 걸리면 주행용 단순 화면으로 바꾼다. */
    private fun watchDrivingRestrictions() = runCatching {
        val c = android.car.Car.createCar(this)
        car = c
        val m = c.getCarManager(android.car.Car.CAR_UX_RESTRICTION_SERVICE) as android.car.drivingstate.CarUxRestrictionsManager
        val toDrive = { if (!isFinishing) startActivity(Intent(this, DriveActivity::class.java)) }
        if (m.currentCarUxRestrictions?.isRequiresDistractionOptimization == true) toDrive()
        m.registerListener { r -> if (r.isRequiresDistractionOptimization) toDrive() }
    }.onFailure { android.util.Log.w("DataBlackBox", "주행 제한 감시 실패: ${it.message}") }

    override fun onDestroy() { runCatching { car?.disconnect() }; super.onDestroy() }

    override fun onResume() {
        super.onResume()
        renderList(); renderCrashCard()
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    private fun requestPermissionsThenStart() {
        val needed = listOf(
            "android.car.permission.CAR_SPEED",
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
        ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isEmpty()) startRecorder() else requestPermissions(needed.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        startRecorder() // 일부가 거부돼도 가능한 신호로 기록은 시작한다
    }

    private fun startRecorder() {
        if (!Recorder.running) startForegroundService(Intent(this, RecorderService::class.java))
    }

    // ── 실시간 ───────────────────────────────────────────────────────────

    private fun renderLive() {
        val b = Recorder.buffer
        val (text, color) = when {
            Recorder.pending != null -> "● 사고 감지 — 충돌 후 상황 기록 중" to Palette.bad
            Recorder.handover != null -> "● 기록 중 · ${Recorder.handover!!.mode.label} 맡김 모드" to Palette.warn
            Recorder.running -> "● 상시 기록 중" to Palette.good
            else -> "○ 기록 꺼짐" to Palette.sub
        }
        status.text = text
        status.setTextColor(color)

        // 골목지킴이
        val a = Recorder.alleyState
        val kph = b.latest(SignalKey.SPEED_KPH) ?: 0.0
        modeView.text = when {
            a.inAlley && a.level >= 3 -> "⚠ 위험 — 즉시 감속"
            a.inAlley && a.level == 2 -> "골목 모드 · 기준 속도 초과"
            a.inAlley -> "골목 모드 · 서행 중"
            a.enterSoon -> "곧 골목 진입 — 준비하세요"
            else -> "일반 도로"
        }
        val tint = when (a.level) { 3 -> Palette.bad; 2 -> Palette.warn; 1 -> Palette.good; else -> if (a.enterSoon) Palette.warn else Palette.sub }
        modeView.setTextColor(tint)
        alleyCard.background = rounded(if (a.level >= 2) Color.argb(60, Color.red(tint), Color.green(tint), Color.blue(tint)) else Palette.card)
        alleySpeed.text = "%.0f".format(kph)
        alleySpeed.setTextColor(if (a.inAlley && kph > a.targetKph) Palette.bad else Palette.text)
        alleyTarget.text = if (a.inAlley || a.enterSoon) "${a.targetKph}" else "-"
        alleyLevel.text = when (a.level) { 0 -> "-"; 1 -> "안전"; 2 -> "주의"; else -> "위험" }
        reasonsView.text = if (a.reasons.isEmpty()) (a.roadName ?: "도로 정보 수신 중") else a.reasons.joinToString(" · ")
        val hz = buildList {
            a.hazardsAhead.take(3).forEach { add("${it.label} ${it.distanceM}m") }
            if (a.hotspotCount > 0) add("급제동 지점 · 최근 ${a.hotspotCount}건")
        }
        hazardsView.text = if (hz.isEmpty()) "주변 위험 지점 없음" else hz.joinToString("   ")
        hazardsView.setTextColor(if (hz.isEmpty()) Palette.sub else Palette.warn)
        msgView.text = Recorder.lastAlleyMessage?.let { "🔊 $it" } ?: ""

        // 블랙박스
        gear.text = when (b.latest(SignalKey.GEAR)?.toInt()) { 0 -> "P"; 1 -> "R"; 2 -> "N"; 3 -> "D"; else -> "-" }
        signal.text = when (b.latest(SignalKey.TURN_SIGNAL)?.toInt()) { 1 -> "◀ 왼쪽"; 2 -> "오른쪽 ▶"; else -> "꺼짐" }
        val braking = b.latest(SignalKey.BRAKE) == 1.0
        brake.text = if (braking) "밟음" else "-"
        brake.setTextColor(if (braking) Palette.bad else Palette.text)
        lights.text = when {
            b.latest(SignalKey.HIGH_BEAM) == 1.0 -> "상향"
            b.latest(SignalKey.LOW_BEAM) == 1.0 -> "켜짐" + (if (b.latest(SignalKey.AUTO_LIGHTS) == 1.0) " (자동)" else "")
            else -> "꺼짐"
        }
        impact.text = "%.2f G".format(Recorder.liveImpactG)
        impact.setTextColor(if (Recorder.liveImpactG >= RecorderService.IMPACT_THRESHOLD_G) Palette.bad else Palette.text)

        val seats = Recorder.unbuckledSeats
        beltBanner.visibility = if (seats.isEmpty()) View.GONE else View.VISIBLE
        beltBanner.text = "⚠️ 안전벨트 미착용: ${seats.joinToString(", ")} — 주행 중 음성 안내하고 기록합니다"
        val n = Recorder.calibrationSamples
        calibView.text = if (n >= 15) "충격 방향 보정 완료" else "충격 방향 보정 중 ($n/15)"
        calibView.setTextColor(if (n >= 15) Palette.good else Palette.sub)
    }

    // ── 사고 직후 카드 ───────────────────────────────────────────────────

    private fun renderCrashCard() {
        crashCard.removeAllViews()
        val latest = CrashStore(this).list().firstOrNull()
        val recent = latest != null && System.currentTimeMillis() - latest.crashTime < 30 * 60_000 && latest.id != dismissedCrashId
        crashCard.visibility = if (recent) View.VISIBLE else View.GONE
        if (!recent || latest == null) return
        val r = CrashReport(latest)
        crashCard.addView(label("🚨 사고가 감지되었습니다 · ${r.crashTimeText}", 30f, Palette.bad, bold = true))
        crashCard.addView(label("차량 기록이 저장됐습니다. 다치신 곳은 없는지 먼저 확인하세요.", 22f, Palette.text))
        crashCard.space(16)
        crashCard.addView(Emergency.buttons(this, r))
        crashCard.space(12)
        crashCard.addView(row(
            actionButton("리포트 보기") { startActivity(Intent(this, ReportActivity::class.java).putExtra("id", latest.id)) },
            actionButton("📱 휴대폰으로 받기") { startActivity(Intent(this, QrActivity::class.java).putExtra("url", Share.crashUrl(r))) },
            ghostButton("닫기") { dismissedCrashId = latest.id; renderCrashCard() },
            gap = 14, weights = false,
        ))
    }

    // ── 맡김 모드 ─────────────────────────────────────────────────────────

    private fun renderHandover() {
        val h = Recorder.handover
        handoverShown = h
        handoverRendered = true
        handoverBox.removeAllViews()
        if (h == null) {
            handoverBox.addView(label("차를 남에게 맡길 때 켜 두세요", 22f, Palette.sub))
            handoverBox.space(16)
            HandoverMode.entries.chunked(2).forEachIndexed { i, pair ->
                if (i > 0) handoverBox.space(14)
                handoverBox.addView(row(*pair.map { mode ->
                    ghostButton("${mode.icon} ${mode.label}") { Recorder.service?.startHandover(mode) }
                }.toTypedArray(), gap = 14))
            }
        } else {
            handoverTitle = label("", 28f, Palette.warn, bold = true)
            handoverStats = label("", 24f, Palette.text)
            handoverBox.addView(handoverTitle)
            handoverBox.addView(handoverStats)
            updateHandoverStats()
            handoverBox.space(16)
            handoverBox.addView(row(actionButton("돌려받기 · 리포트 보기", Palette.warn) {
                Recorder.service?.finishHandover { record ->
                    startActivity(Intent(this, HandoverActivity::class.java).putExtra("id", record.id))
                }
            }, weights = false))
        }
    }

    private fun updateHandoverStats() {
        val h = Recorder.handover ?: return
        val title = handoverTitle ?: return
        val min = (System.currentTimeMillis() - h.start) / 60_000
        title.text = "${h.mode.icon} ${h.mode.label}에 맡김 · ${min}분 경과"
        handoverStats?.text = "주행 %.2f km · 최고 %.0f km/h · 급제동 %d · 급가속 %d".format(h.distanceKm, h.maxSpeedKph, h.hardBrakes, h.hardAccels)
    }

    // ── 기록 목록 ─────────────────────────────────────────────────────────

    private fun renderList() {
        shownCount = Recorder.savedCount
        tabCrash.background = if (tab == Tab.CRASH) rounded(Palette.cardHi, 18f) else null
        tabHandover.background = if (tab == Tab.HANDOVER) rounded(Palette.cardHi, 18f) else null
        tabTrip.background = if (tab == Tab.TRIP) rounded(Palette.cardHi, 18f) else null
        listBox.removeAllViews()
        val fmt = SimpleDateFormat("MM.dd HH:mm:ss", Locale.KOREA)
        when (tab) {
            Tab.TRIP -> {
                val trips = TripStore(this).list()
                if (trips.isEmpty()) listBox.addView(label("주행 일지가 없습니다. 주행하면 자동으로 쌓이고 ${TripStore.RETENTION_DAYS}일 보관됩니다.", 22f, Palette.sub))
                trips.forEach { t ->
                    listBox.addView(listItem(
                        "🛣 ${fmt.format(Date(t.start))}",
                        "%.1f km · %.0f분 · 최고 %.0f km/h".format(t.distanceKm, t.durationMin, t.maxSpeedKph) +
                            (if (t.beltNotices.isNotEmpty()) " · 벨트 안내 ${t.beltNotices.size}회" else "") +
                            (if (t.crashIds.isNotEmpty()) " · 💥 사고 ${t.crashIds.size}건" else ""),
                    ) { startActivity(Intent(this, TripActivity::class.java).putExtra("id", t.id)) })
                }
            }
            Tab.CRASH -> {
                val events = CrashStore(this).list()
                if (events.isEmpty()) listBox.addView(label("사고 기록이 없습니다. 충격이 감지되면 자동으로 저장됩니다.", 22f, Palette.sub))
                events.forEach { e ->
                    val r = CrashReport(e)
                    listBox.addView(listItem(
                        "💥 ${fmt.format(Date(e.crashTime))}",
                        "${e.cause} %.1fG · ${r.speedAtCrash?.let { "%.0f km/h".format(it) } ?: "-"}".format(e.peakG) + (e.direction?.let { " · ${it.label}" } ?: ""),
                    ) { startActivity(Intent(this, ReportActivity::class.java).putExtra("id", e.id)) })
                }
            }
            Tab.HANDOVER -> {
                val records = HandoverStore(this).list()
                if (records.isEmpty()) listBox.addView(label("맡김 기록이 없습니다.", 22f, Palette.sub))
                records.forEach { r ->
                    listBox.addView(listItem(
                        "${r.mode.icon} ${r.mode.label} · ${fmt.format(Date(r.start))}",
                        "%.1f km · %.0f분%s".format(r.distanceKm, r.durationMin, if (r.suspiciousDistance) " · ⚠️ 이상 운행" else ""),
                    ) { startActivity(Intent(this, HandoverActivity::class.java).putExtra("id", r.id)) })
                }
            }
        }
    }

    private fun listItem(title: String, sub: String, onClick: () -> Unit) = card(
        label(title, 26f, Palette.text, bold = true), label(sub, 22f, Palette.sub), pad = 26, color = Palette.cardHi,
    ).apply {
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
    }

    // ── 설정 ─────────────────────────────────────────────────────────────

    private fun settingsCard(): LinearLayout {
        val phone = EditText(this).apply {
            hint = "보험사 사고접수 번호 (예: 1588-0000)"
            setText(settings.insurerPhone)
            inputType = InputType.TYPE_CLASS_PHONE
            textSize = 24f
            setTextColor(Palette.text)
            setHintTextColor(Palette.sub)
        }
        fun toggle(title: String, get: () -> Boolean, set: (Boolean) -> Unit): TextView {
            lateinit var v: TextView
            v = ghostButton("") { set(!get()); v.text = "$title  ${if (get()) "켜짐" else "꺼짐"}" }
            v.text = "$title  ${if (get()) "켜짐" else "꺼짐"}"
            return v
        }
        return card(
            label("설정", 22f, Palette.sub),
            LinearLayout(this).apply { space(10) },
            row(phone, actionButton("저장") { settings.insurerPhone = phone.text.toString(); phone.clearFocus() }, gap = 14, weights = false),
            LinearLayout(this).apply { space(16) },
            label("골목 진입 시 자동 제어 (벗어나면 원래대로)", 20f, Palette.sub),
            LinearLayout(this).apply { space(8) },
            row(
                toggle("전조등·안개등", { settings.autoLights }, { settings.autoLights = it }),
                toggle("창문 살짝", { settings.autoWindow }, { settings.autoWindow = it }),
                gap = 12,
            ),
            LinearLayout(this).apply { space(12) },
            row(
                toggle("볼륨 낮춤", { settings.autoVolume }, { settings.autoVolume = it }),
                toggle("급제동 익명 공유", { settings.shareHardBrakes }, { settings.shareHardBrakes = it }),
                gap = 12,
            ),
        )
    }

    // ── 레이아웃 ─────────────────────────────────────────────────────────

    private fun buildLayout(): ScrollView {
        fun big() = label("-", 40f, Palette.text, bold = true)
        status = label("", 24f, Palette.good, bold = true)
        beltBanner = label("", 26f, Palette.warn, bold = true).apply {
            background = rounded(Color.argb(40, 251, 191, 36), 18f); setPadding(28, 16, 28, 16); visibility = View.GONE
        }
        crashCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.argb(50, 248, 113, 113), 28f, Palette.bad)
            setPadding(36, 30, 36, 30)
            visibility = View.GONE
        }

        modeView = label("일반 도로", 36f, Palette.sub, bold = true)
        alleySpeed = big(); alleyTarget = big(); alleyLevel = big()
        reasonsView = label("", 22f, Palette.text)
        hazardsView = label("", 24f, Palette.sub)
        msgView = label("", 22f, Palette.accent)
        alleyCard = card(
            label("골목지킴이", 22f, Palette.sub),
            modeView,
            LinearLayout(this).apply { space(14) },
            row(tile("현재 속도 km/h", alleySpeed), tile("기준 속도", alleyTarget), tile("단계", alleyLevel)),
            LinearLayout(this).apply { space(14) },
            reasonsView, hazardsView,
            LinearLayout(this).apply { space(6) },
            msgView,
        )

        gear = big(); signal = big(); brake = big(); lights = big(); impact = big()
        calibView = label("", 20f, Palette.sub)
        val live = card(
            label("데이터 블랙박스 · 실시간", 22f, Palette.sub),
            LinearLayout(this).apply { space(12) },
            row(tile("기어", gear), tile("방향지시등", signal), tile("브레이크", brake)),
            LinearLayout(this).apply { space(12) },
            row(tile("전조등", lights), tile("충격 (기준 2.5G)", impact)),
            LinearLayout(this).apply { space(10) },
            calibView,
        )

        handoverBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val handover = card(label("맡김 모드", 22f, Palette.sub), handoverBox)

        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun tabLabel(text: String, t: Tab) = label(text, 26f, Palette.text, bold = true).apply {
            setPadding(30, 12, 30, 12); setOnClickListener { tab = t; renderList() }
        }
        tabCrash = tabLabel("사고 기록", Tab.CRASH)
        tabHandover = tabLabel("맡김 기록", Tab.HANDOVER)
        tabTrip = tabLabel("주행 일지", Tab.TRIP)
        val records = card(row(tabCrash, tabHandover, tabTrip, gap = 8, weights = false), LinearLayout(this).apply { space(16) }, listBox)

        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(alleyCard); space(24); addView(live); space(24); addView(handover)
        }
        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(records); space(24); addView(settingsCard())
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label("골목지킴이", 44f, Palette.text, bold = true))
            addView(label("골목에서 사람이 튀어나오기 전에 — 서행 안내 · 구조적 위험 지점 · 데이터 기록", 22f, Palette.sub))
            space(8); addView(status); space(10); addView(beltBanner); space(10); addView(crashCard)
        }
        val columns = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.15f))
            addView(right, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = 28 })
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 44, 56, 44)
            addView(header); space(24); addView(columns)
        }
        return ScrollView(this).apply { setBackgroundColor(Palette.bg); addView(root) }
    }
}
