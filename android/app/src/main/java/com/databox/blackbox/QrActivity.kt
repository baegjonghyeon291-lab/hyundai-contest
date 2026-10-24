package com.databox.blackbox

import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

/** 휴대폰 카메라로 찍으면 리포트가 열리는 QR. 데이터는 링크 안에만 있고 서버로 가지 않는다. */
class QrActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("url") ?: return finish()
        android.util.Log.i("DataBlackBox", "share url (${url.length}자): $url")

        val qr = ImageView(this).apply {
            setImageBitmap(Share.qr(url))
            background = rounded(android.graphics.Color.WHITE, 24f)
            setPadding(24, 24, 24, 24)
        }
        val text = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 0, 0, 0)
            addView(label("휴대폰으로 리포트 받기", 44f, Palette.text, bold = true))
            space(20)
            addView(label("1. 휴대폰 카메라로 QR을 비추세요", 28f, Palette.text))
            addView(label("2. 열린 리포트를 보험사·경찰에 그대로 제출하거나 저장하세요", 28f, Palette.text))
            space(24)
            addView(label("🔒 기록은 이 링크 안에만 담겨 있어 서버에 저장되지 않습니다", 22f, Palette.sub))
            space(40)
            addView(row(ghostButton("← 돌아가기") { finish() }, weights = false))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Palette.bg)
            setPadding(80, 60, 80, 60)
            addView(qr, LinearLayout.LayoutParams(640, 640))
            addView(text, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        setContentView(root)
    }
}
