package com.shimpyo.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** 화면 공통 색·컴포넌트. 차량 화면(2560×1440, 멀리서 봄)에 맞춰 글씨를 크게 잡는다. */
object Palette {
    val bg = Color.parseColor("#08111F")
    val card = Color.parseColor("#111E31")
    val cardHi = Color.parseColor("#182A44")
    val line = Color.parseColor("#22344F")
    val text = Color.parseColor("#EEF3FA")
    val sub = Color.parseColor("#8A9BB4")
    val accent = Color.parseColor("#3FA9F5")
    val good = Color.parseColor("#4ADE80")
    val warn = Color.parseColor("#FBBF24")
    val bad = Color.parseColor("#F87171")
}

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

fun rounded(color: Int, radius: Float = 28f, stroke: Int? = null) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = radius
    stroke?.let { setStroke(2, it) }
}

fun Context.label(value: String, size: Float = 26f, color: Int = Palette.text, bold: Boolean = false) = TextView(this).apply {
    text = value
    textSize = size
    setTextColor(color)
    if (bold) typeface = Typeface.DEFAULT_BOLD
}

fun Context.card(vararg children: View, pad: Int = 36, color: Int = Palette.card) = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = rounded(color)
    setPadding(pad, pad, pad, pad)
    children.forEach { addView(it) }
}

fun Context.pill(value: String, color: Int) = label(value, 24f, color, bold = true).apply {
    background = rounded(Color.argb(40, Color.red(color), Color.green(color), Color.blue(color)), 60f)
    setPadding(30, 10, 30, 10)
}

fun Context.actionButton(value: String, color: Int = Palette.accent, onClick: () -> Unit) = label(value, 26f, Color.WHITE, bold = true).apply {
    background = rounded(color, 20f)
    gravity = Gravity.CENTER
    setPadding(40, 22, 40, 22)
    setOnClickListener { onClick() }
}

fun Context.ghostButton(value: String, onClick: () -> Unit) = label(value, 24f, Palette.text).apply {
    background = rounded(Palette.cardHi, 20f, Palette.line)
    gravity = Gravity.CENTER
    setPadding(32, 18, 32, 18)
    setOnClickListener { onClick() }
}

/** 큰 숫자 + 작은 제목 타일. */
fun Context.tile(title: String, valueView: TextView) = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = rounded(Palette.cardHi, 22f)
    setPadding(28, 20, 28, 20)
    addView(label(title, 20f, Palette.sub))
    addView(valueView)
}

fun Context.row(vararg children: View, gap: Int = 20, weights: Boolean = true) = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    children.forEachIndexed { i, v ->
        val lp = if (weights) LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        else LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        if (i > 0) lp.leftMargin = gap
        addView(v, lp)
    }
}

fun LinearLayout.space(h: Int) = addView(View(context), LinearLayout.LayoutParams(1, h))
