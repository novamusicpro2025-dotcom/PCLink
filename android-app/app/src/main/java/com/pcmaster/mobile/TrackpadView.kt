package com.pcmaster.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot

class TrackpadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onMouseMove: ((dx: Float, dy: Float) -> Unit)? = null
    var onLeftClick: (() -> Unit)? = null
    var onRightClick: (() -> Unit)? = null
    var onScroll: ((deltaY: Float) -> Unit)? = null

    private var lastX = 0f
    private var lastY = 0f
    private var touchDownTime = 0L
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var isMultiTouch = false
    private var initialPointerCount = 1

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1F2D48")
        strokeWidth = 2f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(6f, 10f), 0f)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4A5F8A")
        textSize = 34f
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        // Draw crosshair guide lines
        canvas.drawLine(w / 2f, 40f, w / 2f, h - 40f, gridPaint)
        canvas.drawLine(40f, h / 2f, w - 40f, h / 2f, gridPaint)

        // Draw subtle helper text
        canvas.drawText("TOUCHPAD SURFACE", w / 2f, h / 2f - 24f, textPaint)
        val subPaint = Paint(textPaint).apply { textSize = 26f; color = Color.parseColor("#324468") }
        canvas.drawText("1-Tap: Left Click • 2-Tap: Right Click • 2-Finger: Scroll", w / 2f, h / 2f + 28f, subPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val pointerCount = event.pointerCount

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownTime = SystemClock.uptimeMillis()
                touchDownX = event.x
                touchDownY = event.y
                lastX = event.x
                lastY = event.y
                isMultiTouch = false
                initialPointerCount = 1
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                isMultiTouch = true
                initialPointerCount = pointerCount
                lastY = (event.getY(0) + event.getY(1)) / 2f
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (pointerCount == 1 && !isMultiTouch) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    // Sensitivity scaling
                    val sensitivity = 1.4f
                    if (abs(dx) > 0.5f || abs(dy) > 0.5f) {
                        onMouseMove?.invoke(dx * sensitivity, dy * sensitivity)
                    }
                    lastX = event.x
                    lastY = event.y
                } else if (pointerCount >= 2) {
                    val currentY = (event.getY(0) + event.getY(1)) / 2f
                    val dy = currentY - lastY
                    if (abs(dy) > 2f) {
                        onScroll?.invoke(dy * 1.5f)
                    }
                    lastY = currentY
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val duration = SystemClock.uptimeMillis() - touchDownTime
                val distance = hypot((event.x - touchDownX).toDouble(), (event.y - touchDownY).toDouble())

                if (duration < 280 && distance < 25) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    if (isMultiTouch || initialPointerCount == 2) {
                        onRightClick?.invoke()
                    } else {
                        onLeftClick?.invoke()
                    }
                }
                isMultiTouch = false
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
