package com.byd.carcontrol

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/** Simple top-down five-seat cabin view; state 1=locked, 2=unlocked, otherwise unknown. */
class SeatbeltStatusView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val states = mutableMapOf<String, Int>()
    private val slots = listOf(
        Triple("SAFETY_BELT_AREA_MAIN", "MOTORISTA", 0),
        Triple("SAFETY_BELT_AREA_DEPUTY", "PASSAGEIRO", 1),
        Triple("SAFETY_BELT_AREA_SECOND_ROW_SEAT_LEFT", "TRÁS ESQ.", 2),
        Triple("SAFETY_BELT_AREA_SECOND_ROW_SEAT_MID", "TRÁS MEIO", 3),
        Triple("SAFETY_BELT_AREA_SECOND_ROW_SEAT_RIGHT", "TRÁS DIR.", 4)
    )

    fun setSeatStates(values: Map<String, Int>) {
        states.clear(); states.putAll(values); invalidate()
        contentDescription = slots.joinToString(". ") { (key, label) ->
            "$label: ${when (states[key]) { 1 -> "afivelado"; 2 -> "desafivelado"; else -> "estado indisponível" }}"
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        paint.color = 0xff334155.toInt(); paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f
        canvas.drawRoundRect(RectF(w * .06f, h * .05f, w * .94f, h * .95f), 32f, 32f, paint)
        paint.style = Paint.Style.FILL; paint.textAlign = Paint.Align.CENTER
        slots.forEach { (key, label, index) ->
            val x = when (index) { 0, 2 -> w * .28f; 1, 4 -> w * .72f; else -> w * .50f }
            val y = if (index < 2) h * .34f else h * .70f
            val color = when (states[key]) { 1 -> 0xff16a34a.toInt(); 2 -> 0xffdc2626.toInt(); else -> 0xff64748b.toInt() }
            paint.color = color
            canvas.drawRoundRect(RectF(x - w * .115f, y - 25f, x + w * .115f, y + 25f), 16f, 16f, paint)
            paint.color = 0xffffffff.toInt(); paint.textSize = 22f
            canvas.drawText(label, x, y + 7f, paint)
        }
        paint.color = 0xff94a3b8.toInt(); paint.textSize = 20f
        canvas.drawText("FRENTE", w * .5f, h * .12f, paint)
        canvas.drawText("TRASEIRA", w * .5f, h * .91f, paint)
    }
}
