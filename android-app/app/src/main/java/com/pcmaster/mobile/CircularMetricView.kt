package com.pcmaster.mobile

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.cos
import kotlin.math.sin

class CircularMetricView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var accentColor: Int = Color.parseColor("#00D2FF")
    private var trackColor: Int = Color.parseColor("#141E33")
    private var strokeWidthPx: Float = 16f
    private var showTipDot: Boolean = true
    private var currentProgress: Float = 0f
    private var targetProgress: Float = 0f

    private var animator: ValueAnimator? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val tipDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val tipGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val arcBounds = RectF()

    init {
        val density = context.resources.displayMetrics.density
        strokeWidthPx = 6f * density

        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.CircularMetricView, defStyleAttr, 0)
            accentColor = a.getColor(R.styleable.CircularMetricView_metricAccentColor, accentColor)
            trackColor = a.getColor(R.styleable.CircularMetricView_metricTrackColor, trackColor)
            strokeWidthPx = a.getDimension(R.styleable.CircularMetricView_metricStrokeWidth, strokeWidthPx)
            showTipDot = a.getBoolean(R.styleable.CircularMetricView_metricShowTipDot, true)
            targetProgress = a.getFloat(R.styleable.CircularMetricView_metricProgress, 0f)
            currentProgress = targetProgress
            a.recycle()
        }

        updatePaints()
    }

    private fun updatePaints() {
        trackPaint.color = trackColor
        trackPaint.strokeWidth = strokeWidthPx

        progressPaint.color = accentColor
        progressPaint.strokeWidth = strokeWidthPx

        tipDotPaint.color = Color.WHITE
        tipGlowPaint.color = accentColor
        tipGlowPaint.alpha = 100
    }

    fun setAccentColor(color: Int) {
        accentColor = color
        updatePaints()
        invalidate()
    }

    fun setProgress(progress: Float, animate: Boolean = true) {
        val clamped = progress.coerceIn(0f, 100f)
        if (!animate) {
            animator?.cancel()
            currentProgress = clamped
            targetProgress = clamped
            invalidate()
            return
        }

        if (targetProgress == clamped && animator?.isRunning == true) return
        targetProgress = clamped

        animator?.cancel()
        animator = ValueAnimator.ofFloat(currentProgress, clamped).apply {
            duration = 320L
            interpolator = DecelerateInterpolator()
            addUpdateListener { va ->
                currentProgress = va.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun getProgress(): Float = targetProgress

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val maxStroke = strokeWidthPx + 12f // padding for tip dot
        arcBounds.set(
            paddingLeft + maxStroke / 2f,
            paddingTop + maxStroke / 2f,
            w - paddingRight - maxStroke / 2f,
            h - paddingBottom - maxStroke / 2f
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = arcBounds.centerX()
        val cy = arcBounds.centerY()
        val radius = arcBounds.width() / 2f
        if (radius <= 0) return

        // Draw background circle track
        canvas.drawCircle(cx, cy, radius, trackPaint)

        // Draw progress arc starting from top (-90 degrees)
        if (currentProgress > 0.5f) {
            val sweepAngle = (currentProgress / 100f) * 360f
            canvas.drawArc(arcBounds, -90f, sweepAngle, false, progressPaint)
        }

        if (showTipDot) {
            val sweepAngle = if (currentProgress > 0.5f) (currentProgress / 100f) * 360f else 0f
            val angleRad = Math.toRadians((-90.0 + sweepAngle))
            val tipX = (cx + radius * cos(angleRad)).toFloat()
            val tipY = (cy + radius * sin(angleRad)).toFloat()

            val dotRadius = strokeWidthPx * 0.75f
            val glowRadius = strokeWidthPx * 1.5f

            // Outer glow
            canvas.drawCircle(tipX, tipY, glowRadius, tipGlowPaint)
            // Inner bright dot
            canvas.drawCircle(tipX, tipY, dotRadius, progressPaint)
            // Center white specular
            canvas.drawCircle(tipX, tipY, dotRadius * 0.45f, tipDotPaint)
        }
    }
}
