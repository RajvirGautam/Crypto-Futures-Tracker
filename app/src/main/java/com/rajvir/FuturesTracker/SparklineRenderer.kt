package com.rajvir.FuturesTracker

import android.graphics.*
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object SparklineRenderer {

    /**
     * Renders a sparkline bitmap with:
     *  • gradient fill under the line
     *  • smooth line
     *  • glowing dot at the current (rightmost) data point
     */
    fun render(
        data: List<Float>,
        positive: Boolean,
        width: Int = 500,
        height: Int = 200,
        plotStepMs: Long = 1_000L
    ): Bitmap {
        val safeWidth = width.coerceAtLeast(220)
        val safeHeight = height.coerceAtLeast(120)
        val dotRadius = (safeHeight * 0.04f).coerceIn(6f, 18f)
        val glowRadius = dotRadius * 1.9f
        val lineWidth = (safeHeight * 0.012f).coerceIn(2.5f, 7f)
        val chartLeft = (safeWidth * 0.11f).coerceAtLeast(44f)
        val chartRight = safeWidth - (safeWidth * 0.016f).coerceAtLeast(8f)
        val chartTop = glowRadius + 2f
        val chartBottom = safeHeight - (safeHeight * 0.12f).coerceAtLeast(18f)
        val axisTextBaseline = safeHeight - (safeHeight * 0.03f).coerceAtLeast(5f)
        val us = DecimalFormatSymbols(Locale.US)
        val axisFmt = DecimalFormat("#,##0.##", us)

        val bmp = Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val lineColor    = if (positive) Color.parseColor("#10B981") else Color.parseColor("#EF4444")
        val gradTopColor = if (positive) Color.parseColor("#4010B981") else Color.parseColor("#40EF4444")
        val gridColor = Color.parseColor("#2FFFFFFF")
        val axisTextColor = Color.parseColor("#A0FFFFFF")

        val max = data.maxOrNull() ?: 1f
        val min = data.minOrNull() ?: 0f
        val range = (max - min).coerceAtLeast(0.01f)

        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = gridColor
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = axisTextColor
            textSize = (safeHeight * 0.08f).coerceIn(12f, 30f)
            style = Paint.Style.FILL
        }

        val yFractions = listOf(0f, 1f / 3f, 2f / 3f, 1f)
        yFractions.forEach { f ->
            val y = chartTop + (chartBottom - chartTop) * f
            canvas.drawLine(chartLeft, y, chartRight, y, gridPaint)

            val value = max - range * f
            val label = axisFmt.format(value)
            canvas.drawText(label, 4f, y + axisPaint.textSize * 0.33f, axisPaint)
        }

        val xFractions = listOf(0f, 1f / 3f, 2f / 3f, 1f)
        val totalSpanMs = plotStepMs.coerceAtLeast(1_000L) * (data.size - 1).coerceAtLeast(1).toLong()
        val xLabels = xFractions.map { fraction ->
            val elapsedMs = ((1f - fraction) * totalSpanMs).toLong()
            if (elapsedMs <= 0L) "Now" else "-${formatElapsed(elapsedMs)}"
        }
        xFractions.forEachIndexed { i, f ->
            val x = chartLeft + (chartRight - chartLeft) * f
            canvas.drawLine(x, chartTop, x, chartBottom, gridPaint)

            val label = xLabels[i]
            val w = axisPaint.measureText(label)
            canvas.drawText(label, x - (w / 2f), axisTextBaseline, axisPaint)
        }

        // Map data → pixel coordinates
        val points = data.mapIndexed { i, v ->
            val x = if (data.size <= 1) (chartLeft + chartRight) / 2f
            else chartLeft + i * ((chartRight - chartLeft) / (data.size - 1f))
            val y = chartTop + (chartBottom - chartTop) * (1f - (v - min) / range)
            x to y
        }

        // Build line path
        val linePath = Path()
        points.forEachIndexed { i, (x, y) ->
            if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
        }

        // ── Gradient fill under the line ──
        if (points.size >= 2) {
            val fillPath = Path(linePath)
            fillPath.lineTo(points.last().first, chartBottom)
            fillPath.lineTo(points.first().first, chartBottom)
            fillPath.close()

            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                shader = LinearGradient(
                    0f, chartTop, 0f, chartBottom,
                    gradTopColor, Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawPath(fillPath, fillPaint)
        }

        // ── Line ──
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = lineColor
            strokeWidth = lineWidth
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawPath(linePath, linePaint)

        // ── Endpoint dot with soft glow ──
        if (points.isNotEmpty()) {
            val (ex, ey) = points.last()
            // Outer glow
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                shader = RadialGradient(
                    ex, ey, glowRadius,
                    Color.argb(80, Color.red(lineColor), Color.green(lineColor), Color.blue(lineColor)),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(ex, ey, glowRadius, glowPaint)
            // Solid dot
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = lineColor
                style = Paint.Style.FILL
            }
            canvas.drawCircle(ex, ey, dotRadius, dotPaint)
        }

        return bmp
    }

    private fun formatElapsed(ms: Long): String {
        val totalSeconds = (ms / 1_000L).coerceAtLeast(0L)
        return when {
            totalSeconds < 60L -> "${totalSeconds}s"
            totalSeconds < 3_600L -> "${totalSeconds / 60L}m"
            totalSeconds < 86_400L -> "${totalSeconds / 3_600L}h"
            else -> "${totalSeconds / 86_400L}d"
        }
    }
}